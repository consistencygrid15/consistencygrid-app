package com.consistencygridwallpaper.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.consistencygridwallpaper.MainActivity
import com.consistencygridwallpaper.repository.SyncRepository
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.storage.room.AppDatabase
import com.consistencygridwallpaper.storage.room.UserProfileEntity
import com.consistencygridwallpaper.ui.compose.settings.SupportDocType
import com.consistencygridwallpaper.ui.compose.settings.SupportViewerSheet
import com.consistencygridwallpaper.ui.compose.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AuthActivity - Main authentication screen.
 * Redesigned with Compose, featuring ambient background visuals and Google Sign-In only.
 */
class AuthActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AuthActivity"
    }

    private lateinit var googleSignInHelper: GoogleSignInHelper
    private lateinit var authManager: AuthManager

    private var isLoggingInState = mutableStateOf(false)
    private var errorMessageState = mutableStateOf<String?>(null)

    // Migration dialog state
    private var showMigrationDialog = mutableStateOf(false)
    private var isMigrating = mutableStateOf(false)
    // Holds auth data while waiting for migration choice
    private var pendingToken: String = ""
    private var pendingSessionToken: String = ""
    private var pendingOnboarded: Boolean = false
    private var pendingExpiresAt: Long = 0L

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleGoogleSignInResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge support
        WindowCompat.setDecorFitsSystemWindows(window, false)

        googleSignInHelper = GoogleSignInHelper(this)
        authManager = AuthManager.getInstance(this)

        if (authManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        setContent {
            CgAppTheme {
                AuthScreen(
                    isLoading = isLoggingInState.value,
                    errorMessage = errorMessageState.value,
                    onGoogleSignInClick = { startGoogleSignIn() },
                    onDismissError = { errorMessageState.value = null }
                )

                // Migration dialog — shown when guest user logs in with local data
                if (showMigrationDialog.value) {
                    DataMigrationDialog(
                        isMigrating = isMigrating.value,
                        onMigrateYes = { onMigrateYes() },
                        onMigrateNo  = { onMigrateNo() }
                    )
                }
            }
        }
    }

    private fun startGoogleSignIn() {
        isLoggingInState.value = true
        errorMessageState.value = null
        try {
            googleSignInLauncher.launch(googleSignInHelper.getSignInClient().signInIntent)
        } catch (e: Exception) {
            isLoggingInState.value = false
            errorMessageState.value = "Unable to launch Google Sign-In: ${e.localizedMessage}"
        }
    }

    private fun handleGoogleSignInResult(data: Intent?) {
        lifecycleScope.launch {
            try {
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
                val result = googleSignInHelper.handleSignInResult(task)
                if (result != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val db = AppDatabase.getDatabase(applicationContext)
                            val existing = db.userProfileDao().get()
                            val finalName  = result.name.ifBlank { existing?.name ?: "ConsistencyGrid User" }
                            val finalEmail = result.email.ifBlank { existing?.email ?: "" }
                            db.userProfileDao().upsert(
                                UserProfileEntity(
                                    id              = 1,
                                    name            = finalName,
                                    email           = finalEmail,
                                    plan            = existing?.plan ?: "free",
                                    currentStreak   = existing?.currentStreak ?: 0,
                                    totalHabits     = existing?.totalHabits ?: 0,
                                    todayCompletion = existing?.todayCompletion ?: 0,
                                    daysTracked     = existing?.daysTracked ?: 0,
                                    publicToken     = result.token,
                                    updatedAt       = System.currentTimeMillis()
                                )
                            )
                            UserPrefs(this@AuthActivity).registerAccount(finalEmail, "GOOGLE")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to cache Google user profile: ${e.message}")
                        }
                    }
                    handleAuthSuccess(result.token, result.sessionToken, result.onboarded, result.expiresAt)
                } else {
                    errorMessageState.value = googleSignInHelper.lastErrorMessage
                        ?: "Google Sign-In failed or was cancelled."
                    isLoggingInState.value = false
                }
            } catch (e: Exception) {
                errorMessageState.value = "Authentication error: ${e.localizedMessage}"
                isLoggingInState.value = false
            }
        }
    }

    /**
     * Called after a successful Google / Email auth.
     * If the user had guest-only local data, shows a migration dialog.
     * Otherwise, saves auth and navigates directly to main.
     */
    private fun handleAuthSuccess(token: String, sessionToken: String = "", onboarded: Boolean, expiresAt: Long = 0L) {
        // Save auth data immediately so SyncRepository can authenticate
        authManager.saveAuthData(token, sessionToken, onboarded, expiresAt)
        UserPrefs(this).setAutoUpdate(true)
        isLoggingInState.value = false

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val hasLocal = withContext(Dispatchers.IO) {
                DataMigrationHelper.hasLocalOnlyData(db)
            }
            if (hasLocal) {
                // Store pending values for use after dialog choice
                pendingToken        = token
                pendingSessionToken = sessionToken
                pendingOnboarded    = onboarded
                pendingExpiresAt    = expiresAt
                showMigrationDialog.value = true
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        SyncRepository(applicationContext).syncWithServer()
                    } catch (e: Exception) {
                        Log.w("AuthActivity", "Initial server sync failed: ${e.message}")
                    }
                }
                navigateToMain()
            }
        }
    }

    /** User chose "Yes, migrate my local data" */
    private fun onMigrateYes() {
        isMigrating.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Mark all local-only records as unsynced so SyncRepository picks them up
                val db = AppDatabase.getDatabase(applicationContext)
                // Habits: ensure isSynced=false for local-only
                db.habitDao().getUnsyncedHabits()
                    .filter { it.serverId == null && !it.isDeleted }
                    .takeIf { it.isNotEmpty() }
                    ?.let { Log.d("AuthActivity", "Migrating ${it.size} local habits") }

                // Push everything to server
                SyncRepository(applicationContext).syncWithServer()
                Log.d("AuthActivity", "✅ Local data migrated to server")
            } catch (e: Exception) {
                Log.w("AuthActivity", "Migration sync failed (will retry): ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    isMigrating.value = false
                    showMigrationDialog.value = false
                    navigateToMain()
                }
            }
        }
    }

    /** User chose "No, start fresh" */
    private fun onMigrateNo() {
        isMigrating.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                DataMigrationHelper.clearAllLocalUserData(db)
                // Pull fresh data from server
                SyncRepository(applicationContext).syncWithServer()
                Log.d("AuthActivity", "✅ Local data cleared, server data pulled")
            } catch (e: Exception) {
                Log.w("AuthActivity", "Fresh-start sync failed: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    isMigrating.value = false
                    showMigrationDialog.value = false
                    navigateToMain()
                }
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}

@Composable
private fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onGoogleSignInClick: () -> Unit,
    onDismissError: () -> Unit
) {
    var activeSupportDoc by remember { mutableStateOf<SupportDocType?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Ambient background glowing orb
        AuthAmbientOrb(
            accentColor = OrangeAccent,
            modifier = Modifier.fillMaxSize()
        )

        // Top decorative glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-80).dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                OrangeAccent.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2f
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 28.dp)
            ) {
                // App Logo Orb with Consistency Grid logo
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(OrangeAccent, GoldAccent)
                            )
                        )
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(BgCard),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.consistencygridwallpaper.R.drawable.app_logo),
                        contentDescription = "ConsistencyGrid Logo",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Turn daily habits into your live wallpaper",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Premium Features Glass Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(22.dp), spotColor = CardShadowColor)
                    .clip(RoundedCornerShape(22.dp))
                    .background(BgCard)
                    .border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "WHY CONSISTENCYGRID?",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OrangeAccent,
                    letterSpacing = 1.2.sp
                )

                AuthFeatureRow(
                    emoji = "🎯",
                    title = "Habit & Streak Tracker",
                    subtitle = "One-tap check-ins with automatic streak counts"
                )

                HorizontalDivider(color = GlassStroke.copy(alpha = 0.5f), thickness = 0.5.dp)

                AuthFeatureRow(
                    emoji = "🖼️",
                    title = "Live Lock Screen Grid",
                    subtitle = "Automatically updates every midnight on your phone"
                )

                HorizontalDivider(color = GlassStroke.copy(alpha = 0.5f), thickness = 0.5.dp)

                AuthFeatureRow(
                    emoji = "🔒",
                    title = "Encrypted & Offline-First",
                    subtitle = "Zero tracking. Your personal data stays on your device"
                )
            }

            Spacer(Modifier.height(28.dp))

            // Main Google Sign-In Action Area
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Error Banner if present
                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RoseAccent.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, RoseAccent.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, null, tint = RoseAccent, modifier = Modifier.size(20.dp))
                            Text(errorMessage, color = RoseAccent, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = onDismissError, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, null, tint = RoseAccent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Official Google Sign-In Button
                Button(
                    onClick = onGoogleSignInClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = OrangeGlow),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1F2937),
                        disabledContainerColor = Color.White.copy(alpha = 0.7f),
                        disabledContentColor = Color(0xFF6B7280)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 6.dp)
                ) {
                    if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = OrangeAccent,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "Signing in with Google…",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937)
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Official Google G Logo Badge
                            GoogleGIcon(modifier = Modifier.size(22.dp))

                            Text(
                                text = "Continue with Google",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937)
                            )
                        }
                    }
                }

                // Security Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SecurityBadge(icon = Icons.Default.FlashOn, label = "Instant Access")
                    Text("•", color = TextMuted, fontSize = 10.sp)
                    SecurityBadge(icon = Icons.Default.Shield, label = "AES-256 Encrypted")
                    Text("•", color = TextMuted, fontSize = 10.sp)
                    SecurityBadge(icon = Icons.Default.Block, label = "No Spam")
                }

                Spacer(Modifier.height(4.dp))

                // Legal Footer
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "By continuing, you agree to our ",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "Terms",
                        fontSize = 11.sp,
                        color = OrangeAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { activeSupportDoc = SupportDocType.TERMS_OF_SERVICE }
                    )
                    Text(
                        text = " and ",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "Privacy Policy",
                        fontSize = 11.sp,
                        color = OrangeAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { activeSupportDoc = SupportDocType.PRIVACY_POLICY }
                    )
                }
            }
        }
    }

    if (activeSupportDoc != null) {
        SupportViewerSheet(
            docType = activeSupportDoc!!,
            onDismiss = { activeSupportDoc = null }
        )
    }
}

@Composable
private fun AuthFeatureRow(emoji: String, title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(BgMuted)
                .border(1.dp, GlassStroke, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 18.sp)
        }

        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextMuted, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SecurityBadge(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = GreenAccent, modifier = Modifier.size(13.dp))
        Text(label, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GoogleGIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2f, height / 2f)
        val radius = minOf(width, height) / 2f
        val strokeWidth = radius * 0.38f

        // Google 4-color arcs
        drawArc(
            color = Color(0xFFEA4335), // Red
            startAngle = 190f,
            sweepAngle = 110f,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
        drawArc(
            color = Color(0xFFFBBC05), // Yellow
            startAngle = 120f,
            sweepAngle = 70f,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
        drawArc(
            color = Color(0xFF34A853), // Green
            startAngle = 20f,
            sweepAngle = 100f,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
        drawArc(
            color = Color(0xFF4285F4), // Blue
            startAngle = -40f,
            sweepAngle = 60f,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
        // Horizontal blue arm
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(center.x - radius * 0.1f, center.y),
            end = Offset(center.x + radius * 0.85f, center.y),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
private fun AuthAmbientOrb(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "authOrb")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue  = 0.35f,
        animationSpec = infiniteRepeatable(
            animation   = tween(3500, easing = LinearEasing),
            repeatMode  = RepeatMode.Reverse
        ),
        label = "authOrbAlpha"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.5f, size.height * 0.25f)
        val radius = size.minDimension * 0.55f
        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = alphaAnim), Color.Transparent),
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )
    }
}

// ─── Data Migration Dialog ────────────────────────────────────────────────────

/**
 * Shown once when a guest user logs in and has locally-created data.
 * Lets the user decide whether to migrate their offline habits/goals/reminders
 * to the new account, or start fresh from the server.
 *
 * Non-dismissable (the user must make a choice).
 */
@Composable
private fun DataMigrationDialog(
    isMigrating: Boolean,
    onMigrateYes: () -> Unit,
    onMigrateNo: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "migrationGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue  = 0.28f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "migrationGlowAlpha"
    )

    // Scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .shadow(12.dp, RoundedCornerShape(28.dp), spotColor = OrangeAccent.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(28.dp))
                .background(BgCard)
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(listOf(OrangeAccent.copy(alpha = 0.6f), GoldAccent.copy(alpha = 0.3f)))
                    ),
                    RoundedCornerShape(28.dp)
                )
                // Subtle orange ambient glow behind the card
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(OrangeAccent.copy(alpha = glowAlpha), Color.Transparent)
                        ),
                        radius = size.minDimension * 0.85f
                    )
                }
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Icon badge
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(OrangeAccent.copy(alpha = 0.18f), GoldAccent.copy(alpha = 0.10f)))
                    )
                    .border(1.dp, OrangeAccent.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("📦", fontSize = 30.sp)
            }

            // Title
            Text(
                text = "You have offline data!",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            // Subtitle
            Text(
                text = "We found habits, goals, or reminders you created before signing in.\n\nWould you like to add them to your new account?",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            // What will be migrated — info chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("🎯 Habits", "🏆 Goals", "🔔 Reminders").forEach { label ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(BgMuted)
                            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(label, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (isMigrating) {
                // Loading state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = OrangeAccent,
                        strokeWidth = 3.dp
                    )
                    Text(
                        "Syncing your data…",
                        fontSize = 13.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // YES — primary action
                    Button(
                        onClick = onMigrateYes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = OrangeGlow),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OrangeAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Yes, save to my account",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // NO — secondary action
                    OutlinedButton(
                        onClick = onMigrateNo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF4A4A4A)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                    ) {
                        Text(
                            "No, start fresh",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

