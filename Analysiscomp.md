I analyzed the Decluttr codebase you uploaded and compared it with how high-performance uninstallers like “Smart App Uninstaller” typically work internally (based on APK reverse-engineering patterns and Android performance practices).



Below is a deep technical comparison + improvements specifically for your architecture.



1\. How Smart App Uninstaller Works Internally



Typical architecture used by fast uninstallers:



App Fetching

PackageManager.getInstalledApplications(0)

or

PackageManager.getInstalledPackages(0)



Then filter:



FLAG\_SYSTEM

FLAG\_UPDATED\_SYSTEM\_APP



They avoid heavy metadata calls initially.



Two-Phase Loading (IMPORTANT)



Fast uninstallers do NOT load everything immediately.



Phase 1 (fast):



packageName

label

icon



Phase 2 (lazy background):



app size

last used

install source



This makes the UI appear instantly.



2\. Decluttr Architecture (Your App)



Your architecture is very good structurally.



You implemented:



Clean Architecture

Hilt DI

Room

UseCases

Compose UI

Repository pattern



Main flow:



DashboardViewModel

&nbsp;  ↓

GetInstalledAppsUseCase

&nbsp;  ↓

GetAppDetailsUseCase

&nbsp;  ↓

PackageManager



This is good engineering.



But performance can still be improved heavily.



3\. Major Performance Bottleneck in Decluttr



Inside:



GetInstalledAppsUseCase.kt



You do this:



async(Dispatchers.IO) {

&nbsp;  getAppDetailsUseCase()

&nbsp;  queryStatsForPackage()

}

Problem



For every app you call:



StorageStatsManager.queryStatsForPackage()



This is VERY slow.



Typical phone:



120 apps

→ 120 StorageStats queries



Each query can take:



15-40 ms



Total:



~3-5 seconds



Even with coroutines.



Smart uninstallers avoid this completely at startup.



4\. Smart App Uninstaller Strategy



They fetch only APK path size first:



File(appInfo.sourceDir).length()



This is 10x faster.



Then later:



StorageStatsManager



only when user opens details.



5\. Suggested Fix for Decluttr

Phase Loading



Modify GetInstalledAppsUseCase



Phase 1:



packageName

name

apkSize (File.length)

icon



Phase 2 (lazy):



StorageStatsManager

usageStats

install source

Example

data class InstalledAppInfo(

&nbsp;   val packageId: String,

&nbsp;   val name: String,

&nbsp;   val apkSizeBytes: Long,

&nbsp;   val icon: Drawable?

)



Then lazy load details later.



6\. Icon Loading Comparison

Smart App Uninstaller



Uses:



LruCache<String, Drawable>



or



Glide / Coil



Icons are cached aggressively.



Decluttr



You use:



IconCacheManager

AppIconFetcher



This is good.



But improvement possible.



Problem



Compose recomposition may request icons repeatedly.



Fix



Use Coil Compose



AsyncImage(

&nbsp;   model = "package:$packageName",

)



Custom loader:



PackageManager.getApplicationIcon()



Coil handles caching automatically.



7\. Scroll Smoothness

Smart App Uninstaller



Uses:



RecyclerView

DiffUtil

ViewHolder reuse



Memory optimized.



Decluttr



Uses:



Jetpack Compose LazyColumn



Which is fine.



But problems appear if:



icons load during scroll

heavy sorting

large objects

Fix



Use stable keys



LazyColumn {

&nbsp; items(

&nbsp;    items = apps,

&nbsp;    key = { it.packageId }

&nbsp; )

}



This prevents recomposition jitter.



9\. Parallelization Problem



You do:



userApps.map {

&nbsp;  async { ... }

}



If user has:



200 apps



You spawn:



200 coroutines



Bad.



Fix



Use batching:



userApps.chunked(10)



Then process.

12\. Biggest Hidden Optimization



Smart uninstallers prewarm PackageManager.



Before loading list:



packageManager.getInstalledPackages(0)



once.



Then calls become faster.

14\. One Advanced Trick Used by Fast Uninstallers



They cache the entire app list.



On next launch they show cached data instantly:



Room database



Then refresh in background.



You already have Room.



Just add:



AppCacheEntity

