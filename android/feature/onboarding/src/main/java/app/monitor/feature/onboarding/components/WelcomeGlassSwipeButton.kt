package app.monitor.feature.onboarding.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * 3D Pure Black Transparent Glass Swipe Button with animated glowing border.
 */
@Composable
fun WelcomeGlassSwipeButton(
    onSwipeComplete: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Start Monitoring"
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val buttonWidth = 290.dp
    val buttonHeight = 60.dp
    val thumbSize = 48.dp
    val padding = 6.dp
    val maxDragPx = with(density) { (buttonWidth - thumbSize - (padding * 2)).toPx() }
    val dragOffset = remember { Animatable(0f) }

    val transition = rememberInfiniteTransition(label = "ButtonBorderGlow")
    val borderPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "borderPhase"
    )
    val shape = RoundedCornerShape(30.dp)

    val isSubmitting = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val pillBg = if (isDark) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.90f)
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
    Box(
        modifier = modifier.width(buttonWidth).height(buttonHeight).clip(shape)
            .background(pillBg)
            .clickable(
                enabled = !isSubmitting.value,
                role = androidx.compose.ui.semantics.Role.Button
            ) {
                isSubmitting.value = true
                coroutineScope.launch {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    dragOffset.animateTo(maxDragPx, tween(260))
                    onSwipeComplete()
                    dragOffset.snapTo(0f)
                    isSubmitting.value = false
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val glowColors = if (isDark) {
            listOf(Color(0xFF00E5FF), Color(0xFF818CF8), Color(0xFFC084FC), Color(0x40FFFFFF), Color(0xFF00E5FF))
        } else {
            listOf(Color(0xFF0284C7), Color(0xFF6366F1), Color(0xFFA855F7), Color(0x40CBD5E1), Color(0xFF0284C7))
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = borderPhase.toDouble(); val w = size.width; val h = size.height; val r = w * 0.6f
            val startX = w * 0.5f + r * cos(p).toFloat()
            val startY = h * 0.5f + r * sin(p).toFloat()
            val endX = w * 0.5f - r * cos(p).toFloat()
            val endY = h * 0.5f - r * sin(p).toFloat()
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = glowColors,
                    start = Offset(startX, startY), end = Offset(endX, endY)
                ),
                cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx()),
                style = Stroke(width = 1.8.dp.toPx())
            )
        }

        val innerBorder = if (isDark) {
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.Black.copy(alpha = 0.5f)))
        } else {
            Brush.verticalGradient(listOf(Color.White, Color(0x30CBD5E1)))
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(1.dp)
                .border(0.8.dp, innerBorder, shape)
        )

        val textAlpha = (1f - (dragOffset.value / maxDragPx) * 0.85f).coerceIn(0.2f, 1f)
        val textColor = if (isDark) Color.White.copy(alpha = textAlpha) else Color(0xFF0F172A).copy(alpha = textAlpha)
        Box(modifier = Modifier.fillMaxSize().padding(start = 54.dp, end = 24.dp), contentAlignment = Alignment.Center) {
            Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor, letterSpacing = 0.6.sp)
        }

        val thumbBg = if (isDark) Brush.verticalGradient(listOf(Color(0xFF222634), Color(0xFF0C0E14))) else Brush.verticalGradient(listOf(Color.White, Color(0xFFE2E8F0)))
        val thumbBorder = if (isDark) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color(0xFF64748B).copy(alpha = 0.20f))) else Brush.verticalGradient(listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8).copy(alpha = 0.30f)))
        val arrowTint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)

        Box(
            modifier = Modifier.padding(start = padding).offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .size(thumbSize)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceIn(0f, maxDragPx)) }
                    },
                    onDragStopped = {
                        coroutineScope.launch {
                            if (dragOffset.value >= maxDragPx * 0.65f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragOffset.animateTo(maxDragPx, spring(dampingRatio = 0.8f))
                                onSwipeComplete()
                                dragOffset.snapTo(0f)
                            } else {
                                dragOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 450f))
                            }
                        }
                    }
                )
                .background(thumbBg, CircleShape)
                .border(1.2.dp, thumbBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = arrowTint, modifier = Modifier.size(20.dp))
        }
    }
    }
}
