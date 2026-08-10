# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.djouhri.cockpit.data.model.**$$serializer { *; }
-keepclassmembers class de.djouhri.cockpit.data.model.** {
    *** Companion;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# ML Kit
-keep class com.google.mlkit.** { *; }

# BouncyCastle (CSR-Bau)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
