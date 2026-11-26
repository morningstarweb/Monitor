package app.monitor.feature.onboarding.components

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PairingDeviceLinkBanner(modifier: Modifier = Modifier) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val transition = rememberInfiniteTransition(label = "DataStream")
    val streamProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "streamProgress"
    )
    val nodeShape = RoundedCornerShape(12.dp)

    val mobColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
    val deskColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(nodeShape)
                    .background(if (isDark) Color(0xFF101322) else Color(0xFFE0F2FE))
                    .border(1.dp, mobColor.copy(alpha = 0.5f), nodeShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val w = size.width; val h = size.height
                    drawRoundRect(
                        color = mobColor,
                        topLeft = Offset(w * 0.22f, h * 0.10f),
                        size = Size(w * 0.56f, h * 0.80f),
                        cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                    drawCircle(color = mobColor, radius = 1.2.dp.toPx(), center = Offset(w * 0.5f, h * 0.78f))
                }
            }
            Text(
                text = "MOBILE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = mobColor
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                drawLine(
                    brush = Brush.horizontalGradient(listOf(Color(0x2000E5FF), Color(0x60818CF8), Color(0x20C084FC))),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2.dp.toPx()
                )
                val dotX = size.width * streamProgress
                drawCircle(color = mobColor, radius = 3.dp.toPx(), center = Offset(dotX, size.height / 2f))
                val dot2X = (dotX + size.width * 0.5f) % size.width
                drawCircle(color = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED), radius = 2.5.dp.toPx(), center = Offset(dot2X, size.height / 2f))
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF0C0E1A) else Color.White)
                    .border(1.dp, if (isDark) Color(0x40818CF8) else Color(0x60CBD5E1), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(modifier = Modifier.size(4.dp).background(Color(0xFF10B981), CircleShape))
                    Text("SYNC BRIDGE", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = deskColor)
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(nodeShape)
                    .background(if (isDark) Color(0xFF101322) else Color(0xFFEEF2FF))
                    .border(1.dp, deskColor.copy(alpha = 0.5f), nodeShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val w = size.width; val h = size.height
                    drawRoundRect(
                        color = deskColor,
                        topLeft = Offset(w * 0.12f, h * 0.15f),
                        size = Size(w * 0.76f, h * 0.54f),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                    drawLine(
                        color = deskColor,
                        start = Offset(w * 0.5f, h * 0.69f),
                        end = Offset(w * 0.5f, h * 0.82f),
                        strokeWidth = 1.6.dp.toPx()
                    )
                    drawLine(
                        color = deskColor,
                        start = Offset(w * 0.32f, h * 0.82f),
                        end = Offset(w * 0.68f, h * 0.82f),
                        strokeWidth = 1.6.dp.toPx()
                    )
                }
            }
            Text(
                text = "DESKTOP",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = deskColor
            )
        }
    }
}
