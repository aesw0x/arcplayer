# Keep LibVLC JNI classes and native callbacks in R8 release builds
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**

# The ArcCloud and ArcFrameRate traces exist for `adb logcat` on a debug build. R8 drops those calls
# from a release binary entirely; warnings and errors stay, because they report real failures.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
