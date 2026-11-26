package app.monitor.feature.dashboard.sections

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProjectsSectionHeader(
    projectCount: Int,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark

    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Living Stacked Glass Layers Scanner Icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) Color(0xFF1E1738) else Color(0xFFEDE9FE))
                    .border(1.dp, if (isDark) Color(0x70C084FC) else Color(0x608B5CF6), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                LivingLayersScanner()
            }

            Text(
                text = "Projects",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) Color(0xFF1F1D38) else Color(0xFFE0E7FF))
                    .border(1.dp, if (isDark) Color(0x60818CF8) else Color(0x406366F1), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$projectCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFF00E5FF) else Color(0xFF4F46E5)
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.92f))
                .border(
                    1.2.dp,
                    if (isDark) Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF818CF8)))
                    else Brush.horizontalGradient(listOf(Color(0x8000E5FF), Color(0x80818CF8))),
                    RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onAddClick)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+ Add",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )
        }
    }
}

@Composable
private fun LivingLayersScanner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "LayerScan")
    val scanY by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanY"
    )

    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width; val h = size.height

        // 3 Stacked Isometric Glass Rhombus Plates
        for (i in 0..2) {
            val yOff = h * (0.24f + i * 0.22f)
            val plate = Path().apply {
                moveTo(w * 0.50f, yOff - h * 0.12f)
                lineTo(w * 0.86f, yOff)
                lineTo(w * 0.50f, yOff + h * 0.12f)
                lineTo(w * 0.14f, yOff)
                close()
            }
            drawPath(
                plate,
                color = if (i == 1) Color(0xFFC084FC) else Color(0xFF818CF8).copy(alpha = 0.7f),
                style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Luminous Scanner Laser Beam Slicing Through the Layers
        val beamY = h * (0.16f + 0.56f * scanY)
        drawLine(
            Color(0xFF00E5FF),
            Offset(w * 0.20f, beamY),
            Offset(w * 0.80f, beamY),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
