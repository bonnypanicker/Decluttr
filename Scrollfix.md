
1|# Claude Haiku 4.5 Code Fix Brief — Decluttr Browse All Apps Smooth Scrolling
2|
3|Use this document as the implementation brief for **Claude Haiku 4.5**.
4|
5|## Goal
6|
7|Guarantee smooth scrolling for the **Browse All Apps** screen in Decluttr by making sure the long list is shown **only after**:
8|
9|1. app metadata is ready
10|2. icons are prewarmed with the **same cache identity** used by the list rows
11|3. icon caching covers **all drawable types**, not just `BitmapDrawable`
12|
13|Do **not** redesign the app. Keep the current UX and architecture style intact.
14|
15|---
16|
17|## Root cause summary
18|
19|Current problems in the repo:
20|
21|1. **Browse All Apps has no dedicated tap-to-open loading gate**
22|   - pressing the button only changes `viewState`
23|   - it does not force a preparation phase before showing the long list
24|
25|2. **Icon preload and row rendering do not share a guaranteed stable cache key**
26|   - preload uses explicit `memoryCacheKey(packageId)`
27|   - `AsyncImage` rows currently do not
28|   - there is no custom `Keyer<AppIconModel>` registered
29|
30|3. **Custom icon cache misses many icons**
31|   - `AppIconFetcher` only stores bytes when the drawable is a `BitmapDrawable`
32|   - adaptive/vector/non-bitmap icons can still be fetched live while scrolling
33|
34|4. **Crossfade is enabled for list icons**
35|   - unnecessary work for a dense scrolling list
36|
37|5. **`IconCacheManager` is unbounded**
38|   - can increase memory pressure and GC churn
39|
40|---
41|
42|## Files to change
43|
44|1. `app/src/main/java/com/example/decluttr/domain/usecase/IconCacheManager.kt`
45|2. `app/src/main/java/com/example/decluttr/presentation/util/AppIconFetcher.kt`
46|3. `app/src/main/java/com/example/decluttr/DecluttrApp.kt`
47|4. `app/src/main/java/com/example/decluttr/presentation/screens/dashboard/DashboardViewModel.kt`
48|5. `app/src/main/java/com/example/decluttr/presentation/screens/dashboard/DashboardScreen.kt`
49|6. `app/src/main/java/com/example/decluttr/presentation/screens/dashboard/DiscoveryScreen.kt`
50|
51|Optional new file:
52|
53|7. `app/src/main/java/com/example/decluttr/presentation/util/AppIconKeyer.kt`
54|
55|---
56|
57|## Required implementation
58|
59|### 1) Add a dedicated preparation/loading flow for Browse All Apps
60|
61|When the user taps **Browse All Apps**:
62|
63|- do **not** immediately enter `DiscoveryViewState.ALL_APPS`
64|- trigger a ViewModel method that prepares the full list for display
65|- show a fullscreen loader during preparation
66|- only switch to `ALL_APPS` when preparation is complete
67|
68|### Expected behavior
69|
70|- first tap on **Browse All Apps** => loader appears
71|- app list opens only after metadata + icon warmup complete
72|- scroll should be smooth on first open, not just second open
73|
74|### Suggested ViewModel API
75|
76|Add state:
77|
78|```kotlin
79|private val _isPreparingAllApps = MutableStateFlow(false)
80|val isPreparingAllApps = _isPreparingAllApps.asStateFlow()
81|```
82|
83|Add method:
84|
85|```kotlin
86|fun prepareAllAppsForDisplay() {
87|    if (_isPreparingAllApps.value) return
88|    viewModelScope.launch {
89|        _isPreparingAllApps.value = true
90|
91|        val result = if (allInstalledApps.value.isEmpty()) {
92|            getUnusedAppsUseCase.fetchAll()
93|        } else {
94|            GetUnusedAppsUseCase.UnusedAppsResult(
95|                allApps = allInstalledApps.value,
96|                unusedApps = unusedApps.value
97|            )
98|        }
99|
100|        _unusedApps.value = result.unusedApps
101|        _allInstalledApps.value = result.allApps
102|
103|        warmIcons(result.allApps)
104|
105|        _isPreparingAllApps.value = false
106|    }
107|}
108|```
109|
110|Move icon warmup into a reusable helper:
111|
112|```kotlin
113|private suspend fun warmIcons(apps: List<GetInstalledAppsUseCase.InstalledAppInfo>) {
114|    withContext(Dispatchers.IO) {
115|        val imageLoader = context.imageLoader
116|        apps.forEach { app ->
117|            val request = ImageRequest.Builder(context)
118|                .data(AppIconModel(app.packageId))
119|                .memoryCacheKey(app.packageId)
120|                .crossfade(false)
121|                .build()
122|            imageLoader.execute(request)
123|        }
124|    }
125|}
126|```
127|
128|Also call this same helper from `loadDiscoveryData()` so both paths stay identical.
129|
130|---
131|
132|### 2) Add a stable Coil key for `AppIconModel`
133|
134|Create a `Keyer<AppIconModel>` so preload requests and row requests resolve to the same memory cache identity.
135|
136|Suggested file:
137|
138|```kotlin
139|package com.example.decluttr.presentation.util
140|
141|import coil.key.Keyer
142|import coil.request.Options
143|
144|class AppIconKeyer : Keyer<AppIconModel> {
145|    override fun key(data: AppIconModel, options: Options): String {
146|        return data.packageName
147|    }
148|}
149|```
150|
151|Register it in `DecluttrApp.kt`.
152|
153|---
154|
155|### 3) Make row image requests use the exact same cache identity as preload
156|
157|Do not rely on a plain model object alone in `AsyncImage`.
158|
159|Replace this pattern:
160|
161|```kotlin
162|AsyncImage(
163|    model = AppIconModel(app.packageId),
164|    ...
165|)
166|```
167|
168|With this pattern:
169|
170|```kotlin
171|val context = LocalContext.current
172|
173|AsyncImage(
174|    model = ImageRequest.Builder(context)
175|        .data(AppIconModel(app.packageId))
176|        .memoryCacheKey(app.packageId)
177|        .crossfade(false)
178|        .build(),
179|    contentDescription = "App Icon",
180|    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
181|)
182|```
183|
184|This must match the warmup path.
185|
186|---
187|
188|### 4) Cache all drawable types, not just `BitmapDrawable`
189|
190|In `AppIconFetcher.kt`, convert any drawable into a bitmap before storing bytes.
191|
192|Do not keep this logic limited to:
193|
194|```kotlin
195|if (icon is BitmapDrawable) { ... }
196|```
197|
198|Add a helper like:
199|
200|```kotlin
201|private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
202|    if (drawable is BitmapDrawable && drawable.bitmap != null) {
203|        return Bitmap.createScaledBitmap(drawable.bitmap, width, height, true)
204|    }
205|
206|    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
207|    val canvas = Canvas(bitmap)
208|    drawable.setBounds(0, 0, canvas.width, canvas.height)
209|    drawable.draw(canvas)
210|    return bitmap
211|}
212|```
213|
214|Recommended cache size target:
215|
216|- use a small fixed bitmap size suitable for a 48dp row icon
217|- `96x96` or `128x128` pixels is acceptable
218|
219|Store bytes for all icons after normalization.
220|
221|---
222|
223|### 5) Replace unbounded cache with bounded `LruCache`
224|
225|Update `IconCacheManager.kt`.
226|
227|Replace the mutable map with a bounded cache.
228|
229|Suggested implementation:
230|
231|```kotlin
232|@Singleton
233|class IconCacheManager @Inject constructor() {
234|    private val cache = object : LruCache<String, ByteArray>(12 * 1024 * 1024) {
235|        override fun sizeOf(key: String, value: ByteArray): Int = value.size
236|    }
237|
238|    fun get(packageId: String): ByteArray? = cache.get(packageId)
239|
240|    fun put(packageId: String, iconBytes: ByteArray?) {
241|        if (iconBytes != null) cache.put(packageId, iconBytes)
242|    }
243|
244|    fun has(packageId: String): Boolean = cache.get(packageId) != null
245|
246|    fun clear() = cache.evictAll()
247|}
248|```
249|
250|Adjust the max size if needed, but keep it bounded.
251|
252|---
253|
254|### 6) Disable crossfade for app icons
255|
256|In `DecluttrApp.kt`:
257|
258|- either remove global `crossfade(true)`
259|- or keep global behavior and explicitly disable crossfade in app-list requests
260|
261|Preferred option for this repo: **disable crossfade for app-list icon requests**.
262|
263|Still register the Keyer in the ImageLoader components:
264|
265|```kotlin
266|override fun newImageLoader(): ImageLoader {
267|    return ImageLoader.Builder(this)
268|        .components {
269|            add(AppIconFetcher.Factory(this@DecluttrApp, iconCacheManager))
270|            add(AppIconKeyer())
271|        }
272|        .memoryCache {
273|            coil.memory.MemoryCache.Builder(this)
274|                .maxSizePercent(0.25)
275|                .build()
276|        }
277|        .build()
278|}
279|```
280|
281|---
282|
283|### 7) Update `DiscoveryScreen` to open All Apps only after preparation completes
284|
285|Keep the current local `viewState`, but add a pending-open flow.
286|
287|Suggested local state:
288|
289|```kotlin
290|var pendingAllAppsOpen by remember { mutableStateOf(false) }
291|```
292|
293|When the user taps the button:
294|
295|```kotlin
296|pendingAllAppsOpen = true
297|onPrepareAllApps()
298|```
299|
300|Then use:
301|
302|```kotlin
303|LaunchedEffect(pendingAllAppsOpen, isPreparingAllApps, allApps) {
304|    if (pendingAllAppsOpen && !isPreparingAllApps && allApps.isNotEmpty()) {
305|        viewState = DiscoveryViewState.ALL_APPS
306|        pendingAllAppsOpen = false
307|    }
308|}
309|```
310|
311|Show loading UI when either of these is true:
312|
313|```kotlin
314|if (isLoading || isPreparingAllApps) {
315|    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
316|        CircularProgressIndicator()
317|    }
318|    return
319|}
320|```
321|
322|Update function parameters accordingly through `DashboardScreen`.
323|
324|---
325|
326|## Important constraints
327|
328|- Keep existing archive/uninstall flows working.
329|- Do not add new libraries unless absolutely necessary.
330|- Do not remove `LazyColumn`, stable keys, or current search/sort behavior.
331|- Do not redesign UI.
332|- Do not move business logic out of ViewModel into Composables.
333|- Keep Hilt/Compose structure consistent with the current project.
334|
335|---
336|
337|## Acceptance criteria
338|
339|The fix is complete only if all of the below are true:
340|
341|1. Tapping **Browse All Apps** shows a loading state before the long list appears.
342|2. The list opens only after icon warmup completes.
343|3. `AsyncImage` rows use the same cache identity as preload.
344|4. Adaptive/vector icons are cached too.
345|5. No icon crossfade is happening in the long list.
346|6. Repeated fling scrolling is visibly smoother on first open.
347|7. No regressions in Rarely Used / Large Apps / Archive flows.
348|
349|---
350|
351|## Suggested manual test flow
352|
353|1. Launch app.
354|2. Wait for dashboard.
355|3. Tap **Browse All Apps**.
356|4. Confirm loader appears immediately.
357|5. After list opens, fling-scroll top-to-bottom multiple times.
358|6. Confirm icons do not pop in late during scroll.
359|7. Repeat after app relaunch.
360|
361|---
362|
363|## Final instruction to Claude Haiku 4.5
364|
365|Implement the above changes directly in the repo.
366|
367|Priority order:
368|
369|1. dedicated Browse All Apps preparation flow
370|2. shared cache key path (`Keyer` + matching `memoryCacheKey`)
371|3. all-drawable icon caching
372|4. bounded icon cache
373|5. crossfade removal for app-list icons
374|
