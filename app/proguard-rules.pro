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

# Suppress harmless compile-time annotation warnings in third-party AARs
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.jetbrains.annotations.**
-dontwarn java.lang.invoke.StringConcatFactory

# Strip verbose debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
