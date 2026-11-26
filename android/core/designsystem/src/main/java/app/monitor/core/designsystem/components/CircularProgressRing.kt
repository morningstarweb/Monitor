package app.monitor.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.monitor.core.designsystem.theme.LocalMonitorColors

/**
 * Circular Progress Ring for Metric Stat Cards across Dashboard & Git Workspace.
 */
@Composable
fun CircularProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier.size(30.dp),
    trackColor: Color? = null,
    strokeWidth: Dp = 3.dp,
) {
    val isDark = LocalMonitorColors.current.isDark
    val resolvedTrack = trackColor ?: if (isDark) Color(0xFF1E2442) else Color(0xFFE2E8F0)
    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = tween(durationMillis = 450),
        label = "ringProgress"
    )

    Canvas(
        modifier = modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(clampedProgress, 0f..1f)
        }
    ) {
        val strokePx = strokeWidth.toPx()
        val diameter = size.minDimension - strokePx
        val arcSize = Size(diameter, diameter)
        val topLeft = Offset(strokePx / 2f, strokePx / 2f)

        // Background Track
        drawArc(
            color = resolvedTrack,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // Progress Arc
        if (animatedProgress > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
    }
}
