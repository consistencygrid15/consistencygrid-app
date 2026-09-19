package com.consistencygridwallpaper.ui.compose.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.consistencygridwallpaper.ui.compose.theme.*
import com.consistencygridwallpaper.utils.PermissionUtils

/**
 * AppSetupSheet — Dedicated page for App Permissions & System Status.
 * Clearly displays ACCEPTED ✅ vs NOT ACCEPTED ❌ status for all app permissions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSetupSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkTrigger++
    }

    val manufacturer = remember { Build.MANUFACTURER.lowercase(java.util.Locale.US) }
    val isOemPhone = remember {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ||
        manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("vivo") ||
        manufacturer.contains("oneplus") || manufacturer.contains("huawei") || manufacturer.contains("honor") ||
        manufacturer.contains("samsung")
    }

    val totalRequiredChecks = remember {
        var count = 1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) count++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) count++
        count
    }

    val passedChecks = remember(checkTrigger) {
        var count = 0
        if (isBatteryIgnored) count++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isExactAlarmGranted) count++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isNotificationGranted) count++
        count
    }
    val allReliabilityConfigured = passedChecks == totalRequiredChecks

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = if (allReliabilityConfigured) GreenAccent.copy(alpha = 0.5f) else OrangeAccent.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (allReliabilityConfigured) GreenAccent.copy(alpha = 0.15f) else OrangeAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (allReliabilityConfigured) Icons.Filled.VerifiedUser else Icons.Filled.Security,
                            contentDescription = null,
                            tint = if (allReliabilityConfigured) GreenAccent else OrangeAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text("App Permissions & Status", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Inspect accepted & pending system permissions", fontSize = 12.sp, color = TextMuted)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            // Summary Status Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (allReliabilityConfigured) GreenAccent.copy(alpha = 0.1f) else OrangeAccent.copy(alpha = 0.1f))
                    .border(1.dp, if (allReliabilityConfigured) GreenAccent.copy(alpha = 0.3f) else OrangeAccent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = if (allReliabilityConfigured) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (allReliabilityConfigured) GreenAccent else OrangeAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (allReliabilityConfigured) "All Permissions Granted ($passedChecks/$totalRequiredChecks)" else "Permissions Pending ($passedChecks/$totalRequiredChecks Granted)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (allReliabilityConfigured) GreenAccent else OrangeAccent
                        )
                        Text(
                            text = if (allReliabilityConfigured) "Your app has all required permissions for automatic daily wallpaper & widget updates."
                            else "Some system permissions are pending. Grant them below to enable automatic midnight wallpaper updates.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Text("SYSTEM PERMISSIONS LIST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.2.sp)

            // 1. Unrestricted Battery Mode
            SetupItemCard(
                icon = Icons.Filled.Power,
                iconTint = if (isBatteryIgnored) GreenAccent else OrangeAccent,
                title = "Unrestricted Battery Mode",
                subtitle = "Allows background wallpaper render worker to run at midnight without being stopped by Android OS.",
                isGranted = isBatteryIgnored,
                buttonText = if (isBatteryIgnored) "Open Settings ➔" else "Grant Permission ➔",
                onClick = { PermissionUtils.openBatteryOptimizationSettings(context) }
            )

            // 2. Exact Alarm (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SetupItemCard(
                    icon = Icons.Filled.Alarm,
                    iconTint = if (isExactAlarmGranted) GreenAccent else OrangeAccent,
                    title = "Exact Midnight Alarm Permission",
                    subtitle = "Required on Android 12+ to trigger the exact 12:00 AM update alarm on schedule.",
                    isGranted = isExactAlarmGranted,
                    buttonText = if (isExactAlarmGranted) "Open Settings ➔" else "Grant Permission ➔",
                    onClick = { PermissionUtils.openExactAlarmSettings(context) }
                )
            }

            // 3. Notifications (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SetupItemCard(
                    icon = Icons.Filled.Notifications,
                    iconTint = if (isNotificationGranted) GreenAccent else SkyAccent,
                    title = "Notification Permission",
                    subtitle = "Allows app to send streak alerts, wallpaper updates confirmation & daily reminders.",
                    isGranted = isNotificationGranted,
                    buttonText = if (isNotificationGranted) "Open Settings ➔" else "Allow Notifications ➔",
                    onClick = {
                        if (!isNotificationGranted) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PermissionUtils.openAppSettings(context)
                        }
                    }
                )
            }

            // 4. OEM Autostart (Xiaomi, Samsung, Oppo, Vivo, etc.)
            if (isOemPhone) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgMuted)
                        .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SkyAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Build, null, tint = SkyAccent, modifier = Modifier.size(18.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Autostart & Background Permission", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                }
                                Text("Special permission for ${manufacturer.replaceFirstChar { it.uppercase() }} devices", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Text(
                            "${manufacturer.replaceFirstChar { it.uppercase() }} phones kill background apps by default. Enable Autostart to ensure wallpaper updates work every night.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 15.sp
                        )
                        Button(
                            onClick = { PermissionUtils.openAutostartSettings(context) },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SkyAccent.copy(alpha = 0.15f), contentColor = SkyAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Open ${manufacturer.replaceFirstChar { it.uppercase() }} Autostart Settings", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun SetupItemCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgMuted)
            .border(1.dp, if (isGranted) GreenAccent.copy(alpha = 0.3f) else GlassStroke, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    
                    // Clear Status Badge (ACCEPTED vs NOT ACCEPTED)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isGranted) GreenAccent.copy(alpha = 0.15f) else RedAccent.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isGranted) "ACCEPTED ✅" else "NOT ACCEPTED ❌",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGranted) GreenAccent else RedAccent
                                )
                            }
                        }
                    }
                }
            }
            Text(subtitle, fontSize = 11.sp, color = TextMuted, lineHeight = 15.sp)
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isGranted) GreenAccent else OrangeAccent
                ),
                border = BorderStroke(1.dp, if (isGranted) GreenAccent.copy(alpha = 0.5f) else OrangeAccent.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

