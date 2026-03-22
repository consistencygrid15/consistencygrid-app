# Android Changes - Components & Implementation

## Document Information

- **Version:** 1.0
- **Date:** 2026-02-04
- **Purpose:** Specify all Android code changes required for native authentication

---

## File Change Summary

### New Files (8)

**Activities:**
1. `android/app/src/main/java/com/consistencygridwallpaper/auth/AuthActivity.kt`
2. `android/app/src/main/java/com/consistencygridwallpaper/auth/EmailAuthActivity.kt`

**Helpers:**
3. `android/app/src/main/java/com/consistencygridwallpaper/auth/GoogleSignInHelper.kt`
4. `android/app/src/main/java/com/consistencygridwallpaper/auth/AuthManager.kt`

**Layouts:**
5. `android/app/src/main/res/layout/activity_auth.xml`
6. `android/app/src/main/res/layout/activity_email_auth.xml`

**Resources:**
7. `android/app/src/main/res/values/styles_auth.xml`
8. `android/app/src/main/res/drawable/google_logo.xml`

### Modified Files (5)

1. `android/app/src/main/java/com/consistencygridwallpaper/MainActivity.kt`
2. `android/app/src/main/java/com/consistencygridwallpaper/storage/UserPrefs.kt`
3. `android/app/src/main/java/com/consistencygridwallpaper/bridge/WebInterface.kt`
4. `android/app/build.gradle`
5. `android/app/src/main/AndroidManifest.xml`

---

## New File Specifications

### 1. AuthActivity.kt

**Location:** `android/app/src/main/java/com/consistencygridwallpaper/auth/AuthActivity.kt`

**Purpose:** Main authentication screen for logged-out users

**Complete Implementation:**

```kotlin
package com.consistencygridwallpaper.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.consistencygridwallpaper.MainActivity
import com.consistencygridwallpaper.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

class AuthActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AuthActivity"
    }

    private lateinit var btnGoogleSignIn: Button
    private lateinit var btnEmailSignup: Button
    private lateinit var btnEmailLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    private lateinit var authManager: AuthManager
    private lateinit var googleSignInHelper: GoogleSignInHelper

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            googleSignInHelper.handleSignInResult(account)
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign-in failed", e)
            showError("Google sign-in failed. Please try again.")
            hideLoading()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if already logged in
        authManager = AuthManager.getInstance(this)
        if (authManager.isLoggedIn()) {
            Log.d(TAG, "User already logged in, redirecting to MainActivity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_auth)

        // Initialize views
        btnGoogleSignIn = findViewById(R.id.btn_google_signin)
        btnEmailSignup = findViewById(R.id.btn_email_signup)
        btnEmailLogin = findViewById(R.id.btn_email_login)
        progressBar = findViewById(R.id.progress_bar)
        tvError = findViewById(R.id.tv_error)

        // Initialize Google Sign-In helper
        googleSignInHelper = GoogleSignInHelper(
            activity = this,
            onSuccess = { token, onboarded, user ->
                handleAuthSuccess(token, onboarded)
            },
            onError = { error ->
                showError(error)
                hideLoading()
            }
        )

        // Set click listeners
        btnGoogleSignIn.setOnClickListener {
            showLoading()
            val signInIntent = googleSignInHelper.getSignInIntent()
            googleSignInLauncher.launch(signInIntent)
        }

        btnEmailSignup.setOnClickListener {
            val intent = Intent(this, EmailAuthActivity::class.java)
            intent.putExtra("MODE", "SIGNUP")
            startActivityForResult(intent, REQUEST_EMAIL_AUTH)
        }

        btnEmailLogin.setOnClickListener {
            val intent = Intent(this, EmailAuthActivity::class.java)
            intent.putExtra("MODE", "LOGIN")
            startActivityForResult(intent, REQUEST_EMAIL_AUTH)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_EMAIL_AUTH && resultCode == RESULT_OK) {
            val token = data?.getStringExtra("token") ?: return
            val onboarded = data.getBooleanExtra("onboarded", false)
            handleAuthSuccess(token, onboarded)
        }
    }

    private fun handleAuthSuccess(token: String, onboarded: Boolean) {
        Log.d(TAG, "Authentication successful, token received")
        
        // Save auth data
        authManager.saveAuthData(token, onboarded)
        
        // Navigate to MainActivity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        btnGoogleSignIn.isEnabled = false
        btnEmailSignup.isEnabled = false
        btnEmailLogin.isEnabled = false
        tvError.visibility = View.GONE
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        btnGoogleSignIn.isEnabled = true
        btnEmailSignup.isEnabled = true
        btnEmailLogin.isEnabled = true
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    companion object {
        private const val REQUEST_EMAIL_AUTH = 1001
    }
}
```

---

### 2. GoogleSignInHelper.kt

**Location:** `android/app/src/main/java/com/consistencygridwallpaper/auth/GoogleSignInHelper.kt`

**Purpose:** Encapsulate Google Sign-In SDK logic

**Complete Implementation:**

```kotlin
package com.consistencygridwallpaper.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class GoogleSignInHelper(
    private val activity: Activity,
    private val onSuccess: (token: String, onboarded: Boolean, user: JSONObject) -> Unit,
    private val onError: (error: String) -> Unit
) {
    companion object {
        private const val TAG = "GoogleSignInHelper"
        private const val SERVER_CLIENT_ID = "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
        private const val API_URL = "https://consistencygrid.netlify.app/api/auth/native/google"
    }

    private val googleSignInClient: GoogleSignInClient

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(SERVER_CLIENT_ID)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(activity, gso)
    }

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account == null) {
            onError("Failed to get Google account")
            return
        }

        val idToken = account.idToken
        if (idToken == null) {
            onError("Failed to get ID token")
            return
        }

        Log.d(TAG, "ID token obtained, verifying with backend")
        verifyWithBackend(idToken)
    }

    private fun verifyWithBackend(idToken: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                
                val json = JSONObject()
                json.put("idToken", idToken)
                
                val body = json.toString().toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        
                        if (jsonResponse.getBoolean("success")) {
                            val token = jsonResponse.getString("token")
                            val onboarded = jsonResponse.getBoolean("onboarded")
                            val user = jsonResponse.getJSONObject("user")
                            
                            onSuccess(token, onboarded, user)
                        } else {
                            val error = jsonResponse.optString("error", "Authentication failed")
                            onError(error)
                        }
                    } else {
                        onError("Server error: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backend verification failed", e)
                withContext(Dispatchers.Main) {
                    onError("Network error. Please check your connection.")
                }
            }
        }
    }
}
```

---

### 3. AuthManager.kt

**Location:** `android/app/src/main/java/com/consistencygridwallpaper/auth/AuthManager.kt`

**Purpose:** Central authentication state management

**Complete Implementation:**

```kotlin
package com.consistencygridwallpaper.auth

import android.content.Context
import com.consistencygridwallpaper.storage.UserPrefs

class AuthManager private constructor(context: Context) {

    private val userPrefs: UserPrefs = UserPrefs(context.applicationContext)

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return !userPrefs.getToken().isNullOrBlank()
    }

    /**
     * Save authentication data
     */
    fun saveAuthData(token: String, onboarded: Boolean) {
        userPrefs.saveToken(token)
        userPrefs.saveOnboardedStatus(onboarded)
    }

    /**
     * Get current auth token
     */
    fun getAuthToken(): String? {
        return userPrefs.getToken()
    }

    /**
     * Check if user has completed onboarding
     */
    fun isOnboarded(): Boolean {
        return userPrefs.isOnboarded()
    }

    /**
     * Clear all auth data (logout)
     */
    fun logout() {
        userPrefs.clear()
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager(context).also { INSTANCE = it }
            }
        }
    }
}
```

---

## Modified File Specifications

### 1. MainActivity.kt

**Location:** `android/app/src/main/java/com/consistencygridwallpaper/MainActivity.kt`

**Changes Required:**

**Change 1: Add auth check in onCreate() (Lines 66-99)**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // NEW: Check auth status FIRST
    val authManager = AuthManager.getInstance(this)
    if (!authManager.isLoggedIn()) {
        Log.d(TAG, "User not logged in, launching AuthActivity")
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
        return
    }

    // EXISTING: Continue with normal flow
    val splashScreen = installSplashScreen()
    splashScreen.setKeepOnScreenCondition { !isDataLoaded }
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    
    // ... rest of existing code
}
```

**Complexity:** Low - Single conditional check at the start

---

### 2. UserPrefs.kt

**Location:** `android/app/src/main/java/com/consistencygridwallpaper/storage/UserPrefs.kt`

**Changes Required:**

**Add new constant (Line 36):**

```kotlin
const val KEY_ONBOARDED = "onboarded"
```

**Add new methods (After line 175):**

```kotlin
/**
 * Save onboarded status
 */
fun saveOnboardedStatus(onboarded: Boolean) {
    prefs.edit().putBoolean(KEY_ONBOARDED, onboarded).apply()
}

/**
 * Get onboarded status
 */
fun isOnboarded(): Boolean {
    return prefs.getBoolean(KEY_ONBOARDED, false)
}
```

**Complexity:** Minimal - Two simple getter/setter methods

---

### 3. WebInterface.kt

**Location:** `android/app/src/main/java/com/consistencygridwallpaper/bridge/WebInterface.kt`

**Changes Required:**

**Add logout method (End of class):**

```kotlin
@JavascriptInterface
fun logout() {
    Log.d(TAG, "Logout called from WebView")
    
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

**Complexity:** Low - Single method addition

---

### 4. build.gradle

**Location:** `android/app/build.gradle`

**Changes Required:**

**Add dependency (After line 67):**

```gradle
dependencies {
    // ... existing dependencies
    
    // Google Sign-In (Native Android SDK)
    implementation 'com.google.android.gms:play-services-auth:20.7.0'
}
```

**Complexity:** Trivial - Single line addition

---

### 5. AndroidManifest.xml

**Location:** `android/app/src/main/AndroidManifest.xml`

**Changes Required:**

**Add activities (After line 71, before closing </application>):**

```xml
<!-- Auth Activities -->
<activity
    android:name=".auth.AuthActivity"
    android:exported="false"
    android:theme="@style/AppTheme" />

<activity
    android:name=".auth.EmailAuthActivity"
    android:exported="false"
    android:theme="@style/AppTheme" />
```

**Complexity:** Low - Two activity declarations

---

## Layout Files

### activity_auth.xml

**Location:** `android/app/src/main/res/layout/activity_auth.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#FFFFFF"
    android:padding="24dp">

    <!-- App Logo -->
    <ImageView
        android:id="@+id/iv_logo"
        android:layout_width="120dp"
        android:layout_height="120dp"
        android:src="@drawable/app_logo"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginTop="80dp" />

    <!-- Welcome Text -->
    <TextView
        android:id="@+id/tv_welcome"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Welcome to\nConsistencyGrid"
        android:textSize="28sp"
        android:textColor="#000000"
        android:textAlignment="center"
        android:textStyle="bold"
        app:layout_constraintTop_toBottomOf="@id/iv_logo"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginTop="32dp" />

    <!-- Google Sign-In Button -->
    <Button
        android:id="@+id/btn_google_signin"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="Continue with Google"
        android:textColor="#FFFFFF"
        android:background="@drawable/btn_google"
        app:layout_constraintTop_toBottomOf="@id/tv_welcome"
        android:layout_marginTop="48dp" />

    <!-- Email Signup Button -->
    <Button
        android:id="@+id/btn_email_signup"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="Sign up with Email"
        android:textColor="#FFFFFF"
        android:background="@drawable/btn_primary"
        app:layout_constraintTop_toBottomOf="@id/btn_google_signin"
        android:layout_marginTop="16dp" />

    <!-- Email Login Button -->
    <Button
        android:id="@+id/btn_email_login"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="Log in with Email"
        android:textColor="#FF7A00"
        android:background="@drawable/btn_outline"
        app:layout_constraintTop_toBottomOf="@id/btn_email_signup"
        android:layout_marginTop="16dp" />

    <!-- Loading Indicator -->
    <ProgressBar
        android:id="@+id/progress_bar"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:visibility="gone"
        app:layout_constraintTop_toBottomOf="@id/btn_email_login"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginTop="24dp" />

    <!-- Error Message -->
    <TextView
        android:id="@+id/tv_error"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="#FF0000"
        android:textAlignment="center"
        android:visibility="gone"
        app:layout_constraintTop_toBottomOf="@id/progress_bar"
        android:layout_marginTop="16dp" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## Summary

### New Components

✅ **AuthActivity** - Main auth screen  
✅ **EmailAuthActivity** - Email/password forms  
✅ **GoogleSignInHelper** - Google Sign-In logic  
✅ **AuthManager** - State management  

### Modified Components

✅ **MainActivity** - Auth check added  
✅ **UserPrefs** - Onboarded flag storage  
✅ **WebInterface** - Logout method  
✅ **build.gradle** - Google Sign-In dependency  
✅ **AndroidManifest.xml** - Activity declarations  

### All Changes Are Additive

**No existing code deleted**  
**No existing logic modified**  
**Fully reversible**  

---

## Next Document

→ **07-migration-strategy.md** - Deployment and rollback plan
