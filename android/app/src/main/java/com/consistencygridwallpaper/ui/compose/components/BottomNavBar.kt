package com.consistencygridwallpaper.ui.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.consistencygridwallpaper.ui.compose.theme.*

data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    NavItem("dashboard", "Home",     Icons.Filled.Home,               Icons.Outlined.Home),
    NavItem("habits",    "Habits",   Icons.Filled.FormatListBulleted,  Icons.Outlined.FormatListBulleted),
    NavItem("goals",     "Goals",    Icons.Filled.Flag,               Icons.Outlined.Flag),
    NavItem("streaks",   "Analytics",Icons.Filled.BarChart,           Icons.Outlined.BarChart),
    NavItem("reminders", "Alerts",   Icons.Filled.Notifications,      Icons.Outlined.NotificationsNone),
    NavItem("settings",  "Settings", Icons.Filled.Settings,           Icons.Outlined.Settings),
)

@Composable
fun BottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation    = 8.dp,
                shape        = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                spotColor    = CardShadowColor,
                ambientColor = CardShadowColor
            )
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(BgCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route
                NavBarItem(
                    item       = item,
                    isSelected = isSelected,
                    onClick    = {
                        if (!isSelected) {
                            navController.navigate(item.route) {
                                popUpTo("dashboard") { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconTint by animateColorAsState(
        targetValue   = if (isSelected) OrangeAccent else TextMuted,
        animationSpec = tween(durationMillis = 200),
        label         = "iconTint"
    )
    val scale by animateFloatAsState(
        targetValue   = if (isSelected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            )
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Icon(
                imageVector        = if (isSelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                tint               = iconTint,
                modifier           = Modifier
                    .size(24.dp)
                    .scale(scale)
            )
        }
        Spacer(Modifier.height(3.dp))
        // Orange dot indicator under selected item
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(OrangeAccent)
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text       = item.label,
            fontSize   = 9.5.sp,
            color      = iconTint,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
