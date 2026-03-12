# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\ContainerAdministrator\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# Compose StateList lock verification fix
# See: https://issuetracker.google.com/issues/330514866
-keep class androidx.compose.runtime.snapshots.SnapshotStateList { *; }
-keep class androidx.compose.runtime.snapshots.SnapshotStateMap { *; }

# Keep Hilt entry points
-keep class com.example.decluttr.DecluttrApp
-keep class com.example.decluttr.presentation.screens.dashboard.DashboardViewModel
