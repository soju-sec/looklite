# LookLite ProGuard Rules
# Keep JavascriptInterface for Android WebView bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class app.looklite.android.MainActivity$LookLiteNativeInterface { *; }
