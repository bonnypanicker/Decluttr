 
I now have a thorough understanding of the issue. Let me create the comprehensive fix document.
Action: file_editor create /app/Decluttr/WISHLIST_SYNC_FIX.md --file-text "# Wishlist Firestore Sync Fix - Root Cause Analysis & Solution

## Problem

After reinstalling the app and logging back in, the **Wishlist page is empty**. All wishlist items previously saved via Play Store share are lost, even though they were synced to Firestore. The Archive feature (which also syncs to Firestore) restores correctly after reinstall.

---

## Root Cause

The core issue is that **`WishlistRepositoryImpl` lacks an `AuthStateListener`**, unlike `AppRepositoryImpl` which has one. This means the wishlist Firestore-to-Room restore is not triggered reliably after login.

### How Archive Sync Works (Correct - `AppRepositoryImpl.kt`)

```
AppRepositoryImpl (Singleton) has an init block:

init {
    firebaseAuthOrNull()?.addAuthStateListener { firebaseAuth ->
        scope.launch {
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                syncFromFirestore()        // <-- Pulls remote data to Room
                schedulePendingSync()      // <-- Pushes pending local changes
            } else {
                dao.deleteAllApps()        // <-- Cleans up on logout
            }
        }
    }
}
```

**Key traits:**
- Runs at **Singleton scope** (application-level, independent of any UI lifecycle)
- `AuthStateListener` fires **immediately** when a user logs in
- Has **retry logic** (`MAX_SYNC_RETRIES = 3`) with exponential backoff
- Has **incremental sync** using `SharedPreferences` (`last_sync_time`)
- **Clears local data on logout** to prevent cross-account data leakage
- Uses `Provider<FirebaseAuth>` and `Provider<FirebaseFirestore>` for safe lazy access

### How Wishlist Sync Works (Broken - `WishlistRepositoryImpl.kt`)

`WishlistRepositoryImpl` has **NO `init` block**, **NO `AuthStateListener`**.

The only call to `wishlistRepository.syncFromFirestore()` is in `DashboardViewModel.kt` line 145:

```kotlin
// DashboardViewModel.kt init block
init {
    viewModelScope.launch {
        authRepository.isUserLoggedIn.collect { loggedIn ->
            if (loggedIn == true) {
                launch { wishlistRepository.syncFromFirestore() }  // <-- Only call site
            }
        }
    }
}
```

**Problems with this approach:**

1. **Lifecycle-dependent**: The sync only runs when `DashboardViewModel` is alive (tied to `DashboardFragment`). If the ViewModel is cleared before sync completes, the coroutine is cancelled via `viewModelScope`.

2. **No retry mechanism**: `syncFromFirestore()` wraps everything in a single `runCatching` and silently swallows any errors:
   ```kotlin
   runCatching { ... }.onFailure { it.printStackTrace() }
   ```
   If the network is flaky or Firestore times out, the sync fails permanently with no retry.

3. **No logout cleanup**: When a user logs out, Room is NOT cleared for wishlist data. If another user logs in, they could briefly see the previous user's wishlist items until sync overwrites them.

4. **Silent early return**: If `auth.currentUser?.uid` is null at the exact moment `syncFromFirestore()` executes (race condition between auth state propagation and the IO dispatcher switch), the function silently returns without syncing.

---

## Detailed Comparison Table

| Feature                          | Archive (`AppRepositoryImpl`)     | Wishlist (`WishlistRepositoryImpl`) |
|----------------------------------|-----------------------------------|-------------------------------------|
| AuthStateListener in repository  | Yes (in `init` block)             | **No**                              |
| Sync trigger scope               | Singleton (app-level)             | **ViewModel (fragment-level)**      |
| Retry on failure                 | Yes (3 retries, exponential backoff) | **No (single attempt)**          |
| Incremental sync (lastSyncTime)  | Yes (SharedPreferences)           | **No (always full fetch)**          |
| Logout data cleanup              | Yes (`dao.deleteAllApps()`)       | **No**                              |
| Local-to-remote push on sync     | Yes (uploads local-only items)    | **No (pull-only)**                  |
| Firebase access pattern          | `Provider<FirebaseAuth>` (safe)   | **Direct injection** (less safe)    |
| Error logging                    | Crashlytics + Log                 | **`printStackTrace()` only**        |

---

## The Fix

Add an `AuthStateListener` to `WishlistRepositoryImpl`, mirroring the pattern used in `AppRepositoryImpl`. This makes the sync lifecycle-independent and reliable.

### Changes Required

#### 1. `WishlistRepositoryImpl.kt` - Add AuthStateListener

```kotlin
@Singleton
class WishlistRepositoryImpl @Inject constructor(
    private val dao: WishlistDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : WishlistRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        auth.addAuthStateListener { firebaseAuth ->
            scope.launch {
                val uid = firebaseAuth.currentUser?.uid
                if (uid != null) {
                    syncFromFirestore()
                } else {
                    // Clear local wishlist on logout to prevent cross-account leakage
                    dao.deleteAll()
                }
            }
        }
    }

    // ... rest of the class unchanged
}
```

#### 2. `WishlistDao.kt` - Add `deleteAll()` method

```kotlin
@Dao
interface WishlistDao {
    // ... existing methods ...

    @Query(\"DELETE FROM wishlist\")
    suspend fun deleteAll()
}
```

#### 3. `WishlistRepositoryImpl.kt` - Add retry logic to `syncFromFirestore()`

```kotlin
override suspend fun syncFromFirestore() = withContext(Dispatchers.IO) {
    val uid = auth.currentUser?.uid
    if (uid == null) {
        android.util.Log.w(\"WishlistRepo\", \"syncFromFirestore: skipped - no user\")
        return@withContext
    }

    repeat(3) { attempt ->
        val result = runCatching {
            val snapshot = firestore
                .collection(\"users\").document(uid)
                .collection(\"wishlist\")
                .get()
                .await()

            val remoteApps = snapshot.documents.mapNotNull { it.toWishlistAppOrNull() }
            val localApps = dao.getAllSnapshot().associateBy { it.packageId }

            remoteApps.forEach { remote ->
                val local = localApps[remote.packageId]
                if (local == null || remote.lastModified >= local.lastModified) {
                    dao.insert(remote.toEntity())
                }
            }

            android.util.Log.d(\"WishlistRepo\", \"syncFromFirestore: restored ${remoteApps.size} items\")
        }

        if (result.isSuccess) return@withContext

        result.exceptionOrNull()?.let {
            android.util.Log.e(\"WishlistRepo\", \"syncFromFirestore attempt ${attempt + 1} failed\", it)
        }

        if (attempt < 2) {
            kotlinx.coroutines.delay(1500L * (attempt + 1))
        }
    }
}
```

#### 4. `DashboardViewModel.kt` - Remove redundant sync call (optional cleanup)

Once the `AuthStateListener` is in the repository, the DashboardViewModel sync call becomes redundant. You can safely remove lines 145 from the init block:

```kotlin
// REMOVE this line (no longer needed, handled by repository)
// launch { wishlistRepository.syncFromFirestore() }
```

---

## Firestore Security Rules

Ensure your Firestore security rules allow read/write access to the `wishlist` subcollection. If rules are only configured for `apps`, the wishlist sync would fail silently.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      // ... existing user doc rules ...

      match /apps/{appId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }

      // ADD THIS RULE if it doesn't exist:
      match /wishlist/{itemId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }
  }
}
```

---

## Verification Steps

1. **Add items to wishlist** via Play Store share
2. **Check Firestore Console** (Firebase Console > Firestore > users > {uid} > wishlist) - confirm documents exist
3. **Uninstall and reinstall** the app
4. **Log in** with the same account
5. **Navigate to Wishlist** - items should appear within a few seconds
6. **Check Logcat** for `WishlistRepo` tags:
   ```
   adb logcat -s WishlistRepo
   ```
   - Expect: `syncFromFirestore: restored N items`
   - If error: Check for auth/network issues in the error log

---

## Additional Recommendations

1. **Add Crashlytics error reporting** to `syncFromFirestore()` and `syncToFirestore()` (currently only using `printStackTrace()`)
2. **Consider incremental sync** using `SharedPreferences` like the archive does, to avoid re-fetching all items every time
3. **Add a manual refresh** button or pull-to-refresh on the Wishlist screen for cases where automatic sync might not have completed
4. **Add a `clearAll` to WishlistRepository interface** and call it on logout for data hygiene
"
Observation: Create successful: /app/Decluttr/WISHLIST_SYNC_FIX.md