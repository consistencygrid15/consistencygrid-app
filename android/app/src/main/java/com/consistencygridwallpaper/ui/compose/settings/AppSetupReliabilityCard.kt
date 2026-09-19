package com.consistencygridwallpaper.ui.compose.settings

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
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
 * AppSetupReliabilityCard — Modular component for settings reliability check.
 * Displays live permission status (Unrestricted Battery, Exact Alarms, Notifications, OEM Autostart)
 * and automatically refreshes when user returns from Android system settings.
 */
@Composable
fun AppSetupReliabilityCard(
    modifier: Modifier = Modifier
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp), spotColor = CardShadowColor)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(
                1.dp,
                if (allReliabilityConfigured) GreenAccent.copy(alpha = 0.3f) else OrangeAccent.copy(alpha = 0.3f),
                RoundedCornerShape(14.dp)
            )
    ) {
        // Header Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (allReliabilityConfigured) GreenAccent.copy(alpha = 0.08f) else OrangeAccent.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (allReliabilityConfigured) GreenAccent.copy(alpha = 0.2f) else OrangeAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (allReliabilityConfigured) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (allReliabilityConfigured) GreenAccent else OrangeAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (allReliabilityConfigured) "100% Ready for Midnight Updates" else "Optimal Settings Needed ($passedChecks/$totalRequiredChecks)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (allReliabilityConfigured) GreenAccent else OrangeAccent
                )
                Text(
                    text = if (allReliabilityConfigured) "System permissions configured for seamless daily updates" else "Configure settings below so wallpaper updates never get delayed",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        HorizontalDivider(color = GlassStroke, thickness = 0.5.dp)

        // Row 1: Unrestricted Battery
        SetupRow(
            icon = Icons.Filled.Power,
            iconTint = if (isBatteryIgnored) GreenAccent else OrangeAccent,
            title = "Unrestricted Battery Mode",
            subtitle = if (isBatteryIgnored) "Unrestricted — Wallpaper worker won't be killed by system" else "Optimized — May delay midnight updates. Tap to allow unrestricted.",
            isConfigured = isBatteryIgnored,
            onClick = {
                PermissionUtils.openBatteryOptimizationSettings(context)
            }
        )

        // Row 2: Exact Midnight Alarm (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = GlassStroke, thickness = 0.5.dp)
            SetupRow(
                icon = Icons.Filled.Alarm,
                iconTint = if (isExactAlarmGranted) GreenAccent else OrangeAccent,
                title = "Exact Midnight Alarm",
                subtitle = if (isExactAlarmGranted) "Granted — Fires exactly at 12:00 AM" else "Not Allowed — Tap to grant exact alarm permission",
                isConfigured = isExactAlarmGranted,
                onClick = {
                    PermissionUtils.openExactAlarmSettings(context)
                }
            )
        }

        // Row 3: Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = GlassStroke, thickness = 0.5.dp)
            SetupRow(
                icon = Icons.Filled.Notifications,
                iconTint = if (isNotificationGranted) GreenAccent else SkyAccent,
                title = "Notifications",
                subtitle = if (isNotificationGranted) "Enabled — Receive daily streak & update alerts" else "Disabled — Tap to enable notifications",
                isConfigured = isNotificationGranted,
                onClick = {
                    if (!isNotificationGranted) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        PermissionUtils.openAppSettings(context)
                    }
                }
            )
        }

        // Row 4: OEM Autostart / Background Run
        if (isOemPhone) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = GlassStroke, thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { PermissionUtils.openAutostartSettings(context) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SkyAccent.copy(alpha = 0.1f))
                        .border(1.dp, SkyAccent.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Build, null, tint = SkyAccent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Autostart & Background Run", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("Enable for ${manufacturer.replaceFirstChar { it.uppercase() }} to prevent auto-kill", fontSize = 12.sp, color = TextMuted, lineHeight = 16.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SkyAccent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Setup", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SkyAccent)
                    Icon(Icons.Filled.ChevronRight, null, tint = SkyAccent, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SetupRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isConfigured: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
            Text(subtitle, fontSize = 12.sp, color = TextMuted, lineHeight = 16.sp)
        }
        StatusBadge(isConfigured = isConfigured)
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
