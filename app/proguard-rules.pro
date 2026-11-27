# JARVIS Android R8 / ProGuard Rules

# Preserve line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room Database entities & DAOs (supplementing Room AAR consumer rules)
-keep class com.jarvis.assistant.data.local.** { *; }

# Keep domain models & JSON-mapped data classes clean
-keep class com.jarvis.assistant.domain.model.** { *; }

# Biometric & Security components
-keep class androidx.biometric.** { *; }

# Android entrypoints & services / workers instantiated via reflection
-keep class com.jarvis.assistant.JarvisApplication { *; }
-keep class com.jarvis.assistant.MainActivity { *; }
-keep class com.jarvis.assistant.core.productivity.ReminderWorker { *; }
-keep class com.jarvis.assistant.core.voice.VoiceMonitorService { *; }
-keep class com.jarvis.assistant.core.device.JarvisNotificationListenerService { *; }

# Google ML Kit (OCR, Barcode, Image Labeling) & Play Services Tasks under R8 full mode
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**

# Suppress harmless compile-time/optional annotation warnings in third-party AARs
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.jetbrains.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.StringConcatFactory

# Strip verbose debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
