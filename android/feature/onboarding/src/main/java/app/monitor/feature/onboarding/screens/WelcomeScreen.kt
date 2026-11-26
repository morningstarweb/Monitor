package app.monitor.feature.onboarding.screens

import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.onboarding.components.WelcomeBackgroundAura
import app.monitor.feature.onboarding.components.WelcomeGlassSwipeButton
import app.monitor.feature.onboarding.components.WelcomeHeroLogo

/**
 * Clean & Minimalist Welcome Screen.
 * Displays animated HomeScreen-style corner wave background, stable Git DAG logo
 * with animated glowing squircle border, and 3D transparent black glass swipe button.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
    }

    // Staggered Entrance Animations
    val logoAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(650, delayMillis = 60, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )
    val logoOffsetY by animateFloatAsState(
        targetValue = if (entered) 0f else 32f,
        animationSpec = tween(650, delayMillis = 60, easing = FastOutSlowInEasing),
        label = "logoOffsetY"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(650, delayMillis = 180, easing = FastOutSlowInEasing),
        label = "titleAlpha"
    )
    val titleOffsetY by animateFloatAsState(
        targetValue = if (entered) 0f else 24f,
        animationSpec = tween(650, delayMillis = 180, easing = FastOutSlowInEasing),
        label = "titleOffsetY"
    )
    val buttonAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(650, delayMillis = 400, easing = FastOutSlowInEasing),
        label = "buttonAlpha"
    )
    val buttonOffsetY by animateFloatAsState(
        targetValue = if (entered) 0f else 22f,
        animationSpec = tween(650, delayMillis = 400, easing = FastOutSlowInEasing),
        label = "buttonOffsetY"
    )

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF07080C) else Color.Transparent)
    ) {
        // Highly Animated Ambient Background Aura
        WelcomeBackgroundAura()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1.1f))

            // Centered App Icon with Levitation & Pulsing Neon Aura
            WelcomeHeroLogo(
                modifier = Modifier
                    .offset { IntOffset(0, logoOffsetY.dp.roundToPx()) }
                    .graphicsLayer { alpha = logoAlpha }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // "Monitor" Title
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, titleOffsetY.dp.roundToPx()) }
                    .graphicsLayer { alpha = titleAlpha },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Monitor",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    letterSpacing = (-0.5).sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Clean Minimalist Tagline
            val tagColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
            val dotColor = if (isDark) Color(0xFF00FF87) else Color(0xFF00A859)
            Row(
                modifier = Modifier.graphicsLayer { alpha = titleAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "DEVICE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = tagColor)
                Box(modifier = Modifier.size(4.dp).background(dotColor, CircleShape))
                Text(text = "CODE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = tagColor)
                Box(modifier = Modifier.size(4.dp).background(dotColor, CircleShape))
                Text(text = "CLOUD", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = tagColor)
            }

            Spacer(modifier = Modifier.weight(1.3f))

            // 3D Obsidian Black Glass Swipe Action Button
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, buttonOffsetY.dp.roundToPx()) }
                    .graphicsLayer { alpha = buttonAlpha },
                contentAlignment = Alignment.Center
            ) {
                WelcomeGlassSwipeButton(
                    text = "Start Monitoring",
                    onSwipeComplete = onGetStarted
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    WelcomeScreen(onGetStarted = {})
}
