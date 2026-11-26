package app.monitor.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Universal Global Animated Background Canvas for all screens in Monitor (< 175 lines).
 * Automatically branches between the signature Dark Obsidian Canvas and the
 * high-chroma Light Aurora Silk Canvas based on [isDarkTheme].
 */
@Composable
fun MonitorGlobalBackgroundCanvas(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
) {
    if (isDarkTheme) {
        DarkObsidianBackgroundCanvas(modifier = modifier)
    } else {
        LightAuroraBackgroundCanvas(modifier = modifier)
    }
}

@Composable
fun DarkObsidianBackgroundCanvas(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "GlobalWavesMotion")

    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase1"
    )

    val phase2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase2"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val p1 = phase1.toDouble()
        val p2 = phase2.toDouble()
        val sin1 = sin(p1).toFloat()
        val cos1 = cos(p1).toFloat()
        val sin2 = sin(p2).toFloat()
        val cos2 = cos(p2).toFloat()

        // 1. Top-Left Ambient Breathing Radial Glow
        val tlRadius = w * (0.70f + 0.08f * sin1)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF7C3AED).copy(alpha = 0.28f + 0.06f * cos2), Color.Transparent),
                center = Offset(0f, 0f), radius = tlRadius
            ),
            radius = tlRadius, center = Offset(0f, 0f)
        )

        // 2. Top-Left Fluid Animated Wave Ribbon
        val pathTl = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * (0.65f + 0.04f * sin1), 0f)
            cubicTo(
                w * (0.45f + 0.05f * cos2), h * (0.12f + 0.03f * sin1),
                w * (0.15f + 0.04f * sin2), h * (0.24f + 0.03f * cos1),
                0f, h * (0.28f + 0.04f * sin2)
            )
            close()
        }
        drawPath(
            path = pathTl,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF7C3AED).copy(alpha = 0.38f + 0.05f * sin1), Color(0xFF4C1D95).copy(alpha = 0.16f), Color.Transparent),
                start = Offset(0f, 0f), end = Offset(w * 0.5f, h * 0.22f)
            )
        )

        // 3. Top-Left Glowing Shimmer Crest
        val crestTl = Path().apply {
            moveTo(w * (0.45f + 0.04f * sin1), 0f)
            cubicTo(
                w * (0.32f + 0.04f * cos2), h * (0.12f + 0.03f * sin1),
                w * (0.12f + 0.03f * sin2), h * (0.25f + 0.03f * cos1),
                0f, h * (0.32f + 0.04f * sin2)
            )
        }
        drawPath(
            path = crestTl,
            brush = Brush.linearGradient(
                colors = listOf(Color(0x00C084FC), Color(0xFFC084FC), Color(0x80818CF8), Color(0x00818CF8)),
                start = Offset(w * (0.45f + 0.10f * sin1), 0f), end = Offset(0f, h * (0.32f + 0.10f * cos1))
            ),
            style = Stroke(width = 2.dp.toPx())
        )

        // 4. Bottom-Right Ambient Breathing Radial Glow
        val brRadius = w * (0.75f + 0.08f * cos1)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF4F46E5).copy(alpha = 0.28f + 0.06f * sin2), Color.Transparent),
                center = Offset(w, h), radius = brRadius
            ),
            radius = brRadius, center = Offset(w, h)
        )

        // 5. Bottom-Right Fluid Animated Wave Ribbon
        val pathBr = Path().apply {
            moveTo(w, h)
            lineTo(w * (0.35f - 0.04f * cos1), h)
            cubicTo(
                w * (0.55f - 0.05f * sin2), h * (0.88f - 0.03f * cos1),
                w * (0.85f - 0.04f * cos2), h * (0.76f - 0.03f * sin1),
                w, h * (0.72f - 0.04f * cos2)
            )
            close()
        }
        drawPath(
            path = pathBr,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF6D28D9).copy(alpha = 0.38f + 0.05f * cos1), Color(0xFF3B82F6).copy(alpha = 0.16f), Color.Transparent),
                start = Offset(w, h), end = Offset(w * 0.5f, h * 0.70f)
            )
        )

        // 6. Bottom-Right Glowing Shimmer Crest
        val crestBr = Path().apply {
            moveTo(w * (0.55f - 0.04f * cos1), h)
            cubicTo(
                w * (0.68f - 0.04f * sin2), h * (0.88f - 0.03f * cos1),
                w * (0.88f - 0.03f * cos2), h * (0.75f - 0.03f * sin1),
                w, h * (0.68f - 0.04f * cos2)
            )
        }
        drawPath(
            path = crestBr,
            brush = Brush.linearGradient(
                colors = listOf(Color(0x00A78BFA), Color(0xFFC084FC), Color(0x80818CF8), Color(0x00818CF8)),
                start = Offset(w * (0.55f - 0.10f * cos1), h), end = Offset(w, h * (0.68f - 0.10f * sin1))
            ),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
