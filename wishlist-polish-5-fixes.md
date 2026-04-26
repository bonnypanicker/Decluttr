# Decluttr — Wishlist Polish: 5-Issue Implementation Guide

> Covers: squircle icons · sort & category pills · Firestore sync · status bar overlap · Play Store share boost

---

## Fix 1 — Squircle icons (Wishlist list + Share popup)

### Root cause
`item_archived_app_list.xml` uses a plain `ImageView` with no clipping. Icons loaded from `play-lh.googleusercontent.com` are full square JPEGs. Without a clip shape they render as squares.

### 1a — Add a squircle drawable

Create **`res/drawable/bg_squircle_icon.xml`**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="16dp" />
    <solid android:color="@android:color/transparent" />
</shape>
```

### 1b — Replace the plain `ImageView` with `ShapeableImageView`

In every layout that shows a wishlist icon:

**`res/layout/item_wishlist_app.xml`** (your wishlist row) — change the icon view:

```xml
<!-- BEFORE -->
<ImageView
    android:id="@+id/app_icon"
    android:layout_width="48dp"
    android:layout_height="48dp" />

<!-- AFTER -->
<com.google.android.material.imageview.ShapeableImageView
    android:id="@+id/app_icon"
    android:layout_width="48dp"
    android:layout_height="48dp"
    app:shapeAppearanceOverlay="@style/SquircleIconShape"
    android:scaleType="centerCrop" />
```

Add the style to **`res/values/themes.xml`**:

```xml
<style name="SquircleIconShape">
    <item name="cornerFamily">rounded</item>
    <item name="cornerSize">22%</item>
</style>
```

22% of 48dp = ~10.5dp radius, matching Play Store's own icon corner radius exactly.

**`res/layout/dialog_wishlist_confirm.xml`** (share popup icon) — same change, but use 56dp size:

```xml
<com.google.android.material.imageview.ShapeableImageView
    android:id="@+id/iv_app_icon"
    android:layout_width="56dp"
    android:layout_height="56dp"
    app:shapeAppearanceOverlay="@style/SquircleIconShape"
    android:scaleType="centerCrop" />
```

### 1c — Load with RoundedCorners transformation as a fallback (Coil)

In your wishlist adapter's `onBindViewHolder`, use a Coil `RoundedCorners` transform as a safety net in case `ShapeableImageView` isn't used everywhere:

```kotlin
// WishlistAdapter.kt
iconView.load(item.iconUrl) {
    crossfade(true)
    transformations(RoundedCornersTransformation(radius = 24f)) // ~10.5dp at density 2.0
    placeholder(R.drawable.ic_app_placeholder)
    error(R.drawable.ic_app_placeholder)
}
```

Add import: `import coil.transform.RoundedCornersTransformation`

> Use `ShapeableImageView` (1b) as the primary fix and `RoundedCornersTransformation` (1c) as backup — they compose safely and won't double-round.

---

## Fix 2 — Sort options + category pills

### 2a — Sort enum

Create **`domain/model/WishlistSortOption.kt`**:

```kotlin
package com.tool.decluttr.domain.model

enum class WishlistSortOption {
    DATE_ADDED,      // default — newest first
    ALPHABETICAL,    // A → Z
    CATEGORY,        // grouped by inferred category
}
```

### 2b — Update `WishlistViewModel`

```kotlin
// WishlistViewModel.kt
@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val repository: WishlistRepository,
) : ViewModel() {

    val sortOption = MutableStateFlow(WishlistSortOption.DATE_ADDED)
    val selectedCategory = MutableStateFlow("All")

    // All distinct categories derived from live list
    val categories: StateFlow<List<String>> = repository.getAll()
        .map { apps ->
            val cats = apps.mapNotNull { it.category }.distinct().sorted()
            listOf("All") + cats
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf("All"))

    // Filtered + sorted list
    val wishlist: StateFlow<List<WishlistApp>> = combine(
        repository.getAll(),
        sortOption,
        selectedCategory,
    ) { apps, sort, category ->
        val filtered = if (category == "All") apps
                       else apps.filter { it.category == category }
        when (sort) {
            WishlistSortOption.DATE_ADDED    -> filtered.sortedByDescending { it.addedAt }
            WishlistSortOption.ALPHABETICAL  -> filtered.sortedBy { it.name.lowercase() }
            WishlistSortOption.CATEGORY      -> filtered.sortedWith(
                compareBy({ it.category ?: "Zzz" }, { it.name.lowercase() })
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSortOption(option: WishlistSortOption) { sortOption.value = option }
    fun setCategory(category: String) { selectedCategory.value = category }

    fun add(app: WishlistApp) = viewModelScope.launch { repository.add(app) }
    fun remove(packageId: String) = viewModelScope.launch { repository.remove(packageId) }
    suspend fun exists(packageId: String) = repository.exists(packageId)
    fun updateNotes(packageId: String, notes: String) =
        viewModelScope.launch { repository.updateNotes(packageId, notes) }
}
```

### 2c — Add sort button + chip row to WishlistFragment layout

In **`res/layout/fragment_wishlist.xml`**, add above the RecyclerView:

```xml
<!-- Sort row -->
<LinearLayout
    android:id="@+id/sort_row"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:paddingHorizontal="16dp"
    android:paddingBottom="4dp"
    android:gravity="center_vertical">

    <!-- Category chip scroll -->
    <HorizontalScrollView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:scrollbars="none">

        <com.google.android.material.chip.ChipGroup
            android:id="@+id/chip_group_categories"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            app:singleSelection="true"
            app:selectionRequired="true" />

    </HorizontalScrollView>

    <!-- Sort button -->
    <ImageButton
        android:id="@+id/btn_sort"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:src="@drawable/ic_sort"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="Sort"
        android:tint="?attr/colorOnSurface" />

</LinearLayout>
```

### 2d — Wire up in WishlistFragment

```kotlin
// WishlistFragment.kt — inside onViewCreated

// Sort popup
btnSort.setOnClickListener { anchor ->
    val popup = PopupMenu(requireContext(), anchor)
    popup.menu.apply {
        add(0, 0, 0, "Date Added").isChecked = viewModel.sortOption.value == WishlistSortOption.DATE_ADDED
        add(0, 1, 1, "Alphabetical").isChecked = viewModel.sortOption.value == WishlistSortOption.ALPHABETICAL
        add(0, 2, 2, "Category").isChecked = viewModel.sortOption.value == WishlistSortOption.CATEGORY
    }
    popup.setOnMenuItemClickListener { item ->
        viewModel.setSortOption(
            when (item.itemId) {
                1 -> WishlistSortOption.ALPHABETICAL
                2 -> WishlistSortOption.CATEGORY
                else -> WishlistSortOption.DATE_ADDED
            }
        )
        true
    }
    popup.show()
}

// Category chips — rebuild only when list changes
viewLifecycleOwner.lifecycleScope.launch {
    viewModel.categories.collect { categories ->
        chipGroupCategories.removeAllViews()
        categories.forEach { cat ->
            val chip = Chip(requireContext()).apply {
                text = cat
                isCheckable = true
                isChecked = cat == viewModel.selectedCategory.value
                setOnCheckedChangeListener { _, checked ->
                    if (checked) viewModel.setCategory(cat)
                }
            }
            chipGroupCategories.addView(chip)
        }
    }
}
```

### 2e — Infer category from `og:description` in `PlayStoreScraper`

The Play Store HTML also contains a category breadcrumb. Scrape it alongside the title:

```kotlin
// PlayStoreScraper.kt — inside the fetch() runCatching block, after description

// Category sits in an itemprop="genre" element
val category = doc.select("[itemprop=genre]").first()?.text()
    ?: doc.select("a[href*='/store/apps/category/']").first()?.text()

PlayStoreAppInfo(packageId, name, iconUrl, description, category)
```

Update `PlayStoreAppInfo` to add `val category: String?`.

Update `WishlistApp` model to add `val category: String? = null`.

Update `WishlistEntity` to add `val category: String?`.

Add to the Room migration (bump version and update `MIGRATION_5_6` or add `MIGRATION_6_7`):

```kotlin
database.execSQL("ALTER TABLE wishlist ADD COLUMN category TEXT")
```

---

## Fix 3 — Firestore sync for Wishlist

Mirror the existing `AppRepositoryImpl` sync pattern.

### 3a — `WishlistRepositoryImpl` with Firestore

```kotlin
// data/repository/WishlistRepositoryImpl.kt

@Singleton
class WishlistRepositoryImpl @Inject constructor(
    private val dao: WishlistDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : WishlistRepository {

    // ── Local reads ──────────────────────────────────────────────────────────

    override fun getAll(): Flow<List<WishlistApp>> =
        dao.getAll().map { it.map { e -> e.toDomain() } }

    override suspend fun exists(packageId: String) = dao.exists(packageId)

    // ── Writes: local first, then cloud ─────────────────────────────────────

    override suspend fun add(app: WishlistApp) {
        dao.insert(app.toEntity())
        syncToFirestore(app)
    }

    override suspend fun remove(packageId: String) {
        dao.delete(packageId)
        deleteFromFirestore(packageId)
    }

    override suspend fun updateNotes(packageId: String, notes: String) {
        dao.getById(packageId)?.let { existing ->
            val updated = existing.copy(notes = notes, lastModified = System.currentTimeMillis())
            dao.insert(updated)
            syncToFirestore(updated.toDomain())
        }
    }

    // ── Firestore write ──────────────────────────────────────────────────────

    private suspend fun syncToFirestore(app: WishlistApp) =
        withContext(Dispatchers.IO) {
            val uid = auth.currentUser?.uid ?: return@withContext
            runCatching {
                firestore
                    .collection("users").document(uid)
                    .collection("wishlist").document(app.packageId)
                    .set(app.toFirestoreMap())
                    .await()
            }.onFailure { it.printStackTrace() }
        }

    private suspend fun deleteFromFirestore(packageId: String) =
        withContext(Dispatchers.IO) {
            val uid = auth.currentUser?.uid ?: return@withContext
            runCatching {
                firestore
                    .collection("users").document(uid)
                    .collection("wishlist").document(packageId)
                    .delete()
                    .await()
            }.onFailure { it.printStackTrace() }
        }

    // ── One-shot sync on login ────────────────────────────────────────────────

    override suspend fun syncFromFirestore() = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        runCatching {
            val snapshot = firestore
                .collection("users").document(uid)
                .collection("wishlist")
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
        }.onFailure { it.printStackTrace() }
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private fun WishlistApp.toFirestoreMap() = mapOf(
        "packageId"    to packageId,
        "name"         to name,
        "iconUrl"      to iconUrl,       // store URL, not bytes — avoids 1MB doc limit
        "description"  to description,
        "playStoreUrl" to playStoreUrl,
        "category"     to (category ?: ""),
        "notes"        to notes,
        "addedAt"      to addedAt,
        "lastModified" to (lastModified),
    )

    private fun DocumentSnapshot.toWishlistAppOrNull(): WishlistApp? {
        val pkg = getString("packageId") ?: id.takeIf { it.isNotBlank() } ?: return null
        return WishlistApp(
            packageId    = pkg,
            name         = getString("name") ?: pkg,
            iconUrl      = getString("iconUrl") ?: "",
            description  = getString("description") ?: "",
            playStoreUrl = getString("playStoreUrl")
                           ?: "https://play.google.com/store/apps/details?id=$pkg",
            category     = getString("category")?.ifBlank { null },
            notes        = getString("notes") ?: "",
            addedAt      = getLong("addedAt") ?: System.currentTimeMillis(),
            lastModified = getLong("lastModified") ?: System.currentTimeMillis(),
        )
    }

    private fun WishlistEntity.toDomain() = WishlistApp(
        packageId    = packageId, name = name, iconUrl = iconUrl,
        description  = description, playStoreUrl = playStoreUrl,
        category     = category, notes = notes,
        addedAt      = addedAt, lastModified = lastModified,
    )

    private fun WishlistApp.toEntity() = WishlistEntity(
        packageId    = packageId, name = name, iconUrl = iconUrl,
        description  = description, playStoreUrl = playStoreUrl,
        category     = category, notes = notes,
        addedAt      = addedAt, lastModified = lastModified,
    )
}
```

### 3b — Add `lastModified` to `WishlistApp` + `WishlistEntity`

```kotlin
// WishlistApp.kt
data class WishlistApp(
    val packageId: String,
    val name: String,
    val iconUrl: String,
    val description: String,
    val playStoreUrl: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),  // ← add
    val notes: String = "",
    val category: String? = null,                         // ← add (from Fix 2)
)
```

```kotlin
// WishlistEntity.kt — add two fields
val lastModified: Long,
val category: String?,
```

Room migration — add both columns in one step:

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS wishlist (
                packageId    TEXT NOT NULL PRIMARY KEY,
                name         TEXT NOT NULL,
                iconUrl      TEXT NOT NULL,
                description  TEXT NOT NULL,
                playStoreUrl TEXT NOT NULL,
                addedAt      INTEGER NOT NULL,
                lastModified INTEGER NOT NULL,
                notes        TEXT NOT NULL,
                category     TEXT
            )
        """.trimIndent())
    }
}
```

### 3c — Add `getAllSnapshot()` to `WishlistDao`

```kotlin
// WishlistDao.kt
@Query("SELECT * FROM wishlist")
suspend fun getAllSnapshot(): List<WishlistEntity>   // non-Flow, for sync use
```

### 3d — Call `syncFromFirestore()` on login

In `DashboardViewModel` or wherever auth state is observed, add alongside the archive sync:

```kotlin
// DashboardViewModel.kt — inside the auth state listener block
authRepository.observeAuthState().collect { user ->
    if (user != null) {
        launch { appRepository.syncFromFirestore() }
        launch { wishlistRepository.syncFromFirestore() }   // ← add
    }
}
```

### 3e — Hilt binding update

```kotlin
// di/AppModule.kt
@Provides @Singleton
fun provideWishlistRepository(impl: WishlistRepositoryImpl): WishlistRepository = impl
```

---

## Fix 4 — Wishlist header overlaps the status bar

### Root cause
`MainActivity` calls `enableEdgeToEdge()`, which sets the app to draw behind the system bars. Every fragment that has a toolbar or custom header must apply top window insets as padding/margin. The wishlist fragment does not do this, so the header draws under the status bar.

### 4a — Apply insets in WishlistFragment

```kotlin
// WishlistFragment.kt — inside onViewCreated, before any other setup

ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    // Push the whole fragment content down by the status bar height
    binding.root.setPadding(
        binding.root.paddingLeft,
        systemBars.top,          // ← this is the fix
        binding.root.paddingRight,
        binding.root.paddingBottom,
    )
    insets
}
```

If you use a `Toolbar` or separate header view rather than padding the root, target that view instead:

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = systemBars.top
    }
    insets
}
```

### 4b — Also handle bottom insets for the RecyclerView

The RecyclerView should not be clipped by the navigation bar:

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.rvWishlist) { view, insets ->
    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    view.updatePadding(bottom = systemBars.bottom)
    view.clipToPadding = false   // lets items scroll under nav bar, then pad to avoid it
    insets
}
```

### 4c — Prevent double-applying insets

If the wishlist layout already has a `fitsSystemWindows="true"` somewhere in its hierarchy, remove it — `setOnApplyWindowInsetsListener` and `fitsSystemWindows` conflict with each other and produce unpredictable padding.

---

## Fix 5 — Show Decluttr only for Play Store shares + boost priority

### Root cause
The current `ShareReceiverActivity` intent filter matches **all** `text/plain` shares — every app that shares text (Chrome, WhatsApp, Twitter, Notes) will show Decluttr in their share sheet. This pollutes the share sheet for unrelated content and makes Decluttr's icon show up in confusing places.

### 5a — Tighten the intent filter to Play Store URLs only

Replace the existing `ShareReceiverActivity` intent filter in **`AndroidManifest.xml`**:

```xml
<activity
    android:name=".receiver.ShareReceiverActivity"
    android:exported="true"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:theme="@android:style/Theme.Translucent.NoTitleBar">

    <!-- ✅ Filter 1: play.google.com URLs shared as text/plain -->
    <intent-filter android:priority="100">
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>

    <!-- ✅ Filter 2: Deep link — tapping a Play Store URL directly opens Decluttr -->
    <intent-filter android:autoVerify="true" android:priority="100">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="play.google.com"
            android:pathPrefix="/store/apps/details" />
    </intent-filter>

    <!-- ✅ Filter 3: market:// URI (some older Play Store versions share this format) -->
    <intent-filter android:priority="100">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="market" />
    </intent-filter>

</activity>
```

`android:priority="100"` is the maximum allowed for third-party apps. This makes Decluttr appear at the top of the share sheet when a Play Store URL is being shared.

### 5b — Guard against non-Play Store text in `ShareReceiverActivity`

Since filter 1 still matches any `text/plain`, add a URL check as the very first gate:

```kotlin
// ShareReceiverActivity.kt — inside handleIntent()

private fun handleIntent(intent: Intent) {
    when (intent.action) {
        Intent.ACTION_SEND -> {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: run { finish(); return }
            // Reject non-Play Store text immediately — don't show Decluttr for WhatsApp messages etc.
            if (!isPlayStoreUrl(sharedText)) {
                finish()
                return
            }
            val packageId = ExtractPackageIdUseCase()(sharedText) ?: run { finish(); return }
            processPackageId(packageId, sharedText)
        }
        Intent.ACTION_VIEW -> {
            val url = intent.dataString ?: run { finish(); return }
            val packageId = ExtractPackageIdUseCase()(url) ?: run { finish(); return }
            processPackageId(packageId, url)
        }
        else -> finish()
    }
}

private fun isPlayStoreUrl(text: String): Boolean {
    return text.contains("play.google.com/store/apps/details") ||
           text.contains("market://details") ||
           text.contains("market://search")
}
```

### 5c — Icon visibility in the share popup

Coil already loads the icon in the share confirm dialog. To make the icon appear faster and avoid a blank flash:

```kotlin
// In showConfirmDialog() — show a coloured placeholder derived from the app name
// while the real icon loads

val placeholderColor = generateColorFromString(info.name)
val placeholder = ColorDrawable(placeholderColor)

if (info.iconUrl.isNotBlank()) {
    iconView.load(info.iconUrl) {
        crossfade(true)
        placeholder(placeholder)       // coloured square while loading
        error(placeholder)             // keep colour if load fails
        memoryCachePolicy(CachePolicy.ENABLED)
        diskCachePolicy(CachePolicy.ENABLED)
    }
} else {
    // Installed app — use PackageManager
    runCatching {
        iconView.setImageDrawable(packageManager.getApplicationIcon(info.packageId))
    }.onFailure {
        iconView.setImageDrawable(placeholder)
    }
}

// Generates a consistent color from a string (no random flicker on rebind)
private fun generateColorFromString(input: String): Int {
    val hash = input.fold(0) { acc, c -> acc * 31 + c.code }
    val hue = (Math.abs(hash) % 360).toFloat()
    return android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.55f))
}
```

---

## Summary of all file changes

| File | Action | Fix |
|---|---|---|
| `res/drawable/bg_squircle_icon.xml` | Create | 1 |
| `res/values/themes.xml` | Add `SquircleIconShape` style | 1 |
| `res/layout/item_wishlist_app.xml` | `ImageView` → `ShapeableImageView` | 1 |
| `res/layout/dialog_wishlist_confirm.xml` | `ImageView` → `ShapeableImageView` | 1 |
| `WishlistAdapter.kt` | Add `RoundedCornersTransformation` | 1 |
| `domain/model/WishlistSortOption.kt` | Create | 2 |
| `domain/model/WishlistApp.kt` | Add `category`, `lastModified` | 2 + 3 |
| `data/local/entity/WishlistEntity.kt` | Add `category`, `lastModified` | 2 + 3 |
| `data/local/dao/WishlistDao.kt` | Add `getAllSnapshot()` | 3 |
| `WishlistViewModel.kt` | Add sort + category filter streams | 2 |
| `res/layout/fragment_wishlist.xml` | Add chip group + sort button | 2 |
| `WishlistFragment.kt` | Wire chips, sort, and insets | 2 + 4 |
| `data/remote/PlayStoreScraper.kt` | Scrape `category` from page | 2 |
| `WishlistRepositoryImpl.kt` | Add full Firestore sync | 3 |
| `DashboardViewModel.kt` | Call `wishlistRepository.syncFromFirestore()` on login | 3 |
| `di/AppModule.kt` | Add WishlistRepository binding | 3 |
| `DecluttrDatabase.kt` | Bump version, add migration | 2 + 3 |
| `AndroidManifest.xml` | Tighten + add intent filters with priority | 5 |
| `ShareReceiverActivity.kt` | Guard non-Play Store text, handle ACTION_VIEW | 5 |

---

## Migration SQL (copy-paste ready)

```kotlin
// DecluttrDatabase.kt
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS wishlist (
                packageId    TEXT NOT NULL PRIMARY KEY,
                name         TEXT NOT NULL,
                iconUrl      TEXT NOT NULL,
                description  TEXT NOT NULL,
                playStoreUrl TEXT NOT NULL,
                addedAt      INTEGER NOT NULL,
                lastModified INTEGER NOT NULL DEFAULT 0,
                notes        TEXT NOT NULL DEFAULT '',
                category     TEXT
            )
        """.trimIndent())
    }
}
```

Add `MIGRATION_5_6` to the Room builder chain in `AppModule.kt`.

*Generated April 2026 against Decluttr-main v2*
