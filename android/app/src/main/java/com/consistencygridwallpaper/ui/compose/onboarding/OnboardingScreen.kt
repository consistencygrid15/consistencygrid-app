package com.consistencygridwallpaper.ui.compose.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.storage.room.AppDatabase
import com.consistencygridwallpaper.storage.room.UserProfileEntity
import com.consistencygridwallpaper.ui.compose.theme.*
import com.consistencygridwallpaper.ui.compose.wallpaper.WallpaperSettings
import com.consistencygridwallpaper.ui.compose.wallpaper.toJson
import com.consistencygridwallpaper.ui.compose.wallpaper.wallpaperSettingsFromJson
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONObject
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ─── Data Model ──────────────────────────────────────────────────────────────

private data class OnboardSlide(
    val emoji:       String,
    val title:       String,
    val subtitle:    String,
    val description: String,
    val accentColor: Color,
    val glowColor:   Color,
    val features:    List<Pair<String, String>> // icon-emoji + label
)

private val SLIDES = listOf(
    OnboardSlide(
        emoji       = "🎯",
        title       = "Welcome to\nConsistencyGrid",
        subtitle    = "Turn habits into your wallpaper",
        description = "The only habit tracker that makes your progress impossible to ignore — it lives right on your lock screen.",
        accentColor = OrangeAccent,
        glowColor   = OrangeGlow,
        features    = listOf(
            "🔥" to "Build streaks",
            "📊" to "Track daily",
            "🖼️" to "Live wallpaper"
        )
    ),
    OnboardSlide(
        emoji       = "✅",
        title       = "Track Habits\nEvery Day",
        subtitle    = "Small steps, massive results",
        description = "Log your habits with a single tap. Watch your streak grow day by day. Miss a day? We'll help you bounce back.",
        accentColor = GreenAccent,
        glowColor   = GreenDim,
        features    = listOf(
            "⚡" to "One-tap logging",
            "📅" to "Daily reminders",
            "🏅" to "Streak rewards"
        )
    ),
    OnboardSlide(
        emoji       = "🏆",
        title       = "Set Goals &\nCrush Them",
        subtitle    = "Dream big. Track everything.",
        description = "Break big ambitions into trackable milestones. Every goal gets a progress bar — you'll always know where you stand.",
        accentColor = VioletAccent,
        glowColor   = VioletGlow,
        features    = listOf(
            "🚀" to "Milestone tracking",
            "📈" to "Progress bars",
            "🎯" to "Priority focus"
        )
    ),
    OnboardSlide(
        emoji       = "🛡️",
        title       = "Control Your\nScreen Time",
        subtitle    = "Take back your attention",
        description = "Reel Control tracks and limits your Instagram Reels and YouTube Shorts usage. Your focus, reclaimed.",
        accentColor = SkyAccent,
        glowColor   = SkyDim,
        features    = listOf(
            "📵" to "App limits",
            "👁️" to "Usage insights",
            "🔒" to "Soft blocking"
        )
    ),
    OnboardSlide(
        emoji       = "🌅",
        title       = "Your Grid Goes\nLive Tonight",
        subtitle    = "Auto-updates at midnight",
        description = "Every night at 12:00 AM, your wallpaper updates automatically to reflect today's consistency. Your progress, always visible.",
        accentColor = GoldAccent,
        glowColor   = GoldDim,
        features    = listOf(
            "🌙" to "Midnight refresh",
            "🖼️" to "Home & lock screen",
            "⚙️" to "Zero effort"
        )
    ),
    OnboardSlide(
        emoji       = "👤",
        title       = "Personalize Your\nConsistency Grid",
        subtitle    = "Setup Canvas Details",
        description = "Choose your date of birth and target life expectancy to compute your life grid wallpaper.",
        accentColor = OrangeAccent,
        glowColor   = OrangeGlow,
        features    = emptyList()
    ),
    OnboardSlide(
        emoji       = "",
        title       = "Setup Preferences",
        subtitle    = "Wallpaper & Widgets",
        description = "Choose where to apply your consistency grid wallpaper and add widgets to your home screen.",
        accentColor = VioletAccent,
        glowColor   = VioletGlow,
        features    = emptyList()
    )
)

// ─── Root Composable ─────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    val pagerState  = rememberPagerState(pageCount = { SLIDES.size })
    val currentPage = pagerState.currentPage
    val isLast      = currentPage == SLIDES.lastIndex
    val prefs       = remember { UserPrefs(context) }

    // Animated background gradient that morphs per-slide
    val accentForBg = SLIDES[currentPage].accentColor

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {

        // ── Ambient background orb ─────────────────────────────────────────
        AmbientOrb(
            accentColor = accentForBg,
            modifier    = Modifier.fillMaxSize()
        )

        // ── Decorative top-right glow ──────────────────────────────────────
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-60).dp)
                .drawBehind {
                    drawCircle(
                        brush  = Brush.radialGradient(
                            colors = listOf(
                                accentForBg.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2f
                    )
                }
        )

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Skip button ────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Progress dots — top left
                StepIndicator(
                    total       = SLIDES.size,
                    current     = currentPage,
                    accentColor = accentForBg
                )

                if (currentPage < 5) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(5) // Jumps to mandatory Profile Setup (page 5)
                            }
                        }
                    ) {
                        Text(
                            "Skip",
                            color      = TextSecondary,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Spacer(Modifier.width(60.dp))
                }
            }

            // ── Pager ──────────────────────────────────────────────────────
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val slide       = SLIDES[page]
                val pageOffset  = (pagerState.currentPage - page) +
                    pagerState.currentPageOffsetFraction
                val scale by animateFloatAsState(
                    targetValue   = if (abs(pageOffset) < 0.5f) 1f else 0.92f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label         = "pageScale"
                )
                val alpha by animateFloatAsState(
                    targetValue   = if (abs(pageOffset) < 0.5f) 1f else 0.55f,
                    animationSpec = tween(300),
                    label         = "pageAlpha"
                )

                if (page == 5) {
                    ProfileSetupSlide(
                        accentColor = slide.accentColor,
                        prefs       = prefs,
                        modifier    = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX      = scale
                                scaleY      = scale
                                this.alpha  = alpha
                            }
                    )
                } else if (page == 6) {
                    PreferencesSetupSlide(
                        accentColor = slide.accentColor,
                        prefs       = prefs,
                        modifier    = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX      = scale
                                scaleY      = scale
                                this.alpha  = alpha
                            }
                    )
                } else {
                    SlideContent(
                        slide    = slide,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX      = scale
                                scaleY      = scale
                                this.alpha  = alpha
                            }
                    )
                }
            }

            // ── Bottom CTA ─────────────────────────────────────────────────
            BottomCta(
                isLast      = isLast,
                accentColor = accentForBg,
                onNext      = {
                    scope.launch {
                        pagerState.animateScrollToPage(currentPage + 1)
                    }
                },
                onComplete  = {
                    UserPrefs(context).saveOnboardedStatus(true)
                    onComplete()
                }
            )
        }
    }
}

// ─── Profile Setup Slide ──────────────────────────────────────────────────────

@Composable
private fun ProfileSetupSlide(
    accentColor: Color,
    prefs: UserPrefs,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val userProfile by db.userProfileDao().getFlow().collectAsState(initial = null)

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("1998-01-01") }
    var lifeExpectancy by remember { mutableStateOf(80) }

    LaunchedEffect(userProfile) {
        userProfile?.let {
            if (name.isBlank() && it.name.isNotBlank()) name = it.name
            if (email.isBlank() && it.email.isNotBlank()) email = it.email
        }
    }

    LaunchedEffect(Unit) {
        val saved = prefs.getWallpaperSettings()
        if (saved != null) {
            runCatching {
                val s = wallpaperSettingsFromJson(JSONObject(saved))
                if (s.dob.isNotBlank()) dob = s.dob
                lifeExpectancy = s.lifeExpectancyYears
            }
        }
    }

    val saveProfile = { newName: String, newEmail: String, newDob: String, newLife: Int ->
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val current = db.userProfileDao().get() ?: UserProfileEntity(id = 1)
            db.userProfileDao().upsert(
                current.copy(
                    name = newName,
                    email = newEmail,
                    updatedAt = System.currentTimeMillis()
                )
            )
            val saved = prefs.getWallpaperSettings() ?: "{}"
            val s = runCatching { wallpaperSettingsFromJson(JSONObject(saved)) }.getOrDefault(WallpaperSettings())
            val updated = s.copy(dob = newDob, lifeExpectancyYears = newLife)
            prefs.saveWallpaperSettings(updated.toJson())
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        FloatingEmojiOrb(
            emoji       = "👤",
            accentColor = accentColor,
            glowColor   = OrangeGlow
        )

        Text(
            text = "Personalize Your\nConsistency Grid",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )

        Text(
            text = "Your details are used to compute your personalized life grid and sync progress across devices.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(4.dp))

        // Profile Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Profile Information",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Name Input
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    saveProfile(name, email, dob, lifeExpectancy)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your name", color = TextMuted) },
                label = { Text("Your Name", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Filled.Person, null, tint = accentColor, modifier = Modifier.size(20.dp)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = GlassStroke,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = accentColor,
                    focusedContainerColor = BgCard,
                    unfocusedContainerColor = BgCard
                ),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
        }

        // DOB Picker Button
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Date of Birth", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            
            val calendar = Calendar.getInstance()
            val datePickerDialog = android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val selectedCal = Calendar.getInstance()
                    selectedCal.set(year, month, dayOfMonth)
                    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    dob = format.format(selectedCal.time)
                    saveProfile(name, email, dob, lifeExpectancy)
                },
                calendar.get(Calendar.YEAR) - 25,
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                    .clickable { datePickerDialog.show() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = accentColor, modifier = Modifier.size(20.dp))
                    Text(dob, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
                Text("Select", fontSize = 13.sp, color = accentColor, fontWeight = FontWeight.Bold)
            }
        }

        // Life Expectancy Slider
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Target Life Expectancy", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("$lifeExpectancy years", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accentColor)
            }

            Slider(
                value = lifeExpectancy.toFloat(),
                onValueChange = {
                    lifeExpectancy = it.toInt()
                    saveProfile(name, email, dob, lifeExpectancy)
                },
                valueRange = 50f..110f,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = BgMuted
                )
            )
            
            Text(
                text = "Used to render the total number of weeks in your life grid.",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        if (userProfile != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GreenAccent.copy(alpha = 0.12f))
                    .border(0.5.dp, GreenAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = GreenAccent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Profile Synced",
                    fontSize = 11.sp,
                    color = GreenAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ─── Slide Content ───────────────────────────────────────────────────────────

@Composable
private fun SlideContent(slide: OnboardSlide, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier
            .padding(horizontal = 24.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // ── Big emoji icon with glowing ring ──────────────────────────────
        FloatingEmojiOrb(
            emoji       = slide.emoji,
            accentColor = slide.accentColor,
            glowColor   = slide.glowColor
        )

        Spacer(Modifier.height(28.dp))

        // ── Title ──────────────────────────────────────────────────────────
        Text(
            text       = slide.title,
            fontSize   = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = TextPrimary,
            textAlign  = TextAlign.Center,
            lineHeight = 38.sp
        )

        Spacer(Modifier.height(6.dp))

        // ── Subtitle pill ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(slide.accentColor.copy(alpha = 0.15f))
                .border(1.dp, slide.accentColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text       = slide.subtitle,
                color      = slide.accentColor,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Description ────────────────────────────────────────────────────
        Text(
            text       = slide.description,
            fontSize   = 15.sp,
            color      = TextSecondary,
            textAlign  = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(24.dp))

        // ── Feature chips ──────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            slide.features.forEach { (emoji, label) ->
                FeatureChip(
                    emoji       = emoji,
                    label       = label,
                    accentColor = slide.accentColor
                )
            }
        }
    }
}

// ─── Sub-Components ──────────────────────────────────────────────────────────

@Composable
private fun FloatingEmojiOrb(
    emoji:       String,
    accentColor: Color,
    glowColor:   Color
) {
    // Pulsing scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue   = 0.97f,
        targetValue    = 1.03f,
        animationSpec  = infiniteRepeatable(
            animation     = tween(2000, easing = FastOutSlowInEasing),
            repeatMode    = RepeatMode.Reverse
        ),
        label          = "pulse"
    )
    val rotAnim by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing)
        ),
        label         = "rot"
    )

    Box(
        modifier         = Modifier
            .size(140.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring (rotating dashes)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 8f
            drawCircle(
                brush  = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    radius = radius
                ),
                radius = radius
            )
            drawArc(
                brush      = Brush.sweepGradient(
                    listOf(Color.Transparent, accentColor.copy(alpha = 0.7f), Color.Transparent)
                ),
                startAngle = rotAnim,
                sweepAngle = 220f,
                useCenter  = false,
                style      = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )
        }

        // Inner orb
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f),
                            BgCard.copy(alpha = 0.95f)
                        )
                    )
                )
                .border(
                    width  = 1.5.dp,
                    brush  = Brush.sweepGradient(
                        listOf(
                            accentColor.copy(alpha = 0.6f),
                            accentColor.copy(alpha = 0.1f),
                            accentColor.copy(alpha = 0.6f)
                        )
                    ),
                    shape  = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 44.sp)
        }
    }
}

@Composable
private fun FeatureChip(emoji: String, label: String, accentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.10f))
            .border(1.dp, accentColor.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(emoji, fontSize = 20.sp)
            Text(
                label,
                fontSize   = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color      = accentColor,
                textAlign  = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StepIndicator(total: Int, current: Int, accentColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            val isActive  = index == current
            val isPast    = index < current
            val dotWidth by animateDpAsState(
                targetValue   = if (isActive) 24.dp else 6.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label         = "dotW"
            )
            val dotColor by animateColorAsState(
                targetValue   = when {
                    isActive -> accentColor
                    isPast   -> accentColor.copy(alpha = 0.5f)
                    else     -> TextMuted.copy(alpha = 0.3f)
                },
                animationSpec = tween(300),
                label         = "dotColor"
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(dotWidth)
                    .clip(CircleShape)
                    .background(color = dotColor)
            )
        }
    }
}

@Composable
private fun BottomCta(
    isLast:      Boolean,
    accentColor: Color,
    onNext:      () -> Unit,
    onComplete:  () -> Unit
) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLast) {
            // ── Get Started (gradient) ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(OrangeAccent, VioletAccent)
                        )
                    )
                    .clickable { onComplete() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Get Started",
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White
                    )
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            // ── Next button ────────────────────────────────────────────────
            Button(
                onClick  = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor   = Color.Black
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Next",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─── Ambient Background Orb ──────────────────────────────────────────────────

@Composable
private fun AmbientOrb(accentColor: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambientOrb")
    val yShift by infiniteTransition.animateFloat(
        initialValue  = -30f,
        targetValue   = 30f,
        animationSpec = infiniteRepeatable(
            animation  = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label         = "orbY"
    )

    Canvas(modifier = modifier) {
        // Bottom-left ambient orb
        drawCircle(
            brush  = Brush.radialGradient(
                colors  = listOf(accentColor.copy(alpha = 0.12f), Color.Transparent),
                center  = Offset(size.width * 0.15f, size.height * 0.75f + yShift),
                radius  = size.minDimension * 0.55f
            ),
            center = Offset(size.width * 0.15f, size.height * 0.75f + yShift),
            radius = size.minDimension * 0.55f
        )
        // Top-right subtle orb
        drawCircle(
            brush  = Brush.radialGradient(
                colors  = listOf(VioletAccent.copy(alpha = 0.07f), Color.Transparent),
                center  = Offset(size.width * 0.85f, size.height * 0.15f - yShift),
                radius  = size.minDimension * 0.45f
            ),
            center = Offset(size.width * 0.85f, size.height * 0.15f - yShift),
            radius = size.minDimension * 0.45f
        )
    }
}

// ─── Preferences Setup Slide ──────────────────────────────────────────────────

@Composable
private fun PreferencesSetupSlide(
    accentColor: Color,
    prefs: UserPrefs,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTarget by remember { mutableStateOf(prefs.getWallpaperTarget()) }
    var selectedUpdateMode by remember { mutableStateOf(prefs.getWallpaperUpdateMode()) }
    var isApplying by remember { mutableStateOf(false) }
    var mockWidgetCompleted by remember { mutableStateOf(false) }
    var mockStreak by remember { mutableStateOf(7) }

    val scrollState = rememberScrollState()
    
    // Check if widget pinning is supported
    val isPinSupported = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
        } else {
            false
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Vector Icon Orb (instead of Emoji Orb)
        FloatingIconOrb(
            icon        = Icons.Default.Settings,
            accentColor = accentColor,
            glowColor   = VioletGlow
        )

        Text(
            text = "Setup Preferences",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )

        Text(
            text = "Choose whether to update your wallpaper & widgets, or widgets only, and set your preferences.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(4.dp))

        // Simulated Phone Previews
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lock Screen Phone Frame
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Lock Screen", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(BgDeep)
                        .border(1.5.dp, GlassStroke, RoundedCornerShape(18.dp))
                ) {
                    // Wallpaper
                    val showGrid = selectedUpdateMode != UserPrefs.UPDATE_MODE_WIDGETS_ONLY && (selectedTarget == "BOTH" || selectedTarget == "LOCK")
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (showGrid) {
                                    Brush.verticalGradient(listOf(BgBase, BgDeep))
                                } else {
                                    Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
                                }
                            )
                    ) {
                        if (showGrid) {
                            // Grid wallpaper simulation pattern
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val rows = 12
                                val cols = 7
                                val w = size.width / cols
                                val h = size.height / rows
                                for (r in 0 until rows) {
                                    for (c in 0 until cols) {
                                        val color = if ((r + c) % 3 == 0) accentColor.copy(alpha = 0.4f) else BgMuted.copy(alpha = 0.2f)
                                        drawRect(
                                            color = color,
                                            topLeft = Offset(c * w + 1.dp.toPx(), r * h + 1.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(w - 2.dp.toPx(), h - 2.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Digital Clock Mock
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("09:41", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Text("Monday, July 20", fontSize = 7.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // Home Screen Phone Frame
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Home Screen", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(BgDeep)
                        .border(1.5.dp, GlassStroke, RoundedCornerShape(18.dp))
                ) {
                    // Wallpaper
                    val showGrid = selectedUpdateMode != UserPrefs.UPDATE_MODE_WIDGETS_ONLY && (selectedTarget == "BOTH" || selectedTarget == "HOME")
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (showGrid) {
                                    Brush.verticalGradient(listOf(BgBase, BgDeep))
                                } else {
                                    Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
                                }
                            )
                    ) {
                        if (showGrid) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val rows = 12
                                val cols = 7
                                val w = size.width / cols
                                val h = size.height / rows
                                for (r in 0 until rows) {
                                    for (c in 0 until cols) {
                                        val color = if ((r + c) % 3 == 0) accentColor.copy(alpha = 0.4f) else BgMuted.copy(alpha = 0.2f)
                                        drawRect(
                                            color = color,
                                            topLeft = Offset(c * w + 1.dp.toPx(), r * h + 1.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(w - 2.dp.toPx(), h - 2.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }

                        // Home Screen Apps Layout Mock
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Status row
                            Spacer(Modifier.height(10.dp))
                            
                            // App Icon grid
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                repeat(3) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        repeat(4) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.White.copy(alpha = 0.25f))
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Dock
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                repeat(4) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color.White.copy(alpha = 0.35f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Update Behaviour Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Update Behaviour", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            WallpaperTargetCard(
                title = "Widgets Only (Default & Recommended)",
                subtitle = "Only refresh home-screen widgets (keeps your custom wallpaper)",
                icon = Icons.Filled.Widgets,
                isSelected = selectedUpdateMode == UserPrefs.UPDATE_MODE_WIDGETS_ONLY,
                accentColor = SkyAccent,
                onClick = {
                    val mode = UserPrefs.UPDATE_MODE_WIDGETS_ONLY
                    prefs.setWallpaperUpdateMode(mode)
                    selectedUpdateMode = mode
                }
            )

            WallpaperTargetCard(
                title = "Wallpaper + Widgets",
                subtitle = "Update live grid wallpaper AND refresh home-screen widgets",
                icon = Icons.Filled.Wallpaper,
                isSelected = selectedUpdateMode == UserPrefs.UPDATE_MODE_WALLPAPER_AND_WIDGETS,
                accentColor = accentColor,
                onClick = {
                    val mode = UserPrefs.UPDATE_MODE_WALLPAPER_AND_WIDGETS
                    prefs.setWallpaperUpdateMode(mode)
                    selectedUpdateMode = mode
                }
            )
        }

        Spacer(Modifier.height(4.dp))

        // Wallpaper Target Section (visible ONLY when Wallpaper + Widgets mode is selected)
        AnimatedVisibility(
            visible = selectedUpdateMode == UserPrefs.UPDATE_MODE_WALLPAPER_AND_WIDGETS,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Wallpaper Target", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                
                val handleTargetSelect = { target: String ->
                    selectedTarget = target
                    prefs.setWallpaperTarget(target)
                    prefs.setAutoUpdate(true)
                    if (selectedUpdateMode != UserPrefs.UPDATE_MODE_WIDGETS_ONLY) {
                        isApplying = true
                        com.consistencygridwallpaper.workers.WallpaperWorker.scheduleImmediate(context)
                        scope.launch {
                            kotlinx.coroutines.delay(2000)
                            isApplying = false
                            android.widget.Toast.makeText(context, "Wallpaper Applied!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Target Saved!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                WallpaperTargetCard(
                    title = "Home & Lock Screen",
                    subtitle = "Update grid wallpaper everywhere (Recommended)",
                    icon = Icons.Filled.Devices,
                    isSelected = selectedTarget == "BOTH",
                    accentColor = accentColor,
                    isLoading = isApplying && selectedTarget == "BOTH",
                    onClick = { handleTargetSelect("BOTH") }
                )

                WallpaperTargetCard(
                    title = "Lock Screen Only",
                    subtitle = "Keep your custom home screen wallpaper",
                    icon = Icons.Filled.Lock,
                    isSelected = selectedTarget == "LOCK",
                    accentColor = accentColor,
                    isLoading = isApplying && selectedTarget == "LOCK",
                    onClick = { handleTargetSelect("LOCK") }
                )

                WallpaperTargetCard(
                    title = "Home Screen Only",
                    subtitle = "Only update home screen wallpaper",
                    icon = Icons.Filled.Home,
                    isSelected = selectedTarget == "HOME",
                    accentColor = accentColor,
                    isLoading = isApplying && selectedTarget == "HOME",
                    onClick = { handleTargetSelect("HOME") }
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Widgets Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Interactive Widget Preview", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            
            WidgetPreviewCard(
                accentColor = accentColor,
                isPinSupported = isPinSupported,
                isCompleted = mockWidgetCompleted,
                streakCount = mockStreak,
                onCheckedChange = { checked ->
                    mockWidgetCompleted = checked
                    mockStreak = if (checked) 8 else 7
                },
                onPinClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val awm = AppWidgetManager.getInstance(context)
                        val provider = ComponentName(context, com.consistencygridwallpaper.widget.HabitWidgetProvider::class.java)
                        awm.requestPinAppWidget(provider, null, null)
                    }
                }
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WallpaperTargetCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.08f) else BgCard)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else GlassStroke,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon Box with circular background
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSelected) accentColor.copy(alpha = 0.15f) else BgMuted),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = accentColor,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) accentColor else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) TextPrimary else TextSecondary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = if (isSelected) TextSecondary.copy(alpha = 0.9f) else TextMuted
            )
        }

        // Checkmark indicator
        if (isSelected) {
            if (isLoading) {
                Text(
                    text = "Applying...",
                    fontSize = 11.sp,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.5.dp, TextMuted.copy(alpha = 0.4f), CircleShape)
            )
        }
    }
}

@Composable
private fun WidgetPreviewCard(
    accentColor: Color,
    isPinSupported: Boolean,
    isCompleted: Boolean,
    streakCount: Int,
    onCheckedChange: (Boolean) -> Unit,
    onPinClick: () -> Unit
) {
    val checkboxScale by animateFloatAsState(
        targetValue = if (isCompleted) 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkboxScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.5.dp, GlassStroke, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Widgets,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Habit Check Widget",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            // Small badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "INTERACTIVE",
                    fontSize = 9.sp,
                    color = accentColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Simulated Widget Preview UI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgDeep)
                .border(1.dp, GlassStroke, RoundedCornerShape(12.dp))
                .clickable { onCheckedChange(!isCompleted) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular Checkbox
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = checkboxScale
                            scaleY = checkboxScale
                        }
                        .clip(CircleShape)
                        .background(if (isCompleted) accentColor else accentColor.copy(alpha = 0.1f))
                        .border(1.5.dp, accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Drink 3L Water",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Daily habit • Tap to check-off",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            // Streak indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text("🔥", fontSize = 12.sp)
                Text("${streakCount}d streak", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OrangeAccent)
            }
        }

        Spacer(Modifier.height(4.dp))

        if (isPinSupported) {
            Button(
                onClick = onPinClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Pin Widget to Home Screen",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgMuted)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "To add: Long-press Home Screen → Add Widget",
                    fontSize = 11.sp,  
                )
            }
        }
    }
}

// ─── Floating Icon Orb ────────────────────────────────────────────────────────

@Composable
private fun FloatingIconOrb(
    icon:        ImageVector,
    accentColor: Color,
    glowColor:   Color
) {
    // Pulsing scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue   = 0.97f,
        targetValue    = 1.03f,
        animationSpec  = infiniteRepeatable(
            animation     = tween(2000, easing = FastOutSlowInEasing),
            repeatMode    = RepeatMode.Reverse
        ),
        label          = "pulse"
    )
    val rotAnim by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing)
        ),
        label         = "rot"
    )

    Box(
        modifier         = Modifier
            .size(140.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring (rotating dashes)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 8f
            drawCircle(
                brush  = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    radius = radius
                ),
                radius = radius
            )
            drawArc(
                brush      = Brush.sweepGradient(
                    listOf(Color.Transparent, accentColor.copy(alpha = 0.7f), Color.Transparent)
                ),
                startAngle = rotAnim,
                sweepAngle = 220f,
                useCenter  = false,
                style      = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )
        }

        // Inner orb
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f),
                            BgCard.copy(alpha = 0.95f)
                        )
                    )
                )
                .border(
                    width  = 1.5.dp,
                    brush  = Brush.sweepGradient(
                        listOf(
                            accentColor.copy(alpha = 0.6f),
                            accentColor.copy(alpha = 0.1f),
                            accentColor.copy(alpha = 0.6f)
                        )
                    ),
                    shape  = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}
