# Implementation Plan - Step-by-Step Guide

## Document Information

- **Version:** 1.0
- **Date:** 2026-02-04
- **Purpose:** Provide detailed step-by-step implementation instructions

---

## Implementation Phases

### Phase 1: Backend API Development (Week 1, Days 1-3)

#### Step 1.1: Install Dependencies

```bash
cd d:/startup/consistencygrid
npm install google-auth-library bcryptjs
```

**Verification:**
```bash
npm list google-auth-library bcryptjs
```

---

#### Step 1.2: Create Token Generation Utility

**File:** `src/lib/token.js`

```javascript
import crypto from 'crypto';

export function generatePublicToken() {
  const randomBytes = crypto.randomBytes(32);
  const token = `pub_${randomBytes.toString('hex')}`;
  return token;
}
```

**Test:**
```javascript
console.log(generatePublicToken());
// Output: pub_a1b2c3d4e5f6...
```

---

#### Step 1.3: Create Google Sign-In Endpoint

**File:** `src/app/api/auth/native/google/route.js`

*See [05-backend-changes.md](./05-backend-changes.md#endpoint-1-google-sign-in-verification) for complete code*

**Test:**
```bash
curl -X POST http://localhost:3000/api/auth/native/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"test_token"}'
```

---

#### Step 1.4: Create Email Signup Endpoint

**File:** `src/app/api/auth/native/email-signup/route.js`

*See [05-backend-changes.md](./05-backend-changes.md#endpoint-2-email-signup) for complete code*

**Test:**
```bash
curl -X POST http://localhost:3000/api/auth/native/email-signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"TestPass123","name":"Test User"}'
```

---

#### Step 1.5: Create Email Login Endpoint

**File:** `src/app/api/auth/native/email-login/route.js`

*See [05-backend-changes.md](./05-backend-changes.md#endpoint-3-email-login) for complete code*

**Test:**
```bash
curl -X POST http://localhost:3000/api/auth/native/email-login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"TestPass123"}'
```

---

#### Step 1.6: Run Backend Tests

```bash
npm test -- __tests__/api/auth/native
```

**Expected:** All tests passing

---

### Phase 2: Android Native UI Development (Week 1, Days 4-7)

#### Step 2.1: Add Google Sign-In Dependency

**File:** `android/app/build.gradle`

```gradle
dependencies {
    // ... existing dependencies
    
    // Google Sign-In
    implementation 'com.google.android.gms:play-services-auth:20.7.0'
}
```

**Sync Gradle:**
```bash
cd d:/startup/ConsistencyGridWallpaper/android
./gradlew --refresh-dependencies
```

---

#### Step 2.2: Create AuthManager

**File:** `android/app/src/main/java/com/consistencygridwallpaper/auth/AuthManager.kt`

*See [06-android-changes.md](./06-android-changes.md#3-authmanagerkt) for complete code*

**Test:**
```kotlin
val authManager = AuthManager.getInstance(context)
println(authManager.isLoggedIn()) // false initially
```

---

#### Step 2.3: Create GoogleSignInHelper

**File:** `android/app/src/main/java/com/consistencygridwallpaper/auth/GoogleSignInHelper.kt`

*See [06-android-changes.md](./06-android-changes.md#2-googlesigninhelperkt) for complete code*

**Update SERVER_CLIENT_ID:**
```kotlin
private const val SERVER_CLIENT_ID = "YOUR_ACTUAL_CLIENT_ID.apps.googleusercontent.com"
```

---

#### Step 2.4: Create AuthActivity Layout

**File:** `android/app/src/main/res/layout/activity_auth.xml`

*See [06-android-changes.md](./06-android-changes.md#activity_authxml) for complete code*

---

#### Step 2.5: Create AuthActivity

**File:** `android/app/src/main/java/com/consistencygridwallpaper/auth/AuthActivity.kt`

*See [06-android-changes.md](./06-android-changes.md#1-authactivitykt) for complete code*

---

#### Step 2.6: Create EmailAuthActivity Layout

**File:** `android/app/src/main/res/layout/activity_email_auth.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="24dp">

        <!-- Title -->
        <TextView
            android:id="@+id/tv_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Create Account"
            android:textSize="28sp"
            android:textStyle="bold"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            android:layout_marginTop="40dp" />

        <!-- Name Input (Signup only) -->
        <com.google.android.material.textfield.TextInputLayout
            android:id="@+id/til_name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:layout_constraintTop_toBottomOf="@id/tv_title"
            android:layout_marginTop="32dp">
            
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/et_name"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Full Name"
                android:inputType="textPersonName" />
        </com.google.android.material.textfield.TextInputLayout>

        <!-- Email Input -->
        <com.google.android.material.textfield.TextInputLayout
            android:id="@+id/til_email"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:layout_constraintTop_toBottomOf="@id/til_name"
            android:layout_marginTop="16dp">
            
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/et_email"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Email"
                android:inputType="textEmailAddress" />
        </com.google.android.material.textfield.TextInputLayout>

        <!-- Password Input -->
        <com.google.android.material.textfield.TextInputLayout
            android:id="@+id/til_password"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:layout_constraintTop_toBottomOf="@id/til_email"
            app:passwordToggleEnabled="true"
            android:layout_marginTop="16dp">
            
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/et_password"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Password"
                android:inputType="textPassword" />
        </com.google.android.material.textfield.TextInputLayout>

        <!-- Submit Button -->
        <Button
            android:id="@+id/btn_submit"
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:text="Create Account"
            app:layout_constraintTop_toBottomOf="@id/til_password"
            android:layout_marginTop="24dp" />

        <!-- Toggle Mode Button -->
        <TextView
            android:id="@+id/tv_toggle_mode"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Already have an account? Log in"
            android:textColor="#FF7A00"
            app:layout_constraintTop_toBottomOf="@id/btn_submit"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            android:layout_marginTop="16dp" />

        <!-- Loading Indicator -->
        <ProgressBar
            android:id="@+id/progress_bar"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:visibility="gone"
            app:layout_constraintTop_toBottomOf="@id/tv_toggle_mode"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            android:layout_marginTop="16dp" />

        <!-- Error Message -->
        <TextView
            android:id="@+id/tv_error"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="#FF0000"
            android:visibility="gone"
            app:layout_constraintTop_toBottomOf="@id/progress_bar"
            android:layout_marginTop="16dp" />

    </androidx.constraintlayout.widget.ConstraintLayout>
</ScrollView>
```

---

#### Step 2.7: Create EmailAuthActivity

**File:** `android/app/src/main/java/com/consistencygridwallpaper/auth/EmailAuthActivity.kt`

```kotlin
package com.consistencygridwallpaper.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.consistencygridwallpaper.R
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class EmailAuthActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EmailAuthActivity"
        private const val BASE_URL = "https://consistencygrid.netlify.app/api/auth/native"
    }

    private lateinit var tvTitle: TextView
    private lateinit var tilName: TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnSubmit: Button
    private lateinit var tvToggleMode: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    private var isSignupMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_auth)

        // Get mode from intent
        val mode = intent.getStringExtra("MODE") ?: "SIGNUP"
        isSignupMode = mode == "SIGNUP"

        // Initialize views
        tvTitle = findViewById(R.id.tv_title)
        tilName = findViewById(R.id.til_name)
        etName = findViewById(R.id.et_name)
        tilEmail = findViewById(R.id.til_email)
        etEmail = findViewById(R.id.et_email)
        tilPassword = findViewById(R.id.til_password)
        etPassword = findViewById(R.id.et_password)
        btnSubmit = findViewById(R.id.btn_submit)
        tvToggleMode = findViewById(R.id.tv_toggle_mode)
        progressBar = findViewById(R.id.progress_bar)
        tvError = findViewById(R.id.tv_error)

        // Update UI based on mode
        updateUI()

        // Set click listeners
        btnSubmit.setOnClickListener {
            if (validateInput()) {
                if (isSignupMode) {
                    performSignup()
                } else {
                    performLogin()
                }
            }
        }

        tvToggleMode.setOnClickListener {
            isSignupMode = !isSignupMode
            updateUI()
        }
    }

    private fun updateUI() {
        if (isSignupMode) {
            tvTitle.text = "Create Account"
            tilName.visibility = View.VISIBLE
            btnSubmit.text = "Create Account"
            tvToggleMode.text = "Already have an account? Log in"
        } else {
            tvTitle.text = "Welcome Back"
            tilName.visibility = View.GONE
            btnSubmit.text = "Log In"
            tvToggleMode.text = "Don't have an account? Sign up"
        }
        tvError.visibility = View.GONE
    }

    private fun validateInput(): Boolean {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val name = etName.text.toString().trim()

        if (isSignupMode && name.length < 2) {
            showError("Name must be at least 2 characters")
            return false
        }

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email")
            return false
        }

        if (password.length < 8) {
            showError("Password must be at least 8 characters")
            return false
        }

        return true
    }

    private fun performSignup() {
        showLoading()

        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                
                val json = JSONObject()
                json.put("name", name)
                json.put("email", email)
                json.put("password", password)
                
                val body = json.toString().toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$BASE_URL/email-signup")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    handleResponse(response.isSuccessful, responseBody)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Signup failed", e)
                withContext(Dispatchers.Main) {
                    showError("Network error. Please check your connection.")
                    hideLoading()
                }
            }
        }
    }

    private fun performLogin() {
        showLoading()

        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                
                val json = JSONObject()
                json.put("email", email)
                json.put("password", password)
                
                val body = json.toString().toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$BASE_URL/email-login")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    handleResponse(response.isSuccessful, responseBody)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                withContext(Dispatchers.Main) {
                    showError("Network error. Please check your connection.")
                    hideLoading()
                }
            }
        }
    }

    private fun handleResponse(success: Boolean, responseBody: String?) {
        if (success && responseBody != null) {
            try {
                val jsonResponse = JSONObject(responseBody)
                
                if (jsonResponse.getBoolean("success")) {
                    val token = jsonResponse.getString("token")
                    val onboarded = jsonResponse.getBoolean("onboarded")
                    
                    // Return result to AuthActivity
                    val resultIntent = Intent()
                    resultIntent.putExtra("token", token)
                    resultIntent.putExtra("onboarded", onboarded)
                    setResult(RESULT_OK, resultIntent)
                    finish()
                } else {
                    val error = jsonResponse.optString("error", "Authentication failed")
                    showError(error)
                    hideLoading()
                }
            } catch (e: Exception) {
                showError("Invalid response from server")
                hideLoading()
            }
        } else {
            showError("Server error. Please try again later.")
            hideLoading()
        }
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        btnSubmit.isEnabled = false
        tvError.visibility = View.GONE
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        btnSubmit.isEnabled = true
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }
}
```

---

### Phase 3: Android Integration (Week 2, Days 1-3)

#### Step 3.1: Update UserPrefs

**File:** `android/app/src/main/java/com/consistencygridwallpaper/storage/UserPrefs.kt`

*See [06-android-changes.md](./06-android-changes.md#2-userprefskt) for changes*

**Add:**
```kotlin
const val KEY_ONBOARDED = "onboarded"

fun saveOnboardedStatus(onboarded: Boolean) {
    prefs.edit().putBoolean(KEY_ONBOARDED, onboarded).apply()
}

fun isOnboarded(): Boolean {
    return prefs.getBoolean(KEY_ONBOARDED, false)
}
```

---

#### Step 3.2: Update MainActivity

**File:** `android/app/src/main/java/com/consistencygridwallpaper/MainActivity.kt`

*See [06-android-changes.md](./06-android-changes.md#1-mainactivitykt) for changes*

**Add at start of onCreate():**
```kotlin
// Check auth status FIRST
val authManager = AuthManager.getInstance(this)
if (!authManager.isLoggedIn()) {
    startActivity(Intent(this, AuthActivity::class.java))
    finish()
    return
}
```

---

#### Step 3.3: Update WebInterface

**File:** `android/app/src/main/java/com/consistencygridwallpaper/bridge/WebInterface.kt`

*See [06-android-changes.md](./06-android-changes.md#3-webinterfacekt) for changes*

**Add logout method:**
```kotlin
@JavascriptInterface
fun logout() {
    activity.runOnUiThread {
        val authManager = AuthManager.getInstance(activity)
        authManager.logout()
        
        val intent = Intent(activity, AuthActivity::class.java)
        activity.startActivity(intent)
        activity.finish()
    }
}
```

---

#### Step 3.4: Update AndroidManifest.xml

**File:** `android/app/src/main/AndroidManifest.xml`

*See [06-android-changes.md](./06-android-changes.md#5-androidmanifestxml) for changes*

**Add before closing `</application>`:**
```xml
<activity
    android:name=".auth.AuthActivity"
    android:exported="false"
    android:theme="@style/AppTheme" />

<activity
    android:name=".auth.EmailAuthActivity"
    android:exported="false"
    android:theme="@style/AppTheme" />
```

---

#### Step 3.5: Build and Test

```bash
cd d:/startup/ConsistencyGridWallpaper/android
./gradlew assembleDebug
```

**Install on device:**
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Test:**
1. Launch app
2. Verify AuthActivity displays
3. Test Google Sign-In
4. Test Email Signup
5. Test Email Login
6. Test Logout

---

### Phase 4: Testing (Week 2, Days 4-7)

#### Step 4.1: Run Unit Tests

**Backend:**
```bash
cd d:/startup/consistencygrid
npm test
```

**Android:**
```bash
cd d:/startup/ConsistencyGridWallpaper/android
./gradlew test
```

---

#### Step 4.2: Run Integration Tests

**Backend:**
```bash
npm run test:integration
```

**Android:**
```bash
./gradlew connectedAndroidTest
```

---

#### Step 4.3: Manual E2E Testing

*See [08-testing-plan.md](./08-testing-plan.md#end-to-end-tests) for test cases*

**Test on physical devices:**
- Android 10
- Android 11
- Android 12
- Android 13
- Android 14

---

### Phase 5: Deployment (Week 3-4)

#### Step 5.1: Deploy Backend to Staging

```bash
cd d:/startup/consistencygrid
git checkout -b native-auth-migration
git add .
git commit -m "Add native auth API endpoints"
git push origin native-auth-migration
```

**Deploy to Netlify staging:**
- Create pull request
- Netlify auto-deploys preview
- Test preview URL

---

#### Step 5.2: Build Android Release APK

```bash
cd d:/startup/ConsistencyGridWallpaper/android
./gradlew assembleRelease
```

**Sign APK:**
```bash
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore release.keystore \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  alias_name
```

---

#### Step 5.3: Deploy to Google Play Internal Testing

1. Open Google Play Console
2. Navigate to "Testing" → "Internal testing"
3. Create new release
4. Upload signed APK
5. Add release notes
6. Save and publish

---

#### Step 5.4: Monitor Metrics

**Track:**
- Crash rate
- Auth success rate
- API error rate
- User feedback

**Tools:**
- Firebase Crashlytics
- Google Analytics
- Backend logs

---

## Implementation Checklist

### Backend

- [ ] Install dependencies
- [ ] Create token utility
- [ ] Create Google Sign-In endpoint
- [ ] Create email signup endpoint
- [ ] Create email login endpoint
- [ ] Write unit tests
- [ ] Write integration tests
- [ ] Deploy to staging
- [ ] Test staging deployment
- [ ] Deploy to production

### Android

- [ ] Add Google Sign-In dependency
- [ ] Create AuthManager
- [ ] Create GoogleSignInHelper
- [ ] Create AuthActivity layout
- [ ] Create AuthActivity
- [ ] Create EmailAuthActivity layout
- [ ] Create EmailAuthActivity
- [ ] Update UserPrefs
- [ ] Update MainActivity
- [ ] Update WebInterface
- [ ] Update AndroidManifest.xml
- [ ] Write unit tests
- [ ] Write integration tests
- [ ] Build debug APK
- [ ] Test on physical devices
- [ ] Build release APK
- [ ] Deploy to internal testing

### Testing

- [ ] All unit tests passing
- [ ] All integration tests passing
- [ ] E2E Test 1: Google Sign-In
- [ ] E2E Test 2: Email Signup
- [ ] E2E Test 3: Auto Login
- [ ] E2E Test 4: Email Login
- [ ] E2E Test 5: App Restart
- [ ] Edge case testing
- [ ] Performance testing
- [ ] Security testing

### Deployment

- [ ] Backend deployed to staging
- [ ] Android deployed to internal testing
- [ ] Internal beta testing complete
- [ ] 10% production rollout
- [ ] 50% production rollout
- [ ] 100% production rollout
- [ ] Monitoring in place
- [ ] No critical issues

---

## Summary

### Total Timeline: 4 Weeks

**Week 1:** Backend + Android development  
**Week 2:** Integration + Testing  
**Week 3:** Staging + Internal beta  
**Week 4:** Production rollout  

### Key Milestones

✅ **Day 3:** Backend APIs complete  
✅ **Day 7:** Android UI complete  
✅ **Day 10:** Integration complete  
✅ **Day 14:** Testing complete  
✅ **Day 21:** Internal beta complete  
✅ **Day 28:** Production rollout complete  

---

## Next Steps

After completing this implementation:

1. **Monitor Production** - Track metrics for 2 weeks
2. **Gather Feedback** - Collect user feedback
3. **Optimize** - Improve based on data
4. **Document** - Update documentation with learnings
5. **Plan Next Feature** - Move to next enhancement

---

**END OF DOCUMENTATION PACKAGE**
