# Avoid ProGuard bytecode optimization bugs (e.g., Okio VerifyError) while retaining shrinking
-dontoptimize

# Keep app entry point
-keep class now.link.periphyllidae.MainKt {
    public static void main(java.lang.String[]);
}

# Keep JNA classes, native library interfaces, callbacks, and structs
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    <fields>;
    <methods>;
}
-dontwarn com.sun.jna.**

# Keep serializable models and class names used in polymorphic serialization (HistoryItemDataSerializer)
-keep class now.link.periphyllidae.models.** { *; }
-keep class now.link.periphyllidae.components.**$Config** { *; }
-keep class now.link.periphyllidae.mvi.HistoryItem* { *; }
-keep class now.link.periphyllidae.cache.CacheMetadata { *; }
-keep class now.link.periphyllidae.repository.SubsonicJsonContainer { *; }
-keep class now.link.periphyllidae.repository.SubsonicJsonResponse { *; }
-keep @kotlinx.serialization.Serializable class now.link.periphyllidae.** { *; }
-keep @kotlinx.serialization.Serializable class com.arkivanov.** { *; }

# Keep ServiceLoader providers used at runtime
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }
-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider { *; }
-keep class * implements io.ktor.server.config.ConfigLoader { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * implements com.arkivanov.decompose.mainthread.MainThreadChecker { *; }
-keep class io.ktor.client.engine.java.** { *; }

# Keep Okio classes intact
-keep class okio.** { *; }

# Keep Ktor / SLF4J / Okio / Coil / Koin reflection & optional platform references
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn okio.**
-dontwarn coil3.**
-dontwarn com.russhwolf.settings.**
-dontwarn org.koin.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
