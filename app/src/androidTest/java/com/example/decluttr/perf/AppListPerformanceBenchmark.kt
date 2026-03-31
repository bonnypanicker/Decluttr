package com.tool.decluttr.perf

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tool.decluttr.domain.usecase.GetInstalledAppsUseCase
import com.tool.decluttr.presentation.screens.dashboard.SortOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppListPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val apps = List(1500) { index ->
        GetInstalledAppsUseCase.InstalledAppInfo(
            packageId = "com.example.app$index",
            name = if (index % 2 == 0) "App $index" else "Tool $index",
            apkSizeBytes = index * 1_024L,
            isOsArchived = false,
            isPlayStoreInstalled = true,
            lastTimeUsed = index.toLong()
        )
    }

    @Test
    fun benchmarkFilterAndSortByName() {
        benchmarkRule.measureRepeated {
            filterAndSortApps(apps, "App", SortOption.NAME)
        }
    }

    @Test
    fun benchmarkFilterAndSortBySize() {
        benchmarkRule.measureRepeated {
            filterAndSortApps(apps, "", SortOption.SIZE)
        }
    }

    private fun filterAndSortApps(
        apps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
        query: String,
        sortOption: SortOption
    ): List<GetInstalledAppsUseCase.InstalledAppInfo> {
        val filteredApps = if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.name.contains(query, ignoreCase = true) }
        }

        return when (sortOption) {
            SortOption.NAME -> filteredApps.sortedBy { it.name.lowercase() }
            SortOption.SIZE -> filteredApps.sortedByDescending { it.apkSizeBytes }
            SortOption.LAST_USED -> filteredApps.sortedBy { it.lastTimeUsed }
        }
    }
}
