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
 * Signature Light Mode Animated Aurora & Prismatic Canvas (< 160 lines).
 * Delivers living iridescent lavender-cyan silk ribbons, spring emerald-azure glows,
 * and shimmering crystalline crest lines over an ultra-clean pearlescent background.
 */
@Composable
fun LightAuroraBackgroundCanvas(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "LightAuroraMotion")

    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(7500, easing = LinearEasing), RepeatMode.Restart),
        label = "lightPhase1"
    )

    val phase2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(11500, easing = LinearEasing), RepeatMode.Restart),
        label = "lightPhase2"
    )

    val phase3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "lightPhase3"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val p1 = phase1.toDouble()
        val p2 = phase2.toDouble()
        val p3 = phase3.toDouble()
        val sin1 = sin(p1).toFloat()
        val cos1 = cos(p1).toFloat()
        val sin2 = sin(p2).toFloat()
        val cos2 = cos(p2).toFloat()
        val sin3 = sin(p3).toFloat()

        // 0. Base Crystalline Gradient Mesh
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFF9FBFC), Color(0xFFF1F5F9), Color(0xFFE8EFF5))
            )
        )

        // 1. Center Floating Prismatic Caustic Orb
        val centerRadius = w * (0.50f + 0.08f * sin3)
        val centerOffset = Offset(w * (0.50f + 0.08f * sin2), h * (0.45f + 0.06f * cos1))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE0E7FF).copy(alpha = 0.40f + 0.08f * cos2), Color(0xFFF0FDF4).copy(alpha = 0.20f), Color.Transparent),
                center = centerOffset, radius = centerRadius
            ),
            radius = centerRadius, center = centerOffset
        )

        // 2. Top-Left Luminous Iris/Cyan Breathing Glow
        val tlRadius = w * (0.78f + 0.08f * sin1)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.25f + 0.05f * cos2), Color(0xFFA855F7).copy(alpha = 0.15f), Color.Transparent),
                center = Offset(0f, 0f), radius = tlRadius
            ),
            radius = tlRadius, center = Offset(0f, 0f)
        )

        // 3. Top-Left Fluid Silk Aurora Ribbon
        val pathTl = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * (0.70f + 0.04f * sin1), 0f)
            cubicTo(
                w * (0.48f + 0.05f * cos2), h * (0.14f + 0.03f * sin1),
                w * (0.16f + 0.04f * sin2), h * (0.28f + 0.03f * cos1),
                0f, h * (0.32f + 0.04f * sin2)
            )
            close()
        }
        drawPath(
            path = pathTl,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.28f + 0.04f * sin1), Color(0xFFA855F7).copy(alpha = 0.16f + 0.03f * cos2), Color.Transparent),
                start = Offset(0f, 0f), end = Offset(w * 0.55f, h * 0.26f)
            )
        )

        // 4. Top-Left Iridescent Shimmer Crest
        val crestTl = Path().apply {
            moveTo(w * (0.48f + 0.04f * sin1), 0f)
            cubicTo(
                w * (0.34f + 0.04f * cos2), h * (0.14f + 0.03f * sin1),
                w * (0.14f + 0.03f * sin2), h * (0.28f + 0.03f * cos1),
                0f, h * (0.36f + 0.04f * sin2)
            )
        }
        drawPath(
            path = crestTl,
            brush = Brush.linearGradient(
                colors = listOf(Color(0x0038BDF8), Color(0xFF0284C7).copy(alpha = 0.80f), Color(0xFF7C3AED).copy(alpha = 0.70f), Color(0x007C3AED)),
                start = Offset(w * (0.48f + 0.08f * sin1), 0f), end = Offset(0f, h * (0.36f + 0.08f * cos1))
            ),
            style = Stroke(width = 2.dp.toPx())
        )

        // 5. Bottom-Right Spring Emerald & Azure Breathing Glow
        val brRadius = w * (0.82f + 0.08f * cos1)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF34D399).copy(alpha = 0.22f + 0.05f * sin2), Color(0xFF60A5FA).copy(alpha = 0.14f), Color.Transparent),
                center = Offset(w, h), radius = brRadius
            ),
            radius = brRadius, center = Offset(w, h)
        )

        // 6. Bottom-Right Fluid Silk Aurora Ribbon
        val pathBr = Path().apply {
            moveTo(w, h)
            lineTo(w * (0.32f - 0.04f * cos1), h)
            cubicTo(
                w * (0.52f - 0.05f * sin2), h * (0.86f - 0.03f * cos1),
                w * (0.84f - 0.04f * cos2), h * (0.72f - 0.03f * sin1),
                w, h * (0.68f - 0.04f * cos2)
            )
            close()
        }
        drawPath(
            path = pathBr,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF10B981).copy(alpha = 0.26f + 0.04f * cos1), Color(0xFF3B82F6).copy(alpha = 0.15f + 0.03f * sin2), Color.Transparent),
                start = Offset(w, h), end = Offset(w * 0.48f, h * 0.68f)
            )
        )

        // 7. Bottom-Right Shimmering Emerald/Azure Crest
        val crestBr = Path().apply {
            moveTo(w * (0.52f - 0.04f * cos1), h)
            cubicTo(
                w * (0.66f - 0.04f * sin2), h * (0.86f - 0.03f * cos1),
                w * (0.86f - 0.03f * cos2), h * (0.72f - 0.03f * sin1),
                w, h * (0.64f - 0.04f * cos2)
            )
        }
        drawPath(
            path = crestBr,
            brush = Brush.linearGradient(
                colors = listOf(Color(0x0010B981), Color(0xFF059669).copy(alpha = 0.85f), Color(0xFF2563EB).copy(alpha = 0.70f), Color(0x002563EB)),
                start = Offset(w * (0.52f - 0.08f * cos1), h), end = Offset(w, h * (0.64f - 0.08f * sin1))
            ),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
