# cero-X ProGuard Rules
# Estabilización para release builds

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Jetpack Compose
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.foundation.** { *; }

# Hilt (Dependency Injection)
-keep class com.google.dagger.hilt.** { *; }
-keep class * extends dagger.hilt.AndroidEntryPoint
-keep class * extends dagger.hilt.HiltAndroidApp
-keep class * extends dagger.hilt.internal_GeneratedComponentManager
-keep class * extends dagger.hilt.internal_GeneratedComponent
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**

# Room Database
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.db.** { *; }
-keep class androidx.sqlite.framework.** { *; }
-dontwarn androidx.room.**
-dontwarn androidx.sqlite.**

# ONNX Runtime
-keep class com.microsoft.onnxruntime.** { *; }
-keep interface com.microsoft.onnxruntime.** { *; }
-dontwarn com.microsoft.onnxruntime.**
-keep class org.onnxruntime.** { *; }
-dontwarn org.onnxruntime.**

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keepattributes *Annotation*, EnclosingMethod, Signature

# Kotlin
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# kotlin.collections
-keep class kotlin.collections.** { *; }

# cero-X app classes
-keep class com.ceroX.app.** { *; }
-keep interface com.ceroX.app.** { *; }

# Keep native libraries
-keepnames class * extends android.app.Activity
-keepnames class * extends android.app.Application
-keepnames class * extends android.app.Service
-keepnames class * extends android.content.BroadcastReceiver
-keepnames class * extends android.content.ContentProvider

# Keep names for native methods
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# R8 optimization warnings (safe to ignore)
-dontnote android.**
-dontnote androidx.**
-dontnote com.google.**
-dontnote com.microsoft.**
-dontnote kotlin.**

# WebView / JavaScript interface (if needed)
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
}

# Keep annotations
-keep @interface androidx.annotation.Keep
-keep @interface com.google.dagger.hilt.InstallIn
-keep @interface javax.inject.Inject
-keep @interface javax.inject.Singleton