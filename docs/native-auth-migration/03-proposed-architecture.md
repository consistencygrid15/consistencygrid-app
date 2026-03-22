# Proposed Architecture - Native Auth + WebView

## Document Information

- **Version:** 1.0
- **Date:** 2026-02-04
- **Purpose:** Define the target architecture after native authentication migration

---

## Architecture Overview

### High-Level Architecture

```
┌───────────────────────────────────────────────────────────────┐
│                    ANDROID APPLICATION                        │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              AUTHENTICATION LAYER (NATIVE)              │ │
│  │                                                         │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │ │
│  │  │   AuthActivity│  │ GoogleSignIn │  │ EmailAuth    │ │ │
│  │  │   (Entry)    │  │   Helper     │  │  Activity    │ │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘ │ │
│  │         │                  │                  │        │ │
│  │         └──────────────────┴──────────────────┘        │ │
│  │                          │                             │ │
│  │                  ┌───────▼────────┐                    │ │
│  │                  │  AuthManager   │                    │ │
│  │                  │  (Coordinator) │                    │ │
│  │                  └───────┬────────┘                    │ │
│  │                          │                             │ │
│  │                  ┌───────▼────────┐                    │ │
│  │                  │   UserPrefs    │                    │ │
│  │                  │ (Token Storage)│                    │ │
│  │                  └────────────────┘                    │ │
│  └─────────────────────────────────────────────────────────┘ │
│                          │                                   │
│                          │ Token + Onboarded Status          │
│                          ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │               CONTENT LAYER (WEBVIEW)                   │ │
│  │                                                         │ │
│  │  ┌──────────────┐                                      │ │
│  │  │  MainActivity│                                      │ │
│  │  │  (WebView    │                                      │ │
│  │  │   Container) │                                      │ │
│  │  └──────┬───────┘                                      │ │
│  │         │                                               │ │
│  │         ▼                                               │ │
│  │  ┌──────────────────────────────────────────────────┐  │ │
│  │  │            WebView                               │  │ │
│  │  │  - /dashboard (if onboarded)                     │  │ │
│  │  │  - /onboarding (if not onboarded)                │  │ │
│  │  │  - NO authentication handling                    │  │ │
│  │  └──────────────────────────────────────────────────┘  │ │
│  └─────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────┘
                          │
                          │ API Calls
                          ▼
┌───────────────────────────────────────────────────────────────┐
│                      BACKEND (NEXT.JS)                        │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │           NEW NATIVE AUTH ENDPOINTS                     │ │
│  │                                                         │ │
│  │  /api/auth/native/google        (Google ID token)     │ │
│  │  /api/auth/native/email-signup  (Email registration)  │ │
│  │  /api/auth/native/email-login   (Email login)         │ │
│  └─────────────────────────────────────────────────────────┘ │
│                          │                                   │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │           EXISTING WEB AUTH (UNCHANGED)                 │ │
│  │                                                         │ │
│  │  NextAuth.js                                           │ │
│  │  - Google OAuth Provider (for web users)              │ │
│  │  - Token-based Provider (for WebView recovery)        │ │
│  │  - Credentials Provider (email/password for web)      │ │
│  └─────────────────────────────────────────────────────────┘ │
│                          │                                   │
│                          ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │                  DATABASE (PRISMA)                      │ │
│  │                                                         │ │
│  │  User Table:                                           │ │
│  │  - email, password (hashed)                            │ │
│  │  - publicToken (for mobile auth)                       │ │
│  │  - onboarded (routing flag)                            │ │
│  └─────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────┘
```

---

## Component Responsibilities

### Android Components

#### 1. AuthActivity

**Purpose:** Entry point for unauthenticated users

**Responsibilities:**
- Display native authentication UI
- Show three options:
  - "Continue with Google" → Launch GoogleSignInHelper
  - "Sign up with Email" → Launch EmailAuthActivity (signup mode)
  - "Log in with Email" → Launch EmailAuthActivity (login mode)
- Check login status on launch
- Redirect to MainActivity if already logged in
- Handle authentication success/failure

**UI Elements:**
- App logo
- Welcome message
- Three authentication buttons
- Loading indicator
- Error message display

**Lifecycle:**
```
onCreate() → Check if logged in
    ↓ NO
Show auth options
    ↓
User selects method
    ↓
Launch appropriate flow
    ↓
Receive result
    ↓
Save token + onboarded status
    ↓
Launch MainActivity
    ↓
finish()
```

---

#### 2. EmailAuthActivity

**Purpose:** Handle email/password authentication

**Responsibilities:**
- Display email/password form
- Support two modes: signup and login
- Validate input fields
- Call backend API endpoints
- Handle success/error responses
- Return result to AuthActivity

**UI Elements:**
- Email input field
- Password input field
- Name input field (signup only)
- Submit button
- Mode toggle (switch between signup/login)
- Loading indicator
- Error message display

**Validation Rules:**
- Email: Valid format, not empty
- Password: Minimum 8 characters, not empty
- Name: Minimum 2 characters (signup only)

**API Calls:**
- Signup: `POST /api/auth/native/email-signup`
- Login: `POST /api/auth/native/email-login`

---

#### 3. GoogleSignInHelper

**Purpose:** Encapsulate Google Sign-In SDK logic

**Responsibilities:**
- Initialize GoogleSignInClient
- Configure sign-in options (request ID token)
- Launch Google account picker
- Handle sign-in result
- Extract ID token from GoogleSignInAccount
- Send ID token to backend for verification
- Return authentication result

**Configuration:**
```kotlin
GoogleSignInOptions.Builder(DEFAULT_SIGN_IN)
    .requestIdToken(SERVER_CLIENT_ID)
    .requestEmail()
    .build()
```

**Flow:**
```
Initialize client
    ↓
Launch sign-in intent
    ↓
User selects Google account
    ↓
Receive GoogleSignInAccount
    ↓
Extract ID token
    ↓
POST /api/auth/native/google { idToken }
    ↓
Receive { token, onboarded, user }
    ↓
Return to AuthActivity
```

---

#### 4. AuthManager

**Purpose:** Central authentication state management

**Responsibilities:**
- Provide single source of truth for auth state
- Coordinate between activities
- Manage token storage via UserPrefs
- Handle logout logic
- Provide helper methods for auth checks

**Public Methods:**
```kotlin
class AuthManager private constructor(context: Context) {
    
    // Check if user is logged in
    fun isLoggedIn(): Boolean
    
    // Save authentication data
    fun saveAuthData(token: String, onboarded: Boolean)
    
    // Get current auth token
    fun getAuthToken(): String?
    
    // Check if user has completed onboarding
    fun isOnboarded(): Boolean
    
    // Clear all auth data (logout)
    fun logout()
    
    companion object {
        // Singleton instance
        fun getInstance(context: Context): AuthManager
    }
}
```

**Singleton Pattern:**
- Single instance across app
- Thread-safe initialization
- Context-independent after creation

---

#### 5. MainActivity (Modified)

**Purpose:** WebView container with auth-aware routing

**New Responsibilities:**
- Check auth status before loading WebView
- Launch AuthActivity if not logged in
- Load WebView with appropriate route based on onboarded status
- Handle logout events from WebView

**Modified Logic:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // NEW: Check auth status FIRST
    val authManager = AuthManager.getInstance(this)
    if (!authManager.isLoggedIn()) {
        // Launch AuthActivity and finish
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
        return
    }
    
    // EXISTING: Continue with WebView setup
    setContentView(R.layout.activity_main)
    setupWebView()
    loadWebsite()
}
```

**Routing Logic:**
```kotlin
private fun loadWebsite() {
    val authManager = AuthManager.getInstance(this)
    val token = authManager.getAuthToken()
    val isOnboarded = authManager.isOnboarded()
    
    val baseUrl = "https://consistencygrid.netlify.app"
    val url = if (isOnboarded) {
        "$baseUrl/login?token=$token"  // → /dashboard
    } else {
        "$baseUrl/login?token=$token"  // → /onboarding
    }
    
    webView.loadUrl(url)
}
```

---

#### 6. UserPrefs (Modified)

**Purpose:** Persistent storage for auth data

**New Methods:**
```kotlin
// Save onboarded status
fun saveOnboardedStatus(onboarded: Boolean) {
    prefs.edit().putBoolean(KEY_ONBOARDED, onboarded).apply()
}

// Get onboarded status
fun isOnboarded(): Boolean {
    return prefs.getBoolean(KEY_ONBOARDED, false)
}
```

**Updated clear() Method:**
```kotlin
fun clear() {
    prefs.edit().clear().apply()
    // Clears: token, onboarded, theme, auto-update, etc.
}
```

---

#### 7. WebInterface (Modified)

**Purpose:** JavaScript bridge for WebView communication

**New Method:**
```kotlin
@JavascriptInterface
fun logout() {
    activity.runOnUiThread {
        // Clear all auth data
        val authManager = AuthManager.getInstance(activity)
        authManager.logout()
        
        // Launch AuthActivity
        val intent = Intent(activity, AuthActivity::class.java)
        activity.startActivity(intent)
        
        // Finish MainActivity
        activity.finish()
    }
}
```

**JavaScript Usage:**
```javascript
// From WebView settings page
Android.logout();
```

---

### Backend Components

#### 1. /api/auth/native/google

**Purpose:** Verify Google ID token and authenticate user

**Request:**
```json
POST /api/auth/native/google
Content-Type: application/json

{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6..."
}
```

**Response (Success):**
```json
{
  "success": true,
  "token": "pub_abc123xyz...",
  "onboarded": false,
  "user": {
    "id": "clx...",
    "email": "user@gmail.com",
    "name": "John Doe",
    "image": "https://..."
  }
}
```

**Response (Error):**
```json
{
  "success": false,
  "error": "Invalid ID token"
}
```

**Logic:**
1. Verify ID token with Google
2. Extract email from token payload
3. Check if user exists in database
4. If new user:
   - Create user with `onboarded = false`
   - Generate `publicToken`
5. If existing user:
   - Return existing `publicToken` and `onboarded` status
6. Return response

---

#### 2. /api/auth/native/email-signup

**Purpose:** Register new user with email/password

**Request:**
```json
POST /api/auth/native/email-signup
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123",
  "name": "John Doe"
}
```

**Response (Success):**
```json
{
  "success": true,
  "token": "pub_abc123xyz...",
  "onboarded": false,
  "user": {
    "id": "clx...",
    "email": "user@example.com",
    "name": "John Doe"
  }
}
```

**Response (Error):**
```json
{
  "success": false,
  "error": "Email already exists"
}
```

**Logic:**
1. Validate email format
2. Validate password strength
3. Check if email already exists
4. Hash password with bcrypt
5. Create user with `onboarded = false`
6. Generate `publicToken`
7. Return response

---

#### 3. /api/auth/native/email-login

**Purpose:** Authenticate existing user with email/password

**Request:**
```json
POST /api/auth/native/email-login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```

**Response (Success):**
```json
{
  "success": true,
  "token": "pub_abc123xyz...",
  "onboarded": true,
  "user": {
    "id": "clx...",
    "email": "user@example.com",
    "name": "John Doe"
  }
}
```

**Response (Error):**
```json
{
  "success": false,
  "error": "Invalid credentials"
}
```

**Logic:**
1. Find user by email
2. Verify password with bcrypt
3. Return `publicToken` and `onboarded` status
4. Return response

---

## Data Flow Diagrams

### New User Flow (Google Sign-In)

```
User opens app
    ↓
AuthActivity shown
    ↓
User taps "Continue with Google"
    ↓
GoogleSignInHelper.signIn()
    ↓
Native Google account picker
    ↓
User selects account
    ↓
GoogleSignInHelper receives GoogleSignInAccount
    ↓
Extract ID token
    ↓
POST /api/auth/native/google
    {
        idToken: "eyJ..."
    }
    ↓
Backend verifies token with Google
    ↓
Backend checks database
    ↓
User NOT found → Create new user
    {
        email: "user@gmail.com",
        name: "John Doe",
        onboarded: false,
        publicToken: "pub_abc123..."
    }
    ↓
Backend returns
    {
        token: "pub_abc123...",
        onboarded: false
    }
    ↓
AuthActivity receives response
    ↓
AuthManager.saveAuthData(token, onboarded=false)
    ↓
UserPrefs.saveToken(token)
UserPrefs.saveOnboardedStatus(false)
    ↓
Launch MainActivity
    ↓
MainActivity.onCreate()
    ↓
Check AuthManager.isLoggedIn() → true
Check AuthManager.isOnboarded() → false
    ↓
Load WebView: /login?token=pub_abc123...
    ↓
NextAuth token-login provider validates token
    ↓
Session created
    ↓
Dashboard checks session.user.onboarded → false
    ↓
Redirect to /onboarding
    ↓
User completes onboarding
    ↓
POST /api/onboarding/complete
    ↓
Database: onboarded = true
    ↓
Redirect to /dashboard
```

### Existing User Flow (Auto-Login)

```
User opens app
    ↓
MainActivity.onCreate()
    ↓
Check AuthManager.isLoggedIn() → true
    ↓
Skip AuthActivity
    ↓
Check AuthManager.isOnboarded() → true
    ↓
Load WebView: /login?token=pub_abc123...
    ↓
NextAuth validates token
    ↓
Session created
    ↓
Dashboard checks session.user.onboarded → true
    ↓
Show dashboard content
```

### Logout Flow

```
User in WebView dashboard
    ↓
Clicks "Logout" in settings
    ↓
JavaScript: Android.logout()
    ↓
WebInterface.logout() called
    ↓
AuthManager.logout()
    ↓
UserPrefs.clear()
    - Token cleared
    - Onboarded status cleared
    - Theme preferences cleared
    ↓
Launch AuthActivity
    ↓
Finish MainActivity
    ↓
User sees native auth screen
```

---

## Security Considerations

### Token Storage

**Current:** SharedPreferences (unencrypted)

**Recommended for Production:**
```kotlin
// Use EncryptedSharedPreferences
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

### Google Sign-In Security

**ID Token Verification:**
- Backend MUST verify token signature with Google
- Check token audience matches your client ID
- Check token expiration
- Extract email from verified token

**Never trust client-provided data without verification**

### API Security

**HTTPS Only:**
- All API endpoints use HTTPS
- Certificate pinning (optional, for extra security)

**Rate Limiting:**
- Prevent brute force attacks on email/password login
- Limit signup attempts per IP

**Input Validation:**
- Sanitize all user inputs
- Validate email format
- Enforce password strength

---

## Performance Considerations

### App Startup Time

**Current Flow:**
```
App Launch → MainActivity → WebView → Page Load
≈ 2-3 seconds
```

**New Flow (Logged In):**
```
App Launch → MainActivity → Auth Check → WebView → Page Load
≈ 2-3 seconds + 50ms auth check
```

**New Flow (Logged Out):**
```
App Launch → MainActivity → Auth Check → AuthActivity
≈ 500ms (faster, no WebView load)
```

**Net Impact:** Negligible for logged-in users, faster for logged-out users

### APK Size Impact

- Google Play Services Auth: ~500KB
- Native UI layouts: ~50KB
- Total: ~550KB increase (acceptable)

---

## Comparison: Before vs After

| Aspect | Before (Chrome Custom Tabs) | After (Native Auth) |
|--------|------------------------------|---------------------|
| **User Experience** | Browser context switch | Fully native |
| **Google Sign-In** | OAuth in browser | Native account picker |
| **Email Auth** | Not supported natively | Native forms |
| **Control** | Limited (browser-dependent) | Full control |
| **Speed** | Slower (browser overhead) | Faster (direct API) |
| **Debugging** | Difficult (browser logs) | Easy (native logs) |
| **Customization** | None | Full UI customization |
| **Reliability** | Browser-dependent | App-controlled |

---

## Summary

### Key Improvements

✅ **Native Experience**
- No browser involvement
- Seamless authentication
- System account picker for Google

✅ **More Auth Methods**
- Google Sign-In (native)
- Email/Password signup
- Email/Password login

✅ **Better Control**
- Custom UI/UX
- Better error handling
- Easier debugging

✅ **Future-Ready**
- Can add biometric auth
- Can add more social logins
- Independent of browser changes

### Architecture Principles

1. **Separation of Concerns**
   - Auth layer handles authentication
   - WebView layer handles content
   - No overlap

2. **Single Responsibility**
   - Each component has one clear purpose
   - AuthManager coordinates auth state
   - MainActivity manages WebView

3. **Additive Changes**
   - No existing code deleted
   - New components added
   - Existing logic preserved

---

## Next Document

→ **04-user-flows.md** - Detailed user journey scenarios
