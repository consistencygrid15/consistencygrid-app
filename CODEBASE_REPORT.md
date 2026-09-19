# ConsistencyGridWallpaper - Codebase Structure Report

**Generated:** 2026-04-11  
**Workspace:** `d:/startup/ConsistencyGridWallpaper`  

This report describes the **current repository structure and major runtime flows** as they exist in the working tree.

---

## 0) Scope and reading guide

**Scope**
- Focuses on the Android app, since that is where the real product logic lives.
- Mentions the React Native + iOS scaffolding only to document what exists and what appears incomplete.

**Assumptions**
- Findings are based on the code currently present in the working directory (including uncommitted/untracked changes).

**How to use**
- Start with Sections **3-6** to understand layout + major flows.
- Use Section **10** as a quick index when navigating the code.

---

## 1) Executive Summary

This repo contains a cross-platform React Native scaffold, but the **real product** is a **native Android app** that:

- Provides **native authentication** (Google + email/password).
- Runs a **native Jetpack Compose UI** backed by **offline-first local storage (Room)**.
- Generates and applies a **daily wallpaper** using a local renderer (WebView + bundled JS) and multiple scheduling fallbacks (AlarmManager / WorkManager / FCM / date-change broadcasts).
- Includes an optional **Digital Wellbeing** feature ("Reel Control") implemented as an AccessibilityService + overlay HUD + limit popup.

There is also an older **WebView-wrapper** mode that can still run, but the current default is to route into the native Compose app.

---

## 2) Technology Stack

### Android
- Language: Kotlin
- UI: Jetpack Compose + Navigation Compose
- Persistence: Room + EncryptedSharedPreferences
- Networking: OkHttp + Retrofit (plus some direct OkHttp usage)
- Background work: WorkManager + AlarmManager + BroadcastReceivers
- Push triggers: Firebase Cloud Messaging

### JavaScript / React Native (scaffold)
- React Native project skeleton with minimal JS entry (`App.js`)
- Jest preset configured
- Node engine requirement: `>= 20`

### iOS (scaffold)
- Standard RN template project and Podfile
- No iOS-specific native features implemented in this repo snapshot

---

## 3) Repository Layout (Top Level)

- `android/` - Android app (Kotlin + resources + Gradle)
- `ios/` - iOS project scaffold (React Native template)
- `docs/` - documentation packages (notably native-auth migration docs)
- `App.js`, `package.json`, `package-lock.json`, `jest.config.js` - RN scaffold tooling
- `build_and_run.*`, `production_build.bat`, `check_environment.bat` - Windows build helpers for Android
- `README.md`, `COMPLETE_DOCUMENTATION.md`, `EXACT_TIMING_GUIDE.md`, `UI_*` docs - project documentation (some documents reflect older architecture)

### 3.1) Quick directory tree (high signal)

```
.
├─ android/
│  ├─ app/
│  │  ├─ src/main/java/com/consistencygridwallpaper/   # Kotlin source
│  │  ├─ src/main/res/                                 # layouts/drawables/values/xml
│  │  ├─ src/main/assets/renderer/                      # offline renderer (index.html + bundle.js)
│  │  └─ google-services.json                           # Firebase config (tracked)
│  ├─ build.gradle / settings.gradle / gradle.properties
│  └─ gradle/wrapper/gradle-wrapper.properties
├─ ios/                                                 # RN iOS template
├─ docs/native-auth-migration/                          # migration docs package
├─ App.js                                               # minimal RN deep-link handler
└─ package.json                                         # RN tooling + scripts
```

---

## 4) Android App - Code Organization

### 4.1) Main packages

Under `android/app/src/main/java/com/consistencygridwallpaper/`:

- `auth/` — Native authentication activities and helpers
- `bridge/` — JavaScript bridge exposed to WebViews (`window.Android.*`)
- `network/` — Retrofit client + API interface
- `repository/` — Data sync orchestration between local Room and server
- `storage/` — Persistent preferences (encrypted)
- `storage/room/` — Room DB, entities, DAOs, migrations
- `ui/` — Legacy fragments (WebView-era)
- `ui/compose/` — Native Compose app shell + screens + view models
- `utils/` — Shared helpers (network check, notifications, dialogs)
- `workers/` — Background scheduling, receivers, workers, renderer helpers

### 4.2) Runtime entry points

- **Application:** `android/app/src/main/java/com/consistencygridwallpaper/MainApplication.kt`
  - Initializes CookieManager cookie acceptance and creates notification channels early.

- **Launcher Activity / Router:** `android/app/src/main/java/com/consistencygridwallpaper/MainActivity.kt`
  - Enforces logged-in state and (by default) routes into the Compose app.
  - Also contains the legacy “WebView wrapper” implementation when native UI is disabled.

- **Compose Shell Activity:** `android/app/src/main/java/com/consistencygridwallpaper/ui/compose/NativeAppActivity.kt`
  - Entry for the native UI; sets up a NavHost with bottom navigation and full-screen routes.

- **Auth Activities:**
  - `android/app/src/main/java/com/consistencygridwallpaper/auth/AuthActivity.kt`
  - `android/app/src/main/java/com/consistencygridwallpaper/auth/EmailAuthActivity.kt`

- **Wallpaper render surface Activity (transparent):**
  - `android/app/src/main/java/com/consistencygridwallpaper/workers/WallpaperRenderActivity.kt`

### 4.3) Compose app surface map (what screens exist)

The Compose app uses a NavHost in `android/app/src/main/java/com/consistencygridwallpaper/ui/compose/NativeAppActivity.kt` with these primary routes:

- `dashboard` - `DashboardScreen` + `DashboardViewModel`
- `habits` - `HabitsScreen` + `HabitsViewModel`
- `goals` - `GoalsScreen` + `GoalsViewModel`
- `streaks` - `StreaksScreen`
- `reminders` - `RemindersScreen` + `RemindersViewModel`
- `settings` - `SettingsScreen`
- Full-screen routes (no bottom bar):
  - `wallpaper_generator` - `WallpaperGeneratorScreen`
  - `reel_control` - `ReelControlScreen`
  - `web_view/{url}/{title}` - `InAppWebScreen`

### 4.4) Manifest-declared components

`android/app/src/main/AndroidManifest.xml` declares:

- Receivers: `BootReceiver`, `DateChangeReceiver`, `AlarmPermissionReceiver`, `MidnightReceiver`
- Services: WorkManager `SystemForegroundService` merge, `WallpaperMessagingService` (FCM), `ReelTrackingService` (AccessibilityService)
- Activities: `MainActivity`, auth activities, Compose activity, wallpaper render activity

---

## 5) Core Features & Data Flows

### 5.1) Authentication & session persistence

**Primary classes**
- `android/app/src/main/java/com/consistencygridwallpaper/auth/AuthManager.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/storage/UserPrefs.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/auth/GoogleSignInHelper.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/auth/EmailAuthActivity.kt`

**Flow**
1. `AuthActivity` launches Google sign-in or email auth.
2. On success, app persists:
   - a stable **public token** (used widely for API auth),
   - a **NextAuth session token** (used by in-app web pages),
   - onboarded status and expiry metadata.
3. `MainActivity` enforces logged-in state and routes to the native UI.

**In-app WebView login**
- `android/app/src/main/java/com/consistencygridwallpaper/ui/compose/web/InAppWebScreen.kt` injects the `next-auth.session-token` cookie when available to keep the user logged in for in-app web pages.

### 5.1.1) Server endpoints touched by auth

- Google sign-in verification: `POST https://consistencygrid.com/api/auth/native/google`
- Email signup/login:
  - `POST https://consistencygrid.com/api/native-auth/email-signup`
  - `POST https://consistencygrid.com/api/native-auth/email-login`
- Silent refresh: `POST https://consistencygrid.com/api/native-auth/refresh`

Legacy/bridge path (WebView wrapper mode):
- `GET https://consistencygrid.com/api/auth/webview-login?...` (sets cookies then redirects)

### 5.2) Offline-first local data model

**Room DB**
- `android/app/src/main/java/com/consistencygridwallpaper/storage/room/AppDatabase.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/storage/room/Entities.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/storage/room/Daos.kt`

Entities include: Habits, Habit Logs, Goals, Reminders, with “sync” flags and soft-delete patterns.

### 5.3) Server sync orchestration

- `android/app/src/main/java/com/consistencygridwallpaper/repository/SyncRepository.kt`

Key behavior:
- Best-effort pushes local offline mutations (ticks/goals/reminders) upstream.
- Pulls server state and writes to Room so the app continues to function fully offline afterward.
- Uses an atomic “in-flight” guard to avoid concurrent sync storms.

### 5.3.1) Server endpoints touched by sync

Via Retrofit (`ApiService`) and direct OkHttp calls:

- State pull (base payload): `GET {baseUrl}/api/wallpaper-data?token=...`
- Push offline ticks: `POST {baseUrl}/api/mobile/habits/tick?token=...`
- Push goal mutations: `POST {baseUrl}/api/mobile/goals/sync?token=...`
- Push reminder mutations: `POST {baseUrl}/api/mobile/reminders/sync?token=...`
- Goal enrichment pull: `GET {baseUrl}/api/goals?token=...`

### 5.4) Wallpaper generation (manual)

**Native Compose wallpaper generator**
- `android/app/src/main/java/com/consistencygridwallpaper/ui/compose/wallpaper/WallpaperGeneratorScreen.kt`

**Payload builder (Room → renderer schema)**
- `android/app/src/main/java/com/consistencygridwallpaper/ui/compose/wallpaper/WallpaperDataBuilder.kt`

**Renderer**
- `android/app/src/main/assets/renderer/index.html`
- `android/app/src/main/assets/renderer/bundle.js`

Renderer contract (important for debugging):
- The JS calls `window.Android.onPageReady()` on `window.onload`.
- The Android side injects a **single JSON string** into `window.renderOfflineData(jsonString)`.
- The renderer calls `window.Android.saveWallpaper(base64Png)` at the end of rendering (so callers must guard preview vs apply).

The generator screen:
- Builds a complete JSON payload from local Room DB + saved settings.
- Injects it into the renderer WebView (`window.renderOfflineData(...)`).
- Supports “preview-only” renders vs “apply wallpaper” renders via state flags.

### 5.5) Wallpaper updates (automatic)

**Worker**
- `android/app/src/main/java/com/consistencygridwallpaper/workers/WallpaperWorker.kt`

**Trigger layers**
- Exact AlarmManager: `android/app/src/main/java/com/consistencygridwallpaper/workers/ExactAlarmScheduler.kt`
- Alarm receiver (+ WakeLock): `android/app/src/main/java/com/consistencygridwallpaper/workers/MidnightReceiver.kt`
- WorkManager periodic backup: `android/app/src/main/java/com/consistencygridwallpaper/workers/MidnightWorkScheduler.kt`
- Date rollover broadcast: `android/app/src/main/java/com/consistencygridwallpaper/workers/DateChangeReceiver.kt`
- Boot / app-update reschedule: `android/app/src/main/java/com/consistencygridwallpaper/workers/BootReceiver.kt`
- FCM push trigger: `android/app/src/main/java/com/consistencygridwallpaper/workers/WallpaperMessagingService.kt`

**Execution strategy**
- Worker builds the same offline JSON used by the generator.
- Loads `file:///android_asset/renderer/index.html` in a WebView, injects JSON, receives base64, applies wallpaper.
- Multiple triggers exist; duplicate-run prevention uses a “last update date” guard.

**Transparent render activity**
- `android/app/src/main/java/com/consistencygridwallpaper/workers/WallpaperRenderActivity.kt`
- Used by some “instant update” paths because a WebView attached to a real Window is more reliable than a headless WebView in the background on some devices.

### 5.5.1) Telemetry / device registration endpoints

- Device token registration (FCM): `POST {baseUrl}/api/device-token`
- Wallpaper update telemetry (best-effort): `POST {baseUrl}/api/telemetry/wallpaper-update`

### 5.6) Reel Control (digital wellbeing)

**AccessibilityService**
- `android/app/src/main/java/com/consistencygridwallpaper/reelcontrol/service/ReelTrackingService.kt`

**State + UI**
- State machine: `android/app/src/main/java/com/consistencygridwallpaper/reelcontrol/manager/ReelManager.kt`
- Preferences: `android/app/src/main/java/com/consistencygridwallpaper/reelcontrol/data/PreferencesManager.kt`
- Floating HUD: `android/app/src/main/java/com/consistencygridwallpaper/reelcontrol/manager/OverlayManager.kt`
- Limit popup: `android/app/src/main/java/com/consistencygridwallpaper/reelcontrol/manager/PopupManager.kt`
- Compose screen: `android/app/src/main/java/com/consistencygridwallpaper/reelcontrol/ui/ReelControlScreen.kt`

**Resources**
- HUD layout: `android/app/src/main/res/layout/overlay_hud.xml`
- Popup layout: `android/app/src/main/res/layout/popup_limit.xml`
- Accessibility config: `android/app/src/main/res/xml/accessibility_service_config.xml`

---

## 6) Configuration, Dependencies, and Build

### Android build configuration
- App module Gradle: `android/app/build.gradle`
  - Compose enabled, Room, Retrofit, Firebase Messaging, EncryptedSharedPreferences, etc.
- Project Gradle: `android/build.gradle`
- Wrapper: `android/gradle/wrapper/gradle-wrapper.properties`
- ProGuard rules: `android/app/proguard-rules.pro` (keeps JS bridge + workers)

### Dev scripts
- `build_and_run.ps1`, `build_and_run.bat` — clean/build/install/launch debug
- `production_build.bat` — “release candidate” build flow (still builds/install debug by default in current snapshot)
- `check_environment.bat` — prints Java/ADB/Gradle diagnostics

### React Native tooling
- `package.json` — scripts (`start`, `android`, `ios`, `lint`, `test`), dependencies, Node engine
- `jest.config.js` — RN jest preset

---

## 7) Permissions & Privacy Surface (Android)

See `android/app/src/main/AndroidManifest.xml` for current declarations. Notable capabilities:

- Wallpaper application: `SET_WALLPAPER`
- Scheduling: `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`
- Boot/app update persistence: `RECEIVE_BOOT_COMPLETED`
- Notifications: `POST_NOTIFICATIONS`, WorkManager foreground service
- Overlays: `SYSTEM_ALERT_WINDOW` (Reel Control HUD/popup)
- Accessibility: `BIND_ACCESSIBILITY_SERVICE` (Reel Control)
- Location permissions are currently declared; location is requested from WebView-origin geolocation prompts in the legacy WebView path.

### 7.1) Permission rationale (high level)

- `SET_WALLPAPER`: apply the rendered bitmap as system wallpaper.
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`: reliable daily scheduling (Android 12+ behavior changes).
- `RECEIVE_BOOT_COMPLETED`: re-arm alarms after reboot.
- `POST_NOTIFICATIONS` + foreground service permissions: show/update the required foreground notification for long-running background work.
- `SYSTEM_ALERT_WINDOW`: display Reel Control HUD/popup on top of other apps.
- Accessibility service binding: detect scroll events in supported apps for Reel Control limits.

---

## 8) Testing & Quality Gates

- JS tests: `npm test` uses Jest (RN preset), but there is minimal JS application logic in-repo.
- Android-specific automated tests are not present in the inspected snapshot (no dedicated unit/instrumentation test suite was found at repo root).

---

## 9) Repo Health Notes (Actionable)

These are structural issues worth tracking in an engineering checklist:

1. **Docs vs current behavior drift**
   - Several docs describe a WebView-wrapper-first app, while runtime defaults to native Compose routing.

2. **Deep-link handling vs native UI routing**
   - `MainActivity` returns early when routing to Compose; if deep links are only handled in `MainActivity`, they may not execute in the default flow.

3. **Hard-coded backend URLs**
   - Multiple classes use `https://consistencygrid.com` directly instead of always using `UserPrefs.getBaseUrl()`.

4. **Sensitive / noisy artifacts tracked**
   - `android/app/google-services.json` is tracked.
   - Large log files appear to be tracked in git (e.g. root `logcat*.txt`, `android_log.txt`).

5. **Stale/unused files**
   - `android/app/src/main/java/com/consistencygridwallpaper/WallpaperUpdateWorker.kt` is empty.
   - Legacy fragments under `android/app/src/main/java/com/consistencygridwallpaper/ui/` appear unreferenced.

6. **Repo cleanliness**
   - The working tree currently contains many build logs and untracked Android source/assets; consider formalizing what is meant to be committed (or updating `.gitignore` / cleanup scripts).

---

## 10) Appendix - Key File Index

**Android entry & routing**
- `android/app/src/main/java/com/consistencygridwallpaper/MainActivity.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/ui/compose/NativeAppActivity.kt`

**Auth**
- `android/app/src/main/java/com/consistencygridwallpaper/auth/AuthActivity.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/auth/EmailAuthActivity.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/auth/GoogleSignInHelper.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/auth/AuthManager.kt`

**Storage + sync**
- `android/app/src/main/java/com/consistencygridwallpaper/storage/UserPrefs.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/storage/room/AppDatabase.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/repository/SyncRepository.kt`

**Wallpaper**
- `android/app/src/main/java/com/consistencygridwallpaper/workers/WallpaperWorker.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/workers/ExactAlarmScheduler.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/workers/MidnightReceiver.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/workers/MidnightWorkScheduler.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/ui/compose/wallpaper/WallpaperGeneratorScreen.kt`
- `android/app/src/main/assets/renderer/index.html`

**Reel Control**
- `android/app/src/main/java/com/consistencygridwallpaper/reelcontrol/service/ReelTrackingService.kt`
- `android/app/src/main/java/com/consistencygridwallpaper/reelcontrol/ui/ReelControlScreen.kt`
- `android/app/src/main/res/xml/accessibility_service_config.xml`
