package app.monitor.feature.dashboard.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedurally draws a high-performance 7-blade aerodynamic fan impeller.
 */
@Composable
fun FanImpellerCanvas(
    rotationAngle: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.rotate(rotationAngle),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f - 2f
            val hubRadius = outerRadius * 0.28f

            drawCircle(
                color = Color(0x3538BDF8),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            val bladeCount = 7
            val angleStep = (2 * Math.PI / bladeCount).toFloat()
            for (i in 0 until bladeCount) {
                val angle = i * angleStep
                val startX = center.x + hubRadius * cos(angle)
                val startY = center.y + hubRadius * sin(angle)
                val endX = center.x + (outerRadius - 3.dp.toPx()) * cos(angle + 0.35f)
                val endY = center.y + (outerRadius - 3.dp.toPx()) * sin(angle + 0.35f)

                drawLine(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF00F2FE), Color(0xFF38BDF8)),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY)
                    ),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF67E8F9), Color(0xFF0E7490)),
                    center = center,
                    radius = hubRadius
                ),
                radius = hubRadius,
                center = center
            )
        }
    }
}
