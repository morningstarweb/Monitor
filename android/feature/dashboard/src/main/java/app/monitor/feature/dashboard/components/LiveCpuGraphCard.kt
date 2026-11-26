package app.monitor.feature.dashboard.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.components.icons.PulseChartIcon
import app.monitor.core.model.SystemSnapshot

/**
 * Clean Minimalist CPU Telemetry Graph Card (< 150 lines).
 * Zero unwanted subtitles: clean title, live percentage pill, equalizer bars, and timeline.
 */
@Composable
fun LiveCpuGraphCard(
    snapshot: SystemSnapshot?,
    modifier: Modifier = Modifier
) {
    val cpuPct = snapshot?.cpu?.totalUsagePercent?.toInt()
    val history = snapshot?.cpu?.history60mPercent.orEmpty()
    val cardShape = RoundedCornerShape(24.dp)

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val cardBg = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color(0xFFFFFFFF).copy(alpha = 0.92f)
    val cardBorder = if (isDark) Brush.linearGradient(listOf(Color(0x60818CF8), Color(0x30C084FC), Color(0x5000E5FF)))
    else Brush.linearGradient(listOf(Color(0x306366F1), Color(0x208B5CF6), Color(0x300284C7)))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBg)
            .border(1.2.dp, cardBorder, cardShape)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Clean Header: Icon + Title + Live Percentage Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isDark) Color(0xFF1E1738) else Color(0xFFEDE9FE))
                            .border(1.dp, if (isDark) Color(0x70C084FC) else Color(0x307C3AED), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        PulseChartIcon(tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                    }

                    Text(
                        text = "CPU History",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A)
                    )
                }

                // Clean Live Badge (real state indicator)
                val isLive = cpuPct != null
                val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isLive) {
                                if (isDark) Color(0xFF064E3B).copy(alpha = 0.50f) else Color(0xFFD1FAE5)
                            } else {
                                if (isDark) Color(0xFF1E293B).copy(alpha = 0.50f) else Color(0xFFF1F5F9)
                            }
                        )
                        .border(1.dp, if (isLive) Color(0x8010B981) else Color(0x4064748B), RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(modifier = Modifier.size(6.dp).background(if (isLive) Color(0xFF10B981) else Color(0xFF64748B), CircleShape))
                        Text(
                            text = if (isLive) "$cpuPct%" else "--%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLive) {
                                if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                            } else {
                                if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
                            },
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Real-Time Equalizer Canvas
            Row(modifier = Modifier.fillMaxWidth().height(115.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.height(115.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("100%", fontSize = 8.sp, color = Color(0xFF64748B))
                    Text("50%", fontSize = 8.sp, color = Color(0xFF64748B))
                    Text("0%", fontSize = 8.sp, color = Color(0xFF64748B))
                }
                Spacer(modifier = Modifier.width(8.dp))
                SpectrogramEqualizerCanvas(history, cpuPct?.toFloat(), Modifier.weight(1f).height(115.dp))
            }

            // Clean Minimalist Timeline
            Row(modifier = Modifier.fillMaxWidth().padding(start = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("-45s", fontSize = 8.5.sp, color = Color(0xFF64748B))
                Text("-30s", fontSize = 8.5.sp, color = Color(0xFF64748B))
                Text("-15s", fontSize = 8.5.sp, color = Color(0xFF64748B))
                Text("Now", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
            }
        }
    }
}

@Composable
private fun SpectrogramEqualizerCanvas(data: List<Float>, currentPct: Float?, modifier: Modifier = Modifier) {
    val radarTransition = rememberInfiniteTransition(label = "RadarTarget")
    val reticlePulse by radarTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "reticlePulse"
    )

    val points = remember(data, currentPct) {
        if (data.isNotEmpty()) data.takeLast(45)
        else if (currentPct != null) listOf(currentPct)
        else emptyList()
    }

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        for (i in 0..2) drawLine(Color(0x15818CF8), Offset(0f, (h / 2f) * i), Offset(w, (h / 2f) * i), strokeWidth = 1.dp.toPx())

        val stepX = if (points.size > 1) w / (points.size - 1) else 0f
        val splinePath = Path()
        var lastX = 0f; var lastY = 0f
        val barWidth = if (points.size > 1) (stepX * 0.55f).coerceAtLeast(2.5f) else 10.dp.toPx()

        points.forEachIndexed { i, v ->
            val x = if (points.size == 1) w - barWidth / 2f else i * stepX
            val barH = ((v / 100f).coerceIn(0.04f, 0.94f) * h)
            val topY = h - barH
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF00E5FF), Color(0xFF818CF8).copy(0.5f), Color(0xFF312E81).copy(0.2f)), topY, h),
                topLeft = Offset(x - barWidth / 2f, topY), size = Size(barWidth, barH), cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
            if (i == 0) splinePath.moveTo(x, topY)
            else {
                val prevX = (i - 1) * stepX
                val prevTopY = h - ((points[i - 1] / 100f).coerceIn(0.05f, 0.95f) * h)
                val cX = (prevX + x) / 2f
                splinePath.cubicTo(cX, prevTopY, cX, topY, x, topY)
            }
            if (i == points.lastIndex) { lastX = x; lastY = topY }
        }

        if (points.isNotEmpty()) {
            drawPath(splinePath, Brush.horizontalGradient(listOf(Color(0xFF818CF8), Color(0xFFC084FC), Color(0xFF00E5FF))), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
            drawLine(Color(0x5000E5FF), Offset(lastX, 0f), Offset(lastX, h), strokeWidth = 1.dp.toPx())
            val rippleR = (3f + reticlePulse * 9f).dp.toPx()
            drawCircle(Color(0xFF00E5FF).copy((1f - reticlePulse) * 0.75f), radius = rippleR, center = Offset(lastX, lastY), style = Stroke(1.5.dp.toPx()))
            drawCircle(Color(0xFF00E5FF), radius = 3.5.dp.toPx(), center = Offset(lastX, lastY))
            drawCircle(Color.White, radius = 1.8.dp.toPx(), center = Offset(lastX, lastY))
        }
    }
}
