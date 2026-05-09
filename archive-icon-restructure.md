# Archive Icon Restructuring — Web-First with Offline Capture

## Design Decision

The archive page shows **Play Store icons by default** for visual consistency — the Play
Store CDN URL is stored as a normalized string, loaded and disk-cached by Coil.

The **one exception** is when the device is offline at the moment of archiving. In that
window the app is still installed and `PackageManager` can provide the real icon. That
icon is captured as `iconBytes` and shown immediately. As soon as the device comes back
online, a connectivity observer triggers a targeted scrape for every entry that has bytes
but no URL, and silently replaces the bytes with the web icon. After that swap the icon
is consistent with every other entry.

The net result:

- **Online at archive time** → Play Store URL fetched immediately, `iconBytes = null`
- **Offline at archive time** → real icon captured as `iconBytes`, URL fetched on reconnect
- **Existing legacy entries** → same treatment as offline entries; backfill on next startup

`PackageManager` is never used as a passive fallback during display. It is only invoked
as a **proactive capture at archive time** when the network is unavailable.

---

## Goal

| Layer | Before | After |
|-------|--------|-------|
| Firestore field | `iconBase64` (up to 24 KB) | `iconUrl` (plain string ~80 chars) |
| Local Room | `iconBytes ByteArray?` | `iconBytes ByteArray?` (offline/legacy capture) + `iconUrl String?` |
| Icon load priority | memory → DB bytes → PackageManager | LRU memory → Coil disk/network (URL) → offline bytes → re-scrape → shimmer placeholder |
| PackageManager use | Primary or passive fallback | **Proactive capture only** — at archive time, offline only |
| Icon shape | Raw bitmap, no shaping | Rounded square (squircle) — archive and wishlist identical |
| Loading state | Blank/placeholder immediately | Per-item shimmer until Coil resolves |

---

## Implementation

### Step 1 — `ArchivedApp.kt` — Add `iconUrl`, clarify `iconBytes` role

```kotlin
data class ArchivedApp(
    val packageId: String,
    val name: String,
    val isPlayStoreInstalled: Boolean = true,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,

    // Offline/legacy capture. Written when:
    //   (a) device is offline at archive time — PackageManager icon compressed to WebP
    //   (b) existing entry migrated from old iconBase64 Firestore field
    // Cleared to null once iconUrl is populated by the connectivity-triggered upgrade.
    val iconBytes: ByteArray? = null,

    // Primary icon source. Normalized play-lh.googleusercontent.com base URL.
    // Null only during the window between offline archiving and reconnection.
    val iconUrl: String? = null,

    val archivedSizeBytes: Long? = null,
    val archivedAt: Long = System.currentTimeMillis(),
    val lastTimeUsed: Long = 0L,
    val folderName: String? = null,
    val lastModified: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArchivedApp) return false
        // ... existing field checks ...
        if (iconUrl != other.iconUrl) return false       // ← ADD
        return true
    }

    override fun hashCode(): Int {
        var result = packageId.hashCode()
        // ... existing fields ...
        result = 31 * result + (iconUrl?.hashCode() ?: 0) // ← ADD
        return result
    }
}
```

---

### Step 2 — `AppEntity.kt` — Add `iconUrl` column

```kotlin
@Entity(tableName = "archived_apps")
data class AppEntity(
    @PrimaryKey val packageId: String,
    val name: String,
    val category: String?,
    val tags: String,
    val notes: String?,
    // Offline/legacy capture — see ArchivedApp.iconBytes
    val iconBytes: ByteArray?,
    // Primary icon field — normalized play-lh base URL
    val iconUrl: String?,
    val archivedSizeBytes: Long?,
    val isPlayStoreInstalled: Boolean = true,
    val archivedAt: Long = System.currentTimeMillis(),
    val lastTimeUsed: Long = 0L,
    val folderName: String? = null,
    val lastModified: Long = System.currentTimeMillis()
)
```

#### Room migration — `DecluttrDatabase.kt`

```kotlin
val MIGRATION_N_to_N1 = object : Migration(N, N + 1) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE archived_apps ADD COLUMN iconUrl TEXT DEFAULT NULL"
        )
    }
}
```

Register: `Room.databaseBuilder(…).addMigrations(MIGRATION_N_to_N1)`.

> Dev-only shortcut: `.fallbackToDestructiveMigration()`. Remove before shipping.

---

### Step 3 — `AppMapper.kt` — Thread `iconUrl` through both directions

```kotlin
fun AppEntity.toArchivedApp(): ArchivedApp = ArchivedApp(
    packageId            = packageId,
    name                 = name,
    isPlayStoreInstalled = isPlayStoreInstalled,
    category             = category,
    tags                 = if (tags.isNotBlank()) tags.split(",") else emptyList(),
    notes                = notes,
    iconBytes            = iconBytes,
    iconUrl              = iconUrl,   // ← NEW
    archivedSizeBytes    = archivedSizeBytes,
    archivedAt           = archivedAt,
    lastTimeUsed         = lastTimeUsed,
    folderName           = folderName,
    lastModified         = lastModified
)

fun ArchivedApp.toAppEntity(): AppEntity = AppEntity(
    packageId            = packageId,
    name                 = name,
    category             = category,
    tags                 = tags.joinToString(","),
    notes                = notes,
    iconBytes            = iconBytes,
    iconUrl              = iconUrl,   // ← NEW
    archivedSizeBytes    = archivedSizeBytes,
    isPlayStoreInstalled = isPlayStoreInstalled,
    archivedAt           = archivedAt,
    lastTimeUsed         = lastTimeUsed,
    folderName           = folderName,
    lastModified         = lastModified
)
```

---

### Step 4 — `PlayStoreScraper.kt` — Robustness + URL normalization

#### 4a. Three-selector icon extraction

```kotlin
/**
 * Tries three selectors in priority order. Returns the first non-blank normalized URL.
 *
 * Selector 1: og:image meta tag — most reliable, present since 2015
 * Selector 2: img[itemprop=image] — present in some regional variants
 * Selector 3: JSON-LD structured data — most structurally stable long-term
 */
private fun extractIconUrl(doc: Document): String? {
    doc.select("meta[property=og:image]").attr("content")
        .takeIf { it.isNotBlank() }
        ?.let { return normalizeIconUrl(it) }

    doc.select("img[itemprop=image]").attr("src")
        .takeIf { it.isNotBlank() }
        ?.let { return normalizeIconUrl(it) }

    doc.select("script[type=application/ld+json]").forEach { script ->
        runCatching {
            val json = org.json.JSONObject(script.data())
            val image = json.optJSONArray("@graph")
                ?.let { arr -> (0 until arr.length()).firstNotNullOfOrNull {
                    arr.optJSONObject(it)?.optString("image")?.takeIf { s -> s.isNotBlank() }
                }}
                ?: json.optString("image").takeIf { it.isNotBlank() }
            image?.let { return normalizeIconUrl(it) }
        }
    }
    return null
}
```

#### 4b. `normalizeIconUrl()` — base URL, no size suffix

Store the bare base URL in Room/Firestore. Append `=w{sizePx}` at load time in
`AppIconFetcher` to serve any screen density without re-scraping.

```kotlin
companion object {
    /**
     * Strips size parameters from a play-lh.googleusercontent.com URL.
     *
     * Input:  https://play-lh.googleusercontent.com/abc123=w240-h480-rw
     * Output: https://play-lh.googleusercontent.com/abc123
     *
     * sizePx == 0 (default) returns the bare base URL.
     * sizePx > 0 appends =w{sizePx} for an immediate-use URL.
     */
    fun normalizeIconUrl(raw: String?, sizePx: Int = 0): String? {
        val url = raw?.trim()?.takeIf { it.startsWith("https://") } ?: return null
        val base = url.substringBefore("=w").substringBefore("=s").trimEnd('=')
        return if (sizePx > 0) "$base=w$sizePx" else base
    }

    fun extractPackageId(sharedText: String): String? =
        Regex("""id=([a-zA-Z0-9_.]+)""").find(sharedText)?.groupValues?.get(1)
}
```

#### 4c. Retry with exponential backoff

```kotlin
suspend fun fetch(packageId: String): PlayStoreAppInfo? =
    withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        // 3 attempts: immediate, +2 s, +5 s
        for (delayMs in listOf(0L, 2_000L, 5_000L)) {
            if (delayMs > 0) delay(delayMs)
            try {
                val result = fetchOnce(packageId)
                if (result != null) return@withContext result
            } catch (e: Exception) {
                lastException = e
                android.util.Log.w(TAG, "Scrape attempt failed pkg=$packageId", e)
            }
        }
        android.util.Log.e(TAG, "Scrape exhausted retries pkg=$packageId", lastException)
        null
    }

private fun fetchOnce(packageId: String): PlayStoreAppInfo? {
    val doc = Jsoup.connect(
        "https://play.google.com/store/apps/details?id=$packageId&hl=en"
    )
        .userAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                   "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        .timeout(8_000)
        .get()

    val iconUrl     = extractIconUrl(doc) ?: return null
    val name        = doc.select("meta[property=og:title]").attr("content")
                        .ifBlank { doc.title().substringBefore(" - Google Play") }.trim()
    val description = doc.select("meta[property=og:description]").attr("content").trim()
    val category    = doc.select("a[itemprop=genre]").text().ifBlank { null }

    return PlayStoreAppInfo(
        packageId    = packageId,
        name         = name,
        iconUrl      = iconUrl,   // base URL, no size suffix
        description  = description,
        category     = category,
        playStoreUrl = "https://play.google.com/store/apps/details?id=$packageId"
    )
}
```

---

### Step 5 — `CaptureAppUseCase.kt` — Online/offline branching at archive time

```kotlin
class CaptureAppUseCase @Inject constructor(
    private val extractPackageIdUseCase: ExtractPackageIdUseCase,
    private val getAppDetailsUseCase: GetAppDetailsUseCase,
    private val playScraper: PlayStoreScraper,
    private val repository: AppRepository,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(shareText: String): String? {
        val packageId = extractPackageIdUseCase(shareText) ?: return null
        if (repository.getAppById(packageId) != null) return packageId

        val isOnline = isNetworkAvailable()

        // Always run local details (name, size, category) — offline-safe
        val deferredDetails = coroutineScope { async { getAppDetailsUseCase(packageId) } }

        // Play Store scrape only if online
        val deferredPlayInfo = if (isOnline) {
            coroutineScope { async {
                runCatching { playScraper.fetch(packageId) }.getOrNull()
            }}
        } else null

        val appDetails = deferredDetails.await()
        val playInfo   = deferredPlayInfo?.await()

        val iconUrl   = playInfo?.iconUrl?.ifBlank { null }

        // Offline capture: grab PackageManager icon NOW before the app is uninstalled.
        // Only when we have no URL — this icon will be replaced by the web icon on reconnect.
        val iconBytes = if (iconUrl == null) {
            captureInstalledIcon(packageId)
        } else {
            null   // URL available — no need to store bytes
        }

        val name     = appDetails?.name?.takeIf { !looksLikePackageId(it) }
            ?: playInfo?.name?.takeIf { it.isNotBlank() }
            ?: packageId
        val category = appDetails?.category ?: playInfo?.category

        val archivedApp = ArchivedApp(
            packageId         = packageId,
            name              = name,
            iconBytes         = iconBytes,   // non-null only when offline
            iconUrl           = iconUrl,     // non-null when online scrape succeeded
            category          = category,
            archivedSizeBytes = appDetails?.archivedSizeBytes
        )

        repository.insertApp(archivedApp)
        return packageId
    }

    /**
     * Captures the installed app icon as compressed WebP bytes.
     * Only called when offline — the icon is still accessible via PackageManager
     * because the app hasn't been uninstalled yet at this point in the flow.
     */
    private fun captureInstalledIcon(packageId: String): ByteArray? {
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageId)
            val bitmap   = drawableToBitmap(drawable, 128, 128)
            val stream   = java.io.ByteArrayOutputStream()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 85, stream)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 85, stream)
            }
            stream.toByteArray().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps    = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun looksLikePackageId(value: String) =
        value.contains('.') && value == value.lowercase() && !value.contains(' ')
}
```

---

### Step 6 — `AppRepositoryImpl.kt`

#### 6a. Firestore write — `iconUrl` replaces `iconBase64`

```kotlin
// REMOVE:
val iconBase64 = compressIconForFirestore(app.iconBytes)
"iconBase64" to iconBase64,

// ADD:
"iconBase64" to FieldValue.delete(),   // one-time cleanup; remove after 2 releases
"iconUrl"    to app.iconUrl,
```

**Do not write `iconBytes` to Firestore.** Offline-captured bytes are local-only.
They are never synced. On a second device the URL backfill (Step 6d) will scrape
the web icon instead.

#### 6b. Firestore read — parse `iconUrl`, keep `iconBase64` read for compatibility

```kotlin
val legacyIconBytes = runCatching { decodeIconBase64(getString("iconBase64")) }.getOrNull()
val iconUrl         = getString("iconUrl")?.takeIf { it.isNotBlank() }

ArchivedApp(
    packageId = id,
    iconBytes = legacyIconBytes,  // null for all new documents
    iconUrl   = iconUrl,
    ...
)
```

#### 6c. Merge logic — prefer URL; clear bytes once URL available

```kotlin
val resolvedIconUrl   = remoteApp.iconUrl ?: localApp.iconUrl
// Once a URL is resolved, bytes serve no purpose — clear them
val resolvedIconBytes = if (resolvedIconUrl != null) null
                        else remoteApp.iconBytes ?: localApp.iconBytes

mergedApp = mergedApp.copy(
    iconUrl   = resolvedIconUrl,
    iconBytes = resolvedIconBytes,
)
```

#### 6d. Remove dead compression code

Delete entirely:

```kotlin
// DELETE:
private fun compressIconForFirestore(iconBytes: ByteArray?): String?
private fun compressIconBytesForFirestore(bitmap: Bitmap): ByteArray?
private const val FIRESTORE_ICON_MAX_BYTES = ...
private val FIRESTORE_JPEG_QUALITIES = ...
private const val FIRESTORE_ICON_MAX_DIM = ...
```

#### 6e. Startup backfill — all entries missing `iconUrl`

Covers both legacy (old `iconBase64` entries) and offline-captured entries from previous
sessions. Rate-limited to 800 ms/scrape ≈ 75 apps/min.

```kotlin
private fun launchIconUrlBackfill() {
    scope.launch {
        val needsUrl = dao.getArchivedAppsSnapshot()
            .filter { it.iconUrl.isNullOrBlank() }

        if (needsUrl.isEmpty()) return@launch
        android.util.Log.d(TAG, "Icon backfill: ${needsUrl.size} apps")

        needsUrl.forEachIndexed { index, entity ->
            if (index > 0) delay(800L)
            val info = runCatching { playScraper.fetch(entity.packageId) }
                .getOrNull() ?: return@forEachIndexed

            val updated = entity.copy(
                iconUrl      = info.iconUrl,
                iconBytes    = null,         // web icon replaces offline bytes
                lastModified = System.currentTimeMillis()
            )
            dao.upsertApp(updated)
            enqueueUpsert(updated.toArchivedApp())
        }
        android.util.Log.d(TAG, "Icon backfill complete")
    }
}
```

Call from `onUserAuthenticated()` or `init {}` after confirming sign-in.

#### 6f. Connectivity-triggered URL upgrade

Register a `NetworkCallback` that fires when the device transitions from offline to online.
This upgrades offline-captured entries in the current session without waiting for the next
startup backfill.

```kotlin
// In AppRepositoryImpl (or a dedicated NetworkObserver class injected here):

private val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        scope.launch {
            // Only process entries that have bytes but no URL —
            // these are offline-captured from this or a previous session.
            val offlineEntries = dao.getArchivedAppsSnapshot()
                .filter { it.iconUrl.isNullOrBlank() && it.iconBytes != null }

            if (offlineEntries.isEmpty()) return@launch
            android.util.Log.d(TAG, "Connectivity restored: upgrading ${offlineEntries.size} offline icons")

            offlineEntries.forEachIndexed { index, entity ->
                if (index > 0) delay(800L)
                val info = runCatching { playScraper.fetch(entity.packageId) }
                    .getOrNull() ?: return@forEachIndexed

                val updated = entity.copy(
                    iconUrl      = info.iconUrl,
                    iconBytes    = null,     // discard offline bytes
                    lastModified = System.currentTimeMillis()
                )
                dao.upsertApp(updated)
                enqueueUpsert(updated.toArchivedApp())
            }
        }
    }
}

fun registerNetworkCallback() {
    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        .build()
    runCatching {
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }
}

fun unregisterNetworkCallback() {
    runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
}
```

Call `registerNetworkCallback()` in `AppRepositoryImpl.init {}` and
`unregisterNetworkCallback()` from `onCleared()` or when the repository scope is
cancelled.

#### 6g. `updateIconUrl()` — used by `AppIconFetcher` tier 3 re-scrape

```kotlin
// AppRepository interface — add:
suspend fun updateIconUrl(packageId: String, iconUrl: String)

// AppRepositoryImpl:
override suspend fun updateIconUrl(packageId: String, iconUrl: String) {
    val current = dao.getAppById(packageId) ?: return
    val updated = current.copy(
        iconUrl      = iconUrl,
        iconBytes    = null,
        lastModified = System.currentTimeMillis()
    )
    dao.upsertApp(updated)
    enqueueUpsert(updated.toArchivedApp())
}
```

---

### Step 7 — `AppIconFetcher.kt` — Four-tier chain, no passive PackageManager

```kotlin
override suspend fun fetch(): FetchResult? {
    val packageName = data.packageName

    // ── Tier 1: In-memory LRU — sync, zero cost ──────────────────────────
    iconCacheManager?.get(packageName)?.let { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
            return DrawableResult(BitmapDrawable(context.resources, bmp),
                isSampled = false, dataSource = DataSource.MEMORY_CACHE)
        }
    }

    val app = appRepository?.getAppById(packageName)

    // ── Tier 2: Coil disk/network via stored iconUrl ─────────────────────
    app?.iconUrl?.let { baseUrl ->
        return fetchUrl(baseUrl, packageName, sizePx = targetSizePx())
    }

    // ── Tier 3: Offline/legacy bytes — sync decode ───────────────────────
    // Present when: (a) archived offline this session, (b) legacy iconBase64 migration.
    // These bytes are temporary — connectivity trigger or backfill will replace them
    // with a URL. Shown here so the user always sees something, never a blank.
    app?.iconBytes?.let { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
            iconCacheManager?.put(packageName, bytes)
            return DrawableResult(BitmapDrawable(context.resources, bmp),
                isSampled = false, dataSource = DataSource.DISK)
        }
    }

    // ── Tier 4: Silent re-scrape — URL absent, no bytes, backfill hasn't run yet ──
    val scrapedUrl = withContext(Dispatchers.IO) {
        runCatching { playScraper.fetch(packageName)?.iconUrl }.getOrNull()
    }
    if (scrapedUrl != null) {
        withContext(Dispatchers.IO) { appRepository?.updateIconUrl(packageName, scrapedUrl) }
        return fetchUrl(scrapedUrl, packageName, sizePx = targetSizePx())
    }

    // ── No icon available → Coil shows shimmer placeholder then error drawable ──
    return null
}

private fun targetSizePx(): Int {
    val dp = 48
    return (dp * context.resources.displayMetrics.density).toInt().coerceIn(64, 256)
}

private suspend fun fetchUrl(baseUrl: String, packageName: String, sizePx: Int): FetchResult? =
    withContext(Dispatchers.IO) {
        runCatching {
            val sizedUrl = "$baseUrl=w$sizePx"
            val conn = java.net.URL(sizedUrl).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout    = 8_000
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            conn.doInput = true
            conn.connect()

            if (conn.responseCode != 200) {
                android.util.Log.w(TAG, "fetchUrl HTTP ${conn.responseCode} pkg=$packageName")
                return@runCatching null
            }

            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bytes.isEmpty()) return@runCatching null

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null

            val stream = java.io.ByteArrayOutputStream()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 85, stream)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 85, stream)
            }
            iconCacheManager?.put(packageName, stream.toByteArray())

            DrawableResult(BitmapDrawable(context.resources, bitmap),
                isSampled = false, dataSource = DataSource.NETWORK)
        }.getOrElse { e ->
            android.util.Log.w(TAG, "fetchUrl failed pkg=$packageName", e)
            null
        }
    }
```

Inject `PlayStoreScraper` into `AppIconFetcherFactory`:

```kotlin
class AppIconFetcherFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val iconCacheManager: IconCacheManager?,
    private val appRepository: AppRepository?,
    private val playScraper: PlayStoreScraper       // ← ADD
) : Fetcher.Factory<AppIconModel> {
    override fun create(data: AppIconModel, options: Options, imageLoader: ImageLoader) =
        AppIconFetcher(context, iconCacheManager, appRepository, playScraper, data)
}
```

---

### Step 8 — Rounded square icons — archive matches wishlist

The wishlist uses `ShapeableImageView` with a squircle shape. Apply the same treatment to
every icon `ImageView` in the archive layouts.

#### 8a. `res/values/styles.xml` — confirm shape style exists (wishlist already has it)

```xml
<!-- Squircle shape for app icons — used by both archive and wishlist -->
<style name="SquircleIconShape" parent="">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">22%</item>   <!-- ~22% of width gives iOS-style squircle -->
</style>
```

#### 8b. `item_archived_app_grid.xml` — replace `ImageView` with `ShapeableImageView`

```xml
<!-- REPLACE: -->
<ImageView
    android:id="@+id/app_icon"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:scaleType="centerCrop" />

<!-- WITH: -->
<com.google.android.material.imageview.ShapeableImageView
    android:id="@+id/app_icon"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:scaleType="centerCrop"
    app:shapeAppearanceOverlay="@style/SquircleIconShape" />
```

#### 8c. `item_archived_app_list.xml` — same replacement

```xml
<com.google.android.material.imageview.ShapeableImageView
    android:id="@+id/app_icon"
    android:layout_width="44dp"
    android:layout_height="44dp"
    android:scaleType="centerCrop"
    app:shapeAppearanceOverlay="@style/SquircleIconShape" />
```

#### 8d. Folder overlay / folder grid slot icons

Any `ImageView` used to preview folder contents (the 2×2 icon preview inside a folder
tile) gets the same treatment. Use a smaller corner radius for the miniature preview:

```xml
<!-- Folder preview slot — smaller icon, same shape family -->
<style name="SquircleIconShapeSmall" parent="">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">22%</item>
</style>
```

Apply `app:shapeAppearanceOverlay="@style/SquircleIconShapeSmall"` to all folder slot
`ImageView`s in `item_folder_grid.xml` (or equivalent layout).

#### 8e. Offline-captured icons (from PackageManager) are shaped identically

`ShapeableImageView` clips by shape regardless of the bitmap source. An offline-captured
icon from `PackageManager` will be clipped to the squircle the same way a Play Store icon
is. No special handling needed.

---

### Step 9 — Shimmer loading placeholder — archive and wishlist

Use the **Shimmer** library from Facebook (already a common dependency with Material
components, or add `com.facebook.shimmer:shimmer:0.5.0`). Each icon `ImageView` shows a
shimmer drawable as a placeholder while Coil resolves the icon, then crossfades to the
real icon on completion.

#### 9a. `res/drawable/shimmer_placeholder.xml` — new file

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Rounded square shimmer placeholder — matches SquircleIconShape corner radius -->
<animated-rotate xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/shimmer_gradient"
    android:pivotX="50%"
    android:pivotY="50%" />
```

A simpler, dependency-free approach using a `ShimmerDrawable`:

```kotlin
// ShimmerPlaceholder.kt — singleton factory
object ShimmerPlaceholder {
    fun create(context: Context): ShimmerDrawable {
        val shimmer = Shimmer.AlphaHighlightBuilder()
            .setDuration(1_000)
            .setBaseAlpha(0.12f)
            .setHighlightAlpha(0.22f)
            .setDirection(Shimmer.Direction.LEFT_TO_RIGHT)
            .setAutoStart(true)
            .build()
        return ShimmerDrawable().apply {
            setShimmer(shimmer)
        }
    }
}
```

#### 9b. `ArchivedAppsAdapter.kt` — wire shimmer into `loadIcon()`

```kotlin
// In ArchivedAppsAdapter — create once per adapter instance
private val shimmerPlaceholder: ShimmerDrawable by lazy {
    ShimmerPlaceholder.create(context)
}

private fun loadIcon(imageView: ImageView, app: ArchivedApp) {
    // Fast path: local bytes in LRU (legacy or offline-captured)
    // Only skips Coil when iconUrl is absent — bytes will still be replaced by
    // the connectivity trigger, which will post a DiffUtil update to rebind this cell.
    if (app.iconBytes != null && app.iconUrl == null) {
        val bmp = IconBitmapCache.getOrDecode(app)
        if (bmp != null) {
            imageView.setImageBitmap(bmp)
            return
        }
    }

    // Coil path — shimmer while loading, crossfade on completion
    imageView.load(AppIconModel(app.packageId)) {
        memoryCacheKey(app.packageId)
        size(coil.size.Size.ORIGINAL)
        placeholder(shimmerPlaceholder)           // ← shimmer until resolved
        error(R.drawable.ic_app_placeholder)      // ← static placeholder on failure
        crossfade(true)
        crossfade(200)                            // 200ms fade-in
    }
}
```

#### 9c. `WishlistAdapter.kt` — add shimmer to icon load

The wishlist already uses `imageView.load(url)` directly. Add the shimmer placeholder:

```kotlin
// In WishlistAdapter.ViewHolder.bind():

private val shimmerPlaceholder: ShimmerDrawable by lazy {
    ShimmerPlaceholder.create(itemView.context)
}

fun bind(app: WishlistApp) {
    // ...
    if (app.iconUrl.isNotBlank()) {
        iconView.load(app.iconUrl) {
            placeholder(shimmerPlaceholder)       // ← ADD
            error(R.drawable.ic_app_placeholder)
            crossfade(true)
            crossfade(200)
            transformations(RoundedCornersTransformation(radius = /* match squircle */ 22f))
        }
    } else {
        iconView.setImageDrawable(shimmerPlaceholder)  // no URL — show shimmer indefinitely
    }
}
```

> **Note on `shimmerPlaceholder` scope**: `ShimmerDrawable` holds a `ValueAnimator`.
> Create one instance per `ViewHolder` (via `lazy`) rather than one per adapter to avoid
> all visible cells pulsing in exact synchrony, which looks mechanical. The lazy delegate
> inside `ViewHolder` achieves this naturally.

#### 9d. `ShimmerPlaceholder.kt` — new shared file

```kotlin
package com.tool.decluttr.presentation.util

import android.content.Context
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerDrawable

object ShimmerPlaceholder {
    /**
     * Creates a shimmer drawable suitable for app icon placeholders.
     * Call once per ViewHolder, not once per adapter.
     */
    fun create(context: Context): ShimmerDrawable {
        val shimmer = Shimmer.AlphaHighlightBuilder()
            .setDuration(900)
            .setBaseAlpha(0.10f)
            .setHighlightAlpha(0.20f)
            .setDirection(Shimmer.Direction.LEFT_TO_RIGHT)
            .setAutoStart(true)
            .build()
        return ShimmerDrawable().apply { setShimmer(shimmer) }
    }
}
```

#### 9e. Gradle dependency — add if not already present

```kotlin
// app/build.gradle.kts
implementation("com.facebook.shimmer:shimmer:0.5.0")
```

---

## File Change Summary

| File | Change |
|------|--------|
| `ArchivedApp.kt` | Add `iconUrl: String?`; clarify `iconBytes` as offline/legacy; update `equals()`/`hashCode()` |
| `AppEntity.kt` | Add `iconUrl: String?` column |
| `DecluttrDatabase.kt` | Bump version; `ALTER TABLE` migration |
| `AppMapper.kt` | Thread `iconUrl` through both mappers |
| `PlayStoreScraper.kt` | `extractIconUrl()` with 3 selectors; `normalizeIconUrl()`; retry with backoff |
| `CaptureAppUseCase.kt` | Online/offline branch; offline captures `PackageManager` bytes; online captures URL only |
| `AppRepositoryImpl.kt` | `iconUrl` write; `iconBase64` read compat; remove compression; backfill; `NetworkCallback` upgrade; `updateIconUrl()` |
| `AppRepository.kt` (interface) | Add `updateIconUrl(packageId, iconUrl)` |
| `AppIconFetcher.kt` | 4-tier chain: LRU → URL → offline bytes → re-scrape; no PackageManager passive fallback |
| `AppIconFetcherFactory.kt` | Inject `PlayStoreScraper` |
| `item_archived_app_grid.xml` | `ImageView` → `ShapeableImageView` with `SquircleIconShape` |
| `item_archived_app_list.xml` | `ImageView` → `ShapeableImageView` with `SquircleIconShape` |
| `item_folder_grid.xml` | Folder slot `ImageView`s → `ShapeableImageView` |
| `res/values/styles.xml` | Add `SquircleIconShape` + `SquircleIconShapeSmall` (if not already present) |
| `ArchivedAppsAdapter.kt` | Shimmer placeholder in `loadIcon()`; `IconBitmapCache` guard for offline/legacy only |
| `WishlistAdapter.kt` | Shimmer placeholder in `bind()` |
| `ShimmerPlaceholder.kt` | **New** — shared shimmer factory |
| `app/build.gradle.kts` | Add `com.facebook.shimmer:shimmer:0.5.0` |

**Deleted dead code:**
`compressIconForFirestore()`, `compressIconBytesForFirestore()`,
`FIRESTORE_ICON_MAX_BYTES`, `FIRESTORE_JPEG_QUALITIES`, `FIRESTORE_ICON_MAX_DIM`,
passive `fetchFromPackageManager()` from `AppIconFetcher`.

**Unchanged:**
`IconCacheManager`, `AppIconKeyer`, `GetAppDetailsUseCase` (name/size/category still used),
`AppDao`, all Fragment/ViewModel files, `WishlistApp`.

---

## Icon Resolution Failure Matrix

| Scenario | Tier 1 | Tier 2 | Tier 3 | Tier 4 | Result |
|----------|--------|--------|--------|--------|--------|
| Recently viewed — LRU warm | ✅ hit | — | — | — | Instant |
| URL present, Coil disk cached | miss | ✅ disk | — | — | Fast |
| URL present, first load | miss | ✅ network | — | — | CDN fetch → cached |
| Archived offline this session | miss | ❌ no URL | ✅ bytes (PackageManager capture) | — | Local bitmap → web icon on reconnect |
| Legacy `iconBase64` entry | miss | ❌ no URL | ✅ bytes (decoded from Firestore) | — | Local bitmap → web icon after backfill |
| No URL, no bytes, backfill pending | miss | ❌ | ❌ | ✅ re-scrape | Shimmer → icon |
| No URL, no bytes, scrape fails | miss | ❌ | ❌ | ❌ | Shimmer → placeholder |
| Sideloaded app — no Play Store page | miss | ❌ | ❌ | ❌ no page | Shimmer → placeholder |

---

## Migration Behaviour for Existing Users

- **Firestore `iconBase64`** — read on sync into `iconBytes`, shown via tier 3 until backfill.
- **Startup backfill** — runs after sign-in, skips apps with `iconUrl` already set, processes
  only missing entries. Idempotent across restarts.
- **Connectivity upgrade** — fires whenever the device moves from offline to online within a
  session. Upgrades offline-captured entries immediately without waiting for next startup.
- **Room `iconBytes`** — cleared to null once `iconUrl` is set (merge logic + backfill +
  connectivity callback all do this). Frees local storage progressively.
- **New archives** — online: `iconBytes = null`, `iconUrl` set immediately. Offline: `iconBytes`
  set from PackageManager, `iconUrl = null` until reconnection.
- **Firestore document size** — 50-app archive: ~1.2MB (50 × 24KB base64) → ~4KB (50 × 80-char URLs).
