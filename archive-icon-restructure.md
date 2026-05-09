# Archive Icon Restructuring — Web-First with Local Capture Window

## Summary

- Store a normalized Play Store CDN icon URL (`iconUrl`) as the primary icon source.
- Keep local-only icon bytes (`iconBytes`) only when a URL cannot be obtained at archive time (offline or scrape failure) or when migrating legacy `iconBase64`.
- Never use `PackageManager` as a passive display fallback; only capture once at archive time when needed.
- Backfill/upgrade entries missing `iconUrl` and clear `iconBytes` once a URL exists.

---

## Decision

The archive page uses **Play Store icons by default** for consistency. The stored value is the **base** Play Store CDN URL (no sizing suffix) so the UI can request different sizes by appending `=w{sizePx}` at load time.

There is a short window where we may not have a URL at archive time (most commonly offline; also possible when the scrape fails). In that case we capture the installed app icon via `PackageManager` as `iconBytes` so the user still sees an icon immediately. Later, a backfill/upgrade writes `iconUrl` and clears `iconBytes`.

---

## Goal (Before → After)

| Layer | Before | After |
|-------|--------|-------|
| Firestore | `iconBase64` (up to 24 KB) | `iconUrl` (plain string, base CDN URL) |
| Room | `iconBytes ByteArray?` | `iconUrl String?` + `iconBytes ByteArray?` (local capture / legacy only) |
| Load priority | memory → DB bytes → PackageManager | LRU → URL → bytes → re-scrape → placeholder |
| PackageManager use | Primary or passive fallback | Capture only at archive time, only when URL missing |
| Icon shape | Inconsistent | Squircle everywhere (archive + wishlist) |
| Loading state | Mixed | Per-item shimmer placeholder until resolved |

---

## Data Model

### `ArchivedApp.kt`

- `iconUrl: String?` is the primary source.
- `iconBytes: ByteArray?` exists only for the local capture window and legacy migration; it is cleared once `iconUrl` is present.

```kotlin
data class ArchivedApp(
    val packageId: String,
    val name: String,
    val isPlayStoreInstalled: Boolean = true,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val iconBytes: ByteArray? = null,
    val iconUrl: String? = null,
    val archivedSizeBytes: Long? = null,
    val archivedAt: Long = System.currentTimeMillis(),
    val lastTimeUsed: Long = 0L,
    val folderName: String? = null,
    val lastModified: Long = System.currentTimeMillis()
)
```

If `ArchivedApp` implements custom `equals()`/`hashCode()`, include `iconUrl` there as well.

### `AppEntity.kt` + Room migration

```kotlin
@Entity(tableName = "archived_apps")
data class AppEntity(
    @PrimaryKey val packageId: String,
    val name: String,
    val category: String?,
    val tags: String,
    val notes: String?,
    val iconBytes: ByteArray?,
    val iconUrl: String?,
    val archivedSizeBytes: Long?,
    val isPlayStoreInstalled: Boolean = true,
    val archivedAt: Long = System.currentTimeMillis(),
    val lastTimeUsed: Long = 0L,
    val folderName: String? = null,
    val lastModified: Long = System.currentTimeMillis()
)
```

```kotlin
val MIGRATION_N_to_N1 = object : Migration(N, N + 1) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE archived_apps ADD COLUMN iconUrl TEXT DEFAULT NULL")
    }
}
```

### `AppMapper.kt`

```kotlin
fun AppEntity.toArchivedApp(): ArchivedApp = ArchivedApp(
    packageId            = packageId,
    name                 = name,
    isPlayStoreInstalled = isPlayStoreInstalled,
    category             = category,
    tags                 = if (tags.isNotBlank()) tags.split(",") else emptyList(),
    notes                = notes,
    iconBytes            = iconBytes,
    iconUrl              = iconUrl,
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
    iconUrl              = iconUrl,
    archivedSizeBytes    = archivedSizeBytes,
    isPlayStoreInstalled = isPlayStoreInstalled,
    archivedAt           = archivedAt,
    lastTimeUsed         = lastTimeUsed,
    folderName           = folderName,
    lastModified         = lastModified
)
```

---

## Flows

### Archive-time capture (`CaptureAppUseCase`)

- Always capture local metadata (name/size/category) via `PackageManager` details use case.
- If online, attempt Play Store scrape.
- If `iconUrl` is missing after that, capture installed icon bytes once (before uninstall).

```kotlin
val isOnline = isNetworkAvailable()
val playInfo = if (isOnline) runCatching { playScraper.fetch(packageId) }.getOrNull() else null

val iconUrl = playInfo?.iconUrl?.ifBlank { null }
val iconBytes = if (iconUrl == null) captureInstalledIcon(packageId) else null
```

### Startup backfill

Backfill any entries where `iconUrl` is missing, then clear `iconBytes` once the URL is set. Rate-limit scrapes.

```kotlin
val needsUrl = dao.getArchivedAppsSnapshot().filter { it.iconUrl.isNullOrBlank() }
needsUrl.forEachIndexed { index, entity ->
    if (index > 0) delay(800L)
    val info = runCatching { playScraper.fetch(entity.packageId) }.getOrNull() ?: return@forEachIndexed
    val updated = entity.copy(iconUrl = info.iconUrl, iconBytes = null, lastModified = System.currentTimeMillis())
    dao.upsertApp(updated)
    enqueueUpsert(updated.toArchivedApp())
}
```

### Connectivity-triggered upgrade

On a transition to validated internet, upgrade only entries that have bytes but no URL.

```kotlin
val offlineEntries = dao.getArchivedAppsSnapshot()
    .filter { it.iconUrl.isNullOrBlank() && it.iconBytes != null }
```

---

## Implementation Checklist (By File)

### 1) `PlayStoreScraper.kt` (robust scraping + normalization)

```kotlin
private fun extractIconUrl(doc: Document): String? {
    doc.select("meta[property=og:image]").attr("content").takeIf { it.isNotBlank() }
        ?.let { return normalizeIconUrl(it) }

    doc.select("img[itemprop=image]").attr("src").takeIf { it.isNotBlank() }
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

```kotlin
fun normalizeIconUrl(raw: String?, sizePx: Int = 0): String? {
    val url = raw?.trim()?.takeIf { it.startsWith("https://") } ?: return null
    val base = url.substringBefore("=w").substringBefore("=s").trimEnd('=')
    return if (sizePx > 0) "$base=w$sizePx" else base
}
```

### 2) `AppRepositoryImpl.kt` (Firestore + merge semantics)

- Write `iconUrl`.
- Delete legacy `iconBase64` field as a temporary cleanup step.
- Read legacy `iconBase64` only for compatibility and map it to `iconBytes`.
- Merge prefers URL and clears bytes once a URL exists.

```kotlin
"iconBase64" to FieldValue.delete(),
"iconUrl"    to app.iconUrl,
```

```kotlin
val legacyIconBytes = runCatching { decodeIconBase64(getString("iconBase64")) }.getOrNull()
val iconUrl = getString("iconUrl")?.takeIf { it.isNotBlank() }
```

```kotlin
val resolvedIconUrl = remoteApp.iconUrl ?: localApp.iconUrl
val resolvedIconBytes = if (resolvedIconUrl != null) null else remoteApp.iconBytes ?: localApp.iconBytes
```

Also remove old Firestore icon compression utilities once the migration is fully rolled out.

### 3) `AppRepository` / `AppRepositoryImpl` (support tier-4 rescrape writes)

```kotlin
suspend fun updateIconUrl(packageId: String, iconUrl: String)
```

```kotlin
override suspend fun updateIconUrl(packageId: String, iconUrl: String) {
    val current = dao.getAppById(packageId) ?: return
    val updated = current.copy(iconUrl = iconUrl, iconBytes = null, lastModified = System.currentTimeMillis())
    dao.upsertApp(updated)
    enqueueUpsert(updated.toArchivedApp())
}
```

### 4) `AppIconFetcher.kt` (four-tier chain, no passive PackageManager)

```kotlin
override suspend fun fetch(): FetchResult? {
    val packageName = data.packageName

    iconCacheManager?.get(packageName)?.let { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
            return DrawableResult(BitmapDrawable(context.resources, bmp),
                isSampled = false, dataSource = DataSource.MEMORY_CACHE)
        }
    }

    val app = appRepository?.getAppById(packageName)

    app?.iconUrl?.let { baseUrl ->
        return fetchUrl(baseUrl, packageName, sizePx = targetSizePx())
    }

    app?.iconBytes?.let { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
            iconCacheManager?.put(packageName, bytes)
            return DrawableResult(BitmapDrawable(context.resources, bmp),
                isSampled = false, dataSource = DataSource.DISK)
        }
    }

    val scrapedUrl = withContext(Dispatchers.IO) {
        runCatching { playScraper.fetch(packageName)?.iconUrl }.getOrNull()
    }
    if (scrapedUrl != null) {
        withContext(Dispatchers.IO) { appRepository?.updateIconUrl(packageName, scrapedUrl) }
        return fetchUrl(scrapedUrl, packageName, sizePx = targetSizePx())
    }

    return null
}
```

Ensure `PlayStoreScraper` is injected into the fetcher factory.

### 5) Squircle shape in archive layouts

Use `ShapeableImageView` for archive icons to match wishlist. Apply the same overlay to folder preview slots.

```xml
<style name="SquircleIconShape" parent="">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">22%</item>
</style>
```

```xml
<com.google.android.material.imageview.ShapeableImageView
    android:id="@+id/app_icon"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:scaleType="centerCrop"
    app:shapeAppearanceOverlay="@style/SquircleIconShape" />
```

### 6) Shimmer placeholder (archive + wishlist)

Use `com.facebook.shimmer:shimmer:0.5.0` and a shared factory.

```kotlin
package com.tool.decluttr.presentation.util

import android.content.Context
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerDrawable

object ShimmerPlaceholder {
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

Wishlist and archive should both use the same placeholder pattern:

```kotlin
private val shimmerPlaceholder: ShimmerDrawable by lazy {
    ShimmerPlaceholder.create(itemView.context)
}

iconView.load(modelOrUrl) {
    placeholder(shimmerPlaceholder)
    error(R.drawable.ic_app_placeholder)
    crossfade(true)
    crossfade(200)
}
```

```kotlin
implementation("com.facebook.shimmer:shimmer:0.5.0")
```

---

## Icon Resolution Matrix (Display)

| Scenario | Tier 1 | Tier 2 | Tier 3 | Tier 4 | Result |
|----------|--------|--------|--------|--------|--------|
| LRU warm | ✅ hit | — | — | — | Instant |
| URL present, cached | miss | ✅ | — | — | Fast |
| URL present, first load | miss | ✅ | — | — | CDN fetch → cached |
| URL missing at archive time | miss | ❌ | ✅ bytes | — | Local icon immediately |
| Legacy `iconBase64` entry | miss | ❌ | ✅ bytes | — | Local icon until backfill |
| No URL, no bytes | miss | ❌ | ❌ | ✅ scrape | Shimmer → icon |
| Scrape fails / no Play Store page | miss | ❌ | ❌ | ❌ | Shimmer → placeholder |

---

## Migration Behavior

- Legacy Firestore `iconBase64` is read into `iconBytes` for compatibility.
- Backfill sets `iconUrl` and clears `iconBytes` progressively.
- Connectivity upgrade accelerates the same process when coming online.
- Offline/local-captured bytes never sync to Firestore.
