
> **Purpose**: This document is a step-by-step implementation guide for an LLM agent to make Decluttr production-ready. It covers Compose-to-Android-Views migration, UI consistency fixes, architecture improvements, security hardening, data integrity, and Play Store compliance.

---

## Table of Contents

1. [Current Architecture Overview](#1-current-architecture-overview)
2. [Compose vs Views Audit](#2-compose-vs-views-audit)
3. [Phase 1: Core Migration — Compose to Android Views](#3-phase-1-core-migration)
4. [Phase 2: UI Consistency Fixes](#4-phase-2-ui-consistency-fixes)
5. [Phase 3: Production Readiness — Build & Security](#5-phase-3-production-readiness-build-security)
6. [Phase 4: Data Integrity & Reliability](#6-phase-4-data-integrity-reliability)
7. [Phase 5: UX Polish & Missing Features](#7-phase-5-ux-polish-missing-features)
8. [Phase 6: Play Store Compliance](#8-phase-6-play-store-compliance)
9. [Phase 7: Testing & CI/CD](#9-phase-7-testing-cicd)
10. [Phase 8: Performance Optimization](#10-phase-8-performance-optimization)
11. [Dependency Changes Summary](#11-dependency-changes-summary)
12. [File-by-File Change Map](#12-file-by-file-change-map)

---

## 1. Current Architecture Overview

```
com.tool.decluttr/
├── DecluttrApp.kt                    # Application class (Hilt, Coil)
├── MainActivity.kt                   # COMPOSE: setContent + NavGraph
├── di/AppModule.kt                   # Hilt DI module
├── data/
│   ├── local/
│   │   ├── DecluttrDatabase.kt       # Room DB (version 3)
│   │   ├── dao/AppDao.kt             # DAO interface
│   │   └── entity/AppEntity.kt       # Room entity
│   ├── mapper/AppMapper.kt           # Entity <-> Domain mapper
│   └── repository/
│       ├── AppRepositoryImpl.kt      # Firebase + Room sync
│       └── AuthRepositoryImpl.kt     # Firebase Auth
├── domain/
│   ├── model/ArchivedApp.kt          # Domain model
│   ├── repository/                   # Interfaces
│   └── usecase/                      # Business logic (8 use cases)
├── presentation/
│   ├── navigation/NavGraph.kt        # COMPOSE: NavHost navigation
│   ├── screens/
│   │   ├── auth/
│   │   │   ├── AuthScreen.kt         # HYBRID: Compose wrapper + AndroidView(XML)
│   │   │   └── AuthViewModel.kt      # ViewModel (clean)
│   │   ├── dashboard/
│   │   │   ├── DashboardScreen.kt    # COMPOSE: Scaffold + tabs + native dialogs
│   │   │   ├── DashboardViewModel.kt # ViewModel (clean)
│   │   │   ├── DiscoveryScreen.kt    # COMPOSE: Compose layout + AndroidView(RecyclerView)
│   │   │   ├── ArchivedAppsList.kt   # COMPOSE: Compose layout + AndroidView bridges
│   │   │   ├── ArchivedAppsRecyclerView.kt  # COMPOSE wrapper for native RecyclerView
│   │   │   ├── DiscoveryDashboardAdapter.kt # NATIVE: ListAdapter
│   │   │   ├── DiscoveryAppsAdapter.kt      # NATIVE: ListAdapter
│   │   │   ├── ArchivedAppsAdapter.kt       # NATIVE: ListAdapter + drag-drop
│   │   │   ├── FolderAppsAdapter.kt         # NATIVE: adapter
│   │   │   ├── FolderExpandOverlay.kt       # NATIVE: full-screen overlay
│   │   │   ├── NativeAppDetailsDialog.kt    # NATIVE: Dialog
│   │   │   ├── NativeBulkReviewDialog.kt    # NATIVE: Dialog + ViewPager2
│   │   │   ├── DashboardModalDialogWrapper.kt # NATIVE: Dialog wrapper
│   │   │   ├── ArchiveNotesCardMolecule.kt  # NATIVE: View molecule
│   │   │   ├── ArchiveNotesStateMachine.kt  # Pure Kotlin state machine
│   │   │   └── ScaledDragShadowBuilder.kt   # NATIVE: DragShadowBuilder
│   │   └── settings/
│   │       ├── SettingsScreen.kt     # COMPOSE: pure Compose screen
│   │       └── SettingsViewModel.kt  # ViewModel (clean)
│   ├── share/ShareViewModel.kt       # ViewModel for share intent
│   └── util/
│       ├── AppIconFetcher.kt         # Coil Fetcher
│       └── AppIconKeyer.kt           # Coil Keyer
├── receiver/
│   ├── ShareReceiverActivity.kt      # ComponentActivity (minimal)
│   └── DecluttrTileService.kt        # Quick Settings tile
└── ui/theme/
    ├── Color.kt                      # COMPOSE colors
    ├── Theme.kt                      # COMPOSE Material3 theme
    └── Type.kt                       # COMPOSE typography
```

**Tech Stack**: Kotlin, Hilt (DI), Room (local DB), Firebase Auth + Firestore (cloud sync), Coil (image loading), Material3 (Design System), Compose + Android Views (hybrid UI)

---

## 2. Compose vs Views Audit

### Files That MUST Be Migrated (currently use Compose APIs)

| File | Compose Usage | Migration Complexity |
|------|---------------|---------------------|
| `MainActivity.kt` | `ComponentActivity`, `setContent{}`, `DecluttrTheme`, `rememberNavController` | **HIGH** — Entire entry point |
| `NavGraph.kt` | `NavHost`, `composable()`, `hiltViewModel()`, `collectAsState`, `LaunchedEffect` | **HIGH** — Navigation architecture |
| `DashboardScreen.kt` | `Scaffold`, `TopAppBar`, `Column`, `AndroidView`, `collectAsState`, `remember`, `DisposableEffect` | **HIGH** — Main screen container |
| `DiscoveryScreen.kt` | `Box`, `Column`, `Card`, `CircularProgressIndicator`, `AndroidView`, `BackHandler`, `remember`, `DisposableEffect`, `AlertDialog`, `MaterialTheme` color extraction | **HIGH** — Complex layout + state |
| `ArchivedAppsList.kt` | `Column`, `Box`, `Text`, `Spacer`, `AndroidView`, `LaunchedEffect`, `remember`, `LazyRow` (unused but imported), `DropdownMenu`, `OutlinedTextField`, `IconButton`, `Surface` | **HIGH** — Complex layout + drag-drop |
| `ArchivedAppsRecyclerView.kt` | `AndroidView` wrapper, `Composable`, `remember`, `DisposableEffect` | **MEDIUM** — Thin wrapper, can inline |
| `SettingsScreen.kt` | `Scaffold`, `TopAppBar`, `Column`, `Button`, `Text`, `CircularProgressIndicator`, `rememberLauncherForActivityResult` | **MEDIUM** — Self-contained screen |
| `AuthScreen.kt` | `AndroidView` wrapper, `Composable`, `collectAsState` | **LOW** — Already mostly XML, just unwrap |
| `Theme.kt` | `MaterialTheme`, `darkColorScheme`, `lightColorScheme`, `dynamicColorScheme` | **MEDIUM** — Replace with XML theme |
| `Color.kt` | `Color()` Compose values | **LOW** — Move to `colors.xml` |
| `Type.kt` | `Typography` Compose | **LOW** — Move to `styles.xml` |

### Files That Are Already Native (NO changes needed)

- All RecyclerView adapters (`DiscoveryDashboardAdapter.kt`, `DiscoveryAppsAdapter.kt`, `ArchivedAppsAdapter.kt`, `FolderAppsAdapter.kt`)
- `FolderExpandOverlay.kt`
- `NativeAppDetailsDialog.kt`, `NativeBulkReviewDialog.kt`
- `DashboardModalDialogWrapper.kt`
- `ArchiveNotesCardMolecule.kt`, `ArchiveNotesStateMachine.kt`
- `ScaledDragShadowBuilder.kt`
- All XML layouts in `res/layout/`
- All ViewModels (they use `StateFlow`/`SharedFlow` which work with both Compose and Views)

### Files That Need Minor Adjustment

- `GetInstalledAppsUseCase.kt` — Remove `@Composable` `@Immutable` annotation from `InstalledAppInfo` data class (line 17). This is a Compose annotation on a domain model — violates clean architecture.
- `DecluttrApp.kt` — No changes needed (pure Android Application class).
- `AppRepositoryImpl.kt` — No Compose dependency but has lifecycle concern (see Phase 4).

---

## 3. Phase 1: Core Migration — Compose to Android Views

### 3.1 Navigation Architecture Replacement

**Current**: Compose `NavHost` with `composable()` routes
**Target**: Single-Activity with Fragment navigation via AndroidX Navigation component (XML)

#### Step 1: Create `nav_graph.xml`

Create file: `res/navigation/nav_graph.xml`

```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"
    xmlns:app=\"http://schemas.android.com/apk/res-auto\"
    android:id=\"@+id/nav_graph\"
    app:startDestination=\"@id/authFragment\">

    <fragment
        android:id=\"@+id/authFragment\"
        android:name=\"com.tool.decluttr.presentation.screens.auth.AuthFragment\"
        android:label=\"Auth\">
        <action
            android:id=\"@+id/action_auth_to_dashboard\"
            app:destination=\"@id/dashboardFragment\"
            app:popUpTo=\"@id/nav_graph\"
            app:popUpToInclusive=\"true\" />
    </fragment>

    <fragment
        android:id=\"@+id/dashboardFragment\"
        android:name=\"com.tool.decluttr.presentation.screens.dashboard.DashboardFragment\"
        android:label=\"Dashboard\">
        <action
            android:id=\"@+id/action_dashboard_to_settings\"
            app:destination=\"@id/settingsFragment\" />
    </fragment>

    <fragment
        android:id=\"@+id/settingsFragment\"
        android:name=\"com.tool.decluttr.presentation.screens.settings.SettingsFragment\"
        android:label=\"Settings\" />
</navigation>
```

#### Step 2: Create `activity_main.xml`

Create file: `res/layout/activity_main.xml`

```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<androidx.fragment.app.FragmentContainerView
    xmlns:android=\"http://schemas.android.com/apk/res/android\"
    xmlns:app=\"http://schemas.android.com/apk/res-auto\"
    android:id=\"@+id/nav_host_fragment\"
    android:name=\"androidx.navigation.fragment.NavHostFragment\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"match_parent\"
    app:defaultNavHost=\"true\"
    app:navGraph=\"@navigation/nav_graph\" />
```

#### Step 3: Rewrite `MainActivity.kt`

```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val dashboardViewModel: DashboardViewModel by viewModels()

    @Inject
    lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e(\"DECLUTTR_CRASH\", \"FATAL EXCEPTION in thread ${thread.name}\", throwable)
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
        }
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !dashboardViewModel.isStartupReady.value
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Set start destination based on auth state
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(
            if (auth.currentUser != null) R.id.dashboardFragment else R.id.authFragment
        )
        navController.graph = graph
    }
}
```

**Key changes**:
- `ComponentActivity` -> `AppCompatActivity`
- Remove `setContent {}` block
- Remove `DecluttrTheme` wrapper (now handled by XML theme)
- Remove `rememberNavController()`
- Use `NavHostFragment` from XML
- Add `setContentView(R.layout.activity_main)`

**Required imports to add**:
```kotlin
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
```

**Imports to remove**:
```kotlin
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.tool.decluttr.ui.theme.DecluttrTheme
import com.tool.decluttr.presentation.navigation.DecluttrNavGraph
import com.tool.decluttr.presentation.navigation.NavRoutes
```

#### Step 4: Add navigation dependencies to `build.gradle.kts`

Add:
```kotlin
implementation(\"androidx.navigation:navigation-fragment-ktx:2.8.5\")
implementation(\"androidx.navigation:navigation-ui-ktx:2.8.5\")
```

Remove (after migration complete):
```kotlin
implementation(\"androidx.navigation:navigation-compose:2.8.0-beta03\")
implementation(\"androidx.hilt:hilt-navigation-compose:1.2.0\")
```

### 3.2 Delete `NavGraph.kt`

The entire file `presentation/navigation/NavGraph.kt` becomes unnecessary. Delete it. The `NavRoutes` object can remain if needed as a constants file, but the XML navigation graph replaces all its functionality.

### 3.3 Convert Screens to Fragments

#### AuthFragment

Create file: `presentation/screens/auth/AuthFragment.kt`

```kotlin
@AndroidEntryPoint
class AuthFragment : Fragment(R.layout.screen_auth) {

    private val viewModel: AuthViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnSkip = view.findViewById<ImageView>(R.id.btn_skip)
        val etEmail = view.findViewById<EditText>(R.id.et_email)
        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val tvError = view.findViewById<TextView>(R.id.tv_error)
        val btnPrimary = view.findViewById<TextView>(R.id.btn_primary_action)
        val progressLoading = view.findViewById<ProgressBar>(R.id.progress_loading)
        val dividerOr = view.findViewById<LinearLayout>(R.id.divider_or)
        val btnGoogle = view.findViewById<LinearLayout>(R.id.btn_google_signin)
        val tvModeToggle = view.findViewById<TextView>(R.id.tv_mode_toggle)

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                maxOf(systemBars.bottom, ime.bottom))
            insets
        }

        // Click handlers
        btnSkip.setOnClickListener { navigateToDashboard() }
        btnPrimary.setOnClickListener { viewModel.authenticate() }
        btnGoogle.setOnClickListener { /* TODO: Google Sign-In */ }
        tvModeToggle.setOnClickListener { viewModel.toggleMode() }

        // Text watchers
        etEmail.addTextChangedListener(SimpleTextWatcher { viewModel.onEmailChange(it) })
        etPassword.addTextChangedListener(SimpleTextWatcher { viewModel.onPasswordChange(it) })

        // Staggered entrance animation
        val animatableViews = listOf(etEmail, etPassword, btnPrimary, dividerOr, btnGoogle, tvModeToggle)
        animatableViews.forEachIndexed { index, v ->
            v.alpha = 0f
            v.translationY = 40f * resources.displayMetrics.density
            v.animate()
                .alpha(1f).translationY(0f)
                .setDuration(400)
                .setStartDelay(index * 50L + 100L)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }

        // Observe ViewModel state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.email.collect { email ->
                        if (etEmail.text.toString() != email) {
                            etEmail.setText(email)
                            etEmail.setSelection(email.length)
                        }
                    }
                }
                launch {
                    viewModel.password.collect { password ->
                        if (etPassword.text.toString() != password) {
                            etPassword.setText(password)
                            etPassword.setSelection(password.length)
                        }
                    }
                }
                launch {
                    viewModel.isLoginMode.collect { isLogin ->
                        btnPrimary.text = if (isLogin) getString(R.string.auth_sign_in) else getString(R.string.auth_sign_up)
                        tvModeToggle.text = if (isLogin) getString(R.string.auth_toggle_to_signup) else getString(R.string.auth_toggle_to_signin)
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        btnPrimary.isEnabled = !loading
                        if (loading) {
                            btnPrimary.text = \"\"
                            progressLoading.visibility = View.VISIBLE
                        } else {
                            btnPrimary.text = if (viewModel.isLoginMode.value) getString(R.string.auth_sign_in) else getString(R.string.auth_sign_up)
                            progressLoading.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.errorMessage.collect { error ->
                        tvError.visibility = if (error != null) View.VISIBLE else View.GONE
                        tvError.text = error
                    }
                }
                launch {
                    settingsViewModel.isLoggedIn.collect { loggedIn ->
                        if (loggedIn) navigateToDashboard()
                    }
                }
            }
        }
    }

    private fun navigateToDashboard() {
        findNavController().navigate(R.id.action_auth_to_dashboard)
    }
}

// Utility class to reduce TextWatcher boilerplate
class SimpleTextWatcher(private val onChanged: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) { onChanged(s.toString()) }
}
```

**Rationale**: The existing `AuthScreen.kt` already inflates `screen_auth.xml` via `AndroidView`. The Fragment version is a clean extraction — same XML layout, same bindings, no Compose wrapper.

#### DashboardFragment

Create file: `presentation/screens/dashboard/DashboardFragment.kt`

This is the most complex migration. Create a new XML layout file first.

Create file: `res/layout/fragment_dashboard.xml`

```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android=\"http://schemas.android.com/apk/res/android\"
    xmlns:app=\"http://schemas.android.com/apk/res-auto\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"match_parent\"
    android:fitsSystemWindows=\"true\">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width=\"match_parent\"
        android:layout_height=\"wrap_content\"
        android:background=\"?attr/colorSurface\"
        app:elevation=\"0dp\">

        <com.google.android.material.appbar.MaterialToolbar
            android:id=\"@+id/toolbar\"
            android:layout_width=\"match_parent\"
            android:layout_height=\"?attr/actionBarSize\"
            app:title=\"Decluttr\"
            app:titleTextAppearance=\"@style/TextAppearance.Material3.TitleLarge\"
            app:menu=\"@menu/toolbar_dashboard\" />
    </com.google.android.material.appbar.AppBarLayout>

    <FrameLayout
        android:id=\"@+id/content_container\"
        android:layout_width=\"match_parent\"
        android:layout_height=\"match_parent\"
        app:layout_behavior=\"@string/appbar_scrolling_view_behavior\" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id=\"@+id/bottom_nav\"
        android:layout_width=\"match_parent\"
        android:layout_height=\"wrap_content\"
        android:layout_gravity=\"bottom\"
        app:menu=\"@menu/bottom_nav_menu\"
        app:labelVisibilityMode=\"labeled\"
        style=\"@style/Widget.Material3.BottomNavigationView\" />
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

Create file: `res/menu/toolbar_dashboard.xml`

```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<menu xmlns:android=\"http://schemas.android.com/apk/res/android\"
    xmlns:app=\"http://schemas.android.com/apk/res-auto\">
    <item
        android:id=\"@+id/action_settings\"
        android:icon=\"@android:drawable/ic_menu_preferences\"
        android:title=\"Settings\"
        app:showAsAction=\"ifRoom\" />
</menu>
```

Then create `DashboardFragment.kt`:

```kotlin
@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: DashboardViewModel by viewModels()
    private var selectedTabIndex = 0
    private var selectedAppId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val bottomNav = view.findViewById<BottomNavigationView>(R.id.bottom_nav)
        val contentContainer = view.findViewById<FrameLayout>(R.id.content_container)

        // Toolbar
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                findNavController().navigate(R.id.action_dashboard_to_settings)
                true
            } else false
        }

        // Bottom navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_discover -> { switchTab(0, contentContainer); true }
                R.id.nav_archive -> { switchTab(1, contentContainer); true }
                else -> false
            }
        }

        // Initial tab
        switchTab(0, contentContainer)

        // Observe review events for bulk review dialog
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reviewEvent.collect { data ->
                    showBulkReviewDialog(data)
                }
            }
        }
    }

    private fun switchTab(index: Int, container: FrameLayout) {
        selectedTabIndex = index
        // Replace content_container child fragment or View
        // For Discovery: inflate the native discovery layout
        // For Archive: inflate the native archive layout
        // Implementation: Use child fragments or direct view management
        // (See DiscoveryFragment and ArchiveFragment below)

        val fragment = when (index) {
            0 -> DiscoveryFragment()
            1 -> ArchiveFragment()
            else -> return
        }
        childFragmentManager.beginTransaction()
            .replace(R.id.content_container, fragment)
            .commit()
    }

    private fun showBulkReviewDialog(data: DashboardViewModel.ReviewData) {
        if (data.archivedApps.isEmpty()) return
        val activity = activity ?: return

        NativeBulkReviewDialog(
            context = activity,
            archivedApps = data.archivedApps,
            onComplete = { notesMap ->
                viewModel.saveReviewNotes(notesMap, data.celebration)
                // Switch to archive tab
                view?.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_archive
            },
            onCancel = {
                viewModel.saveReviewNotes(emptyMap(), data.celebration)
                view?.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_archive
            }
        ).show()
    }
}
```

**Key pattern**: The `DashboardScreen.kt` currently mixes Compose state management (`remember`, `collectAsState`) with native dialog triggering via `DisposableEffect`. In the Fragment version, `lifecycleScope.collect` replaces `collectAsState`, and dialogs are shown directly without `DisposableEffect` wrappers.

#### DiscoveryFragment and ArchiveFragment

These are child fragments of `DashboardFragment`. They replace `DiscoveryScreen.kt` and `ArchivedAppsList.kt` respectively. The existing RecyclerView adapters (`DiscoveryDashboardAdapter`, `ArchivedAppsAdapter`) are already native and can be used as-is.

**The key migration pattern for each**:
1. Replace `@Composable fun` with `class XFragment : Fragment(R.layout.fragment_x)`
2. Replace `val x by viewModel.x.collectAsState()` with `viewModel.x.collect { ... }` in `lifecycleScope`
3. Replace `var x by remember { mutableStateOf(...) }` with class member variables
4. Replace `AndroidView(factory={...}, update={...})` — extract the factory's view creation into `onViewCreated`, and the update logic into `collect` observers
5. Replace `BackHandler` with `requireActivity().onBackPressedDispatcher.addCallback(...)`
6. Replace `DisposableEffect` with lifecycle-aware patterns (add/remove in `onStart`/`onStop`)
7. Replace `LaunchedEffect(key)` with `collect {}` or `observe {}` blocks gated on the key

#### SettingsFragment

Create file: `presentation/screens/settings/SettingsFragment.kt`

Create file: `res/layout/fragment_settings.xml`

```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"
    xmlns:app=\"http://schemas.android.com/apk/res-auto\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"match_parent\"
    android:orientation=\"vertical\">

    <com.google.android.material.appbar.MaterialToolbar
        android:id=\"@+id/toolbar\"
        android:layout_width=\"match_parent\"
        android:layout_height=\"?attr/actionBarSize\"
        app:navigationIcon=\"?attr/homeAsUpIndicator\"
        app:title=\"Settings\"
        app:titleTextAppearance=\"@style/TextAppearance.Material3.TitleLarge\" />

    <ScrollView
        android:layout_width=\"match_parent\"
        android:layout_height=\"match_parent\">

        <LinearLayout
            android:layout_width=\"match_parent\"
            android:layout_height=\"wrap_content\"
            android:orientation=\"vertical\"
            android:padding=\"16dp\">

            <TextView
                android:layout_width=\"wrap_content\"
                android:layout_height=\"wrap_content\"
                android:text=\"Data Management\"
                android:textAppearance=\"?attr/textAppearanceTitleLarge\"
                android:textColor=\"?attr/colorPrimary\"
                android:layout_marginBottom=\"16dp\" />

            <com.google.android.material.button.MaterialButton
                android:id=\"@+id/btn_export\"
                android:layout_width=\"match_parent\"
                android:layout_height=\"wrap_content\"
                android:text=\"Export Archive (JSON)\" />

            <com.google.android.material.button.MaterialButton
                android:id=\"@+id/btn_import\"
                android:layout_width=\"match_parent\"
                android:layout_height=\"wrap_content\"
                android:layout_marginTop=\"16dp\"
                android:text=\"Import Archive (JSON)\" />

            <ProgressBar
                android:id=\"@+id/progress\"
                android:layout_width=\"wrap_content\"
                android:layout_height=\"wrap_content\"
                android:layout_gravity=\"center_horizontal\"
                android:layout_marginTop=\"32dp\"
                android:visibility=\"gone\" />

            <TextView
                android:layout_width=\"wrap_content\"
                android:layout_height=\"wrap_content\"
                android:text=\"Account\"
                android:textAppearance=\"?attr/textAppearanceTitleLarge\"
                android:textColor=\"?attr/colorPrimary\"
                android:layout_marginTop=\"48dp\"
                android:layout_marginBottom=\"16dp\" />

            <TextView
                android:id=\"@+id/tv_email\"
                android:layout_width=\"wrap_content\"
                android:layout_height=\"wrap_content\"
                android:textAppearance=\"?attr/textAppearanceBodyMedium\"
                android:layout_marginBottom=\"16dp\" />

            <com.google.android.material.button.MaterialButton
                android:id=\"@+id/btn_signout\"
                android:layout_width=\"match_parent\"
                android:layout_height=\"wrap_content\"
                android:backgroundTint=\"?attr/colorError\"
                android:text=\"Sign Out\"
                android:visibility=\"gone\" />
        </LinearLayout>
    </ScrollView>
</LinearLayout>
```

The Fragment class follows the same pattern: `by viewModels()`, `lifecycleScope.launch { collect {} }`.

### 3.4 Remove Compose Theme System

After all screens are migrated to Fragments:

1. **Delete** `ui/theme/Theme.kt`, `ui/theme/Color.kt`, `ui/theme/Type.kt`
2. **Enhance** `res/values/themes.xml` to carry the full color palette
3. **Add** `res/values-night/themes.xml` for dark mode
4. **Add** `res/values/colors.xml` with all color values

Create file: `res/values/colors.xml`

```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<resources>
    <!-- Primary -->
    <color name=\"primary_40\">#275CAA</color>
    <color name=\"primary_80\">#AEC6FF</color>

    <!-- Secondary -->
    <color name=\"secondary_40\">#535E78</color>
    <color name=\"secondary_80\">#BBC6E4</color>

    <!-- Tertiary -->
    <color name=\"tertiary_40\">#704A7B</color>
    <color name=\"tertiary_80\">#DFB8EC</color>

    <!-- Dark theme specific -->
    <color name=\"dark_background\">#0D1117</color>
    <color name=\"dark_surface\">#161B22</color>
    <color name=\"dark_primary_text\">#E5E7EB</color>
    <color name=\"dark_secondary_text\">#9CA3AF</color>
    <color name=\"accent_teal\">#14B8A6</color>
</resources>
```

**Rationale**: Compose's `MaterialTheme` is a runtime construct. When Compose is removed, themes must come from XML resources. The native XML views already reference `?attr/colorSurface` etc., which are provided by the `Theme.Material3.DayNight.NoActionBar` parent theme. On Android 12+, dynamic colors are automatic when using Material3 XML themes.

### 3.5 Remove `NativeThemeColors` Bridge

Currently `DiscoveryScreen.kt` and `DiscoveryAppsAdapter.kt` use a custom `NativeThemeColors` data class to pass Compose `MaterialTheme` colors into native Views. After migration, native views get theme colors directly from XML attributes (`?attr/colorOnSurface`), so:

1. **Remove** the `NativeThemeColors` data class from `DiscoveryAppsAdapter.kt`
2. **Update** all adapters to resolve colors via `context.obtainStyledAttributes()` or `MaterialColors.getColor(view, attr)` instead of receiving colors as constructor parameters
3. **Remove** the `themeColors` parameter from all adapter constructors

Example replacement in adapter `bind()`:
```kotlin
// BEFORE (Compose bridge):
name.setTextColor(themeColors.textPrimary)

// AFTER (native theme resolution):
// Not needed — the XML layout already sets android:textColor=\"?attr/colorOnSurface\"
// Remove all manual setTextColor calls that just replicate XML attributes
```

### 3.6 Remove `@Immutable` Annotation from Domain Model

In `GetInstalledAppsUseCase.kt` line 17:

```kotlin
// REMOVE this line:
@androidx.compose.runtime.Immutable
```

This is a Compose annotation on a domain-layer data class. It has no effect in Views and creates an unnecessary Compose dependency in the domain layer.

### 3.7 Clean Up Build Dependencies

After all Compose code is removed, update `app/build.gradle.kts`:

**Remove**:
```kotlin
id(\"org.jetbrains.kotlin.plugin.compose\")  // Plugin
implementation(\"androidx.activity:activity-compose:1.9.0\")
implementation(platform(\"androidx.compose:compose-bom:2024.06.00\"))
implementation(\"androidx.compose.ui:ui\")
implementation(\"androidx.compose.ui:ui-graphics\")
implementation(\"androidx.compose.ui:ui-tooling-preview\")
implementation(\"androidx.compose.material3:material3\")
implementation(\"androidx.navigation:navigation-compose:2.8.0-beta03\")
implementation(\"androidx.hilt:hilt-navigation-compose:1.2.0\")
implementation(\"io.coil-kt:coil-compose:2.6.0\")
androidTestImplementation(platform(\"androidx.compose:compose-bom:2024.06.00\"))
androidTestImplementation(\"androidx.compose.ui:ui-test-junit4\")
debugImplementation(\"androidx.compose.ui:ui-tooling\")
debugImplementation(\"androidx.compose.ui:ui-test-manifest\")
```

**Remove build feature**:
```kotlin
compose = true  // remove this line from buildFeatures
```

**Add**:
```kotlin
implementation(\"androidx.navigation:navigation-fragment-ktx:2.8.5\")
implementation(\"androidx.navigation:navigation-ui-ktx:2.8.5\")
implementation(\"androidx.fragment:fragment-ktx:1.8.5\")
implementation(\"androidx.appcompat:appcompat:1.7.0\")
implementation(\"androidx.coordinatorlayout:coordinatorlayout:1.2.0\")
```

**Enable viewBinding** (recommended for cleaner view access):
```kotlin
buildFeatures {
    buildConfig = true
    viewBinding = true  // Add this
}
```

Also remove from the root `build.gradle.kts`:
```kotlin
id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.1.0\" apply false
```

---

## 4. Phase 2: UI Consistency Fixes

### 4.1 Auth Screen Hardcoded Colors

**Problem**: `screen_auth.xml` uses 15+ hardcoded hex colors (`#0a0c10`, `#e2e8f0`, `#94a3b8`, `#d4af37`, etc.) while every other screen uses Material theme attributes (`?attr/colorSurface`, `?attr/colorOnSurface`).

**Impact**: On light mode, the auth screen is dark while the rest of the app is light. On devices with dynamic colors (Android 12+), the auth screen ignores the user's wallpaper-based palette.

**Fix strategy**: Replace all hardcoded colors with theme attributes or color resources. The dark editorial look can be preserved as a themed overlay:

Create file: `res/values/themes.xml` — Add:
```xml
<style name=\"ThemeOverlay.Decluttr.Auth\" parent=\"\">
    <item name=\"colorSurface\">#0a0c10</item>
    <item name=\"colorOnSurface\">#e2e8f0</item>
    <item name=\"colorOnSurfaceVariant\">#94a3b8</item>
    <item name=\"colorPrimary\">#d4af37</item>
</style>
```

Then in the auth layout root: `android:theme=\"@style/ThemeOverlay.Decluttr.Auth\"`

**Or** (recommended): Align auth screen with app theme by replacing hardcoded colors:

| Hardcoded Color | Replace With |
|----------------|-------------|
| `#0a0c10` (background) | `?attr/colorSurface` |
| `#e2e8f0` (primary text) | `?attr/colorOnSurface` |
| `#94a3b8` (secondary text) | `?attr/colorOnSurfaceVariant` |
| `#d4af37` (accent/gold) | `?attr/colorPrimary` |
| `#ff6e84` (error) | `?attr/colorError` |
| `#1Affffff` (divider) | `?attr/colorOutlineVariant` |

### 4.2 Emoji Icons in Smart Cards

**Problem**: Smart cards use emoji strings (`\"📦\"`, `\"💾\"`) as icons via a `TextView`. Emojis render differently across OEMs and Android versions — some render monochrome, some color, some as squares.

**Fix**: Replace emoji `TextView` icons with Material Design vector drawables:

```kotlin
// In DiscoveryScreen.kt / DiscoveryDashboard():
// BEFORE:
add(DashboardItem.SmartCard(icon = \"📦\", ...))
add(DashboardItem.SmartCard(icon = \"💾\", ...))

// AFTER:
add(DashboardItem.SmartCard(iconRes = R.drawable.ic_archive_outlined, ...))
add(DashboardItem.SmartCard(iconRes = R.drawable.ic_storage_outlined, ...))
```

Update `DashboardItem.SmartCard`:
```kotlin
data class SmartCard(
    val iconRes: Int,  // Changed from String icon
    val title: String,
    val description: String,
    val viewState: DiscoveryViewState
) : DashboardItem()
```

Update `item_smart_card.xml`: Change the icon `TextView` to `ImageView`:
```xml
<!-- REPLACE this: -->
<TextView android:id=\"@+id/card_icon\" ... />
<!-- WITH this: -->
<ImageView
    android:id=\"@+id/card_icon\"
    android:layout_width=\"28dp\"
    android:layout_height=\"28dp\"
    android:layout_gravity=\"center\"
    app:tint=\"?attr/colorOnSurfaceContainer\" />
```

Update `SmartCardViewHolder.bind()`:
```kotlin
// BEFORE:
icon.text = item.icon
// AFTER:
icon.setImageResource(item.iconRes)
```

Create vector drawables `ic_archive_outlined.xml` and `ic_storage_outlined.xml` from Material Icons.

### 4.3 Mixed Button Styles

**Problem**: Auth screen uses a styled `TextView` (`btn_primary_action`) as a button instead of `MaterialButton`. This means no ripple effect, no accessibility role, no Material states.

**Fix**: In `screen_auth.xml`, replace:
```xml
<TextView android:id=\"@+id/btn_primary_action\" ... />
```
With:
```xml
<com.google.android.material.button.MaterialButton
    android:id=\"@+id/btn_primary_action\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"56dp\"
    android:text=\"Sign In\"
    android:textAllCaps=\"false\"
    android:textStyle=\"bold\"
    style=\"@style/Widget.Material3.Button\" />
```

### 4.4 SettingsScreen vs Native Inconsistency

**Problem**: `SettingsScreen.kt` is the only screen written in pure Compose with no native XML fallback. After Fragment migration (Phase 1), this is resolved.

### 4.5 Typography Consistency

**Problem**: Auth screen uses hardcoded `textSize` (`36sp`, `16sp`, `11sp`). Other screens use Material text appearances (`?attr/textAppearanceTitleMedium`, etc.).

**Fix**: Replace all hardcoded `textSize` in `screen_auth.xml`:

| Current | Replace With |
|---------|-------------|
| `android:textSize=\"36sp\"` | `android:textAppearance=\"?attr/textAppearanceDisplaySmall\"` |
| `android:textSize=\"16sp\"` | `android:textAppearance=\"?attr/textAppearanceBodyLarge\"` |
| `android:textSize=\"14sp\"` | `android:textAppearance=\"?attr/textAppearanceBodyMedium\"` |
| `android:textSize=\"11sp\"` | `android:textAppearance=\"?attr/textAppearanceLabelSmall\"` |
| `android:textSize=\"18sp\"` (button) | `android:textAppearance=\"?attr/textAppearanceLabelLarge\"` |
| `android:textSize=\"10sp\"` | `android:textAppearance=\"?attr/textAppearanceLabelSmall\"` |
| `android:textSize=\"12sp\"` | `android:textAppearance=\"?attr/textAppearanceBodySmall\"` |

---

## 5. Phase 3: Production Readiness — Build & Security

### 5.1 Enable R8/ProGuard for Release

**Problem**: `isMinifyEnabled = false` means the release APK includes all code, all reflection paths, and is significantly larger than necessary. R8 also performs dead code elimination and obfuscation.

**Fix** in `app/build.gradle.kts`:
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true  // CHANGED from false
        isShrinkResources = true  // ADD: removes unused resources
        proguardFiles(
            getDefaultProguardFile(\"proguard-android-optimize.txt\"),
            \"proguard-rules.pro\"
        )
    }
}
```

Update `proguard-rules.pro` to protect classes used by reflection (Hilt, Room, Firebase, Coil):
```proguard
# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Coil
-keep class coil.** { *; }
-keep class com.tool.decluttr.presentation.util.AppIconModel { *; }

# Keep data classes used in Firestore serialization
-keep class com.tool.decluttr.domain.model.** { *; }
-keep class com.tool.decluttr.data.local.entity.** { *; }
```

### 5.2 Remove Sensitive Files from Repository

**Problem**: `google-services.json` and `debug.keystore` are committed to the repository.

**Fix**:
1. Add to `.gitignore`:
```
app/google-services.json
app/debug.keystore
app/release.keystore
*.jks
```
2. Move `google-services.json` to a CI/CD secret and inject at build time
3. Document setup in README for new developers

### 5.3 Add Network Security Config

Create file: `res/xml/network_security_config.xml`
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<network-security-config>
    <base-config cleartextTrafficPermitted=\"false\">
        <trust-anchors>
            <certificates src=\"system\" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

Add to `AndroidManifest.xml` in `<application>`:
```xml
android:networkSecurityConfig=\"@xml/network_security_config\"
```

### 5.4 Add Crashlytics

Since Firebase is already integrated, add crash reporting:

`build.gradle.kts` (app):
```kotlin
implementation(\"com.google.firebase:firebase-crashlytics\")
```

`build.gradle.kts` (project):
```kotlin
id(\"com.google.firebase.crashlytics\") version \"3.0.3\" apply false
```

Apply in app-level:
```kotlin
id(\"com.google.firebase.crashlytics\")
```

Replace the custom `Thread.setDefaultUncaughtExceptionHandler` in `MainActivity.kt` — Crashlytics handles this automatically.

### 5.5 Replace Silent Exception Swallowing

**Problem**: Multiple locations use `catch (e: Exception) { e.printStackTrace() }` or `catch (_: Exception) {}`.

**Fix**: After Crashlytics is integrated:
```kotlin
// BEFORE:
} catch (e: Exception) {
    e.printStackTrace()
}

// AFTER:
} catch (e: Exception) {
    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
}
```

Locations to fix:
- `AppRepositoryImpl.kt` lines 46, 82, 93, 109
- `DashboardScreen.kt` line 93-95
- `NativeAppDetailsDialog.kt` line 70
- `ShareReceiverActivity.kt` — already has proper handling

### 5.6 Add Release Signing Config

In `app/build.gradle.kts`, add:
```kotlin
signingConfigs {
    create(\"release\") {
        storeFile = file(System.getenv(\"KEYSTORE_PATH\") ?: \"release.keystore\")
        storePassword = System.getenv(\"KEYSTORE_PASSWORD\") ?: \"\"
        keyAlias = System.getenv(\"KEY_ALIAS\") ?: \"\"
        keyPassword = System.getenv(\"KEY_PASSWORD\") ?: \"\"
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName(\"release\")
        // ...existing config
    }
}
```

---

## 6. Phase 4: Data Integrity & Reliability

### 6.1 Fix Export/Import Missing Fields

**Problem**: `ExportArchiveUseCase.kt` exports only `packageId`, `name`, `category`, `tags`, `notes`, `archivedAt`. It does NOT export:
- `isPlayStoreInstalled`
- `lastTimeUsed`
- `folderName`

Similarly, `ImportArchiveUseCase.kt` does not import these fields.

**Fix** `ExportArchiveUseCase.kt`:
```kotlin
val jsonObject = JSONObject().apply {
    put(\"packageId\", app.packageId)
    put(\"name\", app.name)
    put(\"category\", app.category ?: \"\")
    put(\"tags\", app.tags.joinToString(\",\"))
    put(\"notes\", app.notes ?: \"\")
    put(\"archivedAt\", app.archivedAt)
    put(\"isPlayStoreInstalled\", app.isPlayStoreInstalled)  // ADD
    put(\"lastTimeUsed\", app.lastTimeUsed)                   // ADD
    put(\"folderName\", app.folderName ?: \"\")                 // ADD
}
```

**Fix** `ImportArchiveUseCase.kt`:
```kotlin
val app = ArchivedApp(
    packageId = obj.getString(\"packageId\"),
    name = obj.getString(\"name\"),
    category = obj.optString(\"category\").takeIf { it.isNotBlank() },
    tags = obj.optString(\"tags\").split(\",\").map { it.trim() }.filter { it.isNotEmpty() },
    notes = obj.optString(\"notes\").takeIf { it.isNotBlank() },
    archivedAt = obj.optLong(\"archivedAt\", System.currentTimeMillis()),
    isPlayStoreInstalled = obj.optBoolean(\"isPlayStoreInstalled\", true),  // ADD
    lastTimeUsed = obj.optLong(\"lastTimeUsed\", 0L),                       // ADD
    folderName = obj.optString(\"folderName\").takeIf { it.isNotBlank() },  // ADD
    iconBytes = null
)
```

### 6.2 Fix Destructive Migration Fallback

**Problem**: `Room.databaseBuilder(...).fallbackToDestructiveMigration()` means if the app is updated with a schema change and no migration is provided, **ALL USER DATA IS DELETED SILENTLY**.

**Fix**: Replace `fallbackToDestructiveMigration()` with explicit migrations. Currently at version 3, prepare for version 4:

In `AppModule.kt`:
```kotlin
// BEFORE:
.addMigrations(DecluttrDatabase.MIGRATION_2_3)
.fallbackToDestructiveMigration()

// AFTER:
.addMigrations(DecluttrDatabase.MIGRATION_2_3)
// DO NOT add fallbackToDestructiveMigration()
// App will crash if migration is missing — this is intentional.
// It forces developers to write migrations and never silently lose data.
```

Also enable schema export for migration verification:
```kotlin
// In DecluttrDatabase.kt:
@Database(entities = [AppEntity::class], version = 3, exportSchema = true)  // Changed false -> true
```

And in `build.gradle.kts`:
```kotlin
ksp {
    arg(\"room.schemaLocation\", \"$projectDir/schemas\")
}
```

### 6.3 Fix AppRepositoryImpl Lifecycle Leak

**Problem**: `AppRepositoryImpl` creates its own `CoroutineScope(Dispatchers.IO)` that is never cancelled. This scope lives as long as the singleton exists (forever, since it's `@Singleton`). While technically not a leak for a singleton, the `auth.addAuthStateListener` inside `init` block also never gets removed.

**Fix**: Since the repository is a `@Singleton`, the scope outlives any single component, which is acceptable. But add proper error handling:

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

The `SupervisorJob` ensures one failed coroutine doesn't cancel the entire scope.

### 6.4 Firestore Sync Improvements

**Problem**: `syncFromFirestore()` only syncs FROM Firestore on login. If a local change is made offline and the user logs in on another device, the Firestore state wins.

**Fix (minimal)**: Add a `lastModified` timestamp to both local and remote records. During sync, use last-write-wins strategy:

In `AppEntity.kt`, add:
```kotlin
val lastModified: Long = System.currentTimeMillis()
```

Add migration:
```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(\"ALTER TABLE archived_apps ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0\")
    }
}
```

Update `syncFromFirestore()` to compare timestamps before overwriting.

---

## 7. Phase 5: UX Polish & Missing Features

### 7.1 Non-Functional \"Forgot Password\" Link

**Problem**: In `screen_auth.xml` there is a \"FORGOT PASSWORD?\" `TextView` (line 161-167) that has no click handler and no `android:clickable=\"true\"`.

**Fix options**:
1. **Remove it** — simplest, if forgot-password isn't supported
2. **Implement it** — Add `android:id=\"@+id/tv_forgot_password\"` and wire to `FirebaseAuth.sendPasswordResetEmail()`

Implementation:
```kotlin
// In AuthFragment/AuthScreen:
val tvForgotPassword = view.findViewById<TextView>(R.id.tv_forgot_password)
tvForgotPassword.setOnClickListener {
    val email = etEmail.text.toString().trim()
    if (email.isBlank()) {
        tvError.visibility = View.VISIBLE
        tvError.text = \"Enter your email first\"
        return@setOnClickListener
    }
    FirebaseAuth.getInstance().sendPasswordResetEmail(email)
        .addOnSuccessListener {
            Toast.makeText(context, \"Reset email sent to $email\", Toast.LENGTH_LONG).show()
        }
        .addOnFailureListener { e ->
            tvError.visibility = View.VISIBLE
            tvError.text = e.localizedMessage ?: \"Failed to send reset email\"
        }
}
```

### 7.2 Google Sign-In is TODO

**Problem**: `NavGraph.kt` line 60 has `// TODO: Wire Google Sign-In via Credential Manager` and the `onGoogleSignIn` callback is a no-op.

**Fix**: Implement using Android Credential Manager API (the dependencies are already in `build.gradle.kts`):
```kotlin
implementation(\"androidx.credentials:credentials:1.2.2\")
implementation(\"androidx.credentials:credentials-play-services-auth:1.2.2\")
implementation(\"com.google.android.libraries.identity.googleid:googleid:1.1.1\")
```

Implementation steps:
1. Get the Web Client ID from `google-services.json` or Firebase Console
2. Build a `GetGoogleIdOption` request
3. Call `CredentialManager.create(context).getCredential(request)`
4. Extract `GoogleIdTokenCredential` from result
5. Create `FirebaseAuth` credential with `GoogleAuthProvider.getCredential(idToken, null)`
6. Sign in with `auth.signInWithCredential(credential)`

### 7.3 No Onboarding / Permission Explanation

**Problem**: The app requires `PACKAGE_USAGE_STATS` (a system-level permission) but the only explanation is a small card in the discovery screen. New users may not understand why they need to go to system Settings.

**Fix**: Add a first-launch onboarding flow:
1. Create `res/layout/fragment_onboarding.xml` with 2-3 screens explaining:
   - What Decluttr does (archive before uninstalling)
   - Why usage access is needed (to find rarely used apps)
   - How to grant the permission (with a screenshot)
2. Use `SharedPreferences` to track first launch
3. Show onboarding before auth screen on first launch

### 7.4 No Undo After Delete

**Problem**: Deleting an archived app is immediate and irreversible. The only safeguard is a confirmation dialog.

**Fix**: After deletion, show a `Snackbar` with an \"Undo\" action. Keep the deleted app in memory for 5 seconds:
```kotlin
fun deleteArchivedApp(app: ArchivedApp) {
    viewModelScope.launch {
        appRepository.deleteApp(app)
        // Emit an event to show Snackbar
        _undoEvent.emit(app)
    }
}
```

In the Fragment:
```kotlin
viewModel.undoEvent.collect { deletedApp ->
    Snackbar.make(view, \"${deletedApp.name} removed\", Snackbar.LENGTH_LONG)
        .setAction(\"Undo\") { viewModel.insertApp(deletedApp) }
        .show()
}
```

### 7.5 No Dark/Light Mode Toggle

**Problem**: App relies entirely on system setting. Some users want to override.

**Fix**: Add a toggle in Settings that uses `AppCompatDelegate.setDefaultNightMode()`:
```kotlin
AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)  // default
AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)  // force dark
AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)   // force light
```

Store preference in DataStore (already a dependency).

### 7.6 Missing Loading/Empty State Illustrations

**Problem**: Empty states show plain text (\"Your archive is currently empty.\"). Loading states show a generic `CircularProgressIndicator`.

**Fix**: Add contextual empty state illustrations and more descriptive loading states. At minimum, use Material icons:
```xml
<ImageView
    android:src=\"@drawable/ic_archive_outlined\"
    android:alpha=\"0.3\"
    android:layout_width=\"120dp\"
    android:layout_height=\"120dp\" />
```

---

## 8. Phase 6: Play Store Compliance

### 8.1 QUERY_ALL_PACKAGES Declaration

**Action Required**: When submitting to Play Store, you MUST fill out the **Permissions Declaration Form** in Play Console for `QUERY_ALL_PACKAGES`.

**Justification**: Decluttr's core purpose is discovering and managing installed apps for uninstallation and archiving. Broad app visibility is a fundamental requirement — without it, the app cannot function.

**Documentation needed**: Update Play Store listing to prominently state: \"Decluttr needs to see all installed apps to help you find rarely used ones.\"

### 8.2 Target SDK Compliance

**Current**: `targetSdk = 35` (Android 15)
**Required by August 2026**: `targetSdk = 36` (Android 16)

**Action**: Update to `targetSdk = 36` and `compileSdk = 36` before submission. Test for any behavioral changes in Android 16 (predictive back, per-app language, etc.).

### 8.3 Prominent Disclosure for Usage Stats

**Problem**: `PACKAGE_USAGE_STATS` requires the user to manually enable it in system Settings. Google Play policy requires prominent disclosure before directing users there.

**Fix**: The existing `PermissionWarning` card in the discovery screen partially addresses this, but it should:
1. Explain WHAT data is accessed (last-used timestamps, not app content)
2. Explain WHY (to identify rarely used apps)
3. Link to the privacy policy
4. Only then open the Settings page

### 8.4 Privacy Policy

**Action Required**: Create and host a privacy policy that covers:
- Usage stats data access (read-only, on-device only)
- Firebase Auth (email stored)
- Firestore cloud sync (what data is synced)
- No data sold to third parties
- Data deletion process (sign out clears cloud data)

### 8.5 App Bundle Format

Ensure the release is built as AAB (Android App Bundle), not APK. This is mandatory for Play Store since 2021.

---

## 9. Phase 7: Testing & CI/CD

### 9.1 Current State

- Only 1 test: `ArchiveNotesStateMachineTest.kt` (5 unit tests)
- Benchmark test runner configured but no actual benchmarks
- No UI tests, no integration tests

### 9.2 Minimum Viable Test Suite

Add tests for:

**Unit Tests** (use JUnit + Mockito/MockK):
- `ExtractPackageIdUseCase` — various URL formats
- `ExportArchiveUseCase` — verify JSON structure
- `ImportArchiveUseCase` — valid/invalid JSON
- `AppMapper` — entity <-> domain mapping
- `AuthViewModel` — state transitions, validation
- `DashboardViewModel` — selection logic, progress tracking

**Integration Tests**:
- Room DAO operations (insert, query, delete, update)
- Export -> Import roundtrip
- Migration testing (Room `MigrationTestHelper`)

**UI Tests** (Espresso):
- Auth flow: login, signup, skip
- Discovery tab: permission warning, app list
- Archive tab: search, filter, folder creation
- Settings: export, import, sign out

### 9.3 Fix Test Instrumentation Runner

**Problem**: `testInstrumentationRunner = \"androidx.benchmark.junit4.AndroidBenchmarkRunner\"` is set but the app isn't a benchmark target. Regular instrumented tests won't run.

**Fix**:
```kotlin
testInstrumentationRunner = \"dagger.hilt.android.testing.HiltTestRunner\"
```

Or use the default: `\"androidx.test.runner.AndroidJUnitRunner\"`

Add Hilt testing dependency:
```kotlin
androidTestImplementation(\"com.google.dagger:hilt-android-testing:2.54\")
kspAndroidTest(\"com.google.dagger:hilt-android-compiler:2.54\")
```

---

## 10. Phase 8: Performance Optimization

### 10.1 Icon Storage in Room

**Current**: Icons are stored as `ByteArray` in `AppEntity.iconBytes`. Each icon is ~2-5KB (WEBP 60% quality, 96x96). With 100 archived apps, that's 200-500KB in the database.

**Assessment**: This is acceptable. The icons are small and the Coil pipeline already has memory caching on top.

**Improvement** (optional): Store icons in the app's internal files directory instead of SQLite BLOB. This reduces database size and enables the filesystem cache layer. But current approach works fine for expected usage (<1000 apps).

### 10.2 Icon Prefetch Strategy

**Current**: Pre-fetches first 48 icons blocking, then 12 more async, then lazy-warms remaining in chunks of 12 with parallelism of 6.

**Assessment**: Well-designed. The chunked parallel approach prevents OOM. The blocking count (48) ensures the first two screenfuls are ready before rendering.

**Improvement**: Add memory pressure awareness:
```kotlin
private fun getInitialPreloadCount(): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return if (activityManager.isLowRamDevice) 24 else 48
}
```

### 10.3 RecyclerView Optimization

The existing adapters use `ListAdapter` with `DiffUtil`, which is correct. Ensure:
- `setHasFixedSize(true)` is called on RecyclerViews where item count doesn't change container size
- `RecycledViewPool` is shared between similar RecyclerViews if any are nested
- ViewHolders don't create new objects in `bind()` (current code is clean)

---

## 11. Dependency Changes Summary

### Add
```kotlin
// Navigation (Fragment-based)
implementation(\"androidx.navigation:navigation-fragment-ktx:2.8.5\")
implementation(\"androidx.navigation:navigation-ui-ktx:2.8.5\")

// Fragment KTX (for by viewModels())
implementation(\"androidx.fragment:fragment-ktx:1.8.5\")

// AppCompat (for AppCompatActivity)
implementation(\"androidx.appcompat:appcompat:1.7.0\")

// CoordinatorLayout (for dashboard layout)
implementation(\"androidx.coordinatorlayout:coordinatorlayout:1.2.0\")

// Crashlytics
implementation(\"com.google.firebase:firebase-crashlytics\")

// Hilt testing
androidTestImplementation(\"com.google.dagger:hilt-android-testing:2.54\")
kspAndroidTest(\"com.google.dagger:hilt-android-compiler:2.54\")
```

### Remove (after migration complete)
```kotlin
// Compose core
implementation(platform(\"androidx.compose:compose-bom:2024.06.00\"))
implementation(\"androidx.compose.ui:ui\")
implementation(\"androidx.compose.ui:ui-graphics\")
implementation(\"androidx.compose.ui:ui-tooling-preview\")
implementation(\"androidx.compose.material3:material3\")

// Compose integration
implementation(\"androidx.activity:activity-compose:1.9.0\")
implementation(\"androidx.navigation:navigation-compose:2.8.0-beta03\")
implementation(\"androidx.hilt:hilt-navigation-compose:1.2.0\")
implementation(\"io.coil-kt:coil-compose:2.6.0\")

// Compose testing
androidTestImplementation(platform(\"androidx.compose:compose-bom:2024.06.00\"))
androidTestImplementation(\"androidx.compose.ui:ui-test-junit4\")
debugImplementation(\"androidx.compose.ui:ui-tooling\")
debugImplementation(\"androidx.compose.ui:ui-test-manifest\")
```

### Plugin Changes
```kotlin
// REMOVE from both project and app build.gradle.kts:
id(\"org.jetbrains.kotlin.plugin.compose\")

// ADD to project build.gradle.kts:
id(\"com.google.firebase.crashlytics\") version \"3.0.3\" apply false

// ADD to app build.gradle.kts:
id(\"com.google.firebase.crashlytics\")
```

---

## 12. File-by-File Change Map

### Files to CREATE (new)
| File | Purpose |
|------|---------|
| `res/layout/activity_main.xml` | NavHostFragment container |
| `res/layout/fragment_dashboard.xml` | Dashboard with toolbar + bottom nav + content container |
| `res/layout/fragment_settings.xml` | Settings screen layout |
| `res/layout/fragment_discovery.xml` | Discovery tab content (or reuse programmatic approach) |
| `res/layout/fragment_archive.xml` | Archive tab content |
| `res/navigation/nav_graph.xml` | Navigation graph |
| `res/menu/toolbar_dashboard.xml` | Dashboard toolbar menu |
| `res/values/colors.xml` | Centralized color resources |
| `res/values-night/themes.xml` | Dark theme overrides (if not using DayNight auto) |
| `res/xml/network_security_config.xml` | Network security |
| `presentation/screens/auth/AuthFragment.kt` | Auth screen as Fragment |
| `presentation/screens/dashboard/DashboardFragment.kt` | Dashboard as Fragment |
| `presentation/screens/dashboard/DiscoveryFragment.kt` | Discovery tab as Fragment |
| `presentation/screens/dashboard/ArchiveFragment.kt` | Archive tab as Fragment |
| `presentation/screens/settings/SettingsFragment.kt` | Settings as Fragment |
| `presentation/util/SimpleTextWatcher.kt` | Utility for TextWatcher boilerplate |

### Files to MODIFY
| File | Changes |
|------|---------|
| `MainActivity.kt` | `ComponentActivity` -> `AppCompatActivity`, remove Compose, add Fragment nav |
| `app/build.gradle.kts` | Remove Compose deps, add Fragment/Nav deps, enable R8, add viewBinding |
| `build.gradle.kts` (project) | Remove Compose plugin, add Crashlytics plugin |
| `AndroidManifest.xml` | Add `networkSecurityConfig` |
| `DecluttrDatabase.kt` | `exportSchema = true`, remove `fallbackToDestructiveMigration` |
| `AppModule.kt` | Remove `fallbackToDestructiveMigration()` from Room builder |
| `GetInstalledAppsUseCase.kt` | Remove `@Immutable` annotation |
| `ExportArchiveUseCase.kt` | Add missing fields to export |
| `ImportArchiveUseCase.kt` | Add missing fields to import |
| `AppRepositoryImpl.kt` | `SupervisorJob` in scope, Crashlytics for exceptions |
| `DiscoveryDashboardAdapter.kt` | Remove `NativeThemeColors` param, use XML theme attrs |
| `DiscoveryAppsAdapter.kt` | Remove `NativeThemeColors` param, use XML theme attrs |
| `screen_auth.xml` | Replace hardcoded colors with theme attributes |
| `item_smart_card.xml` | Replace emoji `TextView` with `ImageView` |
| `proguard-rules.pro` | Add keep rules for Firebase, Room, Hilt, Coil |
| `themes.xml` | Potentially add auth overlay theme |
| `.gitignore` | Add `google-services.json`, `debug.keystore` |

### Files to DELETE (after migration complete)
| File | Reason |
|------|--------|
| `presentation/navigation/NavGraph.kt` | Replaced by XML nav graph |
| `presentation/screens/auth/AuthScreen.kt` | Replaced by `AuthFragment.kt` |
| `presentation/screens/dashboard/DashboardScreen.kt` | Replaced by `DashboardFragment.kt` |
| `presentation/screens/dashboard/DiscoveryScreen.kt` | Replaced by `DiscoveryFragment.kt` |
| `presentation/screens/dashboard/ArchivedAppsList.kt` | Replaced by `ArchiveFragment.kt` |
| `presentation/screens/dashboard/ArchivedAppsRecyclerView.kt` | Inlined into `ArchiveFragment.kt` |
| `presentation/screens/settings/SettingsScreen.kt` | Replaced by `SettingsFragment.kt` |
| `ui/theme/Theme.kt` | Replaced by XML themes |
| `ui/theme/Color.kt` | Replaced by `colors.xml` |
| `ui/theme/Type.kt` | Replaced by XML text appearances |

---

## Priority Execution Order

For an LLM implementing these changes, execute in this order to maintain a working app at each step:

1. **Phase 1 (Steps 3.1-3.3)**: Create XML layouts and Fragments FIRST, then switch `MainActivity`. Keep old Compose files temporarily until verified.
2. **Phase 2 (Section 4)**: Fix UI consistency while screens are being migrated.
3. **Phase 3 (Section 5)**: Security and build config.
4. **Phase 4 (Section 6)**: Data integrity fixes.
5. **Phase 1 (Steps 3.4-3.7)**: Final cleanup — remove Compose dependencies only AFTER all screens are verified working as Fragments.
6. **Phase 5 (Section 7)**: UX polish.
7. **Phase 6-8**: Compliance, testing, performance.

> **Critical**: Do NOT remove Compose dependencies until ALL Fragment migrations are verified working. The app should be buildable and runnable after each phase.

---

## Notes for the Implementing LLM

1. **ViewModels are migration-safe**: All ViewModels use `StateFlow`/`SharedFlow` which work identically with both Compose (`collectAsState`) and Views (`lifecycleScope.collect`). No ViewModel changes needed.
2. **Adapters are migration-safe**: All RecyclerView adapters are already native. Only the `NativeThemeColors` bridging class needs removal.
3. **The biggest risk** is in `DiscoveryScreen.kt` and `ArchivedAppsList.kt` — these files mix Compose state management (`remember`, `mutableStateOf`) with native views via `AndroidView`. The Fragment versions need to replicate the same state management using class member variables and lifecycle-aware collection.
4. **Test after each Fragment**: After converting each screen to a Fragment, build and run to verify. Don't batch all migrations.
5. **The `SearchQueryCallback` pattern** (mutable callback reference stored as View tag) is already a native pattern. It can be kept as-is in Fragment implementations.
6. **Keep `DashboardItem` sealed class** and all adapter code exactly as-is. These are pure native code.
"
O