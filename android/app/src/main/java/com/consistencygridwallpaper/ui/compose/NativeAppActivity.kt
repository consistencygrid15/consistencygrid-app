package com.consistencygridwallpaper.ui.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.consistencygridwallpaper.ui.compose.components.BottomNavBar
import com.consistencygridwallpaper.ui.compose.dashboard.DashboardScreen
import com.consistencygridwallpaper.ui.compose.goals.GoalsScreen
import com.consistencygridwallpaper.ui.compose.habits.HabitsScreen
import com.consistencygridwallpaper.ui.compose.onboarding.OnboardingScreen
import com.consistencygridwallpaper.ui.compose.reminders.RemindersScreen
import com.consistencygridwallpaper.ui.compose.settings.SettingsScreen
import com.consistencygridwallpaper.ui.compose.streaks.StreaksScreen
import com.consistencygridwallpaper.ui.compose.theme.BgBase
import com.consistencygridwallpaper.ui.compose.theme.CgAppTheme
import com.consistencygridwallpaper.ui.compose.wallpaper.WallpaperGeneratorScreen
import com.consistencygridwallpaper.ui.compose.widgets.WidgetsGalleryScreen
import com.consistencygridwallpaper.reelcontrol.ui.ReelControlScreen
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.ui.compose.tour.ConsistencyGridTourSteps
import com.consistencygridwallpaper.ui.compose.tour.FeatureTourOverlay
import com.consistencygridwallpaper.ui.compose.tour.TourManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class NativeAppActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            android.util.Log.d("NativeAppActivity", "✅ POST_NOTIFICATIONS permission granted")
        } else {
            android.util.Log.w("NativeAppActivity", "⚠️ POST_NOTIFICATIONS permission denied")
        }
        // Smooth 600ms delay to allow MIUI/OEM WindowManager to settle before showing native battery dialog
        if (!com.consistencygridwallpaper.utils.PermissionUtils.isBatteryOptimizationIgnored(this)) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                com.consistencygridwallpaper.utils.PermissionUtils.openBatteryOptimizationSettings(this)
            }, 600L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        // Prompt native Android system bottom dialog for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        enableEdgeToEdge()
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val userPrefs = UserPrefs(this)
        val startScreen = intent.getStringExtra("open_screen")
        val isFromLogout = intent.hasExtra("from_logout") || intent.getBooleanExtra("bypass_onboarding", false)
        if (isFromLogout) {
            userPrefs.saveOnboardedStatus(true)
        }

        setContent {
            CgAppTheme {
                // Onboarding gate — show onboarding ONLY on fresh install when not yet completed
                var showOnboarding by remember { mutableStateOf(!isFromLogout && !userPrefs.isOnboarded()) }
                var showPermissionsSheet by remember { mutableStateOf(!userPrefs.getBoolean("hasPromptedPermissionsSheet", false)) }

                if (showOnboarding) {
                    OnboardingScreen(
                        onComplete = {
                            userPrefs.saveOnboardedStatus(true)
                            showOnboarding = false
                            // ── Trigger first wallpaper update now that onboarding is finished! ──
                            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.consistencygridwallpaper.workers.WallpaperWorker>()
                                .setInputData(androidx.work.workDataOf("TRIGGER" to "ONBOARDING_FINISH", "FORCE_UPDATE" to true))
                                .build()
                            androidx.work.WorkManager.getInstance(this@NativeAppActivity).enqueueUniqueWork(
                                "WallpaperUpdate_Initial",
                                androidx.work.ExistingWorkPolicy.REPLACE,
                                workRequest
                            )
                            // ── Start feature tour right after onboarding ────
                            TourManager.startTourIfNeeded(this@NativeAppActivity, ConsistencyGridTourSteps)
                        }
                    )
                } else {
                    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        NativeAppShell(startScreen = startScreen)

                        if (showPermissionsSheet) {
                            com.consistencygridwallpaper.ui.compose.components.PermissionsSetupBottomSheet(
                                onDismissRequest = {
                                    userPrefs.setBoolean("hasPromptedPermissionsSheet", true)
                                    showPermissionsSheet = false
                                },
                                onRequestNotification = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndRunMissedDayUpdate()
        lifecycleScope.launch(Dispatchers.IO) {
            val syncRepo = com.consistencygridwallpaper.repository.SyncRepository(applicationContext)
            syncRepo.syncWithServer()
        }
    }

    private fun checkAndRunMissedDayUpdate() {
        val userPrefs = UserPrefs(this)
        // Never trigger background update if auto update disabled or user hasn't finished onboarding!
        if (!userPrefs.isAutoUpdateEnabled() || !userPrefs.isOnboarded()) return
        
        // Schedule next midnight alarms just in case they were cleared by the system
        com.consistencygridwallpaper.workers.ExactAlarmScheduler.scheduleNextMidnightAlarm(this)
        com.consistencygridwallpaper.workers.MidnightWorkScheduler.schedule(this)

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        if (userPrefs.getLastUpdateDate() != today) {
            android.util.Log.d("NativeAppActivity", "Missed daily update detected array on open. Scheduling immediate update.")
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.consistencygridwallpaper.workers.WallpaperWorker>()
                .setInputData(androidx.work.workDataOf("TRIGGER" to "APP_OPEN", "FORCE_UPDATE" to false))
                .build()
            androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
                "WallpaperUpdate_MissedDay", 
                androidx.work.ExistingWorkPolicy.REPLACE, 
                workRequest
            )
        }
    }
}


// Routes that should NOT show the BottomNavBar
private val fullScreenRoutes = setOf("wallpaper_generator", "reel_control", "widgets_gallery", "subscription")

@Composable
fun NativeAppShell(startScreen: String? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(startScreen) {
        if (startScreen != null && startScreen.isNotBlank()) {
            navController.navigate(startScreen) {
                launchSingleTop = true
            }
        }
    }

    // ── Cross-screen tour navigation ──────────────────────────────────────────
    // When the tour wants a different screen than the current one, navigate there.
    val requiredRoute = TourManager.requiredRoute
    LaunchedEffect(requiredRoute, TourManager.isActive) {
        if (TourManager.isActive && requiredRoute != null && currentRoute != requiredRoute) {
            navController.navigate(requiredRoute) {
                popUpTo("dashboard") { saveState = true }
                launchSingleTop = true
                restoreState    = true
            }
        }
    }

    val showBottomBar = currentRoute?.let { route ->
        fullScreenRoutes.none { route.startsWith(it) }
    } ?: true

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(BgBase),
            containerColor = BgBase,
            bottomBar = {
                if (showBottomBar) {
                    BottomNavBar(navController = navController)
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(BgBase)
            ) {
                AppNavGraph(navController = navController)
            }
        }

        // ── Feature tour overlay — always on top of full screen ───────────────────
        val context = androidx.compose.ui.platform.LocalContext.current
        FeatureTourOverlay(
            onNext = { TourManager.nextStep(context) },
            onSkip = { TourManager.skipTour(context) }
        )
    }
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "dashboard",
    ) {
        composable("dashboard")  { DashboardScreen(
            onOpenWallpaper = { navController.navigate("wallpaper_generator") },
            onOpenReelControl = { navController.navigate("reel_control") },
            onOpenHabits = { navController.navigate("habits") },
            onOpenGoals = { navController.navigate("goals") },
            onOpenStreaks = { navController.navigate("streaks") },
            onOpenReminders = { navController.navigate("reminders") },
            onOpenWidgets = { navController.navigate("widgets_gallery") },
            onOpenSubscription = { navController.navigate("subscription") }
        ) }
        composable("habits")     { HabitsScreen(onOpenSubscription = { navController.navigate("subscription") }) }
        composable("goals")      { GoalsScreen(onOpenSubscription = { navController.navigate("subscription") }) }
        composable("streaks")    { StreaksScreen() }
        composable("reminders")  { RemindersScreen(onOpenSubscription = { navController.navigate("subscription") }) }
        composable("settings")   {
            SettingsScreen(
                onOpenWallpaper = { navController.navigate("wallpaper_generator") },
                onOpenReelControl = { navController.navigate("reel_control") },
                onOpenSubscription = { navController.navigate("subscription") }
            )
        }

        // Wallpaper Generator — full screen native with WebView canvas
        composable("wallpaper_generator") {
            WallpaperGeneratorScreen(
                onBack = { navController.popBackStack() },
                onOpenSubscription = { navController.navigate("subscription") }
            )
        }

        // Reel Control — full screen digital wellbeing feature
        composable("reel_control") {
            ReelControlScreen(onBack = { navController.popBackStack() })
        }

        // Widgets Gallery — browse and pin all available widgets
        composable("widgets_gallery") {
            WidgetsGalleryScreen(onBack = { navController.popBackStack() })
        }

        // Subscription Paywall — full screen Google Play billing paywall
        composable("subscription") {
            com.consistencygridwallpaper.ui.compose.subscription.SubscriptionScreen(
                onDismiss = { navController.popBackStack() }
            )
        }
    }
}
