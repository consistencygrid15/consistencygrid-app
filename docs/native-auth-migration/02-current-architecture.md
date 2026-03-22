# Current Architecture - Authentication Flow

## Document Information

- **Version:** 1.0
- **Date:** 2026-02-04
- **Purpose:** Document existing authentication architecture before migration

---

## Overview

This document provides a detailed analysis of the **current authentication architecture** in the ConsistencyGrid application, covering both Android and Web components.

---

## Current Android Authentication Flow

### Component Overview

```
MainActivity.kt
├── WebView (main content)
├── UserPrefs (token storage)
├── WebInterface (JavaScript bridge)
└── Chrome Custom Tabs (OAuth)
```

### Authentication Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    APP LAUNCH                               │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  MainActivity.onCreate()                                    │
│  - Initialize WebView                                       │
│  - Check token in UserPrefs                                 │
└─────────────────────────────────────────────────────────────┘
                          ↓
                    ┌─────────┐
                    │ Token?  │
                    └─────────┘
                    ↙         ↘
              YES ↙             ↘ NO
                ↓                 ↓
    ┌──────────────────┐   ┌──────────────────┐
    │ Load Dashboard   │   │ Load Landing     │
    │ with token       │   │ Page             │
    └──────────────────┘   └──────────────────┘
                                    ↓
                          ┌──────────────────┐
                          │ User clicks      │
                          │ "Login"          │
                          └──────────────────┘
                                    ↓
                          ┌──────────────────┐
                          │ WebView detects  │
                          │ OAuth URL        │
                          └──────────────────┘
                                    ↓
                          ┌──────────────────┐
                          │ Launch Chrome    │
                          │ Custom Tab       │
                          └──────────────────┘
                                    ↓
                          ┌──────────────────┐
                          │ Google OAuth     │
                          │ Flow             │
                          └──────────────────┘
                                    ↓
                          ┌──────────────────┐
                          │ Redirect to      │
                          │ /mobile-auth-    │
                          │ callback         │
                          └──────────────────┘
                                    ↓
                          ┌──────────────────┐
                          │ Deep Link:       │
                          │ consistencygrid: │
                          │ //login-success  │
                          │ ?token=XXX       │
                          └──────────────────┘
                                    ↓
                          ┌──────────────────┐
                          │ MainActivity     │
                          │ receives token   │
                          └──────────────────┘
                                    ↓
                          ┌──────────────────┐
                          │ Save token to    │
                          │ UserPrefs        │
                          └──────────────────┘
                                    ↓
                          ┌──────────────────┐
                          │ Reload WebView   │
                          │ with token       │
                          └──────────────────┘
```

### Key Files and Responsibilities

#### MainActivity.kt

**Location:** `android/app/src/main/java/com/consistencygridwallpaper/MainActivity.kt`

**Current Responsibilities:**
- Initialize WebView with optimal settings
- Check login status via `UserPrefs.getToken()`
- Route to dashboard if logged in, landing page if not
- Handle deep links from OAuth callback
- Manage Chrome Custom Tabs for OAuth
- Persist cookies and tokens

**Key Methods:**
```kotlin
// Lines 459-465: Check if user is logged in
private fun checkLoginStatus(): Boolean {
    val userPrefs = UserPrefs(this)
    val token = userPrefs.getToken()
    return !token.isNullOrBlank()
}

// Lines 478-536: Load website with smart routing
private fun loadWebsite() {
    val isLoggedIn = checkLoginStatus()
    if (isLoggedIn) {
        // Load dashboard with token
    } else {
        // Load landing page
    }
}

// Lines 154-194: Handle deep link from OAuth
private fun handleDeepLink(intent: Intent?) {
    val token = data.getQueryParameter("token")
    if (!token.isNullOrBlank()) {
        userPrefs.saveToken(token)
        loadLoginWithToken(token)
    }
}

// Lines 297-335: URL handling for OAuth
override fun shouldOverrideUrlLoading(...) {
    if (url.contains("accounts.google.com") || 
        url.contains("/api/auth/signin/google")) {
        launchCustomTab(url)
        return true
    }
}

// Lines 658-662: Launch Chrome Custom Tab
private fun launchCustomTab(url: String) {
    val customTabsIntent = CustomTabsIntent.Builder().build()
    customTabsIntent.launchUrl(this, Uri.parse(url))
}
```

#### UserPrefs.kt

**Location:** `android/app/src/main/java/com/consistencygridwallpaper/storage/UserPrefs.kt`

**Current Responsibilities:**
- Store authentication token in SharedPreferences
- Store theme preferences
- Store auto-update settings
- Store wallpaper target preferences

**Key Methods:**
```kotlin
// Lines 79-86: Save token
fun saveToken(token: String) {
    prefs.edit().putString(KEY_TOKEN, token).apply()
}

// Lines 92-94: Retrieve token
fun getToken(): String? {
    return prefs.getString(KEY_TOKEN, null)
}

// Lines 142-145: Clear all data (logout)
fun clear() {
    prefs.edit().clear().apply()
}
```

**Current Storage:**
- `KEY_TOKEN` - Authentication token
- `KEY_AUTO_UPDATE` - Auto-update enabled flag
- `KEY_WALLPAPER_TARGET` - Home/Lock/Both
- `KEY_THEME_COLOR` - Theme color hex
- `KEY_IS_DARK_MODE` - Dark mode flag

**Missing (to be added):**
- `KEY_ONBOARDED` - Onboarded status flag

#### WebInterface.kt

**Location:** `android/app/src/main/java/com/consistencygridwallpaper/bridge/WebInterface.kt`

**Current Responsibilities:**
- Set wallpaper from JavaScript
- Enable/disable auto-updates
- Apply theme changes
- Download images

**Current Methods:**
- `setWallpaper(base64Image)`
- `setAutoUpdateEnabled(enabled)`
- `applyTheme(colorHex, isDark)`
- `downloadImage(base64Image, filename)`

**Missing (to be added):**
- `logout()` - Clear tokens and return to auth screen

---

## Current Web Authentication Flow

### Component Overview

```
Next.js App
├── NextAuth.js (authentication)
├── Google OAuth Provider
├── Credentials Provider (token-based)
├── Prisma (database)
└── Mobile Auth Callback Page
```

### Authentication Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│              USER VISITS LANDING PAGE                       │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  page.js (Server Component)                                 │
│  - Check session with getServerSession()                    │
│  - If logged in → redirect to /dashboard                    │
│  - If not logged in → show LandingPage component            │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  User clicks "Login with Google"                            │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  NextAuth Google Provider                                   │
│  - Redirect to Google OAuth                                 │
│  - User authenticates with Google                           │
│  - Google redirects back to /api/auth/callback/google       │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  authOptions.js - signIn callback                           │
│  - Check if user exists in database                         │
│  - If new user → create with onboarded = false              │
│  - Generate publicToken                                     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  authOptions.js - jwt callback                              │
│  - Attach user ID, onboarded status to JWT                  │
│  - Attach publicToken for mobile handoff                    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  authOptions.js - session callback                          │
│  - Attach user data to session object                       │
│  - session.user.onboarded available to app                  │
└─────────────────────────────────────────────────────────────┘
                          ↓
                    ┌─────────┐
                    │Android? │
                    └─────────┘
                    ↙         ↘
              YES ↙             ↘ NO
                ↓                 ↓
    ┌──────────────────┐   ┌──────────────────┐
    │ Redirect to      │   │ Redirect to      │
    │ /mobile-auth-    │   │ /dashboard or    │
    │ callback         │   │ /onboarding      │
    └──────────────────┘   └──────────────────┘
            ↓
    ┌──────────────────┐
    │ Extract token    │
    │ from session     │
    └──────────────────┘
            ↓
    ┌──────────────────┐
    │ Fire deep link:  │
    │ consistencygrid: │
    │ //login-success  │
    │ ?token=XXX       │
    └──────────────────┘
```

### Key Files and Responsibilities

#### authOptions.js

**Location:** `src/app/api/auth/authOptions.js`

**Current Responsibilities:**
- Configure NextAuth providers (Google, Credentials)
- Handle user creation for new OAuth users
- Generate and manage publicToken for mobile auth
- Attach user metadata to JWT and session

**Key Callbacks:**
```javascript
// Lines 95-114: signIn callback
async signIn({ user }) {
    const existing = await prisma.user.findUnique({
        where: { email: user.email }
    });
    
    if (!existing) {
        await prisma.user.create({
            data: {
                email: user.email,
                name: user.name,
                publicToken: generatePublicToken(),
                emailVerified: new Date()
            }
        });
    }
    return true;
}

// Lines 117-153: jwt callback
async jwt({ token, user }) {
    const dbUser = await prisma.user.findUnique({
        where: { email },
        select: { id, onboarded, publicToken }
    });
    
    token.id = dbUser.id;
    token.onboarded = dbUser.onboarded;
    token.publicToken = dbUser.publicToken;
    
    return token;
}

// Lines 155-165: session callback
async session({ session, token }) {
    session.user.id = token.id;
    session.user.onboarded = token.onboarded;
    session.user.publicToken = token.publicToken;
    return session;
}
```

**Providers:**
1. **GoogleProvider** - OAuth with Google
2. **CredentialsProvider (token-login)** - For WebView token recovery
3. **CredentialsProvider (credentials)** - Email/password login

#### mobile-auth-callback/page.js

**Location:** `src/app/mobile-auth-callback/page.js`

**Current Responsibilities:**
- Receive OAuth callback from Google
- Extract publicToken from session
- Fire deep link to return to Android app
- Show fallback UI if deep link fails

**Key Logic:**
```javascript
// Lines 23-49: Deep link firing
useEffect(() => {
    if (status !== "authenticated") return;
    
    const token = session?.user?.publicToken;
    if (!token) return;
    
    const deepLink = `consistencygrid://login-success?token=${token}`;
    window.location.href = deepLink;
    
    // Fallback after 2.5 seconds
    setTimeout(() => setShowFallback(true), 2500);
}, [status, session]);
```

#### dashboard/page.js

**Location:** `src/app/dashboard/page.js`

**Current Responsibilities:**
- Check if user has completed onboarding
- Redirect to `/onboarding` if not onboarded
- Show dashboard content if onboarded

**Key Logic:**
```javascript
// Lines 23-28: Onboarding check
const session = await getServerSession(authOptions);

if (!session?.user?.onboarded) {
    redirect("/onboarding");
}
```

#### onboarding/page.js

**Location:** `src/app/onboarding/page.js`

**Current Responsibilities:**
- Multi-step onboarding flow
- Collect user data (name, DOB, habits, theme)
- Call `/api/onboarding/complete` to save data
- Set `onboarded = true` in database
- Redirect to dashboard after completion

---

## Token & Onboarding Flow

### Token Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                    TOKEN GENERATION                         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  User signs in via Google OAuth                             │
│  → authOptions.js generates publicToken                     │
│  → Stored in User table                                     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                    TOKEN HANDOFF                            │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  Android receives token via deep link                       │
│  → Saved to UserPrefs (SharedPreferences)                   │
│  → Used for all subsequent WebView loads                    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                    TOKEN USAGE                              │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  MainActivity loads: /login?token=XXX                       │
│  → NextAuth token-login provider validates token            │
│  → Creates session cookie                                   │
│  → User is logged in                                        │
└─────────────────────────────────────────────────────────────┘
```

### Onboarding Flow

```
┌─────────────────────────────────────────────────────────────┐
│  NEW USER CREATED                                           │
│  onboarded = false (default)                                │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  Dashboard checks session.user.onboarded                    │
│  → false → redirect to /onboarding                          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  User completes onboarding steps                            │
│  → Personalization (name, DOB, life expectancy)             │
│  → Habits selection                                         │
│  → Theme selection                                          │
│  → Welcome screen                                           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  POST /api/onboarding/complete                              │
│  → Save user data                                           │
│  → Update onboarded = true                                  │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  Redirect to /dashboard                                     │
│  → User now sees dashboard on all future visits             │
└─────────────────────────────────────────────────────────────┘
```

---

## Database Schema (Current)

### User Table

```prisma
model User {
  id          String   @id @default(cuid())
  name        String?
  email       String   @unique
  password    String?  // For credentials auth
  image       String?
  publicToken String   @unique  // For mobile auth
  emailVerified DateTime?
  onboarded   Boolean  @default(false)  // Onboarding flag
  
  // ... other fields
}
```

**Key Fields for Authentication:**
- `email` - Unique identifier
- `publicToken` - Used for mobile app authentication
- `onboarded` - Controls routing (onboarding vs dashboard)
- `emailVerified` - Set automatically for OAuth users

---

## Current Limitations

### Android Side

1. **Chrome Custom Tabs Dependency**
   - Requires compatible browser installed
   - User leaves app context
   - Cannot customize OAuth UI

2. **No Native Email Auth**
   - Only Google OAuth supported
   - Cannot add email/password signup natively

3. **Complex Deep Link Flow**
   - Multiple redirects
   - Potential for failures
   - Hard to debug

### Web Side

1. **Mobile Detection Logic**
   - Relies on user agent detection
   - Can be unreliable
   - Requires special callback page

2. **No Direct API for Native Apps**
   - Native apps must use OAuth flow
   - Cannot directly authenticate with backend

---

## Summary

### Current Flow Works But Has Issues

✅ **What Works:**
- Token persistence across app restarts
- Onboarding flow for new users
- Dashboard routing for existing users
- Logout functionality

❌ **What Needs Improvement:**
- Browser dependency for authentication
- Non-native user experience
- Limited authentication methods
- Complex OAuth callback flow

### Ready for Migration

The current architecture is **well-structured** and **migration-friendly**:
- Token-based auth already in place
- Onboarding flag already exists
- WebView separation already established
- No major refactoring needed

---

## Next Document

→ **03-proposed-architecture.md** - How native auth will improve this flow
