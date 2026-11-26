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
import app.monitor.core.model.CommitNode
import app.monitor.core.model.GraphEdgeType
import app.monitor.feature.gitworkspace.model.getLaneColor

/**
 * Ultra-responsive Canvas DAG Renderer for Individual Living Commit Cards (< 120 lines).
 */
@Composable
fun LivingDagCanvas(
    commit: CommitNode,
    modifier: Modifier = Modifier,
    isHead: Boolean = false
) {
    Canvas(modifier = modifier) {
        val rowHeight = size.height
        val centerY = rowHeight / 2f
        val bottomY = rowHeight
        val topY = 0f

        val startXPx = 12.dp.toPx()
        val laneSpacingPx = 16.dp.toPx()
        val dotRadiusPx = if (isHead) 6.dp.toPx() else 4.8.dp.toPx()
        val lineWidthPx = 2.dp.toPx()

        fun laneToX(lane: Int): Float = startXPx + (lane * laneSpacingPx)

        for (edge in commit.edges) {
            val edgeColor = getLaneColor(edge.colorIndex)
            val fromX = laneToX(edge.fromLane)
            val toX = laneToX(edge.toLane)

            when (edge.edgeType) {
                GraphEdgeType.PassThrough -> {
                    drawLine(color = edgeColor, start = Offset(fromX, topY), end = Offset(fromX, bottomY), strokeWidth = lineWidthPx, cap = StrokeCap.Round)
                }
                GraphEdgeType.Straight -> {
                    drawLine(color = edgeColor, start = Offset(fromX, centerY), end = Offset(toX, bottomY), strokeWidth = lineWidthPx, cap = StrokeCap.Round)
                }
                GraphEdgeType.MergeIn -> {
                    val path = Path().apply {
                        moveTo(fromX, topY)
                        val controlY = topY + (centerY - topY) * 0.5f
                        cubicTo(fromX, controlY, toX, controlY, toX, centerY)
                    }
                    drawPath(path = path, color = edgeColor, style = Stroke(width = lineWidthPx, cap = StrokeCap.Round))
                }
                GraphEdgeType.BranchOut, GraphEdgeType.MergeBranch, GraphEdgeType.MergeToParent -> {
                    val path = Path().apply {
                        moveTo(fromX, centerY)
                        val controlY = centerY + (bottomY - centerY) * 0.5f
                        cubicTo(fromX, controlY, toX, controlY, toX, bottomY)
                    }
                    drawPath(path = path, color = edgeColor, style = Stroke(width = lineWidthPx, cap = StrokeCap.Round))
                }
            }
        }

        val commitX = laneToX(commit.lane)
        val commitColor = getLaneColor(commit.colorIndex)

        drawLine(color = commitColor, start = Offset(commitX, topY), end = Offset(commitX, centerY), strokeWidth = lineWidthPx, cap = StrokeCap.Round)

        if (isHead) {
            drawCircle(color = Color(0xFF10B981).copy(alpha = 0.28f), radius = dotRadiusPx + 4.5.dp.toPx(), center = Offset(commitX, centerY))
            drawCircle(color = Color(0xFF070A14), radius = dotRadiusPx + 1.5.dp.toPx(), center = Offset(commitX, centerY))
            drawCircle(color = Color(0xFF10B981), radius = dotRadiusPx, center = Offset(commitX, centerY))
            drawCircle(color = Color.White, radius = dotRadiusPx * 0.4f, center = Offset(commitX, centerY))
        } else {
            drawCircle(color = commitColor.copy(alpha = 0.20f), radius = dotRadiusPx + 3.dp.toPx(), center = Offset(commitX, centerY))
            drawCircle(color = Color(0xFF070A14), radius = dotRadiusPx + 1.2.dp.toPx(), center = Offset(commitX, centerY))
            drawCircle(color = commitColor, radius = dotRadiusPx, center = Offset(commitX, centerY))
            drawCircle(color = Color.White, radius = dotRadiusPx * 0.35f, center = Offset(commitX, centerY))
        }
    }
}
