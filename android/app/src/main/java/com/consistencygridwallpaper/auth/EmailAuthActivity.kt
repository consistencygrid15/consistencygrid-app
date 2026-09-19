package com.consistencygridwallpaper.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.consistencygridwallpaper.R
import com.consistencygridwallpaper.storage.UserPrefs
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * EmailAuthActivity - Email/Password authentication
 * Enhanced with Edge-to-Edge support.
 */
class EmailAuthActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EmailAuthActivity"
        const val MODE_SIGNUP = "signup"
        const val MODE_LOGIN = "login"
        private const val BASE_URL = "https://consistencygrid.com/api/native-auth"
        private const val FALLBACK_URL = "https://consistencygrid.netlify.app/api/native-auth"
        private const val TIMEOUT_SECONDS = 15L
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
        
        // Edge-to-Edge support
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_email_auth)

        val mode = intent.getStringExtra("MODE") ?: MODE_SIGNUP
        isSignupMode = mode == MODE_SIGNUP

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

        updateUI()

        btnSubmit.setOnClickListener {
            if (validateInput()) {
                if (isSignupMode) performSignup() else performLogin()
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

        val userPrefs = UserPrefs(this)
        val registeredProvider = userPrefs.getRegisteredProvider(email)
        if (registeredProvider == "GOOGLE") {
            showError("This email is registered via Google Sign-In. Please tap 'Continue with Google'.")
            hideLoading()
            return
        } else if (registeredProvider == "EMAIL") {
            showError("Account already exists with this email. Please tap 'Already have an account? Log in'.")
            hideLoading()
            return
        }

        val baseUrl = userPrefs.getBaseUrl().trimEnd('/')
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                val params = mapOf("name" to name, "email" to email, "password" to password)
                callBackendAPI("$baseUrl/api/native-auth/email-signup", params)
                    ?: callBackendAPI("$BASE_URL/email-signup", params)
            }

            if (response != null && isSuccessJsonResponse(response)) {
                handleResponse(response)
            } else {
                Log.w(TAG, "Signup backend unavailable or rejected the request")
                showError("Unable to create your account right now. Please check your connection and try again.")
                hideLoading()
            }
        }
    }

    private fun performLogin() {
        showLoading()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        val userPrefs = UserPrefs(this)
        val registeredProvider = userPrefs.getRegisteredProvider(email)
        if (registeredProvider == "GOOGLE") {
            showError("This email was registered using Google Sign-In. Please tap 'Continue with Google'.")
            hideLoading()
            return
        }

        val baseUrl = userPrefs.getBaseUrl().trimEnd('/')
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                val params = mapOf("email" to email, "password" to password)
                callBackendAPI("$baseUrl/api/native-auth/email-login", params)
                    ?: callBackendAPI("$BASE_URL/email-login", params)
            }

            if (response != null && isSuccessJsonResponse(response)) {
                handleResponse(response)
            } else {
                Log.w(TAG, "Login backend unavailable or rejected the request")
                showError("Unable to sign in right now. Please check your connection and try again.")
                hideLoading()
            }
        }
    }

    private fun callBackendAPI(url: String, params: Map<String, String>): String? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val body = JSONObject(params).toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                Log.w(TAG, "API call to $url returned HTTP ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "API call to $url failed: ${e.message}")
            null
        }
    }

    private fun isSuccessJsonResponse(body: String): Boolean {
        return try {
            val json = JSONObject(body)
            json.optBoolean("success", false)
        } catch (_: Exception) {
            false
        }
    }

    private fun handleResponse(responseBody: String?) {
        if (!responseBody.isNullOrBlank()) {
            try {
                val jsonResponse = JSONObject(responseBody)
                if (jsonResponse.optBoolean("success", false)) {
                    val token        = jsonResponse.optString("token", "")
                    val sessionToken = jsonResponse.optString("sessionToken", "")
                    val onboarded    = jsonResponse.optBoolean("onboarded", false)
                    val expiresAt    = jsonResponse.optLong("expiresAt", 0L)
                    
                    val email = jsonResponse.optJSONObject("user")?.optString("email")
                        ?.ifBlank { null }
                        ?: jsonResponse.optString("email", "")
                        .ifBlank { null }
                        ?: etEmail.text.toString().trim()

                    val name = jsonResponse.optJSONObject("user")?.optString("name")
                        ?.ifBlank { null }
                        ?: jsonResponse.optString("name", "")
                        .ifBlank { null }
                        ?: etName.text.toString().trim().ifBlank { email.substringBefore("@").replace(".", " ").capitalize() }

                    if (token.isBlank()) {
                        showError("Server returned invalid authentication response.")
                        hideLoading()
                        return
                    }

                    val authManager = AuthManager.getInstance(applicationContext)
                    authManager.saveAuthData(token, sessionToken, onboarded, expiresAt)
                    val userPrefs = UserPrefs(applicationContext)
                    userPrefs.saveToken(token)
                    userPrefs.saveSessionToken(sessionToken)
                    userPrefs.saveOnboardedStatus(onboarded)
                    userPrefs.registerAccount(email, "EMAIL")

                    // Save email + name into Room synchronously before finish()
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                val db = com.consistencygridwallpaper.storage.room.AppDatabase.getDatabase(applicationContext)
                                val existing = db.userProfileDao().get()

                                db.userProfileDao().upsert(
                                    com.consistencygridwallpaper.storage.room.UserProfileEntity(
                                        id          = 1,
                                        name        = name.ifBlank { existing?.name ?: "ConsistencyGrid User" },
                                        email       = email.ifBlank { existing?.email ?: "" },
                                        plan        = existing?.plan ?: "free",
                                        publicToken = token,
                                        updatedAt   = System.currentTimeMillis()
                                    )
                                )
                                Log.d(TAG, "Successfully saved user profile to Room database: name='$name', email='$email'")

                                // Immediately trigger sync so local guest data is pushed and remote cloud data is pulled
                                com.consistencygridwallpaper.repository.SyncRepository(applicationContext).syncWithServer()
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to cache user profile or sync: ${e.message}")
                            }
                        }

                        val resultIntent = Intent().apply {
                            putExtra("token",        token)
                            putExtra("sessionToken", sessionToken)
                            putExtra("onboarded",    onboarded)
                            putExtra("expiresAt",    expiresAt)
                            putExtra("name",         name)
                            putExtra("email",        email)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                } else {
                    val errorMsg = jsonResponse.optString("error", "Authentication failed")
                    showError(errorMsg)
                    hideLoading()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse auth response", e)
                showError("Invalid server response.")
                hideLoading()
            }
        } else {
            showError("Server error. Please try again.")
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
