package com.consistencygridwallpaper.ui.compose.subscription

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.consistencygridwallpaper.billing.BillingManager
import com.consistencygridwallpaper.ui.compose.theme.*
import kotlinx.coroutines.delay

// ─── Dark palette used throughout the paywall ─────────────────────────────────
private val DarkBg       = Color(0xFF0D0D12)
private val DarkCard     = Color(0xFF1A1A24)
private val DarkCard2    = Color(0xFF13131C)
private val DarkBorder   = Color(0xFF2A2A38)
private val GoldPremium  = Color(0xFFFFD700)
private val GoldSoft     = Color(0xFFE5B800)
private val GoldDim2     = Color(0x28FFD700)

// ─────────────────────────────────────────────────────────────────────────────
// Main Paywall Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SubscriptionScreen(
    vm: SubscriptionViewModel = viewModel(),
    onDismiss: () -> Unit = {}
) {
    val context        = LocalContext.current
    val activity       = context as? Activity
    val status         by vm.subscriptionStatus.collectAsStateWithLifecycle()
    val plans          by vm.availablePlans.collectAsStateWithLifecycle()
    val purchaseState  by vm.purchaseState.collectAsStateWithLifecycle()
    val billingReady   by vm.billingReady.collectAsStateWithLifecycle()

    var selectedPlan   by remember { mutableStateOf(BillingManager.PRODUCT_YEARLY) } // Yearly pre-selected
    var showSuccess    by remember { mutableStateOf(false) }

    // Navigate away if already Pro
    LaunchedEffect(status.isPro) {
        if (status.isPro && !showSuccess) {
            showSuccess = true
            delay(1800)
            onDismiss()
        }
    }

    // Handle purchase state changes
    LaunchedEffect(purchaseState) {
        when (purchaseState) {
            is PurchaseState.Success, is PurchaseState.Restored -> {
                showSuccess = true
                delay(1800)
                vm.resetPurchaseState()
                onDismiss()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Hero Section ──────────────────────────────────────────────────
            HeroSection(onDismiss = onDismiss)

            Spacer(Modifier.height(8.dp))

            // ── Features Grid ────────────────────────────────────────────────
            FeaturesSection()

            Spacer(Modifier.height(28.dp))

            // ── Plan Cards ───────────────────────────────────────────────────
            PlansSection(
                selectedPlan   = selectedPlan,
                onSelectPlan   = { selectedPlan = it },
                plans          = plans,
                getPrice       = { vm.getPricingText(it) }
            )

            Spacer(Modifier.height(24.dp))

            // ── CTA Button ───────────────────────────────────────────────────
            CtaSection(
                purchaseState = purchaseState,
                billingReady  = billingReady,
                onSubscribe   = {
                    activity?.let { vm.subscribe(it, selectedPlan) }
                },
                onRestore     = { vm.restorePurchases() }
            )

            Spacer(Modifier.height(24.dp))

            // ── Footer ────────────────────────────────────────────────────────
            FooterSection()

            Spacer(Modifier.height(32.dp))
        }

        // ── Error Snackbar ────────────────────────────────────────────────────
        if (purchaseState is PurchaseState.Error) {
            ErrorBanner(
                message = (purchaseState as PurchaseState.Error).message,
                onDismiss = { vm.resetPurchaseState() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // ── Success Overlay ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showSuccess,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut()
        ) {
            SuccessOverlay()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1A1200),
                            Color(0xFF2A1E00),
                            DarkBg
                        )
                    )
                )
        )

        // Radial glow at top center
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-40).dp)
                .background(
                    Brush.radialGradient(
                        listOf(GoldPremium.copy(0.15f), Color.Transparent)
                    )
                )
        )

        // Close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Close",
                tint = Color.White.copy(0.6f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated crown
            AnimatedCrownIcon()

            Spacer(Modifier.height(16.dp))

            Text(
                "Unlock Full Power",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Unlimited habits, premium themes,\nLife in Weeks, and much more.",
                color = Color.White.copy(0.65f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun AnimatedCrownIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "crown")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crown_scale"
    )
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crown_glow"
    )

    Box(
        modifier = Modifier
            .size(90.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        GoldPremium.copy(glow * 0.3f),
                        GoldPremium.copy(0.08f)
                    )
                )
            )
            .border(1.5.dp, GoldPremium.copy(glow * 0.6f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.WorkspacePremium,
            contentDescription = null,
            tint = GoldPremium,
            modifier = Modifier.size(46.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Features Grid
// ─────────────────────────────────────────────────────────────────────────────

private data class FeatureItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val iconTint: Color
)

@Composable
private fun FeaturesSection() {
    val features = remember {
        listOf(
            FeatureItem(Icons.Rounded.AllInclusive,      "Unlimited Habits",      "No 3-habit cap",           OrangeAccent),
            FeatureItem(Icons.Rounded.TrackChanges,      "Unlimited Goals",       "Track everything",         VioletAccent),
            FeatureItem(Icons.Rounded.HourglassBottom,   "Life in Weeks",         "See your life as a grid",  GoldSoft),
            FeatureItem(Icons.Rounded.Palette,           "All Themes",            "Premium color palettes",   SkyAccent),
            FeatureItem(Icons.Rounded.AddPhotoAlternate, "Custom Background",     "Your photo as wallpaper",  GreenAccent),
            FeatureItem(Icons.Rounded.GridView,          "All Grid Layouts",      "5 layout modes",           OrangeAccent),
            FeatureItem(Icons.Rounded.BarChart,          "Full Year Heatmap",     "365-day history",          VioletAccent),
            FeatureItem(Icons.Rounded.DarkMode,          "Bedtime Wallpaper",     "Grayscale in sleep mode",  SkyAccent),
            FeatureItem(Icons.Rounded.Widgets,           "Home Widget",           "Glanceable stats widget",  GreenAccent),
            FeatureItem(Icons.Rounded.Notifications,     "Smart Reminders",       "Multiple per habit",       GoldSoft),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "Everything in Pro",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        features.chunked(2).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { feature ->
                    FeatureCard(feature, Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FeatureCard(feature: FeatureItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(feature.iconTint.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(feature.icon, null, tint = feature.iconTint, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(feature.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(feature.subtitle, color = Color.White.copy(0.5f), fontSize = 10.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Plan Cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlansSection(
    selectedPlan: String,
    onSelectPlan: (String) -> Unit,
    plans: List<com.android.billingclient.api.ProductDetails>,
    getPrice: (String) -> String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "Choose Your Plan",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Yearly Plan (recommended)
        PlanCard(
            productId    = BillingManager.PRODUCT_YEARLY,
            title        = "Yearly",
            price        = getPrice(BillingManager.PRODUCT_YEARLY) ?: "₹499/year",
            perMonth     = "Just ₹41/month",
            badge        = "BEST VALUE · SAVE 15%",
            isSelected   = selectedPlan == BillingManager.PRODUCT_YEARLY,
            isRecommended = true,
            onClick      = { onSelectPlan(BillingManager.PRODUCT_YEARLY) }
        )

        Spacer(Modifier.height(12.dp))

        // Monthly Plan
        PlanCard(
            productId    = BillingManager.PRODUCT_MONTHLY,
            title        = "Monthly",
            price        = getPrice(BillingManager.PRODUCT_MONTHLY) ?: "₹49/month",
            perMonth     = "Billed monthly",
            badge        = null,
            isSelected   = selectedPlan == BillingManager.PRODUCT_MONTHLY,
            isRecommended = false,
            onClick      = { onSelectPlan(BillingManager.PRODUCT_MONTHLY) }
        )
    }
}

@Composable
private fun PlanCard(
    productId: String,
    title: String,
    price: String,
    perMonth: String,
    badge: String?,
    isSelected: Boolean,
    isRecommended: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) GoldPremium else DarkBorder
    val bgColor     = if (isSelected) GoldDim2 else DarkCard
    val scale by animateFloatAsState(if (isSelected) 1.02f else 1f, label = "plan_scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
    ) {
        // Recommended badge
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-12).dp, y = (-10).dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(listOf(GoldSoft, GoldPremium))
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(badge, color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(bgColor)
                .border(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable { onClick() }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Radio button
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            if (isSelected) GoldPremium else Color.White.copy(0.3f),
                            CircleShape
                        )
                        .background(if (isSelected) GoldPremium else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    }
                }

                Column {
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(perMonth, color = Color.White.copy(0.55f), fontSize = 12.sp)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    price,
                    color = if (isSelected) GoldPremium else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CTA Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CtaSection(
    purchaseState: PurchaseState,
    billingReady: Boolean,
    onSubscribe: () -> Unit,
    onRestore: () -> Unit
) {
    val isLoading = purchaseState is PurchaseState.Loading
    val scale by animateFloatAsState(if (isLoading) 0.97f else 1f, label = "cta_scale")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main subscribe button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .height(60.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (!isLoading && billingReady)
                        Brush.horizontalGradient(listOf(GoldSoft, GoldPremium))
                    else
                        Brush.horizontalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF2A2A2A)))
                )
                .clickable(enabled = !isLoading && billingReady) { onSubscribe() },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Processing…", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.WorkspacePremium,
                        null,
                        tint = if (billingReady) Color.Black else Color.White.copy(0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        if (billingReady) "Start Pro" else "Loading Plans…",
                        color = if (billingReady) Color.Black else Color.White.copy(0.4f),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Cancel anytime from Play Store",
            color = Color.White.copy(0.4f),
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))

        // Restore button
        TextButton(onClick = onRestore, enabled = !isLoading) {
            Icon(
                Icons.Rounded.Restore,
                null,
                tint = OrangeAccent.copy(0.8f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Restore Previous Purchase",
                color = OrangeAccent.copy(0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Footer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FooterSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(bottom = 16.dp))

        Text(
            "Subscription auto-renews until cancelled.\n" +
            "Manage or cancel anytime in Google Play Store.\n" +
            "Payment charged to your Google Play account.",
            color = Color.White.copy(0.35f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B1B)),
        border = BorderStroke(1.dp, RoseAccent.copy(0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = RoseAccent, modifier = Modifier.size(20.dp))
            Text(message, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Close, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Success Overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuccessOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(GoldPremium.copy(0.3f), Color.Transparent))
                    )
                    .border(2.dp, GoldPremium, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    null,
                    tint = GoldPremium,
                    modifier = Modifier.size(52.dp)
                )
            }
            Text("You're Pro! 🎉", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Welcome to the full experience", color = Color.White.copy(0.6f), fontSize = 15.sp)
        }
    }
}
