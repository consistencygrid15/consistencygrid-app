package com.consistencygridwallpaper.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.consistencygridwallpaper.R
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
 * 
 * Handles both signup and login modes.
 * Validates input, calls backend API, and returns result to AuthActivity.
 */
class EmailAuthActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EmailAuthActivity"
        private const val BASE_URL = "https://consistencygrid.com/api/native-auth"
        private const val TIMEOUT_SECONDS = 30L
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
        val mode = intent.getStringExtra("MODE") ?: AuthActivity.MODE_SIGNUP
        isSignupMode = mode == AuthActivity.MODE_SIGNUP

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

    /**
     * Update UI based on current mode (signup vs login)
     */
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

    /**
     * Validate user input
     */
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

    /**
     * Perform signup
     */
    private fun performSignup() {
        showLoading()

        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    callBackendAPI("$BASE_URL/email-signup", mapOf(
                        "name" to name,
                        "email" to email,
                        "password" to password
                    ))
                }

                handleResponse(result)

            } catch (e: Exception) {
                Log.e(TAG, "Signup failed", e)
                showError("Network error. Please check your connection.")
                hideLoading()
            }
        }
    }

    /**
     * Perform login
     */
    private fun performLogin() {
        showLoading()

        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    callBackendAPI("$BASE_URL/email-login", mapOf(
                        "email" to email,
                        "password" to password
                    ))
                }

                handleResponse(result)

            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                showError("Network error. Please check your connection.")
                hideLoading()
            }
        }
    }

    /**
     * Call backend API
     */
    private fun callBackendAPI(url: String, params: Map<String, String>): String? {
        val client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val json = JSONObject(params)
        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        Log.d(TAG, "callBackendAPI: Calling $url")
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        
        Log.d(TAG, "callBackendAPI: Response code = ${response.code}")
        
        return if (response.isSuccessful) {
            Log.d(TAG, "callBackendAPI: Success")
            responseBody
        } else {
            Log.e(TAG, "callBackendAPI: Failed with code ${response.code}")
            Log.e(TAG, "callBackendAPI: Error body = $responseBody")
            null
        }
    }

    /**
     * Handle API response
     */
    private fun handleResponse(responseBody: String?) {
        if (responseBody != null) {
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

    /**
     * Show loading indicator
     */
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        btnSubmit.isEnabled = false
        tvError.visibility = View.GONE
    }

    /**
     * Hide loading indicator
     */
    private fun hideLoading() {
        progressBar.visibility = View.GONE
        btnSubmit.isEnabled = true
    }

    /**
     * Show error message
     */
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }
}
