# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add project specific ProGuard rules here.

# Keep JavaScript Interface methods from being obfuscated
# Crucial for the WebView bridge to function in release builds
-keepclassmembers class com.consistencygridwallpaper.bridge.WebInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Background Workers
# WorkManager needs to instantiate these via reflection
-keep class com.consistencygridwallpaper.workers.** { *; }

# Keep data classes/models if used in JSON serialization (future proofing)
-keep class com.consistencygridwallpaper.storage.** { *; }

# OkHttp rules (usually included automatically, but good to be safe)
-dontwarn okhttp3.**
-dontwarn okio.**
