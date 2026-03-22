# User Flows - Detailed Journey Scenarios

## Document Information

- **Version:** 1.0
- **Date:** 2026-02-04
- **Purpose:** Document all user journey scenarios in the native authentication system

---

## Flow 1: New User - Google Sign-In

### Scenario
First-time user downloads the app and signs in with Google account.

### Prerequisites
- App installed on Android device
- User has Google account on device
- Internet connection available

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────┐
│ STEP 1: App Launch                                          │
└─────────────────────────────────────────────────────────────┘

User taps app icon
    ↓
Splash screen shows (Android 12+ splash)
    ↓
MainActivity.onCreate() executes
    ↓
AuthManager.isLoggedIn() → false (no token)
    ↓
Launch AuthActivity
Finish MainActivity
    ↓
AuthActivity displays:
    - App logo
    - "Welcome to ConsistencyGrid"
    - [Continue with Google] button
    - [Sign up with Email] button
    - [Log in with Email] button

┌─────────────────────────────────────────────────────────────┐
│ STEP 2: User Selects Google Sign-In                        │
└─────────────────────────────────────────────────────────────┘

User taps "Continue with Google"
    ↓
AuthActivity shows loading indicator
    ↓
GoogleSignInHelper.signIn() called
    ↓
Google Sign-In SDK launches
    ↓
Native Android account picker appears:
    ┌──────────────────────────────────┐
    │  Choose an account               │
    │                                  │
    │  ● john.doe@gmail.com           │
    │    John Doe                      │
    │                                  │
    │  ● jane.smith@gmail.com         │
    │    Jane Smith                    │
    │                                  │
    │  [Add another account]           │
    └──────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ STEP 3: Account Selection                                  │
└─────────────────────────────────────────────────────────────┘

User selects john.doe@gmail.com
    ↓
Google Sign-In SDK returns GoogleSignInAccount
    ↓
GoogleSignInHelper extracts ID token
    idToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6..."
    ↓
GoogleSignInHelper calls backend:
    POST https://consistencygrid.netlify.app/api/auth/native/google
    {
        "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6..."
    }

┌─────────────────────────────────────────────────────────────┐
│ STEP 4: Backend Verification                               │
└─────────────────────────────────────────────────────────────┘

Backend receives request
    ↓
Verify ID token with Google
    - Check signature
    - Check expiration
    - Check audience
    ↓
Extract email from token: john.doe@gmail.com
    ↓
Check database for user:
    SELECT * FROM User WHERE email = 'john.doe@gmail.com'
    ↓
User NOT found (new user)
    ↓
Create new user:
    INSERT INTO User (
        email: 'john.doe@gmail.com',
        name: 'John Doe',
        image: 'https://lh3.googleusercontent.com/...',
        publicToken: 'pub_a1b2c3d4e5f6...',
        onboarded: false,
        emailVerified: NOW()
    )
    ↓
Return response:
    {
        "success": true,
        "token": "pub_a1b2c3d4e5f6...",
        "onboarded": false,
        "user": {
            "id": "clx123...",
            "email": "john.doe@gmail.com",
            "name": "John Doe",
            "image": "https://..."
        }
    }

┌─────────────────────────────────────────────────────────────┐
│ STEP 5: Save Authentication Data                           │
└─────────────────────────────────────────────────────────────┘

AuthActivity receives response
    ↓
AuthManager.saveAuthData(
    token = "pub_a1b2c3d4e5f6...",
    onboarded = false
)
    ↓
UserPrefs.saveToken("pub_a1b2c3d4e5f6...")
UserPrefs.saveOnboardedStatus(false)
    ↓
Data saved to SharedPreferences:
    KEY_TOKEN = "pub_a1b2c3d4e5f6..."
    KEY_ONBOARDED = false

┌─────────────────────────────────────────────────────────────┐
│ STEP 6: Launch Main Activity                               │
└─────────────────────────────────────────────────────────────┘

AuthActivity launches MainActivity
    ↓
AuthActivity.finish()
    ↓
MainActivity.onCreate() executes
    ↓
AuthManager.isLoggedIn() → true (token exists)
    ↓
Skip AuthActivity
    ↓
Setup WebView
    ↓
Load website:
    url = "https://consistencygrid.netlify.app/login?token=pub_a1b2c3d4e5f6...&canvasWidth=1080&canvasHeight=2400"
    ↓
WebView loads URL

┌─────────────────────────────────────────────────────────────┐
│ STEP 7: Web Session Creation                               │
└─────────────────────────────────────────────────────────────┘

NextAuth receives /login?token=pub_a1b2c3d4e5f6...
    ↓
Token-login CredentialsProvider triggered
    ↓
Find user by publicToken:
    SELECT * FROM User WHERE publicToken = 'pub_a1b2c3d4e5f6...'
    ↓
User found
    ↓
Create JWT with user data:
    {
        id: "clx123...",
        email: "john.doe@gmail.com",
        onboarded: false
    }
    ↓
Set session cookie
    ↓
Create session object:
    {
        user: {
            id: "clx123...",
            email: "john.doe@gmail.com",
            name: "John Doe",
            onboarded: false
        }
    }

┌─────────────────────────────────────────────────────────────┐
│ STEP 8: Onboarding Redirect                                │
└─────────────────────────────────────────────────────────────┘

/login page checks session
    ↓
Session exists → Redirect to /dashboard
    ↓
/dashboard page (server component) executes:
    const session = await getServerSession()
    if (!session?.user?.onboarded) {
        redirect('/onboarding')
    }
    ↓
onboarded = false → Redirect to /onboarding
    ↓
WebView loads /onboarding page

┌─────────────────────────────────────────────────────────────┐
│ STEP 9: Onboarding Flow                                    │
└─────────────────────────────────────────────────────────────┘

Onboarding page displays:
    ┌──────────────────────────────────┐
    │  Step 1/4: Personalization       │
    │                                  │
    │  What's your name?               │
    │  [John Doe            ]          │
    │                                  │
    │  When were you born?             │
    │  [1990-01-15          ]          │
    │                                  │
    │  Life expectancy (years)?        │
    │  [85                  ]          │
    │                                  │
    │  [Next →]                        │
    └──────────────────────────────────┘

User completes all 4 steps:
    1. Personalization (name, DOB, life expectancy)
    2. Habits (select habits to track)
    3. Theme (choose wallpaper theme)
    4. Welcome (final confirmation)
    ↓
User clicks "Complete Onboarding"
    ↓
POST /api/onboarding/complete
    {
        name: "John Doe",
        dob: "1990-01-15",
        lifeExpectancyYears: 85,
        habits: ["Exercise", "Read"],
        theme: "dark-minimal"
    }
    ↓
Backend saves data:
    - Create WallpaperSettings
    - Create Habit records
    - Update User.onboarded = true
    ↓
Response: { success: true }
    ↓
Redirect to /dashboard

┌─────────────────────────────────────────────────────────────┐
│ STEP 10: Dashboard Access                                  │
└─────────────────────────────────────────────────────────────┘

/dashboard page checks session
    ↓
session.user.onboarded = true (updated in DB)
    ↓
Show dashboard content:
    - Life calendar wallpaper
    - Habit tracking
    - Stats and progress
    ↓
User sees full dashboard
```

### Expected Outcome
✅ User successfully authenticated  
✅ User completed onboarding  
✅ User has access to dashboard  
✅ Token persisted for future sessions  

---

## Flow 2: New User - Email Signup

### Scenario
First-time user signs up with email and password.

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────┐
│ STEP 1: App Launch                                          │
└─────────────────────────────────────────────────────────────┘

(Same as Google Sign-In flow)
AuthActivity displays

┌─────────────────────────────────────────────────────────────┐
│ STEP 2: User Selects Email Signup                          │
└─────────────────────────────────────────────────────────────┘

User taps "Sign up with Email"
    ↓
Launch EmailAuthActivity (mode: SIGNUP)
    ↓
EmailAuthActivity displays:
    ┌──────────────────────────────────┐
    │  Create Account                  │
    │                                  │
    │  Full Name                       │
    │  [                    ]          │
    │                                  │
    │  Email                           │
    │  [                    ]          │
    │                                  │
    │  Password                        │
    │  [                    ] 👁       │
    │                                  │
    │  [Create Account]                │
    │                                  │
    │  Already have an account?        │
    │  [Log in]                        │
    └──────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ STEP 3: User Fills Form                                    │
└─────────────────────────────────────────────────────────────┘

User enters:
    Name: "Jane Smith"
    Email: "jane.smith@example.com"
    Password: "SecurePass123"
    ↓
User taps "Create Account"
    ↓
EmailAuthActivity validates:
    ✓ Name: length >= 2
    ✓ Email: valid format
    ✓ Password: length >= 8
    ↓
All valid → Show loading indicator
    ↓
POST /api/auth/native/email-signup
    {
        "name": "Jane Smith",
        "email": "jane.smith@example.com",
        "password": "SecurePass123"
    }

┌─────────────────────────────────────────────────────────────┐
│ STEP 4: Backend Processing                                 │
└─────────────────────────────────────────────────────────────┘

Backend receives request
    ↓
Validate email format
    ↓
Check if email exists:
    SELECT * FROM User WHERE email = 'jane.smith@example.com'
    ↓
Email NOT found (good, can proceed)
    ↓
Hash password:
    bcrypt.hash("SecurePass123", 10)
    → "$2a$10$N9qo8uLOickgx2ZMRZoMye..."
    ↓
Create user:
    INSERT INTO User (
        email: 'jane.smith@example.com',
        name: 'Jane Smith',
        password: '$2a$10$N9qo8uLOickgx2ZMRZoMye...',
        publicToken: 'pub_x7y8z9...',
        onboarded: false
    )
    ↓
Return response:
    {
        "success": true,
        "token": "pub_x7y8z9...",
        "onboarded": false,
        "user": {
            "id": "clx456...",
            "email": "jane.smith@example.com",
            "name": "Jane Smith"
        }
    }

┌─────────────────────────────────────────────────────────────┐
│ STEP 5-10: Same as Google Sign-In                          │
└─────────────────────────────────────────────────────────────┘

(Identical to Google Sign-In flow from Step 5 onwards)
- Save token
- Launch MainActivity
- Load WebView
- Redirect to onboarding
- Complete onboarding
- Access dashboard
```

### Expected Outcome
✅ User successfully registered  
✅ Password securely hashed  
✅ User completed onboarding  
✅ User has access to dashboard  

---

## Flow 3: Existing User - Auto Login

### Scenario
User who has already signed up opens the app again.

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────┐
│ STEP 1: App Launch                                          │
└─────────────────────────────────────────────────────────────┘

User taps app icon
    ↓
MainActivity.onCreate() executes
    ↓
AuthManager.isLoggedIn() checks:
    UserPrefs.getToken()
    → "pub_a1b2c3d4e5f6..." (exists)
    ↓
isLoggedIn() → true
    ↓
Skip AuthActivity (don't launch)
    ↓
Continue with MainActivity setup

┌─────────────────────────────────────────────────────────────┐
│ STEP 2: Load WebView with Token                            │
└─────────────────────────────────────────────────────────────┘

MainActivity.loadWebsite() executes
    ↓
Get token from UserPrefs:
    token = "pub_a1b2c3d4e5f6..."
    ↓
Get onboarded status:
    onboarded = true (from UserPrefs)
    ↓
Construct URL:
    url = "https://consistencygrid.netlify.app/login?token=pub_a1b2c3d4e5f6...&canvasWidth=1080&canvasHeight=2400"
    ↓
WebView.loadUrl(url)

┌─────────────────────────────────────────────────────────────┐
│ STEP 3: Web Session Restoration                            │
└─────────────────────────────────────────────────────────────┘

NextAuth token-login provider validates token
    ↓
Find user by publicToken
    ↓
User found with onboarded = true
    ↓
Create session
    ↓
Redirect to /dashboard

┌─────────────────────────────────────────────────────────────┐
│ STEP 4: Dashboard Display                                  │
└─────────────────────────────────────────────────────────────┘

/dashboard checks session.user.onboarded → true
    ↓
Show dashboard content immediately
    ↓
User sees their wallpaper, habits, stats
```

### Expected Outcome
✅ Instant login (no auth screen shown)  
✅ Direct dashboard access  
✅ Session persisted across app restarts  
✅ Fast app startup  

---

## Flow 4: Email Login (Existing User)

### Scenario
User who signed up with email logs in again after logout.

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────┐
│ STEP 1: App Launch After Logout                            │
└─────────────────────────────────────────────────────────────┘

User opens app
    ↓
MainActivity checks AuthManager.isLoggedIn() → false
    ↓
Launch AuthActivity
    ↓
AuthActivity displays

┌─────────────────────────────────────────────────────────────┐
│ STEP 2: User Selects Email Login                           │
└─────────────────────────────────────────────────────────────┘

User taps "Log in with Email"
    ↓
Launch EmailAuthActivity (mode: LOGIN)
    ↓
EmailAuthActivity displays:
    ┌──────────────────────────────────┐
    │  Welcome Back                    │
    │                                  │
    │  Email                           │
    │  [jane.smith@example.com]        │
    │                                  │
    │  Password                        │
    │  [                    ] 👁       │
    │                                  │
    │  [Log In]                        │
    │                                  │
    │  Don't have an account?          │
    │  [Sign up]                       │
    └──────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ STEP 3: User Enters Credentials                            │
└─────────────────────────────────────────────────────────────┘

User enters:
    Email: "jane.smith@example.com"
    Password: "SecurePass123"
    ↓
User taps "Log In"
    ↓
EmailAuthActivity validates:
    ✓ Email: not empty
    ✓ Password: not empty
    ↓
Show loading indicator
    ↓
POST /api/auth/native/email-login
    {
        "email": "jane.smith@example.com",
        "password": "SecurePass123"
    }

┌─────────────────────────────────────────────────────────────┐
│ STEP 4: Backend Verification                               │
└─────────────────────────────────────────────────────────────┘

Backend receives request
    ↓
Find user by email:
    SELECT * FROM User WHERE email = 'jane.smith@example.com'
    ↓
User found
    ↓
Verify password:
    bcrypt.compare("SecurePass123", user.password)
    → true (match)
    ↓
Return response:
    {
        "success": true,
        "token": "pub_x7y8z9...",
        "onboarded": true,
        "user": {
            "id": "clx456...",
            "email": "jane.smith@example.com",
            "name": "Jane Smith"
        }
    }

┌─────────────────────────────────────────────────────────────┐
│ STEP 5: Save and Continue                                  │
└─────────────────────────────────────────────────────────────┘

Save token and onboarded status
    ↓
Launch MainActivity
    ↓
Load WebView with token
    ↓
Since onboarded = true, go directly to dashboard
```

### Expected Outcome
✅ Successful login with email/password  
✅ Direct dashboard access (skip onboarding)  
✅ Token saved for future auto-login  

---

## Flow 5: Logout

### Scenario
User logs out from the app.

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────┐
│ STEP 1: User in Dashboard                                  │
└─────────────────────────────────────────────────────────────┘

User navigates to Settings page in WebView
    ↓
Settings page displays logout button
    ↓
User taps "Logout"

┌─────────────────────────────────────────────────────────────┐
│ STEP 2: JavaScript Bridge Call                             │
└─────────────────────────────────────────────────────────────┘

Settings page JavaScript executes:
    Android.logout()
    ↓
WebInterface.logout() method called
    ↓
Runs on UI thread:
    activity.runOnUiThread {
        AuthManager.getInstance(activity).logout()
    }

┌─────────────────────────────────────────────────────────────┐
│ STEP 3: Clear Authentication Data                          │
└─────────────────────────────────────────────────────────────┘

AuthManager.logout() executes:
    UserPrefs.clear()
    ↓
SharedPreferences cleared:
    - KEY_TOKEN removed
    - KEY_ONBOARDED removed
    - KEY_THEME_COLOR removed
    - KEY_IS_DARK_MODE removed
    - KEY_AUTO_UPDATE removed
    ↓
All user data cleared from local storage

┌─────────────────────────────────────────────────────────────┐
│ STEP 4: Return to Auth Screen                              │
└─────────────────────────────────────────────────────────────┘

WebInterface.logout() continues:
    val intent = Intent(activity, AuthActivity::class.java)
    activity.startActivity(intent)
    activity.finish()
    ↓
AuthActivity launches
MainActivity finishes (destroyed)
    ↓
User sees native auth screen:
    - "Welcome to ConsistencyGrid"
    - [Continue with Google]
    - [Sign up with Email]
    - [Log in with Email]
```

### Expected Outcome
✅ All local data cleared  
✅ User logged out  
✅ Auth screen displayed  
✅ User can log in again  

---

## Flow 6: Error Scenarios

### Scenario 6a: Invalid Email/Password

```
User enters wrong password
    ↓
POST /api/auth/native/email-login
    ↓
Backend: bcrypt.compare() → false
    ↓
Return error:
    {
        "success": false,
        "error": "Invalid credentials"
    }
    ↓
EmailAuthActivity displays error:
    "Invalid email or password. Please try again."
    ↓
User can retry
```

### Scenario 6b: Network Failure

```
User taps "Continue with Google"
    ↓
GoogleSignInHelper gets ID token
    ↓
POST /api/auth/native/google
    ↓
Network timeout / no internet
    ↓
Catch error in AuthActivity
    ↓
Display error:
    "Network error. Please check your connection and try again."
    ↓
User can retry
```

### Scenario 6c: Duplicate Email Signup

```
User tries to sign up with existing email
    ↓
POST /api/auth/native/email-signup
    ↓
Backend: Email already exists
    ↓
Return error:
    {
        "success": false,
        "error": "Email already exists"
    }
    ↓
EmailAuthActivity displays error:
    "This email is already registered. Please log in instead."
    ↓
Show "Log in" button
```

---

## Summary

### All Flows Covered

✅ New user - Google Sign-In  
✅ New user - Email signup  
✅ Existing user - Auto login  
✅ Existing user - Email login  
✅ Logout  
✅ Error scenarios  

### Key Principles

1. **Seamless Experience:** Minimal steps, fast authentication
2. **Clear Feedback:** Loading states, error messages
3. **Persistent Sessions:** Auto-login on app restart
4. **Graceful Errors:** User-friendly error messages

---

## Next Document

→ **05-backend-changes.md** - Detailed backend API specifications
