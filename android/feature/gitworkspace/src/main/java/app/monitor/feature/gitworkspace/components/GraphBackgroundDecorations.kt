package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.monitor.core.designsystem.components.MonitorGlobalBackgroundCanvas

/**
 * Custom vector path drawing the Git Branch / Fork icon.
 */
@Composable
fun GitBranchVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val stroke = 1.8.dp.toPx()
        val nodeRadius = 2.dp.toPx()

        val mainX = w * 0.32f
        val branchX = w * 0.72f
        val topY = h * 0.28f
        val midY = h * 0.52f
        val bottomY = h * 0.76f

        // Main vertical branch stem
        drawLine(
            color = tint,
            start = Offset(mainX, topY),
            end = Offset(mainX, bottomY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Branching curve leading out to fork
        val forkPath = Path().apply {
            moveTo(mainX, midY)
            cubicTo(
                mainX + w * 0.12f, midY,
                branchX, midY + h * 0.05f,
                branchX, topY
            )
        }
        drawPath(
            path = forkPath,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // Draw 3 branch nodes (circles)
        drawCircle(color = tint, radius = nodeRadius, center = Offset(mainX, topY))
        drawCircle(color = tint, radius = nodeRadius, center = Offset(mainX, bottomY))
        drawCircle(color = tint, radius = nodeRadius, center = Offset(branchX, topY))
    }
}

/**
 * Sweeping violet and dark ambient background decorations for Git Graph.
 * Delegates to the unified global animated background canvas.
 */
@Composable
fun GraphBackgroundDecorations(
    modifier: Modifier = Modifier
) {
    MonitorGlobalBackgroundCanvas(modifier = modifier)
}
