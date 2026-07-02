# ---------------------------------------------------------------------------
# Un-Distract Me â€” R8 / ProGuard rules
# 100% offline app: no reflection-based networking/serialization SDKs.
# ---------------------------------------------------------------------------

# Keep generic signatures and annotations required by Room, Hilt and Kotlin.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# ---------------------------------------------------------------------------
# Hilt / Dagger
# ---------------------------------------------------------------------------
-dontwarn dagger.hilt.**
-dontwarn dagger.internal.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
# Keep members annotated for injection.
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
-dontwarn androidx.room.**
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }

# ---------------------------------------------------------------------------
# App entities / DAOs / domain models referenced by generated Room + Hilt code.
# ---------------------------------------------------------------------------
-keep class com.undistractme.data.local.** { *; }
-keep class com.undistractme.domain.model.** { *; }

# ---------------------------------------------------------------------------
# Kotlin coroutines
# ---------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---------------------------------------------------------------------------
# Compose keeps its own consumer rules; nothing extra required here.
# ---------------------------------------------------------------------------
