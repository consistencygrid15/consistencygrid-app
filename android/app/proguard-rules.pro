# Add project specific ProGuard rules here.

# ─────────────────────────────────────────────────────────────────────────────
# ACTIVITIES — Must NOT be renamed/removed by R8.
# Android resolves Activity class names from the Manifest at runtime.
# If R8 renames an Activity, the Manifest still holds the old name → CRASH.
# ─────────────────────────────────────────────────────────────────────────────
-keep public class * extends android.app.Activity
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends androidx.fragment.app.FragmentActivity

# Explicitly keep all app Activities (belt-and-suspenders)
-keep class com.consistencygridwallpaper.MainActivity { *; }
-keep class com.consistencygridwallpaper.ui.compose.NativeAppActivity { *; }
-keep class com.consistencygridwallpaper.auth.AuthActivity { *; }
-keep class com.consistencygridwallpaper.auth.EmailAuthActivity { *; }

# ─────────────────────────────────────────────────────────────────────────────
# SERVICES — BroadcastReceivers and Services resolved by name from Manifest
# ─────────────────────────────────────────────────────────────────────────────
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.appwidget.AppWidgetProvider

# ─────────────────────────────────────────────────────────────────────────────
# JAVASCRIPT BRIDGE — WebView JavascriptInterface methods
# ─────────────────────────────────────────────────────────────────────────────
-keepclassmembers class com.consistencygridwallpaper.bridge.WebInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# ─────────────────────────────────────────────────────────────────────────────
# BACKGROUND WORKERS — WorkManager instantiates via reflection
# ─────────────────────────────────────────────────────────────────────────────
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ─────────────────────────────────────────────────────────────────────────────
# ROOM DATABASE — Annotation processor reflection
# ─────────────────────────────────────────────────────────────────────────────
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# ─────────────────────────────────────────────────────────────────────────────
# NETWORKING & SERIALIZATION (Retrofit, Gson, OkHttp)
# ─────────────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep interface com.consistencygridwallpaper.network.** { *; }
-keep class com.consistencygridwallpaper.network.** { *; }
-keep class com.consistencygridwallpaper.storage.room.** { *; }

# ─────────────────────────────────────────────────────────────────────────────
# GOOGLE PLAY BILLING — Must NOT be obfuscated
# BillingClient uses reflection internally for purchase callbacks.
# Renaming these classes causes silent billing failures on release builds.
# ─────────────────────────────────────────────────────────────────────────────
-keep class com.android.billingclient.** { *; }
-keep interface com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**
