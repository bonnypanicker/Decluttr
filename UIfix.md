I now have a thorough understanding of the entire codebase. Let me create the comprehensive `.md` guide. Here's what I found:

**Key issues with current Discover page cards:**
1. `card_background.xml` is hardcoded `#FFFFFF` — breaks dark mode
2. App items are flat `LinearLayout`s with no card elevation, corners, or M3 surface treatment
3. Smart cards/storage meter use plain drawables instead of `MaterialCardView`
4. No theme-aware styling — everything relies on programmatic color setting from adapters
5. Rest of the app (Compose parts) uses proper M3 `Card`, `Surface`, `MaterialTheme.colorScheme`
Action: file_editor create /app/CARD_FIX_PROMPT.md --file-text "# Decluttr: Discovery Page Card Style Fix — Material 3 Implementation Guide

## Context & Problem

The Decluttr Android app's **Discover page** was recently refactored from Compose `LazyColumn` scrolling to native `AndroidView` + `RecyclerView` scrolling for scroll performance (jank fix). The trade-off: the native XML layout cards now look visually inconsistent with the rest of the Material 3 Compose app.

**The Compose parts** of the app (DashboardScreen, ArchivedAppsList, celebration dialogs, etc.) use proper Material 3 tokens — `MaterialTheme.colorScheme.surface`, `CardDefaults`, `RoundedCornerShape(24.dp)`, `Surface` with `tonalElevation`, etc. The native XML cards are plain `LinearLayout`s with a hardcoded white background drawable that breaks in dark mode and has no elevation, tonal surface treatment, or Material 3 shape language.

### Goal
Make every native XML card on the Discover page look like it belongs in the same app as the Compose-built screens — matching Material 3 color tokens, elevation, shape, and spacing — without touching the scrolling architecture (keep RecyclerView).

---

## Architecture Overview (Do Not Change)

```
DiscoveryScreen.kt (Compose)
  └── DiscoveryDashboard (Compose wrapper)
        └── AndroidView { RecyclerView }
              └── DiscoveryDashboardAdapter (heterogeneous list)
                    ├── VIEW_TYPE_STORAGE_METER   → item_storage_meter.xml
                    ├── VIEW_TYPE_PERMISSION_WARNING → item_permission_warning.xml
                    ├── VIEW_TYPE_SMART_CARD       → item_smart_card.xml
                    ├── VIEW_TYPE_ALL_APPS_HEADER  → item_all_apps_header.xml
                    ├── VIEW_TYPE_SEARCH_BAR       → item_search_bar.xml
                    └── VIEW_TYPE_APP_ITEM         → item_discovery_app.xml

  └── SpecificAppListDisplay (Compose wrapper for sub-lists)
        └── AndroidView { RecyclerView }
              └── DiscoveryAppsAdapter
                    └── item_discovery_app.xml
```

**Theme bridge**: Compose extracts `MaterialTheme.colorScheme` values as `Int` (ARGB) into `NativeThemeColors` data class, passed to adapters. This is how native views receive M3 colors.

---

## Files to Modify

| File | Type | Purpose |
|---|---|---|
| `app/src/main/res/layout/item_discovery_app.xml` | XML Layout | Individual app row item |
| `app/src/main/res/layout/item_smart_card.xml` | XML Layout | \"Rarely Used Apps\" / \"Large Apps\" cards |
| `app/src/main/res/layout/item_storage_meter.xml` | XML Layout | Storage waste meter card |
| `app/src/main/res/layout/item_permission_warning.xml` | XML Layout | Usage permission prompt card |
| `app/src/main/res/layout/item_all_apps_header.xml` | XML Layout | \"All Apps\" section header |
| `app/src/main/res/layout/item_search_bar.xml` | XML Layout | Inline search field |
| `app/src/main/res/drawable/card_background.xml` | Drawable | Shared card background (currently hardcoded white) |
| `app/src/main/res/values/themes.xml` | Theme | App theme definition |
| `presentation/screens/dashboard/DiscoveryAppsAdapter.kt` | Kotlin | Adapter for app list items |
| `presentation/screens/dashboard/DiscoveryDashboardAdapter.kt` | Kotlin | Adapter for all dashboard item types |
| `presentation/screens/dashboard/DiscoveryScreen.kt` | Kotlin | `NativeThemeColors` data class & Compose wrapper |

---

## Current Theme Reference (Match These)

### Color.kt (Compose side)
```kotlin
// Dark Mode
val DarkBackground = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)
val DarkPrimaryText = Color(0xFFE5E7EB)
val DarkSecondaryText = Color(0xFF9CA3AF)
val AccentTeal = Color(0xFF14B8A6)

// Light Mode
val Primary40 = Color(0xFF275CAA)
val Secondary40 = Color(0xFF535E78)
val Tertiary40 = Color(0xFF704A7B)
```

### Theme.kt
- Uses **dynamic colors** on Android 12+ (Material You)
- Falls back to custom `DarkColorScheme` / `LightColorScheme`
- Dark scheme explicitly sets `background`, `surface`, `onPrimary`, `onSecondary`, `onSurface`

### What Compose Cards Look Like in This App
From `DashboardScreen.kt` (celebration dialog):
```kotlin
Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.padding(16.dp)
)
```
From `ArchivedAppsList.kt` (folder items):
```kotlin
Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 8.dp,
)
```
From `DiscoveryScreen.kt` (uninstall progress):
```kotlin
Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
)
```

**Key M3 patterns used in this app:**
- `RoundedCornerShape(16.dp)` to `RoundedCornerShape(24.dp)` for cards
- `MaterialTheme.colorScheme.surface` as card container
- `MaterialTheme.colorScheme.surfaceVariant` with `alpha = 0.5f` for input fields
- `MaterialTheme.colorScheme.primaryContainer` for selected states
- `tonalElevation` for subtle depth differentiation

---

## Step-by-Step Implementation

### Step 1: Update `themes.xml` — Use Material 3 Base Theme

**File:** `app/src/main/res/values/themes.xml`

**Current (broken):**
```xml
<style name=\"Theme.Decluttr\" parent=\"android:Theme.Material.Light.NoActionBar\" />
```

This is the Android framework `Material` theme (not Material 3 / Material Components). It means native XML views don't have access to M3 attributes like `?attr/colorSurface`, `?attr/colorSurfaceVariant`, `?attr/colorOnSurface`, `?attr/shapeAppearanceCornerMedium`, etc.

**Replace with:**
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<resources>
    <style name=\"Theme.Decluttr.Splash\" parent=\"Theme.SplashScreen\">
        <item name=\"windowSplashScreenAnimatedIcon\">@android:drawable/sym_def_app_icon</item>
        <item name=\"windowSplashScreenBackground\">@android:color/white</item>
        <item name=\"postSplashScreenTheme\">@style/Theme.Decluttr</item>
    </style>

    <style name=\"Theme.Decluttr\" parent=\"Theme.Material3.DayNight.NoActionBar\">
        <!-- Material 3 base theme gives us all ?attr/colorSurface, 
             ?attr/colorOnSurface, ?attr/colorSurfaceVariant etc.
             Compose's DecluttrTheme overrides with dynamic colors at runtime,
             but this gives native XML views proper fallback tokens. -->
    </style>
</resources>
```

> **Why this matters:** Without `Theme.Material3.DayNight.NoActionBar` as the base, native views cannot resolve `?attr/colorSurface`, `?attr/colorOnSurface`, `?attr/colorSurfaceContainerHighest`, `?attr/shapeAppearanceCornerMedium`, or any Material 3 design tokens. The current `android:Theme.Material.Light.NoActionBar` is the old framework theme — it doesn't know about Material 3.

> **Dependency check:** The app already includes `androidx.compose.material3:material3` via BOM, which transitionally includes `com.google.android.material:material` (MDC). Verify `com.google.android.material:material:1.11.0` or later is in `build.gradle.kts`. If not, add:
> ```kotlin
> implementation(\"com.google.android.material:material:1.12.0\")
> ```

---

### Step 2: Replace `card_background.xml` with Theme-Aware Drawable

**File:** `app/src/main/res/drawable/card_background.xml`

**Current (broken in dark mode):**
```xml
<shape android:shape=\"rectangle\">
    <corners android:radius=\"16dp\" />
    <solid android:color=\"#FFFFFF\" />
</shape>
```

**Replace with:**
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:shape=\"rectangle\">
    <corners android:radius=\"16dp\" />
    <solid android:color=\"?attr/colorSurfaceContainerHigh\" />
</shape>
```

> `?attr/colorSurfaceContainerHigh` is the Material 3 token for elevated card surfaces. In light mode this resolves to a slightly tinted off-white; in dark mode it resolves to a lighter surface variant (e.g., `#1D2228` on your scheme). This is the closest match to how Compose's `Card` with `tonalElevation = 2.dp` renders.

> **Alternative** if you prefer the exact same `surface` as Compose Cards: use `?attr/colorSurface`. But `colorSurfaceContainerHigh` gives better visual separation between cards and background.

---

### Step 3: Convert `item_smart_card.xml` to `MaterialCardView`

**File:** `app/src/main/res/layout/item_smart_card.xml`

This is the most visually prominent card — \"Rarely Used Apps\" and \"Large Apps\" suggestion cards.

**Replace the entire file with:**
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android=\"http://schemas.android.com/apk/res/android\"
    xmlns:app=\"http://schemas.android.com/apk/res-auto\"
    android:id=\"@+id/card_root\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"wrap_content\"
    android:layout_marginHorizontal=\"4dp\"
    android:layout_marginBottom=\"12dp\"
    android:clickable=\"true\"
    android:focusable=\"true\"
    app:cardBackgroundColor=\"?attr/colorSurfaceContainerHigh\"
    app:cardCornerRadius=\"16dp\"
    app:cardElevation=\"0dp\"
    app:rippleColor=\"?attr/colorPrimary\"
    app:strokeWidth=\"0dp\"
    style=\"@style/Widget.Material3.CardView.Filled\">

    <LinearLayout
        android:layout_width=\"match_parent\"
        android:layout_height=\"wrap_content\"
        android:orientation=\"horizontal\"
        android:gravity=\"center_vertical\"
        android:padding=\"20dp\">

        <!-- Emoji icon container with tinted circular background -->
        <FrameLayout
            android:layout_width=\"48dp\"
            android:layout_height=\"48dp\">

            <View
                android:layout_width=\"48dp\"
                android:layout_height=\"48dp\"
                android:background=\"@drawable/bg_smart_card_icon\" />

            <TextView
                android:id=\"@+id/card_icon\"
                android:layout_width=\"48dp\"
                android:layout_height=\"48dp\"
                android:gravity=\"center\"
                android:text=\"\"
                android:textSize=\"24sp\" />
        </FrameLayout>

        <LinearLayout
            android:layout_width=\"0dp\"
            android:layout_height=\"wrap_content\"
            android:layout_weight=\"1\"
            android:layout_marginStart=\"16dp\"
            android:orientation=\"vertical\">

            <TextView
                android:id=\"@+id/card_title\"
                android:layout_width=\"wrap_content\"
                android:layout_height=\"wrap_content\"
                android:text=\"Title\"
                android:textSize=\"16sp\"
                android:textStyle=\"bold\"
                android:textColor=\"?attr/colorOnSurface\" />

            <TextView
                android:id=\"@+id/card_description\"
                android:layout_width=\"wrap_content\"
                android:layout_height=\"wrap_content\"
                android:layout_marginTop=\"4dp\"
                android:text=\"Description\"
                android:textSize=\"14sp\"
                android:textColor=\"?attr/colorOnSurfaceVariant\" />
        </LinearLayout>

        <com.google.android.material.button.MaterialButton
            android:id=\"@+id/card_button\"
            android:layout_width=\"wrap_content\"
            android:layout_height=\"36dp\"
            android:text=\"Review\"
            android:textSize=\"13sp\"
            app:cornerRadius=\"18dp\"
            style=\"@style/Widget.Material3.Button.TonalButton\" />

    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

**Create new drawable:** `app/src/main/res/drawable/bg_smart_card_icon.xml`
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:shape=\"oval\">
    <solid android:color=\"?attr/colorPrimaryContainer\" />
</shape>
```

> **What changed:**
> - Root is now `MaterialCardView` with M3 `Filled` style
> - Uses `?attr/colorSurfaceContainerHigh` for the card background (theme-aware)
> - Emoji icon gets a circular tinted background (`colorPrimaryContainer`) for visual weight
> - \"Review\" button is now a Material 3 `TonalButton` (subtle, not aggressive)
> - Text colors use M3 tokens directly in XML: `?attr/colorOnSurface`, `?attr/colorOnSurfaceVariant`
> - Ripple effect via `app:rippleColor`

**Adapter update** in `DiscoveryDashboardAdapter.kt` — `SmartCardViewHolder`:

The `card_root` ID is now on the `MaterialCardView` itself. Update the type:

```kotlin
inner class SmartCardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val icon: TextView = view.findViewById(R.id.card_icon)
    private val title: TextView = view.findViewById(R.id.card_title)
    private val description: TextView = view.findViewById(R.id.card_description)
    private val button: com.google.android.material.button.MaterialButton = view.findViewById(R.id.card_button)
    // card_root is now the itemView itself (MaterialCardView)

    fun bind(item: DashboardItem.SmartCard) {
        icon.text = item.icon
        title.text = item.title
        description.text = item.description

        // Click the whole card or the button
        itemView.setOnClickListener { onNavigateToList(item.viewState) }
        button.setOnClickListener { onNavigateToList(item.viewState) }

        // Theme colors are now handled by XML attrs (?attr/colorOnSurface etc.)
        // So we can remove the manual title.setTextColor / description.setTextColor calls.
        // If you still need programmatic override for dynamic color support, keep them:
        title.setTextColor(themeColors.textPrimary)
        description.setTextColor(themeColors.textSecondary)
    }
}
```

> **Note:** Remove `private val cardRoot: LinearLayout = view.findViewById(R.id.card_root)` and the `cardRoot.setOnClickListener` call — the `card_root` ID is now on the `MaterialCardView` (which is `itemView`).

---

### Step 4: Convert `item_storage_meter.xml` to `MaterialCardView`

**File:** `app/src/main/res/layout/item_storage_meter.xml`

**Replace with:**
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android=\"http://schemas.android.com/apk/res/android\"
    xmlns:app=\"http://schemas.android.com/apk/res-auto\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"wrap_content\"
    android:layout_marginHorizontal=\"4dp\"
    android:layout_marginBottom=\"12dp\"
    app:cardBackgroundColor=\"?attr/colorSurfaceContainerHigh\"
    app:cardCornerRadius=\"16dp\"
    app:cardElevation=\"0dp\"
    app:strokeWidth=\"0dp\"
    style=\"@style/Widget.Material3.CardView.Filled\">

    <LinearLayout
        android:layout_width=\"match_parent\"
        android:layout_height=\"wrap_content\"
        android:orientation=\"vertical\"
        android:padding=\"20dp\">

        <LinearLayout
            android:layout_width=\"match_parent\"
            android:layout_height=\"wrap_content\"
            android:orientation=\"horizontal\"
            android:gravity=\"center_vertical\">

            <LinearLayout
                android:layout_width=\"0dp\"
                android:layout_height=\"wrap_content\"
                android:layout_weight=\"1\"
                android:orientation=\"vertical\">

                <TextView
                    android:id=\"@+id/storage_label\"
                    android:layout_width=\"wrap_content\"
                    android:layout_height=\"wrap_content\"
                    android:text=\"Potential Storage Freed\"
                    android:textSize=\"13sp\"
                    android:textColor=\"?attr/colorOnSurfaceVariant\"
                    android:letterSpacing=\"0.02\" />

                <TextView
                    android:id=\"@+id/storage_value\"
                    android:layout_width=\"wrap_content\"
                    android:layout_height=\"wrap_content\"
                    android:layout_marginTop=\"4dp\"
                    android:text=\"0 MB\"
                    android:textSize=\"28sp\"
                    android:textStyle=\"bold\"
                    android:textColor=\"?attr/colorPrimary\" />
            </LinearLayout>

            <TextView
                android:id=\"@+id/waste_score\"
                android:layout_width=\"wrap_content\"
                android:layout_height=\"wrap_content\"
                android:text=\"Waste Score: 0%\"
                android:textSize=\"13sp\"
                android:textStyle=\"bold\"
                android:textColor=\"?attr/colorOnSurfaceVariant\" />
        </LinearLayout>

        <com.google.android.material.progressindicator.LinearProgressIndicator
            android:id=\"@+id/storage_progress\"
            android:layout_width=\"match_parent\"
            android:layout_height=\"wrap_content\"
            android:layout_marginTop=\"16dp\"
            app:indicatorColor=\"?attr/colorPrimary\"
            app:trackColor=\"?attr/colorSurfaceVariant\"
            app:trackCornerRadius=\"6dp\"
            app:trackThickness=\"12dp\"
            android:max=\"100\"
            android:progress=\"0\" />

    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

> **What changed:**
> - Wrapped in `MaterialCardView` with M3 Filled style
> - Storage label uses `colorOnSurfaceVariant` (secondary text)
> - Storage value uses `colorPrimary` (accent — matches `AccentTeal` or dynamic primary)
> - Waste score uses `colorOnSurfaceVariant`
> - `ProgressBar` replaced with `LinearProgressIndicator` from Material Components (rounded track, M3 colors)

**Adapter update** in `DiscoveryDashboardAdapter.kt` — `StorageMeterViewHolder`:

Update the progress bar type:
```kotlin
inner class StorageMeterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val storageValue: TextView = view.findViewById(R.id.storage_value)
    private val wasteScore: TextView = view.findViewById(R.id.waste_score)
    private val progressBar: com.google.android.material.progressindicator.LinearProgressIndicator =
        view.findViewById(R.id.storage_progress)

    fun bind(item: DashboardItem.StorageMeter) {
        storageValue.text = \"${bytesToMB(item.wasteSize)} MB\"
        wasteScore.text = \"Waste Score: ${item.percentage}%\"
        progressBar.progress = item.percentage

        // Apply Compose theme colors for dynamic color support
        storageValue.setTextColor(themeColors.checkboxTint) // primary color
        wasteScore.setTextColor(
            if (item.percentage > 15) android.graphics.Color.parseColor(\"#EF4444\")
            else themeColors.textSecondary
        )
        // No need to manually tint the progress bar — XML attrs handle it
    }
}
```

> **Note:** Remove the `progressBar.progressDrawable.setColorFilter(...)` line. The `LinearProgressIndicator` uses `app:indicatorColor` and `app:trackColor` from XML and handles M3 theming natively.

---

### Step 5: Redesign `item_discovery_app.xml` — App Row Item

**File:** `app/src/main/res/layout/item_discovery_app.xml`

This is the most-repeated item in the list. It needs to look like a proper M3 list item with subtle card treatment.

**Replace with:**
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android=\"http://schemas.android.com/apk/res/android\"
    xmlns:app=\"http://schemas.android.com/apk/res-auto\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"wrap_content\"
    android:layout_marginHorizontal=\"4dp\"
    android:layout_marginBottom=\"6dp\"
    android:clickable=\"true\"
    android:focusable=\"true\"
    app:cardBackgroundColor=\"?attr/colorSurface\"
    app:cardCornerRadius=\"12dp\"
    app:cardElevation=\"0dp\"
    app:rippleColor=\"?attr/colorPrimary\"
    app:strokeWidth=\"0dp\"
    style=\"@style/Widget.Material3.CardView.Filled\">

    <LinearLayout
        android:layout_width=\"match_parent\"
        android:layout_height=\"wrap_content\"
        android:orientation=\"horizontal\"
        android:gravity=\"center_vertical\"
        android:paddingHorizontal=\"12dp\"
        android:paddingVertical=\"12dp\">

        <com.google.android.material.checkbox.MaterialCheckBox
            android:id=\"@+id/app_checkbox\"
            android:layout_width=\"wrap_content\"
            android:layout_height=\"wrap_content\"
            android:clickable=\"false\"
            android:focusable=\"false\" />

        <com.google.android.material.imageview.ShapeableImageView
            android:id=\"@+id/app_icon\"
            android:layout_width=\"44dp\"
            android:layout_height=\"44dp\"
            android:layout_marginStart=\"8dp\"
            android:contentDescription=\"App Icon\"
            app:shapeAppearanceOverlay=\"@style/RoundedIconShape\" />

        <LinearLayout
            android:layout_width=\"0dp\"
            android:layout_height=\"wrap_content\"
            android:layout_weight=\"1\"
            android:layout_marginStart=\"14dp\"
            android:orientation=\"vertical\">

            <LinearLayout
                android:layout_width=\"wrap_content\"
                android:layout_height=\"wrap_content\"
                android:orientation=\"horizontal\"
                android:gravity=\"center_vertical\">

                <TextView
                    android:id=\"@+id/app_name\"
                    android:layout_width=\"wrap_content\"
                    android:layout_height=\"wrap_content\"
                    android:textSize=\"15sp\"
                    android:textStyle=\"bold\"
                    android:maxLines=\"1\"
                    android:ellipsize=\"end\"
                    android:textColor=\"?attr/colorOnSurface\" />

                <ImageView
                    android:id=\"@+id/warning_icon\"
                    android:layout_width=\"16dp\"
                    android:layout_height=\"16dp\"
                    android:layout_marginStart=\"4dp\"
                    android:src=\"@android:drawable/ic_dialog_alert\"
                    android:visibility=\"gone\"
                    android:tint=\"?attr/colorError\" />
            </LinearLayout>

            <TextView
                android:id=\"@+id/app_details\"
                android:layout_width=\"wrap_content\"
                android:layout_height=\"wrap_content\"
                android:layout_marginTop=\"2dp\"
                android:textSize=\"12sp\"
                android:textColor=\"?attr/colorOnSurfaceVariant\" />

            <TextView
                android:id=\"@+id/app_context_label\"
                android:layout_width=\"wrap_content\"
                android:layout_height=\"wrap_content\"
                android:layout_marginTop=\"2dp\"
                android:textSize=\"11sp\"
                android:textColor=\"?attr/colorTertiary\"
                android:visibility=\"gone\" />

        </LinearLayout>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

**Add shape style to `themes.xml`:**
```xml
<style name=\"RoundedIconShape\" parent=\"\">
    <item name=\"cornerFamily\">rounded</item>
    <item name=\"cornerSize\">12dp</item>
</style>
```

> **What changed:**
> - Root is `MaterialCardView` with `colorSurface` background and 12dp corners (less prominent than smart cards since this repeats many times)
> - `CheckBox` → `MaterialCheckBox` (M3 animated check)
> - `ImageView` → `ShapeableImageView` with 12dp rounded corners (squircle icon look)
> - Text colors use M3 XML tokens
> - Bottom margin reduced to `6dp` (tighter list spacing)
> - Ripple effect on the entire card

**Adapter update** in `DiscoveryAppsAdapter.kt`:

Update the selection background logic to use `MaterialCardView`:
```kotlin
inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val cardView = view as com.google.android.material.card.MaterialCardView
    private val checkBox: com.google.android.material.checkbox.MaterialCheckBox = view.findViewById(R.id.app_checkbox)
    private val icon: com.google.android.material.imageview.ShapeableImageView = view.findViewById(R.id.app_icon)
    private val name: TextView = view.findViewById(R.id.app_name)
    private val warningIcon: ImageView = view.findViewById(R.id.warning_icon)
    private val details: TextView = view.findViewById(R.id.app_details)
    private val contextLabel: TextView = view.findViewById(R.id.app_context_label)

    init {
        itemView.setOnClickListener {
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onToggle(getItem(adapterPosition).info.packageId)
            }
        }
    }

    fun bind(item: AppListItem) {
        val app = item.info

        name.text = app.name
        checkBox.isChecked = item.isSelected
        warningIcon.visibility = if (app.isPlayStoreInstalled) View.GONE else View.VISIBLE

        val sizeLabel = \"${bytesToMB(app.apkSizeBytes)} MB\"
        val now = System.currentTimeMillis()
        val timeString = if (app.lastTimeUsed > 0) {
            val daysAgo = ((now - app.lastTimeUsed) / DateUtils.DAY_IN_MILLIS).toInt()
            when {
                daysAgo <= 0 -> \"Today\"
                daysAgo == 1 -> \"1 day ago\"
                else -> \"$daysAgo days ago\"
            }
        } else {
            \"Never used\"
        }
        details.text = \"$sizeLabel \u2022 $timeString\"

        if (item.contextLabel != null) {
            contextLabel.text = item.contextLabel
            contextLabel.visibility = View.VISIBLE
        } else {
            contextLabel.visibility = View.GONE
        }

        // Apply selection state via MaterialCardView
        if (item.isSelected) {
            cardView.setCardBackgroundColor(themeColors.selectedBackground)
        } else {
            cardView.setCardBackgroundColor(themeColors.normalBackground)
        }

        // Theme colors for text
        name.setTextColor(themeColors.textPrimary)
        details.setTextColor(themeColors.textSecondary)
        contextLabel.setTextColor(themeColors.textTertiary)

        // Load icon
        icon.load(AppIconModel(app.packageId)) {
            memoryCacheKey(app.packageId)
            crossfade(false)
            size(96)
        }
    }
}
```

**Also update the `AppItemViewHolder`** in `DiscoveryDashboardAdapter.kt` similarly — cast `itemView` to `MaterialCardView` and use `setCardBackgroundColor()` instead of `setBackgroundColor()`.

---

### Step 6: Update `item_permission_warning.xml`

**File:** `app/src/main/res/layout/item_permission_warning.xml`

**Replace with:**
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android=\"http://schemas.android.com/apk/res/android\"
    xmlns:app=\"http://schemas.android.com/apk/res-auto\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"wrap_content\"
    android:layout_marginHorizontal=\"4dp\"
    android:layout_marginBottom=\"12dp\"
    app:cardBackgroundColor=\"?attr/colorErrorContainer\"
    app:cardCornerRadius=\"16dp\"
    app:cardElevation=\"0dp\"
    app:strokeWidth=\"0dp\"
    style=\"@style/Widget.Material3.CardView.Filled\">

    <LinearLayout
        android:layout_width=\"match_parent\"
        android:layout_height=\"wrap_content\"
        android:orientation=\"vertical\"
        android:padding=\"16dp\">

        <LinearLayout
            android:layout_width=\"match_parent\"
            android:layout_height=\"wrap_content\"
            android:orientation=\"horizontal\"
            android:gravity=\"center_vertical\">

            <ImageView
                android:layout_width=\"24dp\"
                android:layout_height=\"24dp\"
                android:src=\"@android:drawable/ic_dialog_alert\"
                android:tint=\"?attr/colorOnErrorContainer\" />

            <TextView
                android:layout_width=\"wrap_content\"
                android:layout_height=\"wrap_content\"
                android:layout_marginStart=\"8dp\"
                android:text=\"Usage Access Required\"
                android:textStyle=\"bold\"
                android:textSize=\"16sp\"
                android:textColor=\"?attr/colorOnErrorContainer\" />
        </LinearLayout>

        <TextView
            android:layout_width=\"match_parent\"
            android:layout_height=\"wrap_content\"
            android:layout_marginTop=\"8dp\"
            android:text=\"We need permission to detect which apps you haven't used recently.\"
            android:textSize=\"14sp\"
            android:textColor=\"?attr/colorOnErrorContainer\" />

        <com.google.android.material.button.MaterialButton
            android:id=\"@+id/grant_permission_button\"
            android:layout_width=\"wrap_content\"
            android:layout_height=\"wrap_content\"
            android:layout_marginTop=\"12dp\"
            android:text=\"Grant Permission\"
            style=\"@style/Widget.Material3.Button\" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

> **What changed:**
> - Uses `colorErrorContainer` / `colorOnErrorContainer` — the M3 way to show warning/error cards
> - Native `Button` replaced with `MaterialButton` (M3 filled button)
> - Warning icon tinted with `colorOnErrorContainer`

---

### Step 7: Update `item_all_apps_header.xml`

**File:** `app/src/main/res/layout/item_all_apps_header.xml`

**Replace with:**
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"wrap_content\"
    android:orientation=\"horizontal\"
    android:gravity=\"center_vertical\"
    android:paddingHorizontal=\"4dp\"
    android:paddingVertical=\"12dp\">

    <TextView
        android:layout_width=\"0dp\"
        android:layout_height=\"wrap_content\"
        android:layout_weight=\"1\"
        android:text=\"All Apps\"
        android:textSize=\"16sp\"
        android:textStyle=\"bold\"
        android:textColor=\"?attr/colorOnSurface\"
        android:letterSpacing=\"0.01\" />

    <com.google.android.material.button.MaterialButton
        android:id=\"@+id/search_icon\"
        android:layout_width=\"40dp\"
        android:layout_height=\"40dp\"
        android:insetTop=\"0dp\"
        android:insetBottom=\"0dp\"
        android:padding=\"0dp\"
        app:icon=\"@android:drawable/ic_menu_search\"
        app:iconGravity=\"textStart\"
        app:iconPadding=\"0dp\"
        app:iconSize=\"20dp\"
        app:cornerRadius=\"20dp\"
        style=\"@style/Widget.Material3.Button.IconButton.Filled.Tonal\"
        xmlns:app=\"http://schemas.android.com/apk/res-auto\" />
</LinearLayout>
```

> **What changed:**
> - Text uses `?attr/colorOnSurface`
> - Search icon is now a Material 3 `IconButton.Filled.Tonal` — gives it a subtle circular tinted background

**Adapter update** in `DiscoveryDashboardAdapter.kt` — `AllAppsHeaderViewHolder`:

Change from `ImageView` to `MaterialButton`:
```kotlin
inner class AllAppsHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val searchButton: com.google.android.material.button.MaterialButton = view.findViewById(R.id.search_icon)

    init {
        searchButton.setOnClickListener { onSearchToggle() }
    }

    fun bind(item: DashboardItem.AllAppsHeader) {
        searchButton.setIconResource(
            if (item.isSearchActive) android.R.drawable.ic_menu_close_clear_cancel
            else android.R.drawable.ic_menu_search
        )
    }
}
```

---

### Step 8: Update `item_search_bar.xml`

**File:** `app/src/main/res/layout/item_search_bar.xml`

**Replace with:**
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android=\"http://schemas.android.com/apk/res/android\"
    xmlns:app=\"http://schemas.android.com/apk/res-auto\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"wrap_content\"
    android:layout_marginHorizontal=\"4dp\"
    android:layout_marginBottom=\"8dp\"
    app:cardBackgroundColor=\"?attr/colorSurfaceContainerHighest\"
    app:cardCornerRadius=\"28dp\"
    app:cardElevation=\"0dp\"
    app:strokeWidth=\"0dp\"
    style=\"@style/Widget.Material3.CardView.Filled\">

    <LinearLayout
        android:layout_width=\"match_parent\"
        android:layout_height=\"wrap_content\"
        android:orientation=\"horizontal\"
        android:paddingHorizontal=\"16dp\"
        android:paddingVertical=\"4dp\"
        android:gravity=\"center_vertical\">

        <ImageView
            android:layout_width=\"20dp\"
            android:layout_height=\"20dp\"
            android:src=\"@android:drawable/ic_menu_search\"
            android:tint=\"?attr/colorOnSurfaceVariant\"
            android:layout_marginEnd=\"12dp\"
            android:contentDescription=\"Search\" />

        <EditText
            android:id=\"@+id/search_edit_text\"
            android:layout_width=\"0dp\"
            android:layout_height=\"48dp\"
            android:layout_weight=\"1\"
            android:hint=\"Search apps...\"
            android:textColorHint=\"?attr/colorOnSurfaceVariant\"
            android:textColor=\"?attr/colorOnSurface\"
            android:inputType=\"text\"
            android:maxLines=\"1\"
            android:background=\"@android:color/transparent\"
            android:importantForAutofill=\"no\"
            android:textSize=\"15sp\" />

        <ImageView
            android:id=\"@+id/clear_button\"
            android:layout_width=\"24dp\"
            android:layout_height=\"24dp\"
            android:src=\"@android:drawable/ic_menu_close_clear_cancel\"
            android:tint=\"?attr/colorOnSurfaceVariant\"
            android:layout_marginStart=\"8dp\"
            android:visibility=\"gone\"
            android:clickable=\"true\"
            android:focusable=\"true\"
            android:background=\"?attr/selectableItemBackgroundBorderless\"
            android:contentDescription=\"Clear\" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

> **What changed:**
> - Wrapped in `MaterialCardView` with `colorSurfaceContainerHighest` and fully-rounded corners (28dp) matching M3 search bar spec
> - Text colors use M3 tokens
> - Removed old `android:background=\"@android:drawable/edit_text\"` (Android 1.0 era styling)

---

### Step 9: Update `NativeThemeColors` — Add Missing Tokens

**File:** `presentation/screens/dashboard/DiscoveryAppsAdapter.kt`

Extend `NativeThemeColors` with the additional M3 tokens you may need for dynamic color fidelity:

```kotlin
data class NativeThemeColors(
    val textPrimary: Int,         // colorOnSurface
    val textSecondary: Int,       // colorOnSurfaceVariant
    val textTertiary: Int,        // colorTertiary
    val selectedBackground: Int,  // colorPrimaryContainer
    val normalBackground: Int,    // colorSurface
    val checkboxTint: Int,        // colorPrimary
    val cardSurface: Int,         // colorSurfaceContainerHigh
    val errorColor: Int           // colorError
)
```

**Update in `DiscoveryScreen.kt`** where `NativeThemeColors` is constructed:

```kotlin
val themeColors = NativeThemeColors(
    textPrimary = MaterialTheme.colorScheme.onSurface.toArgb(),
    textSecondary = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
    textTertiary = MaterialTheme.colorScheme.tertiary.toArgb(),
    selectedBackground = MaterialTheme.colorScheme.primaryContainer.toArgb(),
    normalBackground = MaterialTheme.colorScheme.surface.toArgb(),
    checkboxTint = MaterialTheme.colorScheme.primary.toArgb(),
    cardSurface = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
    errorColor = MaterialTheme.colorScheme.error.toArgb()
)
```

> This ensures that even when dynamic colors override the static theme (Android 12+), the native views receive the exact same resolved colors that Compose is using. The XML `?attr/` tokens serve as fallbacks for the static theme; the programmatic `themeColors` overrides ensure pixel-perfect match with Compose.

---

### Step 10: Update RecyclerView Padding in `DiscoveryScreen.kt`

Currently the RecyclerView uses `app_icon_size / 6` for padding which is unintuitive. Replace with explicit dp values that match M3 spacing:

**In `DiscoveryDashboard` composable:**
```kotlin
AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { context ->
        RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = DiscoveryDashboardAdapter(/* ... */)
            val dp12 = (12 * context.resources.displayMetrics.density).toInt()
            val dp80 = (80 * context.resources.displayMetrics.density).toInt()
            setPadding(dp12, dp12, dp12, if (selectedApps.isNotEmpty()) dp80 else dp12)
            clipToPadding = false
        }
    },
    update = { recyclerView ->
        val adapter = recyclerView.adapter as DiscoveryDashboardAdapter
        adapter.themeColors = themeColors
        adapter.submitList(dashboardItems)
        val dp12 = (12 * recyclerView.context.resources.displayMetrics.density).toInt()
        val dp80 = (80 * recyclerView.context.resources.displayMetrics.density).toInt()
        recyclerView.setPadding(dp12, dp12, dp12, if (selectedApps.isNotEmpty()) dp80 else dp12)
    }
)
```

---

## Dependency Check

Ensure `build.gradle.kts` includes the Material Components library. The app already has it transitively via Compose Material 3, but add it explicitly to be safe:

```kotlin
// In app/build.gradle.kts dependencies block
implementation(\"com.google.android.material:material:1.12.0\")
```

This provides:
- `MaterialCardView`
- `MaterialButton`
- `MaterialCheckBox`
- `ShapeableImageView`
- `LinearProgressIndicator`
- `Theme.Material3.DayNight.NoActionBar`

---

## Summary of All Changes

| File | Change | Why |
|---|---|---|
| `values/themes.xml` | Parent → `Theme.Material3.DayNight.NoActionBar` + add `RoundedIconShape` | Enable M3 attr resolution in XML |
| `drawable/card_background.xml` | `#FFFFFF` → `?attr/colorSurfaceContainerHigh` | Fix dark mode, match M3 tokens |
| `drawable/bg_smart_card_icon.xml` | **New file** — circle with `colorPrimaryContainer` | Emoji icon background for smart cards |
| `layout/item_smart_card.xml` | `LinearLayout` → `MaterialCardView` + `TonalButton` | M3 card, proper elevation/shape/ripple |
| `layout/item_storage_meter.xml` | `LinearLayout` → `MaterialCardView` + `LinearProgressIndicator` | M3 card, proper progress bar |
| `layout/item_discovery_app.xml` | `LinearLayout` → `MaterialCardView` + `ShapeableImageView` + `MaterialCheckBox` | M3 list item card, rounded icon |
| `layout/item_permission_warning.xml` | `LinearLayout` → `MaterialCardView` with error container | M3 warning card styling |
| `layout/item_all_apps_header.xml` | `ImageView` → `MaterialButton.IconButton.Filled.Tonal` | M3 icon button |
| `layout/item_search_bar.xml` | Wrapped in `MaterialCardView` with full-radius corners | M3 search bar style |
| `DiscoveryAppsAdapter.kt` | Updated view types, selection via `setCardBackgroundColor` | Work with `MaterialCardView` |
| `DiscoveryDashboardAdapter.kt` | Updated all ViewHolders for new M3 widget types | Work with new layouts |
| `DiscoveryScreen.kt` | Extended `NativeThemeColors`, updated padding | More M3 tokens, consistent spacing |
| `build.gradle.kts` | Add explicit `material:1.12.0` dependency | Ensure M3 components available |

---

## Visual Hierarchy (After Fix)

```
┌─────────────────────────────────────┐
│  Storage Meter Card                 │  ← MaterialCardView, colorSurfaceContainerHigh
│  ┌──────────────────┐ Waste: 72%   │     LinearProgressIndicator with colorPrimary
│  │ 135 MB           │              │
│  └──────────────────┘              │
│  ████████████░░░░░░░░░░░           │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ (●) Rarely Used Apps         [Review]│  ← MaterialCardView, TonalButton
│      19 apps · 135 MB               │     Emoji on colorPrimaryContainer circle
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ (●) Large Apps               [Review]│
│      3 apps · 1.2 GB                │
└─────────────────────────────────────┘

  All Apps                         (🔍)   ← Tonal IconButton

┌─────────────────────────────────────┐
│ ☐ [icon] App Name                   │  ← MaterialCardView, colorSurface, 12dp radius
│          45 MB · 3 days ago         │     ShapeableImageView with squircle icon
└─────────────────────────────────────┘
┌─────────────────────────────────────┐
│ ☑ [icon] Another App                │  ← Selected: colorPrimaryContainer background
│          12 MB · Never used         │
└─────────────────────────────────────┘
```

---

## Testing Checklist

- [ ] Build successfully (`./gradlew :app:assembleDebug`)
- [ ] Light mode: cards have subtle off-white surface, text is readable
- [ ] Dark mode: cards use dark surface variants, no white backgrounds
- [ ] Dynamic colors (Android 12+): cards follow wallpaper-extracted palette
- [ ] Smart card \"Review\" button triggers navigation
- [ ] App item checkbox toggle works
- [ ] App item selection highlights with primaryContainer color
- [ ] Storage meter progress bar renders correctly
- [ ] Permission warning card displays with error container color
- [ ] Search bar is functional and visually consistent
- [ ] Scroll performance is unchanged (no additional compose recompositions)
- [ ] Back button behavior unchanged in all view states

---

## Common Pitfalls

1. **`?attr/colorSurfaceContainerHigh` not found:** Your theme parent must be `Theme.Material3.*`. If still using `android:Theme.Material.*`, these M3 tokens don't exist.

2. **`MaterialCardView` import:** Ensure it's `com.google.android.material.card.MaterialCardView`, not `androidx.cardview.widget.CardView` (the old one doesn't support M3 styling).

3. **`LinearProgressIndicator` not found:** Requires `com.google.android.material:material:1.6.0` or later. You need version 1.12.0 for full M3 support.

4. **Dark mode not working:** If using `Theme.Material3.Light.NoActionBar` instead of `Theme.Material3.DayNight.NoActionBar`, dark mode falls back to light theme for native views.

5. **Programmatic colors override XML attrs:** The `themeColors` applied programmatically in adapters will override the XML `?attr/` values. This is intentional — it ensures Compose's resolved dynamic colors (which may differ from static theme) are applied consistently. If you remove the programmatic coloring, the XML attrs will work as fallbacks.
"
Observation: Create successful: /app/CARD_FIX_PROMPT.md