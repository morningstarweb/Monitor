package app.monitor.feature.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Living Multi-Core CPU Spectrogram Equalizer Canvas (< 100 lines).
 */
@Composable
fun LivingCpuMultiCoreSpectrogramCanvas(
    perCoreUsage: List<Float>,
    totalUsage: Float,
    wavePhase: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val coreCount = if (perCoreUsage.isNotEmpty()) perCoreUsage.size.coerceAtLeast(4) else 8
        val spacing = 3.dp.toPx()
        val totalSpacing = spacing * (coreCount - 1)
        val barWidth = ((w - totalSpacing) / coreCount).coerceAtLeast(3.dp.toPx())

        for (i in 0 until coreCount) {
            val realCoreLoad = if (i < perCoreUsage.size) perCoreUsage[i] else totalUsage
            val dynamicWave = sin((wavePhase * 2 * Math.PI + (i * 0.75f)).toFloat()) * 0.08f
            val effectiveLoad = (realCoreLoad / 100f + dynamicWave).coerceIn(0.08f, 1f)
            val barHeight = h * effectiveLoad

            val x = i * (barWidth + spacing)
            val y = h - barHeight

            val barColor = when {
                effectiveLoad > 0.75f -> Color(0xFFF43F5E)
                effectiveLoad > 0.40f -> Color(0xFFFFB300)
                else -> accentColor
            }

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(barColor, barColor.copy(alpha = 0.35f)),
                    startY = y,
                    endY = h
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

/**
 * Living RAM Memory Architecture Canvas (< 80 lines).
 */
@Composable
fun LivingRamArchitectureCanvas(
    usagePercent: Float,
    wavePhase: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isDark: Boolean = true
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val fillWidth = w * (usagePercent / 100f).coerceIn(0.05f, 1f)

        // Track
        drawRoundRect(
            color = if (isDark) Color(0xFF101424) else Color(0xFFE2E8F0),
            size = Size(w, h),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )

        // Memory Active Band
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF00E5FF),
                    Color(0xFFC084FC),
                    Color(0xFF00E5FF)
                ),
                startX = wavePhase * w,
                endX = (wavePhase + 1f) * w
            ),
            size = Size(fillWidth, h),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )

        // Segment dividers
        val segCount = 6
        val step = w / segCount
        for (i in 1 until segCount) {
            val x = i * step
            drawLine(
                color = if (isDark) Color(0x40070B18) else Color(0x20000000),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}
