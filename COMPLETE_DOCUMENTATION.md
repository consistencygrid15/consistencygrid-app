# ConsistencyGrid Wallpaper - Complete Technical Documentation

## 📋 Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [File Structure](#file-structure)
4. [Core Components](#core-components)
5. [Data Flow](#data-flow)
6. [Background Processing](#background-processing)
7. [Security & Storage](#security--storage)
8. [Build Configuration](#build-configuration)
9. [How Everything Works Together](#how-everything-works-together)

---

## 🎯 Overview

### What is This App?
ConsistencyGrid Wallpaper is a **native Android application** that wraps the ConsistencyGrid web application in a WebView while providing powerful native Android integrations.

### Key Features:
- 🌐 **WebView Wrapper** - Loads the ConsistencyGrid website
- 🖼️ **Native Wallpaper Setting** - Set wallpapers on Home, Lock, or Both screens
- ⏰ **Automatic Midnight Updates** - Wallpaper updates daily at 12:00 AM
- 💾 **Session Persistence** - Login stays active across app restarts
- 📥 **File Downloads** - Download images and CSV exports
- 🎨 **Theme Synchronization** - Native UI matches web app theme
- 🔄 **Boot Persistence** - Alarms restored after device reboot

### Technology Stack:
- **Language**: Kotlin
- **Min SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)
- **Build System**: Gradle 8.0.2
- **Key Libraries**:
  - AndroidX Core & AppCompat
  - WorkManager (background tasks)
  - OkHttp (networking)
  - WebView (web content)

---

## 🏗️ Architecture

### High-Level Architecture Diagram:

```
┌─────────────────────────────────────────────────────────────┐
│                     USER INTERFACE                          │
│  ┌────────────────────────────────────────────────────┐    │
│  │         MainActivity (WebView Container)            │    │
│  │  - Loads https://consistencygrid.netlify.app       │    │
│  │  - Handles navigation, cookies, downloads          │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                  JAVASCRIPT BRIDGE                          │
│  ┌────────────────────────────────────────────────────┐    │
│  │              WebInterface (Bridge)                  │    │
│  │  - saveWallpaper()      - saveToken()              │    │
│  │  - setWallpaperTarget() - setAutoUpdateEnabled()   │    │
│  │  - updateAppTheme()     - downloadFile()           │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                 BACKGROUND WORKERS                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ WorkScheduler│→ │MidnightReceiver│→│WallpaperWorker│    │
│  │ (Schedules)  │  │ (Triggers)    │  │ (Executes)    │    │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│         ↑                                                    │
│  ┌──────────────┐                                          │
│  │ BootReceiver │ (Restores alarms after reboot)          │
│  └──────────────┘                                          │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                  STORAGE & UTILITIES                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  UserPrefs   │  │ NetworkUtils │  │NotificationHelper│  │
│  │(SharedPrefs) │  │(Connectivity)│  │  (Channels)   │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

---

## 📁 File Structure

```
ConsistencyGridWallpaper/
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/consistencygridwallpaper/
│   │   │   │   ├── MainActivity.kt              # Main WebView activity
│   │   │   │   ├── MainApplication.kt           # Application entry point
│   │   │   │   ├── bridge/
│   │   │   │   │   └── WebInterface.kt          # JavaScript bridge
│   │   │   │   ├── workers/
│   │   │   │   │   ├── WallpaperWorker.kt       # Background wallpaper updater
│   │   │   │   │   ├── WorkScheduler.kt         # Alarm scheduler
│   │   │   │   │   ├── MidnightReceiver.kt      # Midnight alarm receiver
│   │   │   │   │   └── BootReceiver.kt          # Boot event receiver
│   │   │   │   ├── storage/
│   │   │   │   │   └── UserPrefs.kt             # Preferences manager
│   │   │   │   └── utils/
│   │   │   │       ├── NetworkUtils.kt          # Network checks
│   │   │   │       └── NotificationHelper.kt    # Notification channels
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   └── activity_main.xml        # Main layout
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml              # App strings
│   │   │   │   │   └── styles.xml               # App theme
│   │   │   │   └── drawable/
│   │   │   │       └── app_logo.png             # App icon
│   │   │   └── AndroidManifest.xml              # App manifest
│   │   ├── build.gradle                         # App build config
│   │   └── proguard-rules.pro                   # ProGuard rules
│   ├── build.gradle                             # Project build config
│   ├── settings.gradle                          # Project settings
│   └── gradle.properties                        # Gradle properties
├── build_and_run.bat                            # Build script (Windows)
├── build_and_run.ps1                            # Build script (PowerShell)
├── run_on_device.bat                            # Quick run script
├── check_environment.bat                        # Environment checker
├── fix_java_home.bat                            # Java setup fixer
├── README.md                                    # Project overview
├── REFINEMENTS.md                               # Recent improvements
└── EXACT_TIMING_GUIDE.md                        # Timing setup guide
```

---

## 🔧 Core Components

### 1. MainActivity.kt
**Purpose**: Main entry point and WebView container

**Responsibilities**:
- Loads the ConsistencyGrid website
- Manages WebView lifecycle and configuration
- Handles cookie persistence for login sessions
- Processes file downloads
- Applies theme to native UI elements
- Handles back button navigation

**Key Methods**:
```kotlin
onCreate()              // Initializes WebView, restores state, applies theme
setupWebView()          // Configures WebView settings and bridge
loadWebsite()           // Loads URL with screen dimensions
applyTheme()            // Updates status bar, nav bar colors
showVerificationDialog()// Shows testing instructions
```

**Important Settings**:
- Hardware acceleration enabled
- JavaScript enabled (required for web app)
- DOM storage enabled (for web app state)
- Cookies accepted and persisted to disk
- Custom user agent for consistency

---

### 2. WebInterface.kt
**Purpose**: JavaScript bridge between web app and native Android

**Production Stability Features**:
- **Background Threading**: All wallpaper processing (`saveWallpaper`, `saveWallpaperUrl`) runs on background threads to prevent UI freezes (ANRs) and Main Thread crashes.
- **Memory Management**: Large bitmaps are decoded with explicit cleanup (`recycle()`) and memory checks to prevent OutOfMemory errors on low-end devices.
- **Session Persistence**: `saveToken()` forces an immediate `CookieManager.flush()` to ensure login state is saved before any potential app kill or navigation.

**How It Works**:
The web app can call native Android functions using:
```javascript
window.Android.saveWallpaper(base64Image)
window.Android.setAutoUpdateEnabled(true)
```

**Available Methods**:

| Method | Purpose | Parameters |
|--------|---------|------------|
| `saveWallpaper()` | Set wallpaper (Background Thread) | base64Image: String |
| `saveWallpaperUrl()` | Set from URL (Background Thread) | url: String |
| `saveToken()` | Save auth & Flash Cookies | token: String |
| `setWallpaperTarget()` | Set target screen | target: "HOME"/"LOCK"/"BOTH" |
| `getWallpaperTarget()` | Get current target | - |
| `isAutoUpdateEnabled()` | Check auto-update status | - |
| `setAutoUpdateEnabled()` | Enable/disable auto-update | enabled: Boolean |
| `clearToken()` | Logout and clear data | - |
| `updateAppTheme()` | Update native UI theme | colorHex: String, isDark: Boolean |
| `showToast()` | Show toast message | message: String |
| `downloadFile()` | Download file | base64Data, fileName, mimeType |

**Security**:
- All methods annotated with `@JavascriptInterface`
- Input validation on all parameters
- ProGuard rules prevent obfuscation

---

### 3. WallpaperWorker.kt
**Purpose**: Background worker that renders and applies wallpapers

**How It Works**:
1. **Triggered by**: MidnightReceiver at 12:00 AM
2. **Loads**: Headless WebView with wallpaper renderer
3. **URL**: `https://consistencygrid.netlify.app/wallpaper-renderer?token=XXX&canvasWidth=1080&canvasHeight=2400`
4. **Waits**: For JavaScript to call `Android.saveWallpaper(base64Image)`
5. **Applies**: Wallpaper to Home/Lock/Both based on user preference
6. **Timeout**: 60 seconds max

**Process Flow**:
```
MidnightReceiver triggers
    ↓
WallpaperWorker starts
    ↓
Creates headless WebView
    ↓
Loads wallpaper-renderer page
    ↓
Web app renders wallpaper
    ↓
Calls Android.saveWallpaper()
    ↓
Worker decodes base64
    ↓
Applies to WallpaperManager
    ↓
Worker completes
```

**Error Handling**:
- Network errors → Retry
- Timeout → Retry
- Invalid token → Failure (no retry)
- Decode errors → Retry

---

### 4. WorkScheduler.kt
**Purpose**: Manages alarm scheduling for daily updates

**How It Works**:
1. Calculates next midnight (12:00 AM)
2. Creates PendingIntent for MidnightReceiver
3. Schedules alarm with AlarmManager
4. Checks for exact alarm permission (Android 12+)

**Alarm Types Used**:
- **Android 12+**: `setExactAndAllowWhileIdle()` (requires permission)
- **Android 11-**: `setExactAndAllowWhileIdle()` (no permission needed)
- **Fallback**: `setAndAllowWhileIdle()` (inexact, may delay)

**Permission Handling**:
```kotlin
if (alarmManager.canScheduleExactAlarms()) {
    // Use exact alarm - updates at 12:00 AM ±1 minute
} else {
    // Use inexact alarm - may delay 1-6 hours
    // Show dialog to guide user to settings
}
```

**Key Methods**:
```kotlin
scheduleDailyUpdate()              // Schedules next midnight alarm
cancelDailyUpdate()                // Cancels all alarms
calculateNext12AM()                // Calculates next midnight timestamp
showExactAlarmPermissionDialog()   // Guides user to grant permission
```

---

### 5. MidnightReceiver.kt
**Purpose**: Receives midnight alarm and triggers wallpaper update

**Lifecycle**:
```
AlarmManager fires at 12:00 AM
    ↓
MidnightReceiver.onReceive() called
    ↓
Enqueues WallpaperWorker via WorkManager
    ↓
Reschedules next alarm for tomorrow
    ↓
Receiver completes
```

**Why Reschedule?**:
- Alarms are **one-shot** (fire once and disappear)
- Must reschedule for next day to continue cycle

---

### 6. BootReceiver.kt
**Purpose**: Restores alarms after device reboot

**Why Needed?**:
- Android clears all alarms on reboot
- Without this, auto-updates would stop working

**Process**:
```
Device boots
    ↓
BootReceiver.onReceive() called
    ↓
Checks if auto-update is enabled
    ↓
If enabled: Reschedules daily alarm
    ↓
Receiver completes
```

---

### 7. UserPrefs.kt
**Purpose**: Persistent storage for user preferences and session data

**Stored Data**:
| Key | Type | Purpose |
|-----|------|---------|
| `user_token` | String | Authentication token |
| `auto_update` | Boolean | Auto-update enabled? |
| `wallpaper_target` | String | HOME/LOCK/BOTH |
| `theme_color` | String | Hex color (#FF7A00) |
| `is_dark_mode` | Boolean | Dark mode enabled? |
| `base_url` | String | Web app URL |

**Storage Mechanism**:
- Uses `SharedPreferences` (key-value storage)
- Data persists across app restarts
- Cleared on logout

**Future Enhancement**:
- Can be upgraded to `EncryptedSharedPreferences` for token security

---

### 8. NetworkUtils.kt
**Purpose**: Centralized network connectivity checks

**Usage**:
```kotlin
if (NetworkUtils.isNetworkAvailable(context)) {
    // Proceed with network operation
} else {
    // Show offline message
}
```

**Checks**:
- WiFi connection
- Cellular data
- Ethernet connection

---

### 9. NotificationHelper.kt
**Purpose**: Manages notification channels

**Current Use**:
- Creates "Wallpaper Updates" channel
- Prepared for future notifications (success/error alerts)

**Future Enhancements**:
- Show notification on successful update
- Alert user if update fails

---

## 🔄 Data Flow

### 1. User Sets Wallpaper Manually

```
User generates wallpaper in web app
    ↓
Web app calls: window.Android.saveWallpaper(base64Image)
    ↓
WebInterface.saveWallpaper() receives call
    ↓
Decodes base64 to Bitmap
    ↓
Reads wallpaper target from UserPrefs (HOME/LOCK/BOTH)
    ↓
Calls WallpaperManager.setBitmap() with appropriate flags
    ↓
Shows toast: "HOME UPDATED 🏠" / "LOCK UPDATED 🔒" / "BOTH SCREENS UPDATED ✨"
```

### 2. User Enables Auto-Updates

```
User toggles auto-update in web app
    ↓
Web app calls: window.Android.setAutoUpdateEnabled(true)
    ↓
WebInterface.setAutoUpdateEnabled() receives call
    ↓
Saves preference: UserPrefs.setAutoUpdate(true)
    ↓
Calls: WorkScheduler.scheduleDailyUpdate(context)
    ↓
WorkScheduler checks for exact alarm permission
    ↓
If permission granted:
    - Schedules exact alarm for next midnight
    - Shows: "✅ Exact 12:00 AM update scheduled!"
If permission missing:
    - Schedules inexact alarm
    - Shows dialog to guide user to settings
    - Shows: "⚠️ Update scheduled (may not be exact)"
```

### 3. Automatic Midnight Update

```
12:00 AM arrives
    ↓
AlarmManager fires alarm
    ↓
MidnightReceiver.onReceive() triggered
    ↓
Logs: "⏰ Midnight alarm received!"
    ↓
Enqueues WallpaperWorker via WorkManager
    ↓
Reschedules alarm for next midnight
    ↓
MidnightReceiver completes
    ↓
WallpaperWorker.doWork() starts
    ↓
Retrieves token from UserPrefs
    ↓
Creates headless WebView
    ↓
Loads: /wallpaper-renderer?token=XXX&canvasWidth=1080&canvasHeight=2400
    ↓
Web app renders wallpaper
    ↓
Web app calls: Android.saveWallpaper(base64Image)
    ↓
Worker decodes base64
    ↓
Applies wallpaper via WallpaperManager
    ↓
Worker completes successfully
    ↓
Logs: "✅ Background update completed successfully"
```

### 4. Device Reboot

```
Device powers on
    ↓
Android fires BOOT_COMPLETED broadcast
    ↓
BootReceiver.onReceive() triggered
    ↓
Logs: "📱 Device boot completed"
    ↓
Checks: UserPrefs.isAutoUpdateEnabled()
    ↓
If enabled:
    - Calls: WorkScheduler.scheduleDailyUpdate(context)
    - Logs: "✅ Auto-update is enabled. Rescheduling alarm..."
If disabled:
    - Logs: "ℹ️ Auto-update disabled, skipping."
    ↓
BootReceiver completes
```

### 5. User Logs Out

```
User clicks logout in web app
    ↓
Web app calls: window.Android.clearToken()
    ↓
WebInterface.clearToken() receives call
    ↓
Clears all SharedPreferences (UserPrefs.clear())
    ↓
Cancels all alarms (WorkScheduler.cancelDailyUpdate())
    ↓
Cancels all WorkManager tasks
    ↓
Clears all WebView cookies
    ↓
Shows toast: "Logged out successfully. Updates stopped."
```

---

## ⚙️ Background Processing

### WorkManager vs AlarmManager

**Why Both?**:
- **AlarmManager**: Triggers at exact time (12:00 AM)
- **WorkManager**: Executes the actual work (rendering wallpaper)

**Advantages**:
- WorkManager handles retries automatically
- WorkManager respects battery optimization
- AlarmManager ensures exact timing
- Combination provides reliability + precision

### Exact Alarm Permission (Android 12+)

**Why Required?**:
- Android 12+ restricts exact alarms to save battery
- Apps must request `SCHEDULE_EXACT_ALARM` permission

**How We Handle It**:
1. Check permission: `alarmManager.canScheduleExactAlarms()`
2. If granted: Use `setExactAndAllowWhileIdle()`
3. If denied: Show dialog to guide user
4. Fallback: Use `setAndAllowWhileIdle()` (inexact)

**User Experience**:
```
Permission Granted → "✅ Exact 12:00 AM update scheduled!"
Permission Denied  → "⚠️ Update scheduled (may not be exact)"
                   → Dialog: "Enable Exact Timing" with "Open Settings" button
```

---

## 🔒 Security & Storage

### Authentication Token Storage

**Current Implementation**:
- Stored in `SharedPreferences` (unencrypted)
- Cleared on logout
- Not exposed to other apps

**Future Enhancement**:
```kotlin
// Upgrade to EncryptedSharedPreferences
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "encrypted_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

### Cookie Management

**Session Persistence**:
- Cookies saved to disk automatically
- Flushed on: page load, pause, resume, destroy
- Ensures login persists across app restarts

**Implementation**:
```kotlin
val cookieManager = CookieManager.getInstance()
cookieManager.setAcceptCookie(true)
cookieManager.setAcceptThirdPartyCookies(webView, true)

// Flush to disk
CookieManager.getInstance().flush()
```

### ProGuard Rules

**Purpose**: Prevent code obfuscation in release builds

**Critical Rules**:
```proguard
# Keep JavaScript Interface methods
-keepclassmembers class com.consistencygridwallpaper.bridge.WebInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Workers (WorkManager uses reflection)
-keep class com.consistencygridwallpaper.workers.** { *; }

# Keep Storage classes (future JSON serialization)
-keep class com.consistencygridwallpaper.storage.** { *; }
```

**Why Needed?**:
- JavaScript bridge breaks if methods are renamed
- WorkManager can't instantiate workers if obfuscated
- Release builds would crash without these rules

---

## 🛠️ Build Configuration

### Gradle Files

**android/build.gradle** (Project-level):
```gradle
buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.0.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    }
}
```

**android/app/build.gradle** (App-level):
```gradle
android {
    compileSdk 34
    defaultConfig {
        minSdkVersion 24
        targetSdkVersion 34
    }
    buildTypes {
        release {
            minifyEnabled true  // Enable ProGuard
            proguardFiles getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
        }
    }
}

dependencies {
    implementation "androidx.work:work-runtime-ktx:2.9.0"  // Background tasks
    implementation "com.squareup.okhttp3:okhttp:4.12.0"    // Networking
}
```

**android/gradle.properties**:
```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.17.10-hotspot
android.useAndroidX=true
android.nonTransitiveRClass=true
android.suppressUnsupportedCompileSdk=34
```

### AndroidManifest.xml

**Permissions**:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.SET_WALLPAPER" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

**Components**:
```xml
<application
    android:name=".MainApplication"
    android:hardwareAccelerated="true"
    android:largeHeap="true">
    
    <!-- Main Activity -->
    <activity android:name=".MainActivity"
        android:launchMode="singleTask"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
    
    <!-- Boot Receiver -->
    <receiver android:name=".workers.BootReceiver"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
        </intent-filter>
    </receiver>
    
    <!-- Midnight Receiver -->
    <receiver android:name=".workers.MidnightReceiver"
        android:exported="false" />
</application>
```

---

## 🎯 How Everything Works Together

### Complete User Journey

#### 1. **First Launch**
```
User installs app
    ↓
User opens app
    ↓
MainActivity.onCreate() runs
    ↓
NotificationHelper creates notification channels
    ↓
WebView loads: https://consistencygrid.netlify.app?canvasWidth=1080&canvasHeight=2400
    ↓
User sees login page
    ↓
User logs in
    ↓
Web app calls: Android.saveToken("user_token_here")
    ↓
Token saved to SharedPreferences
    ↓
User is logged in
```

#### 2. **Setting Wallpaper**
```
User generates wallpaper in web app
    ↓
User clicks "Set Wallpaper"
    ↓
Web app renders wallpaper to canvas
    ↓
Web app converts canvas to base64
    ↓
Web app calls: Android.saveWallpaper(base64Image)
    ↓
WebInterface decodes base64
    ↓
Checks UserPrefs for target (HOME/LOCK/BOTH)
    ↓
Calls WallpaperManager.setBitmap() with appropriate flags
    ↓
Wallpaper applied
    ↓
Toast shown: "BOTH SCREENS UPDATED ✨"
```

#### 3. **Enabling Auto-Updates**
```
User goes to Settings in web app
    ↓
User toggles "Auto-Update" ON
    ↓
Web app calls: Android.setAutoUpdateEnabled(true)
    ↓
UserPrefs.setAutoUpdate(true) saves preference
    ↓
WorkScheduler.scheduleDailyUpdate() called
    ↓
Checks for exact alarm permission
    ↓
If Android 13 and permission not granted:
    - Shows dialog: "Enable Exact Timing"
    - User taps "Open Settings"
    - Settings app opens to "Alarms & reminders"
    - User enables permission
    - Returns to app
    ↓
Alarm scheduled for next midnight
    ↓
Toast shown: "✅ Exact 12:00 AM update scheduled!"
```

#### 4. **Midnight Update Cycle**
```
11:59:59 PM → 12:00:00 AM
    ↓
AlarmManager fires alarm
    ↓
MidnightReceiver wakes up
    ↓
Logs: "⏰ Midnight alarm received!"
    ↓
Enqueues WallpaperWorker
    ↓
Reschedules alarm for tomorrow
    ↓
WallpaperWorker starts
    ↓
Checks network connectivity
    ↓
If offline: Returns failure (will retry later)
If online: Continues
    ↓
Retrieves token from UserPrefs
    ↓
If no token: Returns failure (user logged out)
If token exists: Continues
    ↓
Creates headless WebView
    ↓
Loads: /wallpaper-renderer?token=XXX&canvasWidth=1080&canvasHeight=2400
    ↓
Web app authenticates token
    ↓
Web app fetches user's data
    ↓
Web app renders wallpaper
    ↓
Web app calls: Android.saveWallpaper(base64Image)
    ↓
Worker decodes base64 to Bitmap
    ↓
Worker reads target from UserPrefs
    ↓
Worker applies wallpaper
    ↓
Worker logs: "✅ Background update completed successfully"
    ↓
Worker returns Result.success()
    ↓
User wakes up to new wallpaper! 🎉
```

#### 5. **After Device Reboot**
```
User restarts phone
    ↓
Android clears all alarms
    ↓
Device finishes booting
    ↓
BootReceiver.onReceive() triggered
    ↓
Checks: UserPrefs.isAutoUpdateEnabled()
    ↓
If true:
    - WorkScheduler.scheduleDailyUpdate() called
    - Alarm restored for next midnight
    - Logs: "✅ Auto-update is enabled. Rescheduling alarm..."
If false:
    - Logs: "ℹ️ Auto-update disabled, skipping."
    ↓
Auto-updates continue working after reboot
```

#### 6. **Changing Wallpaper Target**
```
User goes to Settings
    ↓
User selects "Lock Screen Only"
    ↓
Web app calls: Android.setWallpaperTarget("LOCK")
    ↓
UserPrefs.setWallpaperTarget("LOCK") saves preference
    ↓
Toast shown: "Preference: LOCK ✅"
    ↓
Next wallpaper update will only affect lock screen
    ↓
Home screen wallpaper preserved
```

#### 7. **Downloading Files**
```
User exports data as CSV
    ↓
Web app generates CSV file
    ↓
Web app converts to base64
    ↓
Web app calls: Android.downloadFile(base64Data, "data.csv", "text/csv")
    ↓
WebInterface decodes base64
    ↓
Saves file to Downloads folder
    ↓
Notifies DownloadManager
    ↓
Toast shown: "File saved to Downloads: data.csv ✅"
    ↓
File appears in Downloads app
```

#### 8. **Theme Synchronization**
```
User changes theme color in web app
    ↓
User selects orange (#FF7A00) and dark mode
    ↓
Web app calls: Android.updateAppTheme("#FF7A00", true)
    ↓
UserPrefs.saveTheme("#FF7A00", true) saves preference
    ↓
MainActivity.applyTheme() called
    ↓
Status bar color → #FF7A00
Navigation bar color → #FF7A00
Progress bar color → #FF7A00
System icons → White (for dark background)
    ↓
Native UI matches web app theme
```

#### 9. **Logging Out**
```
User clicks logout in web app
    ↓
Web app calls: Android.clearToken()
    ↓
UserPrefs.clear() wipes all data
    ↓
WorkScheduler.cancelDailyUpdate() cancels alarms
    ↓
WorkManager.cancelAllWorkByTag() cancels pending work
    ↓
CookieManager.removeAllCookies() clears cookies
    ↓
Toast shown: "Logged out successfully. Updates stopped."
    ↓
User sees login page
    ↓
Auto-updates stopped
```

---

## 🐛 Debugging & Monitoring

### Viewing Logs

**Real-time logs**:
```bash
adb logcat -s MainActivity:D WallpaperWorker:D WebInterface:D WorkScheduler:D MidnightReceiver:D BootReceiver:D
```

**Filter by component**:
```bash
# Only MainActivity
adb logcat -s MainActivity:D

# Only background workers
adb logcat -s WallpaperWorker:D MidnightReceiver:D

# Only alarm scheduling
adb logcat -s WorkScheduler:D
```

### Log Patterns

**Successful midnight update**:
```
WorkScheduler: ⏰ Scheduling next update for: Wed Jan 29 00:00:00 GMT+05:30 2026
MidnightReceiver: ⏰ Midnight alarm received! Triggering wallpaper update...
MidnightReceiver: ✅ WallpaperWorker enqueued
MidnightReceiver: 🔄 Next alarm scheduled
WallpaperWorker: 🚀 Starting background wallpaper update...
WallpaperWorker: ✅ Token retrieved, proceeding with update
WallpaperWorker: 🌐 Loading wallpaper renderer...
WallpaperWorker: 📱 Screen dimensions: 1080x2400
WallpaperWorker: 🔗 Loading URL: https://consistencygrid.netlify.app/wallpaper-renderer?token=XXX&canvasWidth=1080&canvasHeight=2400
WallpaperWorker: 📄 Page finished loading
WallpaperWorker: 📥 saveWallpaper called from JavaScript!
WallpaperWorker: ✅ Bitmap decoded successfully (1080x2400)
WallpaperWorker: 🎯 Applying wallpaper to target: BOTH
WallpaperWorker: ✅ Both screens updated
WallpaperWorker: ✅ Wallpaper applied successfully, resuming coroutine
WallpaperWorker: ✅ Background update completed successfully
```

**Permission issue**:
```
WorkScheduler: ⚠️ No exact alarm permission - updates may be delayed
WorkScheduler: Cannot show dialog - context is not an Activity
```

**Network error**:
```
WallpaperWorker: 🚀 Starting background wallpaper update...
WallpaperWorker: ❌ WebView error: net::ERR_INTERNET_DISCONNECTED (code: -2)
WallpaperWorker: ❌ Background update failed: timeout
```

---

## 📊 Performance Considerations

### Memory Management
- **Large heap enabled**: `android:largeHeap="true"`
- **Hardware acceleration**: Enabled for smooth WebView rendering
- **Bitmap recycling**: Bitmaps automatically garbage collected
- **WebView cleanup**: Destroyed after background rendering

### Battery Optimization
- **Exact alarms**: Only fire once per day (minimal impact)
- **WorkManager**: Respects Doze mode and battery saver
- **Background work**: Limited to 60 seconds max
- **Network checks**: Prevents unnecessary work when offline

### Network Efficiency
- **Cached WebView**: Reuses existing web app cache
- **Conditional loading**: Only loads when network available
- **Single request**: One network call per day for background update

---

## 🚀 Future Enhancements

### Planned Features
1. **Encrypted token storage** - Use EncryptedSharedPreferences
2. **Success notifications** - Show notification after successful update
3. **Error notifications** - Alert user if update fails
4. **Update history** - Track last 10 updates with timestamps
5. **Manual trigger** - Button to force immediate update
6. **Widget support** - Home screen widget showing current wallpaper
7. **Multiple schedules** - Update at custom times (not just midnight)
8. **Offline mode** - Cache last wallpaper for offline viewing

### Code Improvements
1. **Dependency injection** - Use Hilt/Koin for better testing
2. **Unit tests** - Test all business logic
3. **UI tests** - Automated testing of WebView interactions
4. **Crash reporting** - Firebase Crashlytics integration
5. **Analytics** - Track usage patterns and errors

---

## 📝 Summary

This app is a **sophisticated native Android wrapper** that:

1. **Loads a web app** in a WebView with full session persistence
2. **Bridges JavaScript and native Android** for wallpaper operations
3. **Schedules exact midnight alarms** with permission handling
4. **Renders wallpapers in background** using headless WebView
5. **Persists across reboots** by restoring alarms
6. **Handles all edge cases** with comprehensive error handling
7. **Provides excellent UX** with clear feedback and guidance

The architecture is **modular, maintainable, and production-ready**, with:
- ✅ Comprehensive documentation
- ✅ Proper error handling
- ✅ Security considerations
- ✅ Battery optimization
- ✅ ProGuard configuration
- ✅ User-friendly guidance

**Total Lines of Code**: ~2,000 lines of Kotlin
**Build Time**: ~30 seconds
**APK Size**: ~5 MB
**Supported Devices**: Android 7.0+ (API 24+)
**Target Audience**: Users who want automated, personalized wallpapers

---

*Last Updated: January 29, 2026*
*Version: 1.0*
*Author: ConsistencyGrid Team*
