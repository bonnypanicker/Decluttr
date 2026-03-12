package com.example.decluttr.perf

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.decluttr.domain.usecase.GetInstalledAppsUseCase
import com.example.decluttr.presentation.screens.dashboard.SortOption
import com.example.decluttr.presentation.screens.dashboard.filterAndSortApps
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
            iconBytes = null,
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
}
