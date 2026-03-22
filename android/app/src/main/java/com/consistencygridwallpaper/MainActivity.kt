package com.consistencygridwallpaper

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.consistencygridwallpaper.BuildConfig
import com.consistencygridwallpaper.bridge.WebInterface
import com.consistencygridwallpaper.utils.NetworkUtils
import com.consistencygridwallpaper.utils.NotificationHelper
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * MainActivity - Full-Screen WebView Wrapper for ConsistencyGrid
 *
 * This activity provides a native Android wrapper around the ConsistencyGrid web application.
 * It handles:
 * - Full-screen browser experience with session persistence
 * - Disk-level cookie syncing to maintain login state across app restarts
 * - Native wallpaper setting through JavaScript bridge
 * - File downloads (images and CSV exports)
 * - Theme customization synchronized with the web app
 * - Automatic wallpaper updates scheduling
 *
 * The WebView is configured for optimal performance with hardware acceleration,
 * proper caching, and consistent user agent to prevent session invalidation.
 *
 * @see WebInterface for JavaScript bridge methods
 * @see com.consistencygridwallpaper.workers.WorkScheduler for background update scheduling
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val APP_USER_AGENT = "ConsistencyGridApp/1.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"

        /**
         * Payment-related URL patterns that must NEVER load inside the WebView.
         * These are redirected to an external browser for Play Store compliance.
         */
        private val BLOCKED_PAYMENT_PATTERNS = listOf(
            "/pricing",
            "/payment",
            "/subscribe",
            "checkout.razorpay.com",
            "js.stripe.com",
            "api.razorpay.com",
            "checkout.stripe.com"
        )

        /** Check if a URL is payment-related and must be opened externally. */
        fun isPaymentUrl(url: String): Boolean {
            val lower = url.lowercase()
            return BLOCKED_PAYMENT_PATTERNS.any { lower.contains(it) }
        }
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var isDataLoaded = false
    private var loginRetryCount = 0 // Prevents infinite redirect loop if backend rejects token

    // 👉 ADD THIS: Handles the native Android permission popup for GPS
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            Log.d(TAG, "Location permission granted securely.")
            // The user clicked Allow on the native prompt!
            // The WebChromeClient callback we stored will now be triggered
            pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, true, false)
        } else {
            Log.w(TAG, "Location permission denied.")
            pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, false, false)
        }
        pendingGeolocationCallback = null
        pendingGeolocationOrigin = null
    }

    // 👉 ADD THESE: Temporary storage for the WebView's permission callback
    private var pendingGeolocationCallback: android.webkit.GeolocationPermissions.Callback? = null
    private var pendingGeolocationOrigin: String? = null

    // Call this explicitly from JavaScript to guarantee the prompt appears
    fun requestLocationPermissionNatively() {
        Log.d(TAG, "requestLocationPermissionNatively: Prompting for location")
        runOnUiThread {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            handleImagePicked(uri)
        }
    }

    fun openImagePicker() {
        Log.d(TAG, "openImagePicker: Launching gallery")
        imagePickerLauncher.launch("image/*")
    }

    private fun handleImagePicked(uri: Uri) {
        Log.d(TAG, "handleImagePicked: Processing URI $uri")
        Thread {
            try {
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    val outputStream = ByteArrayOutputStream()
                    val compressFormat = if (mimeType.contains("png")) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    bitmap.compress(compressFormat, 80, outputStream)
                    val byteArray = outputStream.toByteArray()
                    val base64Data = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                    // Execute JS on the main thread
                    runOnUiThread {
                        val jsCall = "window.onAndroidImagePicked('$base64Data', '$mimeType')"
                        webView.evaluateJavascript(jsCall, null)
                    }
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                } else {
                    Log.e(TAG, "handleImagePicked: Failed to decode bitmap")
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleImagePicked: Error processing image", e)
            }
        }.start()
    }

    /**
     * Initializes the activity, sets up the WebView, and loads the website.
     * Restores previous state if available, applies saved theme, and schedules
     * automatic updates if enabled.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // ✅ THIS LINE IS MANDATORY FOR ANDROID 12+ (Must be called before super.onCreate)
        val splashScreen = installSplashScreen()
        
        // Keep splash on screen until we explicitly signal it's ready
        splashScreen.setKeepOnScreenCondition { !isDataLoaded }

        super.onCreate(savedInstanceState)

        // 🔐 AUTH CHECK: Redirect to AuthActivity if not logged in
        val authManager = com.consistencygridwallpaper.auth.AuthManager.getInstance(this)
        if (!authManager.isLoggedIn()) {
            Log.d(TAG, "User not logged in, launching AuthActivity")
            startActivity(android.content.Intent(this, com.consistencygridwallpaper.auth.AuthActivity::class.java))
            finish()
            return
        }

        // 2️⃣ SET CONTENT VIEW (ONLY ONCE ❗)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate: Initializing MainActivity")

        // 3️⃣ Init notification channels
        NotificationHelper.createNotificationChannels(this)

        // 4️⃣ Bind views
        webView = findViewById(R.id.webview_main)
        progressBar = findViewById(R.id.progress_loading)

        // 5️⃣ Setup WebView
        setupWebView()




        // 6️⃣ Restore or load site
        if (savedInstanceState != null) {
            Log.d(TAG, "Restoring WebView state from saved instance")
            webView.restoreState(savedInstanceState)
        } else {
            // 🧹 Smart Cache Clearing: Only clear on version upgrade, NOT on every launch.
            // Clearing cache on every launch is the root cause of "clear cache required" for new users:
            // it forces a fresh full Next.js bundle download even when the cached version is fine.
            // Instead we only clear when the app version changes (upgrade/fresh install).
            // NOTE: clearStaleNextAuthCookies() is called INSIDE clearCacheIfVersionChanged() only
            // on version change — NOT on every launch. Clearing NextAuth cookies on every launch
            // was forcing a full re-login via the bridge endpoint every single time the app opened.
            clearCacheIfVersionChanged()
            loadWebsite()
        }

        // 7️⃣ Apply saved theme preferences
        val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(this)
        applyTheme(userPrefs.getThemeColor(), userPrefs.isDarkMode())

        // 8️⃣ Ensure auto-update schedule (Force Enable & Check Permissions)
        if (userPrefs.isAutoUpdateEnabled() || authManager.isLoggedIn()) {
             // Force enable if logged in but currently disabled (migration fix)
             if (!userPrefs.isAutoUpdateEnabled()) {
                 Log.d(TAG, "🔧 Migrating legacy user: Force-enabling auto-update")
                 userPrefs.setAutoUpdate(true)
             }

            Log.d(TAG, "Auto-update is enabled, ensuring schedule is active")
            
            // Check for Exact Alarm permission on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "⚠️ Exact alarm permission missing! Prompting user...")
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            }

            // 🔋 Request Battery Optimization Exemption
            // Without this, B   attery Saver / Doze mode kills the WallpaperWorker before it can run.
            // This shows a system dialog: "Allow app to run unrestricted in background?"
            // It is now enforced via checkBatteryOptimizationExemption() in onResume()

            // Schedule the exact exact midnight update
            com.consistencygridwallpaper.workers.ExactAlarmScheduler.scheduleNextMidnightAlarm(this)
        }


        // 9️⃣ Back button handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        // 🔟 Handle Deep Links (Payment Success)
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Handles deep links, specifically for payment success flow.
     * Extracts token from URL: consistencygrid://payment-success?token=XYZ
     */
    /**
     * Handles deep links, specifically for payment success flow and OAuth login.
     * Extracts token from URL: 
     * - consistencygrid://payment-success?token=XYZ
     * - consistencygrid://login-success?token=XYZ
     */
    /**
     * Handles deep links for payment and OAuth login.
     * 
     * Schemes handled:
     * 1. consistencygrid://payment-success?token=XYZ
     * 2. consistencygrid://login-success?token=XYZ
     */
    private fun handleDeepLink(intent: android.content.Intent?) {
        val data: Uri? = intent?.data
        Log.d(TAG, "handleDeepLink: Processing intent data: $data")
        
        if (data != null) {
            val scheme = data.scheme
            val host = data.host
            val path = data.path

            // Check for both Custom Scheme AND App Links
            val isCustomScheme = scheme == "consistencygrid"
            val isAppLink = scheme == "https" && host == "consistencygrid.com" && path?.startsWith("/mobile-auth-callback") == true

            if (isCustomScheme || isAppLink) {
                // Extract token
                val token = data.getQueryParameter("token")
                
                if (!token.isNullOrBlank()) {
                    if (host == "payment-success") {
                        Log.d(TAG, "💰 Payment success token found")
                        Toast.makeText(this, "Subscription Activated! Reloading...", Toast.LENGTH_LONG).show()
                    } else if (host == "login-success" || path?.contains("mobile-auth-callback") == true) {
                        Log.d(TAG, "🔐 Login success token found")
                        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                    }

                    // 1. Save token securely
                    val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(this)
                    userPrefs.saveToken(token)
                    
                    // 2. Flush cookies to ensure clean state
                    android.webkit.CookieManager.getInstance().flush()
                    
                    // 3. Reload WebView with token to establish session
                    loadLoginWithToken(token)
                } else {
                    Log.w(TAG, "handleDeepLink: Token was null or blank")
                }
            }
        }
    }

    /**
     * Injects the NextAuth session cookie into the WebView's CookieManager.
     *
     * This is the CRITICAL fix that prevents the WebView from redirecting to /login.
     * The cookie name must exactly match what NextAuth expects on the server side:
     *   - Production:  __Secure-next-auth.session-token
     *   - Development: next-auth.session-token
     *
     * The cookie attributes (Secure, HttpOnly, Path, SameSite) must match NextAuth's defaults
     * otherwise the middleware will not recognise the session.
     *
     * @param baseUrl   e.g. "https://consistencygrid.netlify.app"
     * Injects the two cookies that the server needs to identify a native Android session:
     *  - publicToken  (httpOnly) → read by getUniversalSession() on every API route
     *  - native_auth  (non-httpOnly) → read by middleware to bypass NextAuth JWT check
     *
     * This completely replaces the webview-login bridge page for all logged-in users.
     * The middleware already lets the app through via the ConsistencyGridApp User-Agent,
     * so these cookies are just the data-layer auth for API calls (habits, streaks, etc.)
     * and the wallpaper renderer.
     *
     * Cookie attributes mirror what the server sets in /api/auth/webview-login/route.js:
     *   publicToken: httpOnly, secure, SameSite=None, 30-day maxAge
     *   native_auth: non-httpOnly, secure, SameSite=None, 30-day maxAge
     */
    private fun injectNativeAuthCookies(baseUrl: String, publicToken: String) {
        if (publicToken.isBlank()) {
            Log.w(TAG, "injectNativeAuthCookies: publicToken is blank, skipping")
            return
        }

        val cookieManager = CookieManager.getInstance()

        // publicToken — httpOnly so JS can't read it, but every same-origin fetch sends it.
        // SameSite=None is required for WebView cross-origin requests to work.
        cookieManager.setCookie(baseUrl, "publicToken=$publicToken; HttpOnly; Secure; Path=/; SameSite=None; Max-Age=2592000")

        // native_auth — non-httpOnly so the middleware can read it AND the JS bridge can detect it.
        cookieManager.setCookie(baseUrl, "native_auth=true; Secure; Path=/; SameSite=None; Max-Age=2592000")

        cookieManager.flush()
        Log.d(TAG, "injectNativeAuthCookies: ✅ publicToken + native_auth injected and flushed")
    }

    // Keep old name as a thin wrapper so any other callers still compile
    private fun injectSessionCookie(baseUrl: String, sessionToken: String) {
        // sessionToken-based injection is no longer used (server dropped synthetic JWTs).
        // Delegate to the cookie-only approach instead.
        val publicToken = com.consistencygridwallpaper.storage.UserPrefs(this).getToken() ?: ""
        injectNativeAuthCookies(baseUrl, publicToken)
    }

    /**
     * Loads the login page with the token to establish a web session.
     * Also used after payment-success / deep-link callbacks.
     */
    private fun loadLoginWithToken(token: String) {
        val baseUrl = "https://consistencygrid.com"
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels

        val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(this)
        val publicToken = userPrefs.getToken() ?: ""

        // ✅ Use deployed bridge endpoint — server sets httpOnly publicToken + native_auth cookies.
        // The page now waits 800ms (via JS setTimeout) before redirecting, giving the
        // Android CookieManager time to flush the Set-Cookie response headers to disk.
        // We also schedule a proactive CookieManager.flush() here as an extra guarantee.
        val isOnboarded = userPrefs.isOnboarded()
        val targetPath = if (isOnboarded) "/dashboard" else "/onboarding"
        val url = "$baseUrl/api/auth/webview-login?token=$publicToken&callbackUrl=$targetPath?canvasWidth=$width%26canvasHeight=$height"
        Log.d(TAG, "loadLoginWithToken: → $url")
        webView.loadUrl(url)

        // Flush cookies 1 second after loading the login bridge page.
        // The server-side HTML waits 800ms before JS-redirecting, so by 1s the
        // cookies should be committed and the redirect is safe.
        webView.postDelayed({
            android.webkit.CookieManager.getInstance().flush()
            Log.d(TAG, "loadLoginWithToken: proactive cookie flush completed")
        }, 1000L)
    }

    /**
     * Injects the platform flag into the WebView's localStorage.
     * This allows the website to detect it's running in the Android app
     * and hide payment UI/customise behavior.
     */
    private fun setPlatformFlag() {
        val javascript = """
            (function() {
                try {
                    localStorage.setItem('consistencygrid_platform', 'android');
                    // Also set a global variable for immediate check
                    window.consistencyGridPlatform = 'android';
                    console.log('[Android] Platform marked successfully');
                } catch (e) {
                    console.error('[Android] Platform marking failed:', e);
                }
            })();
        """.trimIndent()

        // Make sure webView is initialized
        if (::webView.isInitialized) {
            webView.evaluateJavascript(javascript, null)
        }
    }

    /**
     * Configures the WebView with optimal settings for performance and security.
     * 
     * Sets up:
     * - Disk-level cookie persaistence for session management
     * - Hardware acceleration for smooth rendering
     * - JavaScript and DOM storage for web app functionality
     * - Consistent user agent to prevent session invalidation
     * - Download listener for file exports
     * - WebViewClient for navigation and error handling
     * - WebChromeClient for progress tracking
     * - JavaScript bridge for native Android integration
     */
    private fun setupWebView() {
        Log.d(TAG, "setupWebView: Configuring WebView settings")
        
        // Initialize CookieManager with aggressive persistence
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }

        // Force hardware acceleration
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.settings.apply {
            // Persistence & Storage
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // 👉 ADD THIS LINE: Tells WebView it is allowed to process GPS
            setGeolocationEnabled(true)
            
            // Modern Web Experience
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            
            // UI & Scaling
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // Mixed Content: Compatibility mode allows some mixed content
            // but is more secure than ALWAYS_ALLOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            // Consistent User Agent
            userAgentString = APP_USER_AGENT
        }

        webView.webViewClient = object : WebViewClient() {
            /**
             * Simplified URL handling to fix login redirection issues.
             * 
             * We force the WebView to load ALL http/https links internally.
             * This prevents the app from 'swallowing' redirects or opening browsers 
             * unnecessarily during the OAuth/Login flow.
             */
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false

                // 💳 Payment / Pricing → External Browser (Play Store Compliance)
                // Block ALL payment-related URLs from loading inside WebView.
                // This includes our pricing/payment pages AND third-party payment SDKs.
                if (isPaymentUrl(url)) {
                    Log.d(TAG, "Payment URL → External Browser: $url")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }

                // 📎 External schemes (mailto, tel, etc.)
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Log.e(TAG, "External URL failed: $url", e)
                    }
                    return true
                }

                // 🌐 Intercept /login or /signup redirects from the WebView.
                // With our bridge-free approach, these should NEVER happen if cookies are set correctly.
                // If they do (e.g. race condition, cookie cleared by OS), we re-inject native auth
                // cookies and navigate directly to dashboard — no bridge page needed.
                try {
                    val uriPath = Uri.parse(url).path ?: ""
                    if (uriPath == "/login" || uriPath == "/signup") {
                        val authMgr = com.consistencygridwallpaper.auth.AuthManager.getInstance(this@MainActivity)
                        val token = authMgr.getToken()
                        if (!token.isNullOrBlank()) {
                            if (loginRetryCount < 2) {
                                loginRetryCount++
                                Log.w(TAG, "shouldOverride: /login redirect with valid token (attempt $loginRetryCount) — re-injecting cookies")
                                val baseUrl = "https://consistencygrid.com"
                                val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(this@MainActivity)
                                val isOnboarded = userPrefs.isOnboarded()
                                val targetPath = if (isOnboarded) "/dashboard" else "/onboarding"
                                val metrics = resources.displayMetrics
                                injectNativeAuthCookies(baseUrl, token)
                                webView.postDelayed({
                                    CookieManager.getInstance().flush()
                                    webView.loadUrl("$baseUrl$targetPath?canvasWidth=${metrics.widthPixels}&canvasHeight=${metrics.heightPixels}")
                                }, 300L)
                            } else {
                                // Server is consistently rejecting our token — it may be revoked
                                Log.e(TAG, "shouldOverride: Repeated /login redirects — token rejected by server. Logging out.")
                                authMgr.logout()
                                startActivity(Intent(this@MainActivity, com.consistencygridwallpaper.auth.AuthActivity::class.java))
                                finish()
                            }
                        } else {
                            Log.d(TAG, "shouldOverride: /login with no token — launching AuthActivity")
                            authMgr.logout()
                            startActivity(Intent(this@MainActivity, com.consistencygridwallpaper.auth.AuthActivity::class.java))
                            finish()
                        }
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "shouldOverride: URL parse error", e)
                }

                // 🌐 Everything else → WebView
                return false
            }


            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                
                if (url == null) {
                    progressBar.visibility = View.VISIBLE
                    return
                }

                // 💳 Block payment pages from rendering inside WebView (server-side redirect safety net)
                if (isPaymentUrl(url)) {
                    Log.d(TAG, "onPageStarted: Payment page detected, blocking and opening external browser: $url")
                    view?.stopLoading()
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return
                }

                // 🌐 Server-side redirect to /login (e.g. Next.js middleware sent 302).
                // Same recovery: re-inject cookies and go straight to dashboard.
                try {
                    val uriPath = Uri.parse(url).path ?: ""
                    if (uriPath == "/login" || uriPath == "/signup") {
                        Log.w(TAG, "onPageStarted: Server redirected to $uriPath — stopping and recovering")
                        view?.stopLoading()
                        val authMgr = com.consistencygridwallpaper.auth.AuthManager.getInstance(this@MainActivity)
                        val token = authMgr.getToken()
                        if (!token.isNullOrBlank()) {
                            if (loginRetryCount < 2) {
                                loginRetryCount++
                                Log.w(TAG, "onPageStarted: Re-injecting native auth cookies (attempt $loginRetryCount)")
                                val baseUrl = "https://consistencygrid.com"
                                val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(this@MainActivity)
                                val isOnboarded = userPrefs.isOnboarded()
                                val targetPath = if (isOnboarded) "/dashboard" else "/onboarding"
                                val metrics = resources.displayMetrics
                                injectNativeAuthCookies(baseUrl, token)
                                webView.postDelayed({
                                    CookieManager.getInstance().flush()
                                    webView.loadUrl("$baseUrl$targetPath?canvasWidth=${metrics.widthPixels}&canvasHeight=${metrics.heightPixels}")
                                }, 300L)
                            } else {
                                Log.e(TAG, "onPageStarted: Repeated login redirects — token rejected. Logging out.")
                                authMgr.logout()
                                startActivity(Intent(this@MainActivity, com.consistencygridwallpaper.auth.AuthActivity::class.java))
                                finish()
                            }
                        } else {
                            Log.d(TAG, "onPageStarted: No token — launching AuthActivity")
                            authMgr.logout()
                            startActivity(Intent(this@MainActivity, com.consistencygridwallpaper.auth.AuthActivity::class.java))
                            finish()
                        }
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "onPageStarted: URL parse error", e)
                }
                
                Log.d(TAG, "onPageStarted: Loading $url")
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "onPageFinished: Page loaded - $url")
                progressBar.visibility = View.GONE
                
                // Reset failure counter if we successfully reach dashboard or onboarding
                if (url?.contains("/dashboard") == true || url?.contains("/onboarding") == true) {
                    loginRetryCount = 0
                }
                
                // Flush cookies to ensure session tokens are saved immediately
                CookieManager.getInstance().flush()
                Log.d(TAG, "onPageFinished: Cookies flushed to disk")
                
                // Signal that the splash screen can now be removed
                isDataLoaded = true

                // Inject platform flag
                setPlatformFlag()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                
                // Only show error for main frame failures
                if (request?.isForMainFrame == true) {
                    val errorMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.description?.toString() ?: "Unknown error"
                    } else {
                        "Network error occurred"
                    }
                    
                    Log.e(TAG, "onReceivedError: Failed to load page - $errorMessage")
                    
                    // Also clear splash screen on error so user isn't stuck
                    isDataLoaded = true
                    
                    // Check if it's a network connectivity issue
                    if (!NetworkUtils.isNetworkAvailable(applicationContext)) {
                        Toast.makeText(
                            applicationContext,
                            "No internet connection. Please check your network settings.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            applicationContext,
                            "Failed to load page: $errorMessage",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }



        webView.webChromeClient = object : WebChromeClient() {
            // 👉 ADD THIS FUNCTION: Captures the website asking for GPS and shows the Android native prompt
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                pendingGeolocationCallback = callback
                pendingGeolocationOrigin = origin
                
                // Launch the native Android permission request dialog
                locationPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        // Add JavaScript bridge for native Android integration
        webView.addJavascriptInterface(WebInterface(this), "Android")

        // Support native downloads for images and CSV exports
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            try {
                Log.d(TAG, "Download requested: $url (type: $mimetype)")
                
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimetype)
                
                // Use cookies for authorized downloads
                val cookies = CookieManager.getInstance().getCookie(url)
                if (cookies != null) {
                    request.addRequestHeader("cookie", cookies)
                }
                request.addRequestHeader("User-Agent", userAgent)
                
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                request.setDescription("Downloading from ConsistencyGrid")
                request.setTitle(fileName)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                
                Toast.makeText(applicationContext, "Downloading $fileName...", Toast.LENGTH_LONG).show()
                Log.d(TAG, "Download started: $fileName")
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                Toast.makeText(
                    applicationContext,
                    "Download failed: ${e.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Checks if the user is logged in by verifying token existence.
     * 
     * This enables Amazon-lite authentication where users remain logged in
     * across app restarts and phone reboots.
     * 
     * @return true if authentication token exists, false otherwise
     */
    private fun checkLoginStatus(): Boolean {
        val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(this)
        val token = userPrefs.getToken()
        val isLoggedIn = !token.isNullOrBlank()
        Log.d(TAG, "checkLoginStatus: User ${if (isLoggedIn) "IS" else "NOT"} logged in")
        return isLoggedIn
    }

    /**
     * Loads the ConsistencyGrid website with Amazon-lite authentication.
     * 
     * Implements smart routing:
     * - If user is logged in (token exists): Load dashboard directly
     * - If user is logged out (no token): Load landing page
     * 
     * This ensures users don't have to log in again after app restarts or phone reboots.
     * Prioritizes session persistence by ensuring cookies are synced before load.
     * Detects screen dimensions for perfect wallpaper sizing.
     */
    /**
     * Smart cache clearing: only clears the WebView HTTP cache when the app version has
     * changed since the last launch (i.e., on fresh install or version upgrade).
     *
     * Clearing cache on every launch was the root cause of:
     * - "Please clear cache" required for new downloads
     * - Slow first-load because all Next.js chunks re-downloaded
     *
     * This preserves the cache for returning users while still busting stale chunks
     * after an app upgrade (which might have a new Next.js build on the server).
     */
    private fun clearCacheIfVersionChanged() {
        val prefs = getSharedPreferences("app_cache_meta", android.content.Context.MODE_PRIVATE)
        val storedVersionCode = prefs.getInt("last_version_code", -1)
        val currentVersionCode = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode
            }
        } catch (e: Exception) {
            Log.e(TAG, "clearCacheIfVersionChanged: Could not read version", e)
            -1
        }

        if (storedVersionCode != currentVersionCode) {
            // Version changed (or first install) → clear cache to bust stale Next.js chunks
            Log.d(TAG, "🧹 Version changed ($storedVersionCode → $currentVersionCode): Clearing WebView cache")
            webView.clearCache(true)
            webView.clearFormData()
            // Also purge stale NextAuth cookies so the new build doesn't inherit a
            // mismatched JWT from the old version. Safe to do here because we're about
            // to reload via the webview-login bridge anyway.
            clearStaleNextAuthCookies()
            prefs.edit().putInt("last_version_code", currentVersionCode).apply()
        } else {
            Log.d(TAG, "✅ Same version ($currentVersionCode): Skipping cache/cookie clear — reusing session")
        }
    }

    /**
     * Clears stale next-auth session cookies injected by older builds.
     * This prevents NextAuth from throwing exceptions when it encounters
     * a malformed or expired JWT in the session cookie.
     */
    private fun clearStaleNextAuthCookies() {
        val baseUrl = "https://consistencygrid.com"
        val cookieManager = CookieManager.getInstance()
        // Expire these cookies by setting max-age=0
        cookieManager.setCookie(baseUrl, "next-auth.session-token=; Max-Age=0; Path=/")
        cookieManager.setCookie(baseUrl, "__Secure-next-auth.session-token=; Max-Age=0; Secure; Path=/")
        cookieManager.setCookie(baseUrl, "next-auth.callback-url=; Max-Age=0; Path=/")
        cookieManager.setCookie(baseUrl, "next-auth.csrf-token=; Max-Age=0; Path=/")
        cookieManager.flush()
        Log.d(TAG, "clearStaleNextAuthCookies: Cleared old next-auth cookies")
    }

    private fun loadWebsite() {
        Log.d(TAG, "loadWebsite: Preparing to load website")

        // Ensure cookies are synced before the first load request
        CookieManager.getInstance().flush()

        // Check network connectivity before loading
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Log.w(TAG, "loadWebsite: No network connection available")
            Toast.makeText(
                this,
                "No internet connection. Please check your network settings and try again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val baseUrl = "https://consistencygrid.com"

            Log.d(TAG, "loadWebsite: Screen dimensions - ${width}x${height}")

            val isLoggedIn = checkLoginStatus()

            if (isLoggedIn) {
                val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(this)
                val token = userPrefs.getToken()

                if (!token.isNullOrBlank()) {
                    val isOnboarded = userPrefs.isOnboarded()
                    val targetPath = if (isOnboarded) "/dashboard" else "/onboarding"
                    val directUrl = "$baseUrl$targetPath?canvasWidth=$width&canvasHeight=$height"

                    // ✅ BRIDGE-FREE ROUTING: Always inject publicToken + native_auth cookies natively
                    // and load the dashboard directly. No "Signing you in..." page ever.
                    //
                    // Why this works:
                    // 1. The middleware lets Android through via ConsistencyGridApp User-Agent alone
                    //    (see middleware.js line 58-96 — isNativeAndroid → NextResponse.next())
                    // 2. API routes (habits, goals, streaks, wallpaper-renderer) need the `publicToken`
                    //    cookie to identify the user via getUniversalSession()
                    // 3. We set both cookies natively here — no server round-trip, no bridge HTML page
                    Log.d(TAG, "loadWebsite: Injecting native auth cookies and loading $directUrl")
                    injectNativeAuthCookies(baseUrl, token)

                    // Load directly after a 200ms flush delay
                    webView.postDelayed({
                        CookieManager.getInstance().flush()
                        webView.loadUrl(directUrl)
                        Log.d(TAG, "loadWebsite: ✅ Loaded dashboard directly — no bridge")
                    }, 200L)
                } else {
                    // Token unexpectedly missing — load landing page
                    val url = "$baseUrl?canvasWidth=$width&canvasHeight=$height"
                    webView.loadUrl(url)
                }
            } else {
                // User is logged out — show landing page
                val url = "$baseUrl?canvasWidth=$width&canvasHeight=$height"
                webView.loadUrl(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadWebsite: Failed to load website", e)
            Toast.makeText(
                this,
                "Failed to load website: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }



    /**
     * Saves the WebView state when the activity is being destroyed.
     * Also flushes cookies to disk to ensure session persistence.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(TAG, "onSaveInstanceState: Saving WebView state")
        webView.saveState(outState)
        CookieManager.getInstance().flush()
    }

    /**
     * Flushes cookies to disk when the app is minimized.
     * This ensures login state is preserved even if the app is killed by the system.
     */
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Flushing cookies to disk")
        CookieManager.getInstance().flush()
    }

    /**
     * Flushes cookies when the app resumes to ensure consistency.
     * Also proactively checks whether the JWT is expired or expiring soon,
     * triggering a silent refresh before the WebView encounters a 401.
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Activity resumed")
        CookieManager.getInstance().flush()

        // 🔄 Reset loginRetryCount so a stale counter from a previous session
        // doesn't incorrectly force logout when the user returns to the app.
        loginRetryCount = 0

        // Restrict user if Battery Saver is active
        checkBatteryOptimizationExemption()

        // Silent token refresh check
        val authManager = com.consistencygridwallpaper.auth.AuthManager.getInstance(this)
        if (authManager.isLoggedIn()) {
            if (authManager.isTokenExpired()) {
                Log.w(TAG, "onResume: JWT expired — attempting silent refresh")
                handleTokenExpiry(forceLogout = false)
            } else if (authManager.isTokenExpiringSoon()) {
                Log.d(TAG, "onResume: JWT expiring soon — proactive refresh")
                handleTokenExpiry(forceLogout = false)
            }
        }
    }

    /**
     * Attempts a silent token refresh.
     * If refresh succeeds, re-injects cookies and reloads the WebView.
     * If refresh fails and forceLogout=true, logs the user out.
     */
    private fun handleTokenExpiry(forceLogout: Boolean) {
        // The publicToken stored in UserPrefs never expires (it's a stable DB identifier).
        // The sessionToken (NextAuth JWT) CAN expire, but /api/native-auth/refresh is currently
        // disabled on the server. Since we now use cookie-based auth (publicToken + native_auth)
        // instead of JWT verification, we simply re-inject the native auth cookies to restore
        // the session instead of triggering a server round-trip refresh.
        lifecycleScope.launch {
            val authManager = com.consistencygridwallpaper.auth.AuthManager.getInstance(this@MainActivity)
            val token = authManager.getToken()

            if (!token.isNullOrBlank()) {
                Log.d(TAG, "handleTokenExpiry: Re-injecting native auth cookies to restore session")
                val baseUrl = "https://consistencygrid.com"
                injectNativeAuthCookies(baseUrl, token)
                webView.postDelayed({
                    CookieManager.getInstance().flush()
                    Log.d(TAG, "handleTokenExpiry: ✅ Cookies refreshed — continuing session")
                }, 200L)
                // DO NOT force logout just because the JWT is stale.
                // The publicToken-based auth in getUniversalSession() doesn't use the JWT at all.
            } else {
                Log.w(TAG, "handleTokenExpiry: No publicToken found — user must re-login")
                authManager.logout()
                startActivity(Intent(this@MainActivity, com.consistencygridwallpaper.auth.AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }
    }

    /**
     * Applies a custom theme to the app's system UI elements.
     * 
     * Updates:
     * - Status bar color
     * - Navigation bar color
     * - Progress bar color
     * - System UI icon colors (light/dark based on theme)
     * 
     * @param colorHex The color in hex format (e.g., "#FF7A00")
     * @param isDark Whether the theme is dark mode
     */
    fun applyTheme(colorHex: String, isDark: Boolean) {
        try {
            Log.d(TAG, "applyTheme: Applying theme - color=$colorHex, isDark=$isDark")
            
            val color = android.graphics.Color.parseColor(colorHex)
            
            // 1. Status Bar
            window.statusBarColor = color
            
            // 2. Navigation Bar
            window.navigationBarColor = color
            
            // 3. Progress Bar
            progressBar.progressDrawable.setTint(color)
            
            // 4. System Appearance (Icons color)
            val decorView = window.decorView
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val controller = decorView.windowInsetsController
                if (controller != null) {
                    if (isDark) {
                        // White icons for dark background
                        controller.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                    } else {
                        // Dark icons for light background
                        controller.setSystemBarsAppearance(
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        )
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                if (!isDark) {
                    decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }
            
            Log.d(TAG, "applyTheme: Theme applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "applyTheme: Failed to apply theme", e)
        }
    }

    /**
     * Shows a dialog explaining how to verify that automatic wallpaper updates are working.
     * 
     * Provides instructions for testing the midnight update functionality and
     * tips for ensuring reliability on different device manufacturers.
     */
    fun showVerificationDialog() {
        Log.d(TAG, "showVerificationDialog: Showing verification instructions")
        
        com.consistencygridwallpaper.utils.DialogUtils.showCustomDialog(
            context = this,
            title = "Verify Auto Update ✅",
            message = "To test if it's working:\n\n" +
                "1. Set phone time to 11:59 PM.\n" +
                "2. Wait for 12:00 AM.\n" +
                "3. Wallpaper should change automatically!\n\n" +
                "💡 Pro Tip: On Xiaomi/Samsung/Oppo, set this app to 'Unrestricted' battery mode for 100% reliability.",
            positiveButtonText = "Got it!",
            positiveButtonAction = {}
        )
    }

    /**
     * Shows a warning dialog about third-party lock screen carousels (Glance, etc.)
     * that might override the app's lock screen wallpaper over time.
     */
    fun showGlanceWarningDialog() {
        Log.d(TAG, "showGlanceWarningDialog: Showing glance warning")
        
        com.consistencygridwallpaper.utils.DialogUtils.showCustomDialog(
            context = this,
            title = "Lock Screen Warning ⚠️",
            message = "If you use a custom lock screen app like 'Glance', 'Wallpaper Carousel', or 'Dynamic Lock Screen' (common on Xiaomi, POCO, Samsung, and Realme), it may override ConsistencyGrid over time.\n\n" +
                "To prevent this, please disable these features in your phone's Settings -> Lock Screen menu.",
            positiveButtonText = "I Understand",
            positiveButtonAction = {}
        )
    }

    /**
     * Enforces the battery optimization exemption.
     * Starts a blocking dialog if the user is not exempt, preventing usage of the app until granted.
     */
    private fun checkBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.w(TAG, "⚠️ App is NOT whitelisted from battery optimization — showing blocker")
                com.consistencygridwallpaper.utils.DialogUtils.showCustomDialog(
                    context = this,
                    title = "Battery Optimization Required",
                    message = "To ensure your wallpaper updates automatically, you must allow ConsistencyGrid to run unrestricted in the background.\n\nYou cannot continue without granting this permission.",
                    positiveButtonText = "Allow Access",
                    positiveButtonAction = {
                        try {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val fallBackIntent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                startActivity(fallBackIntent)
                            } catch (e2: Exception) {
                                Toast.makeText(this@MainActivity, "Please disable battery optimization manually.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    negativeButtonText = "Exit App",
                    negativeButtonAction = {
                        finish()
                    },
                    cancelable = false
                )
            } else {
                Log.d(TAG, "✅ App is already whitelisted from battery optimization")
            }
        }
    }

    /**
     * Launches a URL in a Chrome Custom Tab for secure, isolated browsing.
     * Used principally for Google OAuth.
     */
    private fun launchCustomTab(url: String) {
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(this, Uri.parse(url))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Performing final cleanup")
        // Final flush on app close
        CookieManager.getInstance().flush()
    }
}
