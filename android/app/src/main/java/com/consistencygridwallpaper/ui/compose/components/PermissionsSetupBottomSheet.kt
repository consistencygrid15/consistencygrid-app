package com.consistencygridwallpaper.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consistencygridwallpaper.ui.compose.theme.*
import com.consistencygridwallpaper.utils.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSetupBottomSheet(
    onDismissRequest: () -> Unit,
    onRequestNotification: () -> Unit
) {
    val context = LocalContext.current

    var isNotificationGranted by remember { mutableStateOf(PermissionUtils.isNotificationGranted(context)) }
    var isExactAlarmGranted by remember { mutableStateOf(PermissionUtils.isExactAlarmGranted(context)) }
    var isBatteryOptimizationIgnored by remember { mutableStateOf(PermissionUtils.isBatteryOptimizationIgnored(context)) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(48.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(GlassStroke)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Header Icon & Title ──────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(OrangeMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = OrangeAccent,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column {
                    Text(
                        text = "Background Updates & Permissions",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "Allow background execution for 12:00 AM updates",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Card 1: Notification & Alarms ───────────────────────────────
            PermissionCard(
                icon = Icons.Default.Notifications,
                title = "Notifications & Midnight Alarm",
                description = "Allows midnight update notifications and exact alarm triggers.",
                isGranted = isNotificationGranted && isExactAlarmGranted,
                buttonText = if (!isNotificationGranted) "Allow Notifications" else if (!isExactAlarmGranted) "Exact Alarm Permission" else "Allowed ✅",
                onClick = {
                    if (!isNotificationGranted) {
                        onRequestNotification()
                    } else if (!isExactAlarmGranted) {
                        PermissionUtils.openExactAlarmSettings(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Card 2: Unrestricted Battery Saver ─────────────────────────
            PermissionCard(
                icon = Icons.Default.BatteryFull,
                title = "Unrestricted Battery Saver",
                description = "Keeps CPU active during midnight wallpaper generation.",
                isGranted = isBatteryOptimizationIgnored,
                buttonText = if (isBatteryOptimizationIgnored) "Unrestricted ✅" else "Set Unrestricted",
                onClick = {
                    PermissionUtils.openBatteryOptimizationSettings(context)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Card 3: Auto-Start (Xiaomi, Oppo, Vivo, Samsung) ────────────
            PermissionCard(
                icon = Icons.Default.PowerSettingsNew,
                title = "Auto-Start Permission",
                description = "Prevents OEM battery saver (MIUI, ColorOS) from killing background sync.",
                isGranted = false,
                buttonText = "Enable Auto-Start",
                onClick = {
                    PermissionUtils.openAutostartSettings(context)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Done / Save Button ───────────────────────────────────────────
            Button(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrangeAccent,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Done & Continue",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgBase),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassStroke)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) GreenAccent else TextSecondary,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isGranted) GreenAccent else OrangeAccent
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isGranted) GreenAccent.copy(alpha = 0.5f) else OrangeAccent.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = buttonText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
