package app.monitor.feature.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Living Plasma Energy Reactor Canvas with flowing photon waves and laser chambers (< 120 lines).
 */
@Composable
fun LivingPlasmaReactorCanvas(
    batteryPercent: Int,
    isCharging: Boolean,
    isAcMains: Boolean,
    plasmaPhase: Float,
    glowPulse: Float,
    modifier: Modifier = Modifier,
    isDark: Boolean = true
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val fillWidth = w * (batteryPercent / 100f).coerceIn(0.04f, 1f)

        // 1. Chamber Background
        drawRoundRect(
            color = if (isDark) Color(0xFF101424) else Color(0xFFE2E8F0),
            size = Size(w, h),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )

        // 2. Glowing Liquid Plasma Core
        val plasmaBrush = if (isCharging) {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF00E699),
                    Color(0xFF00E5FF),
                    Color(0xFF38BDF8),
                    Color(0xFF00E699)
                ),
                startX = plasmaPhase * w * 0.8f,
                endX = (plasmaPhase + 1f) * w * 0.8f
            )
        } else if (isAcMains) {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.85f),
                    Color(0xFFC084FC).copy(alpha = 0.85f)
                )
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF00E699).copy(alpha = 0.90f),
                    Color(0xFF10B981)
                )
            )
        }

        drawRoundRect(
            brush = plasmaBrush,
            size = Size(fillWidth, h),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )

        // 3. Energetic Grid Scanline & Laser Markers
        val segmentCount = 10
        val segStep = w / segmentCount
        for (i in 1 until segmentCount) {
            val x = i * segStep
            drawLine(
                color = if (isDark) Color(0x35070B18) else Color(0x20000000),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        // 4. Flowing Light Wave along the Chamber Top
        if (isCharging || isAcMains) {
            val waveY = h * 0.28f
            val wavePath = Path().apply {
                moveTo(0f, waveY)
                val waveLength = 120f
                var curX = 0f
                while (curX < fillWidth) {
                    val nextX = curX + waveLength
                    val midX = curX + waveLength / 2f
                    val crestY = waveY - (4f * glowPulse)
                    val troughY = waveY + (4f * glowPulse)
                    cubicTo(midX, crestY, midX, troughY, nextX.coerceAtMost(fillWidth), waveY)
                    curX = nextX
                }
            }
            drawPath(
                path = wavePath,
                color = if (isDark) Color.White.copy(alpha = 0.65f * glowPulse) else Color.White.copy(alpha = 0.85f * glowPulse),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 5. Outer Chamber High-Contrast Bezel
        drawRoundRect(
            color = if (isDark) Color(0x30FFFFFF) else Color(0x20000000),
            size = Size(w, h),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
