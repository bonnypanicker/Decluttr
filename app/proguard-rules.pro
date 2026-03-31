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

# Serialized app models
-keep class com.tool.decluttr.domain.model.** { *; }
-keep class com.tool.decluttr.data.local.entity.** { *; }
