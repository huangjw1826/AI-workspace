# Add project specific ProGuard rules here.

# Keep Hilt classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}

# Keep Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep KotlinX Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.airecorder.android.**$$serializer { *; }
-keepclassmembers class com.airecorder.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.airecorder.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes
-keep class com.airecorder.android.data.model.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
