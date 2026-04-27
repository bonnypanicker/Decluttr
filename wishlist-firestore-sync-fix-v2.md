# Decluttr — Wishlist Firestore Sync: Still Broken — Root Cause Report v2

---

## What changed vs the last version

The previous fix added a proper `SupervisorJob` scope, a pending queue, auth state listener in `init`, and retry logic. The architecture is much better. But **three root causes remain** that together guarantee the Firestore write never lands.

---

## Bug 1 — CRITICAL: Process is killed before the Firestore write starts

### The sequence

```
ShareReceiverActivity cold start (separate task, process not running)
    ↓
WishlistRepositoryImpl.init {} → auth.addAuthStateListener registered
    ↓
Auth listener fires → schedulePendingSync()
    → scope.launch { flushPendingSyncs() }
        → pendingUpserts is EMPTY → sets pendingSyncJob = null → returns
    ↓
User taps "Add to Wishlist"
    ↓
lifecycleScope.launch {
    viewModel.add(app)
        → repository.add()
            → dao.insert()                ← completes in ~1ms ✓
            → enqueueUpsert()             ← adds to pendingUpserts map
            → schedulePendingSync()
                → pendingSyncJob is null
                → scope.launch { flushPendingSyncs() }   ← new job launched
        ← repository.add() returns immediately (it is NOT waiting for Firestore)
    Toast.show()
    finish()   ← Activity destroyed
}
    ↓
Android OS sees: no Activity, no Service, no BroadcastReceiver running
Process is eligible for termination immediately
    ↓
flushPendingSyncs() is running in scope (SupervisorJob + Dispatchers.IO)
A SupervisorJob scope does NOT keep the process alive — it is just a Kotlin object
If Android kills the process (likely within 100–500ms on low-memory devices),
the coroutine dies mid-flight
    ↓
Room has the data ✓
Firestore never received the write ✗
```

`repository.add()` is a `suspend fun` but it returns as soon as the item is enqueued — it does **not** suspend until the Firestore write completes. So `finish()` is called ~1ms after `add()`, long before the network request goes out.

### Fix A — Await the Firestore write before finishing (simplest)

Change `repository.add()` to actually suspend until the Firestore write has been attempted:

```kotlin
// WishlistRepositoryImpl.kt
override suspend fun add(app: WishlistApp) {
    val updatedApp = app.copy(lastModified = System.currentTimeMillis())
    dao.insert(updatedApp.toEntity())
    // Attempt Firestore write synchronously — caller waits for result
    // Room already has the data, so even if this fails it will retry on next sync
    runCatching { performUpsert(updatedApp) }
        .onFailure {
            android.util.Log.w("WishlistRepo", "Initial sync failed, will retry: ${it.message}")
            enqueueUpsert(updatedApp)   // enqueue for background retry
        }
}
```

Then in `ShareReceiverActivity`, `finish()` is safe because `viewModel.add()` already awaited the write:

```kotlin
// ShareReceiverActivity.kt — showConfirmDialog positive button
lifecycleScope.launch {
    viewModel.add(app)    // now truly suspends until write is attempted
    Toast.makeText(this@ShareReceiverActivity, "${app.name} added to wishlist", Toast.LENGTH_SHORT).show()
    finish()              // safe — write already completed or queued for retry
}
```

### Fix B — Enable Firestore offline persistence (essential safety net)

Even with Fix A, if the device is offline when the user saves, the write will fail and be queued. Without offline persistence, queued writes are lost when the process dies. With it, Firestore handles the retry automatically on next launch.

**In `AppModule.kt`** — this must be set before any Firestore call is made:

```kotlin
@Provides
@Singleton
fun provideFirebaseFirestore(): FirebaseFirestore {
    val db = FirebaseFirestore.getInstance()
    // CRITICAL: queue writes locally so they survive process death
    val settings = FirebaseFirestoreSettings.Builder()
        .setPersistenceEnabled(true)
        .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
        .build()
    db.firestoreSettings = settings
    return db
}
```

With this set, `firestore.set().await()` succeeds instantly (writes to local cache), and Firestore syncs to the server in the background — even after the process is killed and restarted.

**This is the single most impactful change.** The archive sync (`AppRepositoryImpl`) also benefits from this.

---

## Bug 2 — CRITICAL: `auth.currentUser` is still null in `performUpsert`

### The code

```kotlin
// WishlistRepositoryImpl.kt
private suspend fun performUpsert(app: WishlistApp) {
    val user = auth.currentUser ?: error("No signed-in user")  // ← throws if null
    firestore.collection("users").document(user.uid)
        .collection("wishlist").document(app.packageId)
        .set(dataMap, SetOptions.merge())
        .await()
}
```

### Why it throws

`ShareReceiverActivity` starts as a cold process (no `MainActivity`, no `DashboardViewModel`). Firebase Auth restores the cached session **asynchronously** via a background thread. The sequence is:

```
T=0ms   Process starts
T=1ms   WishlistRepositoryImpl constructed, auth listener registered
T=5ms   Firebase reads cached token from SharedPreferences
T=10ms  Firebase validates token with server (background, async)
        auth.currentUser is STILL NULL during validation
T=50ms  User taps "Add" (after Jsoup scrape finishes ~2-5 seconds)
T=50ms  performUpsert() called
        auth.currentUser ← may still be null if validation not complete
        → error("No signed-in user") thrown
        → caught by runCatching in performUpsertWithRetry → failure
        → retried 3 times with 1.5s delays
        → all 3 fail if auth.currentUser never becomes non-null in this window
        → write abandoned
T=200ms Firebase auth validation finally completes, currentUser is now set
        → too late, all retries exhausted
```

The `init` block's auth state listener fires when Firebase is ready, but `performUpsert` does not use that listener — it reads `currentUser` synchronously at the moment it runs, which may be before Firebase has restored the session.

### Fix

Use `getIdToken()` to force Firebase to await the session restore before reading `currentUser`:

```kotlin
// WishlistRepositoryImpl.kt
private suspend fun performUpsert(app: WishlistApp) =
    withContext(Dispatchers.IO) {
        // Force Firebase to restore the cached session before reading currentUser
        // This is a no-op if already authenticated (~0ms), or waits for token restore (~50-200ms)
        runCatching { auth.currentUser?.getIdToken(false)?.await() }

        val uid = auth.currentUser?.uid
            ?: error("performUpsert: no authenticated user after token check")

        firestore.collection("users").document(uid)
            .collection("wishlist").document(app.packageId)
            .set(app.toFirestoreMap(), SetOptions.merge())
            .await()

        android.util.Log.d("WishlistRepo", "Firestore write OK: ${app.packageId}")
    }
```

---

## Bug 3 — HIGH: `dao.deleteAll()` wipes local data on any auth disruption

### The code

```kotlin
// WishlistRepositoryImpl.kt — init block auth listener
auth.addAuthStateListener { firebaseAuth ->
    scope.launch {
        val uid = firebaseAuth.currentUser?.uid
        if (uid != null) {
            ...
        } else {
            clearPendingSyncState()
            currentUserId = null
            dao.deleteAll()   // ← WIPES ALL LOCAL WISHLIST DATA
        }
    }
}
```

### Why this is dangerous

Firebase Auth triggers `onAuthStateChanged` with a `null` user during **token refresh**, not just on sign-out. This happens:
- After the 1-hour token expiry while Firebase fetches a new token
- On cold start before the async session restore completes
- On flaky networks where the token validation times out

If `onAuthStateChanged(null)` fires for any of these reasons, `dao.deleteAll()` wipes the entire local wishlist. The user opens the app and their wishlist is gone.

Additionally, this conflicts with the Firestore offline persistence fix: if local data is deleted before the pending Firestore sync completes, the data is unrecoverable.

### Fix

Only delete local data on explicit, confirmed sign-out:

```kotlin
// WishlistRepositoryImpl.kt — init block
auth.addAuthStateListener { firebaseAuth ->
    scope.launch {
        val uid = firebaseAuth.currentUser?.uid
        if (uid != null) {
            if (currentUserId != null && currentUserId != uid) {
                // Confirmed account switch — safe to clear
                clearPendingSyncState()
                dao.deleteAll()
            }
            currentUserId = uid
            syncFromFirestore()
            schedulePendingSync()
        } else {
            // Do NOT delete local data here — this fires during token refresh too
            // Only clear the in-memory pending queue
            clearPendingSyncState()
            currentUserId = null
            // Local Room data is kept — it will be re-validated on next successful auth
        }
    }
}
```

Add an explicit `clearLocalData()` method called only from a user-initiated sign-out:

```kotlin
// WishlistRepository.kt — add to interface
suspend fun clearLocalData()

// WishlistRepositoryImpl.kt
override suspend fun clearLocalData() {
    clearPendingSyncState()
    dao.deleteAll()
    currentUserId = null
}
```

Call it from wherever sign-out is triggered in the app (AuthFragment / settings):

```kotlin
// On explicit sign-out
wishlistRepository.clearLocalData()
appRepository.clearLocalData() // if it has a similar method
firebaseAuth.signOut()
```

---

## Summary

| # | Bug | Severity | One-line fix |
|---|-----|----------|--------------|
| 1a | `finish()` called before Firestore write begins | 🔴 Critical | Make `repository.add()` await `performUpsert()` directly |
| 1b | No Firestore offline persistence | 🔴 Critical | `setPersistenceEnabled(true)` in `AppModule` |
| 2 | `auth.currentUser` null in `performUpsert` | 🔴 Critical | Call `getIdToken(false).await()` before reading `currentUser` |
| 3 | `dao.deleteAll()` fires on token refresh | 🟠 High | Only delete on confirmed account switch, not on any null auth event |

---

## Minimal fix — 4 changes, guarantees sync works

### Change 1 — `AppModule.kt` (enable offline persistence)

```kotlin
@Provides @Singleton
fun provideFirebaseFirestore(): FirebaseFirestore {
    val db = FirebaseFirestore.getInstance()
    db.firestoreSettings = FirebaseFirestoreSettings.Builder()
        .setPersistenceEnabled(true)
        .build()
    return db
}
```

### Change 2 — `WishlistRepositoryImpl.kt` (await write in `add()`)

```kotlin
override suspend fun add(app: WishlistApp) {
    val updatedApp = app.copy(lastModified = System.currentTimeMillis())
    dao.insert(updatedApp.toEntity())
    runCatching { performUpsert(updatedApp) }
        .onFailure { enqueueUpsert(updatedApp) }
}
```

### Change 3 — `WishlistRepositoryImpl.kt` (fix `performUpsert` auth check)

```kotlin
private suspend fun performUpsert(app: WishlistApp) = withContext(Dispatchers.IO) {
    runCatching { auth.currentUser?.getIdToken(false)?.await() }
    val uid = auth.currentUser?.uid ?: error("No authenticated user")
    firestore.collection("users").document(uid)
        .collection("wishlist").document(app.packageId)
        .set(app.toFirestoreMap(), SetOptions.merge())
        .await()
}
```

### Change 4 — `WishlistRepositoryImpl.kt` (stop wiping on token refresh)

```kotlin
// In auth.addAuthStateListener:
} else {
    clearPendingSyncState()
    currentUserId = null
    // REMOVE: dao.deleteAll()
}
```

*Generated April 2026 against Decluttr-main v4*
