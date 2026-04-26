# Decluttr — Wishlist Play Store Scraper Implementation

> Feature: Share a Play Store URL into Decluttr → app name + icon fetched automatically → saved to wishlist

---

## File structure to create

```
app/src/main/java/com/tool/decluttr/
├── data/
│   ├── local/
│   │   ├── dao/
│   │   │   └── WishlistDao.kt              ← new
│   │   └── entity/
│   │       └── WishlistEntity.kt           ← new
│   ├── remote/
│   │   └── PlayStoreScraper.kt             ← new
│   └── repository/
│       └── WishlistRepositoryImpl.kt       ← new
├── domain/
│   ├── model/
│   │   └── WishlistApp.kt                  ← new
│   └── repository/
│       └── WishlistRepository.kt           ← new
├── presentation/
│   └── screens/
│       └── wishlist/
│           ├── WishlistFragment.kt         ← new
│           └── WishlistViewModel.kt        ← new
└── receiver/
    └── ShareReceiverActivity.kt            ← modify existing
```

---

## Step 1 — Add Jsoup dependency

**`app/build.gradle.kts`**

```kotlin
dependencies {
    // existing deps ...
    implementation("org.jsoup:jsoup:1.17.2")
}
```

Sync project after adding.

---

## Step 2 — Domain model

**`domain/model/WishlistApp.kt`**

```kotlin
package com.tool.decluttr.domain.model

data class WishlistApp(
    val packageId: String,
    val name: String,
    val iconUrl: String,          // empty string if app is installed locally
    val description: String,
    val playStoreUrl: String,
    val addedAt: Long = System.currentTimeMillis(),
    val notes: String = "",
)
```

---

## Step 3 — Room entity + DAO

**`data/local/entity/WishlistEntity.kt`**

```kotlin
package com.tool.decluttr.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wishlist")
data class WishlistEntity(
    @PrimaryKey val packageId: String,
    val name: String,
    val iconUrl: String,
    val description: String,
    val playStoreUrl: String,
    val addedAt: Long,
    val notes: String,
)
```

**`data/local/dao/WishlistDao.kt`**

```kotlin
package com.tool.decluttr.data.local.dao

import androidx.room.*
import com.tool.decluttr.data.local.entity.WishlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {

    @Query("SELECT * FROM wishlist ORDER BY addedAt DESC")
    fun getAll(): Flow<List<WishlistEntity>>

    @Query("SELECT * FROM wishlist WHERE packageId = :packageId LIMIT 1")
    suspend fun getById(packageId: String): WishlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WishlistEntity)

    @Query("DELETE FROM wishlist WHERE packageId = :packageId")
    suspend fun delete(packageId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM wishlist WHERE packageId = :packageId)")
    suspend fun exists(packageId: String): Boolean
}
```

**`data/local/DecluttrDatabase.kt`** — add the new entity and DAO:

```kotlin
// Add WishlistEntity to the entities list and bump version
@Database(
    entities = [AppEntity::class, WishlistEntity::class],  // ← add WishlistEntity
    version = 6,                                            // ← bump version
    exportSchema = false
)
abstract class DecluttrDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun wishlistDao(): WishlistDao                 // ← add this

    companion object {
        // Add a no-op migration since we're just adding a new table
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS wishlist (
                        packageId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        iconUrl TEXT NOT NULL,
                        description TEXT NOT NULL,
                        playStoreUrl TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        notes TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
```

---

## Step 4 — Play Store scraper

**`data/remote/PlayStoreScraper.kt`**

```kotlin
package com.tool.decluttr.data.remote

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

data class PlayStoreAppInfo(
    val packageId: String,
    val name: String,
    val iconUrl: String,
    val description: String,
)

@Singleton
class PlayStoreScraper @Inject constructor() {

    suspend fun fetch(packageId: String): PlayStoreAppInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val doc = Jsoup
                    .connect("https://play.google.com/store/apps/details?id=$packageId&hl=en")
                    .userAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36")
                    .timeout(8_000)
                    .get()

                // og:title arrives as "App Name - Tagline", strip the tagline part
                val rawTitle = doc.select("meta[property=og:title]").attr("content")
                val name = rawTitle.substringBefore(" - ").trim().ifBlank { rawTitle }

                // og:image is the app icon on play-lh.googleusercontent.com
                // Replace the size suffix for a clean 256 × 256 px icon
                val iconUrl = doc.select("meta[property=og:image]").attr("content")
                    .replace(Regex("=w\\d+-h\\d+.*$"), "=w256-h256")

                val description = doc
                    .select("meta[property=og:description]")
                    .attr("content")

                PlayStoreAppInfo(packageId, name, iconUrl, description)
            }
            .onFailure { it.printStackTrace() }
            .getOrNull()
        }

    companion object {
        /**
         * Pulls the package ID out of any Play Store share URL.
         * Handles:
         *   https://play.google.com/store/apps/details?id=com.example.app
         *   https://market.android.com/details?id=com.example.app
         *   market://details?id=com.example.app
         */
        fun extractPackageId(sharedUrl: String): String? =
            Uri.parse(sharedUrl).getQueryParameter("id")
    }
}
```

---

## Step 5 — Repository

**`domain/repository/WishlistRepository.kt`**

```kotlin
package com.tool.decluttr.domain.repository

import com.tool.decluttr.domain.model.WishlistApp
import kotlinx.coroutines.flow.Flow

interface WishlistRepository {
    fun getAll(): Flow<List<WishlistApp>>
    suspend fun add(app: WishlistApp)
    suspend fun remove(packageId: String)
    suspend fun exists(packageId: String): Boolean
    suspend fun updateNotes(packageId: String, notes: String)
}
```

**`data/repository/WishlistRepositoryImpl.kt`**

```kotlin
package com.tool.decluttr.data.repository

import com.tool.decluttr.data.local.dao.WishlistDao
import com.tool.decluttr.data.local.entity.WishlistEntity
import com.tool.decluttr.domain.model.WishlistApp
import com.tool.decluttr.domain.repository.WishlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WishlistRepositoryImpl @Inject constructor(
    private val dao: WishlistDao,
) : WishlistRepository {

    override fun getAll(): Flow<List<WishlistApp>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun add(app: WishlistApp) =
        dao.insert(app.toEntity())

    override suspend fun remove(packageId: String) =
        dao.delete(packageId)

    override suspend fun exists(packageId: String): Boolean =
        dao.exists(packageId)

    override suspend fun updateNotes(packageId: String, notes: String) {
        dao.getById(packageId)?.let { existing ->
            dao.insert(existing.copy(notes = notes))
        }
    }

    private fun WishlistEntity.toDomain() = WishlistApp(
        packageId   = packageId,
        name        = name,
        iconUrl     = iconUrl,
        description = description,
        playStoreUrl = playStoreUrl,
        addedAt     = addedAt,
        notes       = notes,
    )

    private fun WishlistApp.toEntity() = WishlistEntity(
        packageId   = packageId,
        name        = name,
        iconUrl     = iconUrl,
        description = description,
        playStoreUrl = playStoreUrl,
        addedAt     = addedAt,
        notes       = notes,
    )
}
```

---

## Step 6 — Hilt bindings

**`di/AppModule.kt`** — add inside the existing `@Module`:

```kotlin
// Wishlist DAO
@Provides
fun provideWishlistDao(db: DecluttrDatabase): WishlistDao = db.wishlistDao()

// Wishlist repository binding
@Provides @Singleton
fun provideWishlistRepository(impl: WishlistRepositoryImpl): WishlistRepository = impl
```

Also add the new migration to the Room builder:

```kotlin
@Provides @Singleton
fun provideDecluttrDatabase(app: Application): DecluttrDatabase {
    return Room.databaseBuilder(app, DecluttrDatabase::class.java, DATABASE_NAME)
        .addMigrations(
            DecluttrDatabase.MIGRATION_2_3,
            DecluttrDatabase.MIGRATION_3_4,
            DecluttrDatabase.MIGRATION_4_5,
            DecluttrDatabase.MIGRATION_5_6,   // ← add this
        )
        .build()
}
```

---

## Step 7 — ShareReceiverActivity

Replace the body of your existing `ShareReceiverActivity.kt`:

```kotlin
package com.tool.decluttr.receiver

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.tool.decluttr.R
import com.tool.decluttr.data.remote.PlayStoreAppInfo
import com.tool.decluttr.data.remote.PlayStoreScraper
import com.tool.decluttr.domain.model.WishlistApp
import com.tool.decluttr.presentation.screens.wishlist.WishlistViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : AppCompatActivity() {

    @Inject lateinit var scraper: PlayStoreScraper
    private val viewModel: WishlistViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: run {
            finish(); return
        }

        val packageId = PlayStoreScraper.extractPackageId(sharedText) ?: run {
            Toast.makeText(this, "Not a valid Play Store link", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val playStoreUrl = "https://play.google.com/store/apps/details?id=$packageId"

        lifecycleScope.launch {
            // Already on wishlist? Skip
            if (viewModel.exists(packageId)) {
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "Already in your wishlist",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }

            // 1. Try local PackageManager first — instant, no network
            val info = getLocalAppInfo(packageId)
                ?: scraper.fetch(packageId)   // 2. Fall back to Play Store HTML

            if (info != null) {
                showConfirmDialog(info, playStoreUrl)
            } else {
                // Offline and not installed — save with package ID only
                viewModel.add(
                    WishlistApp(
                        packageId    = packageId,
                        name         = packageId,
                        iconUrl      = "",
                        description  = "",
                        playStoreUrl = playStoreUrl,
                    )
                )
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "Saved to wishlist (details unavailable offline)",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun getLocalAppInfo(packageId: String): PlayStoreAppInfo? = try {
        val appInfo = packageManager.getApplicationInfo(packageId, 0)
        PlayStoreAppInfo(
            packageId   = packageId,
            name        = packageManager.getApplicationLabel(appInfo).toString(),
            iconUrl     = "",          // load from PackageManager directly in the dialog
            description = "",
        )
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun showConfirmDialog(info: PlayStoreAppInfo, playStoreUrl: String) {
        // Inflate a simple dialog view — create res/layout/dialog_wishlist_confirm.xml
        val view = layoutInflater.inflate(R.layout.dialog_wishlist_confirm, null)

        val iconView  = view.findViewById<android.widget.ImageView>(R.id.iv_app_icon)
        val nameView  = view.findViewById<android.widget.TextView>(R.id.tv_app_name)
        val descView  = view.findViewById<android.widget.TextView>(R.id.tv_app_desc)

        nameView.text = info.name
        descView.text = info.description.ifBlank { "No description available" }

        if (info.iconUrl.isNotBlank()) {
            iconView.load(info.iconUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_app_placeholder)
                error(R.drawable.ic_app_placeholder)
            }
        } else {
            // App is installed — load icon from PackageManager
            runCatching {
                iconView.setImageDrawable(packageManager.getApplicationIcon(info.packageId))
            }
        }

        AlertDialog.Builder(this, R.style.Theme_Decluttr_Dialog)
            .setView(view)
            .setPositiveButton("Add to Wishlist") { _, _ ->
                lifecycleScope.launch {
                    viewModel.add(
                        WishlistApp(
                            packageId    = info.packageId,
                            name         = info.name,
                            iconUrl      = info.iconUrl,
                            description  = info.description,
                            playStoreUrl = playStoreUrl,
                        )
                    )
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        "${info.name} added to wishlist",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
```

---

## Step 8 — WishlistViewModel

**`presentation/screens/wishlist/WishlistViewModel.kt`**

```kotlin
package com.tool.decluttr.presentation.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tool.decluttr.domain.model.WishlistApp
import com.tool.decluttr.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val repository: WishlistRepository,
) : ViewModel() {

    val wishlist: StateFlow<List<WishlistApp>> = repository
        .getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(app: WishlistApp) = viewModelScope.launch {
        repository.add(app)
    }

    fun remove(packageId: String) = viewModelScope.launch {
        repository.remove(packageId)
    }

    suspend fun exists(packageId: String): Boolean =
        repository.exists(packageId)

    fun updateNotes(packageId: String, notes: String) = viewModelScope.launch {
        repository.updateNotes(packageId, notes)
    }
}
```

---

## Step 9 — Confirm dialog layout

Create **`res/layout/dialog_wishlist_confirm.xml`**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="20dp"
    android:gravity="center_vertical"
    android:background="@color/surface">

    <ImageView
        android:id="@+id/iv_app_icon"
        android:layout_width="56dp"
        android:layout_height="56dp"
        android:scaleType="centerCrop"
        android:background="@drawable/bg_app_icon_placeholder" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="14dp"
        android:orientation="vertical">

        <TextView
            android:id="@+id/tv_app_name"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceTitleMedium"
            android:maxLines="1"
            android:ellipsize="end" />

        <TextView
            android:id="@+id/tv_app_desc"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textAppearance="?attr/textAppearanceBodySmall"
            android:alpha="0.6"
            android:maxLines="2"
            android:ellipsize="end" />

    </LinearLayout>

</LinearLayout>
```

---

## Step 10 — AndroidManifest.xml

The `ShareReceiverActivity` is already registered. No changes needed — `INTERNET` permission already exists and `play.google.com` is HTTPS so the network security config passes.

---

## End-to-end flow

```
User opens Play Store → taps Share → selects Decluttr
          ↓
ShareReceiverActivity.onCreate receives EXTRA_TEXT URL
          ↓
extractPackageId() → "com.example.app"
          ↓
Already on wishlist? → toast "Already saved" → finish()
          ↓
Is app installed on device?
    YES → PackageManager.getApplicationLabel/Icon  (instant, no network)
    NO  → Jsoup fetches play.google.com/store/apps/details?id=...
              reads og:title  → app name
              reads og:image  → icon URL (play-lh.googleusercontent.com)
              reads og:description → short description
          ↓
showConfirmDialog (icon + name + description)
          ↓
User taps "Add to Wishlist"
          ↓
WishlistViewModel.add() → WishlistRepositoryImpl → WishlistDao → Room
          ↓
Toast "App Name added to wishlist" → finish()
```

---

## Offline fallback behaviour

| State | Result |
|---|---|
| App installed + online | PackageManager (instant) |
| App installed + offline | PackageManager (instant) |
| App not installed + online | Jsoup fetch from Play Store |
| App not installed + offline | Saved with package ID only, details fetched on next open |

---

## Notes

- Jsoup parses only the `<head>` meta tags — the full page HTML is not stored or processed further.
- The icon URL (`play-lh.googleusercontent.com`) is a stable Google CDN link. Coil caches it to disk after first load so it shows instantly on subsequent opens even offline.
- The `og:title` cleanup (`substringBefore(" - ")`) is needed because Google formats the title as `"PDF Merger Pro - Combine PDFs"`. Without it the full string shows in the dialog.
- If Google changes the Play Store page structure, only the Jsoup selectors in `PlayStoreScraper.kt` need updating — nothing else in the architecture changes.

*Generated April 2026*
