package com.consistencygridwallpaper.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.consistencygridwallpaper.MainActivity
import com.consistencygridwallpaper.R
import kotlinx.coroutines.launch

/**
 * AuthActivity - Main authentication screen
 * 
 * Entry point for logged-out users.
 * Provides options for Google Sign-In, Email Signup, and Email Login.
 */
class AuthActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AuthActivity"
        const val MODE_SIGNUP = "SIGNUP"
        const val MODE_LOGIN = "LOGIN"
    }

    private lateinit var btnGoogleSignIn: Button
    private lateinit var btnEmailSignup: Button
    private lateinit var btnEmailLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    private lateinit var googleSignInHelper: GoogleSignInHelper
    private lateinit var authManager: AuthManager

    // Launcher for Google Sign-In
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Google Sign-In result received")
        handleGoogleSignInResult(result.data)
    }

    // Launcher for Email Auth
    private val emailAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val token = data?.getStringExtra("token")
            val onboarded = data?.getBooleanExtra("onboarded", false) ?: false

            if (token != null) {
                handleAuthSuccess(token, sessionToken = "", onboarded = onboarded)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        // Initialize helpers
        googleSignInHelper = GoogleSignInHelper(this)
        authManager = AuthManager.getInstance(this)

        // Check if already logged in (shouldn't happen, but safety check)
        if (authManager.isLoggedIn()) {
            Log.d(TAG, "User already logged in, redirecting to MainActivity")
            navigateToMain()
            return
        }

        // Initialize views
        btnGoogleSignIn = findViewById(R.id.btn_google_signin)
        btnEmailSignup = findViewById(R.id.btn_email_signup)
        btnEmailLogin = findViewById(R.id.btn_email_login)
        progressBar = findViewById(R.id.progress_bar)
        tvError = findViewById(R.id.tv_error)

        // Set click listeners
        btnGoogleSignIn.setOnClickListener {
            startGoogleSignIn()
        }

        btnEmailSignup.setOnClickListener {
            startEmailAuth(MODE_SIGNUP)
        }

        btnEmailLogin.setOnClickListener {
            startEmailAuth(MODE_LOGIN)
        }
    }

    /**
     * Start Google Sign-In flow
     */
    private fun startGoogleSignIn() {
        Log.d(TAG, "Starting Google Sign-In")
        showLoading()
        
        val signInIntent = googleSignInHelper.getSignInClient().signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    /**
     * Handle Google Sign-In result
     */
    private fun handleGoogleSignInResult(data: Intent?) {
        lifecycleScope.launch {
            try {
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
                val result = googleSignInHelper.handleSignInResult(task)

                if (result != null) {
                    handleAuthSuccess(
                        token = result.token,
                        sessionToken = result.sessionToken,
                        onboarded = result.onboarded,
                        expiresAt = result.expiresAt
                    )
                } else {
                    showError("Google Sign-In failed. Please try again.")
                    hideLoading()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling Google Sign-In result", e)
                showError("An error occurred. Please try again.")
                hideLoading()
            }
        }
    }

    /**
     * Start Email Authentication flow
     */
    private fun startEmailAuth(mode: String) {
        Log.d(TAG, "Starting Email Auth: $mode")
        
        val intent = Intent(this, EmailAuthActivity::class.java)
        intent.putExtra("MODE", mode)
        emailAuthLauncher.launch(intent)
    }

    /**
     * Handle successful authentication
     */
    private fun handleAuthSuccess(
        token: String,
        sessionToken: String = "",
        onboarded: Boolean,
        expiresAt: Long = 0L
    ) {
        Log.d(TAG, "Authentication successful, saving data")
        
        // Save auth data (includes expiry)
        authManager.saveAuthData(token, sessionToken, onboarded, expiresAt)
        
        // 🚀 Enable Auto-Update by default for new logins
        val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(this)
        userPrefs.setAutoUpdate(true)
        
        // Navigate to MainActivity
        navigateToMain()
    }

    /**
     * Navigate to MainActivity
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Show loading indicator
     */
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        btnGoogleSignIn.isEnabled = false
        btnEmailSignup.isEnabled = false
        btnEmailLogin.isEnabled = false
        tvError.visibility = View.GONE
    }

    /**
     * Hide loading indicator
     */
    private fun hideLoading() {
        progressBar.visibility = View.GONE
        btnGoogleSignIn.isEnabled = true
        btnEmailSignup.isEnabled = true
        btnEmailLogin.isEnabled = true
    }

    /**
     * Show error message
     */
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }
}
