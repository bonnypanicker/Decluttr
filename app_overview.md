# Decluttr: App Overview & Architecture

## 💡 The Idea
**Decluttr** is a storage management utility for Android designed to solve the problem of "app bloat." It helps users reclaim storage space by identifying apps that are either massive in size or haven't been opened in months.

### Core Value Proposition
*   **Intelligent Discovery:** Categorizes apps into "Rarely Used," "Large Apps (>100MB)," and "All Apps."
*   **Archive Mechanism:** Instead of just deleting, Decluttr "archives" app metadata (category, notes, tags, install source) before uninstalling. This allows users to keep a record of what they had and why they removed it.
*   **Bulk Operations:** Users can search, filter, and batch-uninstall dozens of apps in a single flow.

---

## 🏗 Architecture Context

The project follows **Clean Architecture** principles combined with modern Android best practices (Jetpack Compose + Hilt).

### 1. Layers
*   **Domain (Use Cases):** Contains pure business logic. Examples: [GetUnusedAppsUseCase](file:///c:/Reminder/Decluttr/app/src/main/java/com/example/decluttr/domain/usecase/GetUnusedAppsUseCase.kt#11-84), [ArchiveAndUninstallUseCase](file:///c:/Reminder/Decluttr/app/src/main/java/com/example/decluttr/domain/usecase/ArchiveAndUninstallUseCase.kt#7-35). Logic here is independent of Frameworks.
*   **Data (Repositories & Local):**
    *   **Room DB:** Stores [ArchivedApp](file:///c:/Reminder/Decluttr/app/src/main/java/com/example/decluttr/domain/model/ArchivedApp.kt#3-45) entities (metadata persists even after the APK is gone).
    *   **DataStore:** Used for simple preferences and lifetime stats.
    *   **PackageManager:** Interfaces with the Android system to fetch app metrics (size, last used time, icons).
*   **Presentation (MVVM):**
    *   **ViewModels:** Manage UI state and handle asynchronous operations (like pre-decoding icons).
    *   **Compose UI:** A state-driven, "flat" design focused on performance and modern aesthetics.

### 2. Performance-First Design
Because Decluttr handles lists of 100+ components (each with a high-res icon), the architecture includes a specialized **Performance Layer**:
*   **IconCacheManager:** A logic singleton that caches raw byte arrays to minimize system calls.
*   **ViewModel Bitmap Cache:** A `StateFlow<Map<String, ImageBitmap>>` that stores fully decoded bitmaps. Decodes happen on `Dispatchers.Default` (background) to keep the Main Thread free for 120fps scrolling.
*   **Stability:** Heavy use of `@Immutable` models and custom [equals()](file:///c:/Reminder/Decluttr/app/src/main/java/com/example/decluttr/domain/model/ArchivedApp.kt#14-33) implementations to allow Compose to skip redundant UI updates.

### 3. Tech Stack
*   **Language:** Kotlin
*   **UI:** Jetpack Compose (Material 3)
*   **DI:** Hilt
*   **DB:** Room
*   **Concurrency:** Kotlin Coroutines & Flow
*   **Image Loading:** Custom cache + Pre-decoding (BitmapFactory)

---

## ⚙️ Performance Audit: All Apps List

### Primary Bottlenecks
*   **Eager bitmap decoding:** Pre-decoding icons for every installed app up front caused large, bursty allocations and GC pressure.
*   **Overly broad bitmap cache invalidation:** A full cache update triggered unnecessary UI work, especially when list data refreshed.
*   **Suboptimal list reuse:** List items lacked explicit recycling hints, and spacing was rendered as extra items.
*   **Scroll-driven work on the main thread:** Icon decode fallback could happen during composition, increasing frame time variance.

### Profiling Notes
*   **Browser DevTools / DOM / JS do not apply to Jetpack Compose.**
*   Compose equivalents are **Android Studio Profiler**, **Layout Inspector**, and **Recomposition counts**, focusing on main-thread frame time, layout passes, and allocation spikes.

---

## ✅ Optimization Strategy Implemented
*   **Lazy icon decoding:** Icons decode only for visible and near-visible items, not the entire dataset.
*   **Debounced scroll prefetch:** Visible range updates are debounced to avoid work spikes during fast scrolls.
*   **Bounded bitmap cache:** A capped in-memory cache prevents unbounded memory growth while keeping hot icons ready.
*   **List recycling hints:** Stable keys and content types improve reuse; spacing uses `LazyColumn` arrangement instead of extra items.
*   **Memoized filtering and sorting:** Expensive list transforms are derived and reused across recompositions.
*   **Render containment:** List items clip to bounds to reduce overdraw and isolate drawing.

---

## 📊 Benchmarks & Regression Tests
*   **Benchmark harness:** [AppListPerformanceBenchmark.kt](file:///c:/Reminder/Decluttr/app/src/androidTest/java/com/example/decluttr/perf/AppListPerformanceBenchmark.kt) measures filter/sort cost for large lists.
*   **Run command:** `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.decluttr.perf.AppListPerformanceBenchmark`
*   **Before/after comparison:** Capture the benchmark output on the same device and compare median/95th percentile times to verify regressions.

---

## 🎯 Success Criteria
*   **Frame time stability:** 95th percentile frame time under 16.6ms on mid-tier devices.
*   **Scroll jank elimination:** Janky frames under 1% during sustained fast scrolls.
*   **Memory usage control:** Icon cache stays bounded with no continuous heap growth during long scroll sessions.
*   **Recomposition control:** List items recompose only for visible items or data changes.
