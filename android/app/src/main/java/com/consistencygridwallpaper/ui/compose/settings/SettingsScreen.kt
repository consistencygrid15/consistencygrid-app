package com.consistencygridwallpaper.ui.compose.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.consistencygridwallpaper.auth.AuthActivity
import com.consistencygridwallpaper.auth.AuthManager
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.network.ApiClient
import com.consistencygridwallpaper.utils.AppLogger
import com.consistencygridwallpaper.ui.compose.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.consistencygridwallpaper.utils.PermissionUtils

@Composable
fun SettingsScreen(
    onOpenWallpaper: () -> Unit = {},
    onOpenReelControl: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs   = remember { UserPrefs(context) }
    val auth    = remember { AuthManager.getInstance(context) }
    val vm      = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>()
    val profile by vm.profile.collectAsState()

    // Derived display values from cached Room profile
    val displayName  = profile.name.ifBlank { "ConsistencyGrid User" }
    val displayEmail = profile.email.ifBlank { "" }
    val displayPlan  = if (profile.isPremium) "Premium" else ""
    val planColor    = if (profile.isPremium) GoldAccent else OrangeAccent
    // Avatar initials: first letter of first and last word of name, or fallback "CG"
    val avatarInitials = run {
        val parts = profile.name.trim().split(" ").filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
            parts.size == 1 -> parts.first().take(2).uppercase()
            else             -> "CG"
        }
    }

    val tokenDisplay = remember(profile.publicToken) {
        val t = profile.publicToken.ifBlank { prefs.getToken() ?: "" }
        if (t.length > 8) "···${t.takeLast(8)}" else t
    }

    val coroutineScope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }

    // Computed once, used in multiple places (profile card + account section visibility)
    val isUserLoggedIn = auth.isLoggedIn() && displayEmail.isNotBlank()

    var profilePhotoPath by remember { mutableStateOf(prefs.getProfilePhotoPath()) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val localPath = copyUriToInternal(context, uri)
                if (localPath != null) {
                    prefs.setProfilePhotoPath(localPath)
                    profilePhotoPath = localPath
                }
            }
        }
    )

    var activeSupportDoc by remember { mutableStateOf<SupportDocType?>(null) }
    var isLoggingOut by remember { mutableStateOf(false) }

    // ── Update behaviour preference (persisted via UserPrefs) ──────────────
    var wallpaperUpdateMode by remember {
        mutableStateOf(prefs.getWallpaperUpdateMode())
    }

    var showAppSetupSheet by remember { mutableStateOf(false) }

    // Live reliability check for summary badge
    val lifecycleOwner = LocalLifecycleOwner.current
    var checkTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isBatteryIgnored = remember(checkTrigger) { PermissionUtils.isBatteryOptimizationIgnored(context) }
    val isExactAlarmGranted = remember(checkTrigger) { PermissionUtils.isExactAlarmGranted(context) }
    val isNotificationGranted = remember(checkTrigger) { PermissionUtils.isNotificationGranted(context) }

    val allReliabilityConfigured = remember(checkTrigger) {
        var isOk = isBatteryIgnored
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isOk = isOk && isExactAlarmGranted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) isOk = isOk && isNotificationGranted
        isOk
    }

    // ── Direct Google Sign-In Launcher in Settings ─────────────────────────
    val activity = context as? android.app.Activity
    val googleSignInHelper = remember(activity) {
        activity?.let { com.consistencygridwallpaper.auth.GoogleSignInHelper(it) }
    }
    var isGoogleSigningIn by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (googleSignInHelper != null) {
            coroutineScope.launch {
                try {
                    isGoogleSigningIn = true
                    val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    val authResult = googleSignInHelper.handleSignInResult(task)
                    if (authResult != null) {
                        withContext(Dispatchers.IO) {
                            val db = com.consistencygridwallpaper.storage.room.AppDatabase.getDatabase(context)
                            val existing = db.userProfileDao().get()
                            val finalName  = authResult.name.ifBlank { existing?.name ?: "ConsistencyGrid User" }
                            val finalEmail = authResult.email.ifBlank { existing?.email ?: "" }
                            db.userProfileDao().upsert(
                                com.consistencygridwallpaper.storage.room.UserProfileEntity(
                                    id              = 1,
                                    name            = finalName,
                                    email           = finalEmail,
                                    plan            = existing?.plan ?: "free",
                                    currentStreak   = existing?.currentStreak ?: 0,
                                    totalHabits     = existing?.totalHabits ?: 0,
                                    todayCompletion = existing?.todayCompletion ?: 0,
                                    daysTracked     = existing?.daysTracked ?: 0,
                                    publicToken     = authResult.token,
                                    updatedAt       = System.currentTimeMillis()
                                )
                            )
                            prefs.registerAccount(finalEmail, "GOOGLE")
                        }
                        auth.saveAuthData(authResult.token, authResult.sessionToken, authResult.onboarded, authResult.expiresAt)
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            googleSignInHelper.lastErrorMessage
                                ?: if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
                                    "Google Sign-In cancelled or unavailable"
                                } else {
                                    "Google Sign-In failed"
                                },
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    AppLogger.e("SettingsScreen", "Google sign-in error: ${e.message}")
                    android.widget.Toast.makeText(
                        context,
                        "Google Sign-In error: ${e.localizedMessage ?: "try again"}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } finally {
                    isGoogleSigningIn = false
                }
            }
        }
    }


    if (showLogoutDialog) {
        LogoutSheet(
            userName = displayName,
            userEmail = displayEmail,
            userPlan = displayPlan,
            avatarInitials = avatarInitials,
            profilePhotoPath = profilePhotoPath,
            isLoggingOut = isLoggingOut,
            onDismiss = { if (!isLoggingOut) showLogoutDialog = false },
            onConfirmLogout = {
                isLoggingOut = true
                coroutineScope.launch {
                    try {
                        doLogout(context, auth)
                    } finally {
                        isLoggingOut = false
                        showLogoutDialog = false
                    }
                }
            }
        )
    }

    if (showAppSetupSheet) {
        AppSetupSheet(
            onDismiss = { showAppSetupSheet = false }
        )
    }

    activeSupportDoc?.let { doc ->
        SupportViewerSheet(
            docType = doc,
            onDismiss = { activeSupportDoc = null }
        )
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest  = { showEditNameDialog = false },
            containerColor    = BgCard,
            titleContentColor = TextPrimary,
            textContentColor  = TextSecondary,
            shape             = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Edit, null, tint = OrangeAccent, modifier = Modifier.size(20.dp))
                    Text("Edit Name", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = { editNameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter your name", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrangeAccent,
                        unfocusedBorderColor = GlassStroke,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = OrangeAccent,
                        focusedContainerColor = BgCard,
                        unfocusedContainerColor = BgMuted
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editNameInput. isNotBlank()) {
                            vm.updateName(editNameInput.trim())
                            showEditNameDialog = false
                        }
                    }
                ) {
                    Text("Save", color = OrangeAccent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }





    LazyColumn(
        modifier       = Modifier.fillMaxSize().background(BgBase),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        item {
            Row(
                modifier             = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp)
                    .background(BgCard)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier         = Modifier.size(38.dp).clip(CircleShape).background(OrangeMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Settings, null, tint = OrangeAccent, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text("Manage your account & app", fontSize = 12.sp, color = TextMuted)
                }
            }
        }

        // ── Profile / Cloud Sync Card ───────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            // isUserLoggedIn is now hoisted to SettingsContent scope above
            if (!isUserLoggedIn) {
                // ── Highlighted Continue with Google Card ──────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = CardShadowColor)
                        .clip(RoundedCornerShape(20.dp))
                        .background(BgCard)
                        .border(
                            BorderStroke(1.dp, GlassStroke),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Backup Habits & Streaks",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Sign in to keep your data safe & synced across devices",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = {
                                googleSignInHelper?.let { helper ->
                                    isGoogleSigningIn = true
                                    googleSignInLauncher.launch(helper.getSignInClient().signInIntent)
                                } ?: run {
                                    context.startActivity(Intent(context, AuthActivity::class.java))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = OrangeAccent.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(OrangeAccent, Color(0xFFFF5500))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Continue with Google",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = OrangeAccent.copy(alpha = 0.15f))
                        .clip(RoundedCornerShape(20.dp))
                        .background(BgCard)
                        .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    // Avatar with orange gradient ring
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Brush.sweepGradient(listOf(OrangeAccent, Color(0xFFFF4500), OrangeLight, OrangeAccent)))
                        )
                        Box(
                            modifier         = Modifier.size(56.dp).clip(CircleShape).background(BgCard),
                            contentAlignment = Alignment.Center
                        ) {
                            val path = profilePhotoPath
                            if (path != null && File(path).exists()) {
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier.size(50.dp).clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier         = Modifier.size(50.dp).clip(CircleShape).background(OrangeMuted),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(avatarInitials, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = OrangeAccent)
                                }
                            }
                        }
                        // Camera overlay
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .align(Alignment.BottomEnd)
                                .border(1.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change Photo",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                editNameInput = displayName
                                showEditNameDialog = true
                            }
                        ) {
                            Text(displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Name",
                                tint = OrangeAccent.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (displayEmail.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(displayEmail, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                        }
                    }

                }
            }
        }
    }



        // ── App Section ────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            SettingsSectionLabel("App", OrangeAccent)
        }
        item {
            SettingsGroup(Modifier.padding(horizontal = 16.dp)) {
                SettingsRow(
                    icon     = Icons.Filled.Wallpaper,
                    iconTint = VioletAccent,
                    title    = "Wallpaper Generator",
                    subtitle = "Build your custom grid wallpaper",
                    onClick  = { onOpenWallpaper() }
                )


                SettingsDivider()
                SettingsRow(
                    icon     = Icons.Filled.Explore,
                    iconTint = OrangeAccent,
                    title    = "Replay Feature Tour",
                    subtitle = "Restart the interactive onboarding walkthrough",
                    onClick  = {
                        com.consistencygridwallpaper.ui.compose.tour.TourManager.restartTour(
                            context,
                            com.consistencygridwallpaper.ui.compose.tour.ConsistencyGridTourSteps
                        )
                        android.widget.Toast.makeText(context, "Feature tour started!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }


        // ── Update Behaviour ──────────────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            SettingsSectionLabel("Update Behaviour", SkyAccent)
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(2.dp, RoundedCornerShape(14.dp), spotColor = CardShadowColor)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(SkyDim)
                            .border(1.dp, SkyAccent.copy(alpha = 0.25f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AutoMode, null, tint = SkyAccent, modifier = Modifier.size(16.dp))
                    }
                    Column {
                        Text("What gets updated nightly?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Choose what the midnight update refreshes", fontSize = 11.sp, color = TextMuted)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // — Option row: Wallpaper + Widgets —
                UpdateModeOption(
                    icon        = Icons.Filled.Wallpaper,
                    title       = "Wallpaper + Widgets",
                    subtitle    = "Re-renders your consistency grid wallpaper AND refreshes all widgets",
                    isSelected  = wallpaperUpdateMode == com.consistencygridwallpaper.storage.UserPrefs.UPDATE_MODE_WALLPAPER_AND_WIDGETS,
                    accentColor = VioletAccent,
                    onClick     = {
                        val mode = com.consistencygridwallpaper.storage.UserPrefs.UPDATE_MODE_WALLPAPER_AND_WIDGETS
                        prefs.setWallpaperUpdateMode(mode)
                        wallpaperUpdateMode = mode
                        com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(context)
                        com.consistencygridwallpaper.workers.WallpaperWorker.scheduleImmediate(context)
                    }
                )

                Spacer(Modifier.height(8.dp))

                // — Option row: Widgets Only —
                UpdateModeOption(
                    icon        = Icons.Filled.Widgets,
                    title       = "Widgets Only",
                    subtitle    = "Skip wallpaper rendering. Only refreshes your home-screen widgets (saves battery)",
                    isSelected  = wallpaperUpdateMode == com.consistencygridwallpaper.storage.UserPrefs.UPDATE_MODE_WIDGETS_ONLY,
                    accentColor = SkyAccent,
                    onClick     = {
                        val mode = com.consistencygridwallpaper.storage.UserPrefs.UPDATE_MODE_WIDGETS_ONLY
                        prefs.setWallpaperUpdateMode(mode)
                        wallpaperUpdateMode = mode
                        com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(context)
                    }
                )
            }
        }


        // ── App Permissions & System Status ─────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            SettingsSectionLabel("App Permissions & Status", if (allReliabilityConfigured) GreenAccent else OrangeAccent)
        }
        item {
            SettingsGroup(Modifier.padding(horizontal = 16.dp)) {
                SettingsRow(
                    icon = Icons.Filled.VerifiedUser,
                    iconTint = if (allReliabilityConfigured) GreenAccent else OrangeAccent,
                    title = "App Permissions Status",
                    subtitle = if (allReliabilityConfigured) "Tap to view permission status (All Granted ✅)" else "Tap to check permissions (Accepted vs Pending) ➔",
                    trailing = {
                        StatusBadge(isConfigured = allReliabilityConfigured)
                    },
                    onClick = { showAppSetupSheet = true }
                )
            }
        }


        // ── Support ────────────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            SettingsSectionLabel("Support", TextMuted)
        }
        item {
            SettingsGroup(Modifier.padding(horizontal = 16.dp)) {
                SettingsRow(
                    icon     = Icons.Outlined.Share,
                    iconTint = GreenAccent,
                    title    = "Share with Friends",
                    subtitle = "Invite others to build habits",
                    onClick  = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Build better habits with ConsistencyGrid!")
                        }
                        context.startActivity(Intent.createChooser(share, "Share ConsistencyGrid"))
                    }
                )
                SettingsDivider()
                SettingsRow(
                    icon     = Icons.Outlined.HelpOutline,
                    iconTint = OrangeAccent,
                    title    = "Help & Support",
                    subtitle = "Read FAQs or contact our team",
                    onClick  = { activeSupportDoc = SupportDocType.HELP_SUPPORT }
                )
                SettingsDivider()
                SettingsRow(
                    icon     = Icons.Outlined.Security,
                    iconTint = VioletAccent,
                    title    = "Privacy Policy",
                    subtitle = "Review how we manage and protect data",
                    onClick  = { activeSupportDoc = SupportDocType.PRIVACY_POLICY }
                )
                SettingsDivider()
                SettingsRow(
                    icon     = Icons.Outlined.Description,
                    iconTint = Color(0xFF00BFA5),
                    title    = "Terms of Service",
                    subtitle = "Read our service terms & policies",
                    onClick  = { activeSupportDoc = SupportDocType.TERMS_OF_SERVICE }
                )
            }
        }


        // ── Account (Danger zone) — only shown when logged in ─────────────
        if (isUserLoggedIn) {
            item {
                Spacer(Modifier.height(16.dp))
                SettingsSectionLabel("Account", RoseAccent)
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .shadow(2.dp, RoundedCornerShape(14.dp), spotColor = CardShadowColor)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgCard)
                        .border(1.dp, RoseAccent.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                ) {
                    // Log Out Row
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable { showLogoutDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(RoseAccent.copy(alpha = 0.1f))
                                .border(1.dp, RoseAccent.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Logout, null, tint = RoseAccent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Log Out", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, null, tint = TextMuted.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // ── Version footer ─────────────────────────────────────────────────
        item {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(top = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("v1.0 • Built with ❤️", fontSize = 11.sp, color = TextMuted.copy(alpha = 0.7f))
            }
        }
    }
}

// ── Reusable components ────────────────────────────────────────────────────────

@Composable
private fun UpdateModeOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.07f) else BgMuted,
        animationSpec = tween(200),
        label = "UpdateModeBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.55f) else GlassStroke,
        animationSpec = tween(200),
        label = "UpdateModeBorder"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) accentColor.copy(alpha = 0.15f) else GlassFill)
                .border(1.dp, if (isSelected) accentColor.copy(alpha = 0.3f) else GlassStroke, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (isSelected) accentColor else TextMuted, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (isSelected) accentColor else TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextMuted, lineHeight = 15.sp)
        }
        // Selection indicator
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isSelected) accentColor else Color.Transparent)
                .border((1.5).dp, if (isSelected) accentColor else GlassStroke, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Filled.Check, null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}


@Composable
private fun StatusBadge(isConfigured: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isConfigured) GreenAccent.copy(alpha = 0.12f) else OrangeAccent.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (isConfigured) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (isConfigured) GreenAccent else OrangeAccent,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = if (isConfigured) "Configured" else "Fix Now",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isConfigured) GreenAccent else OrangeAccent
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String, accentColor: Color) {
    Row(
        modifier          = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(accentColor))
        Text(
            text.uppercase(),
            fontSize      = 10.sp,
            fontWeight    = FontWeight.ExtraBold,
            color         = accentColor,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp), spotColor = CardShadowColor)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, GlassStroke, RoundedCornerShape(14.dp)),
        content  = content
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = GlassStroke, thickness = 0.5.dp)
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.1f))
                .border(1.dp, iconTint.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, color = TextMuted, lineHeight = 16.sp)
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(Icons.Filled.ChevronRight, null, tint = TextMuted.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────
private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private suspend fun doLogout(context: Context, auth: AuthManager) {
    withContext(Dispatchers.IO) {
        val db = com.consistencygridwallpaper.storage.room.AppDatabase.getDatabase(context)
        try {
            db.clearAllTables()
            db.openHelper.writableDatabase.execSQL("DELETE FROM sqlite_sequence")
        } catch (e: Exception) {
            AppLogger.e("SettingsScreen", "Wiping database tables on logout failed", e)
        }
    }
    auth.logout()
    val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(context)
    userPrefs.saveOnboardedStatus(true)
    val intent = Intent(context, com.consistencygridwallpaper.ui.compose.NativeAppActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("from_logout", true)
        putExtra("bypass_onboarding", true)
        putExtra("open_screen", "dashboard")
    }
    context.startActivity(intent)
}

private fun copyUriToInternal(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = File(context.filesDir, "profile_photo.jpg")
        val outputStream = FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        AppLogger.e("Settings", "Failed to copy profile image: ${e.message}")
        null
    }
}
