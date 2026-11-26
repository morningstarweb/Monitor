package app.monitor.feature.gitworkspace.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun UpstreamHeroCard(
    currentBranch: String,
    upstreamBranch: String?,
    aheadCount: Int,
    behindCount: Int,
    borderPhase: Float,
    cloudPulse: Float,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(22.dp)
    val cardBackground = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.92f)

    Box(modifier = modifier.fillMaxWidth().clip(cardShape).background(cardBackground)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val p = borderPhase.toDouble()
            val w = size.width
            val h = size.height
            val r = w * 0.6f
            val startX = w * 0.5f + r * cos(p).toFloat()
            val startY = h * 0.5f + r * sin(p).toFloat()
            val endX = w * 0.5f - r * cos(p).toFloat()
            val endY = h * 0.5f - r * sin(p).toFloat()
            val colors = if (isDark) listOf(Color(0xFF00E5FF), Color(0xFF818CF8), Color(0xFFC084FC), Color(0x35FFFFFF), Color(0xFF00E699), Color(0xFF00E5FF))
                         else listOf(Color(0xFF0284C7), Color(0xFF6366F1), Color(0xFFA855F7), Color(0x40CBD5E1), Color(0xFF10B981), Color(0xFF0284C7))
            drawRoundRect(
                brush = Brush.linearGradient(colors, start = Offset(startX, startY), end = Offset(endX, endY)),
                cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                style = Stroke(width = 1.3.dp.toPx())
            )
        }

        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "UPSTREAM REPOSITORY",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            letterSpacing = 0.6.sp
                        )
                        Box(modifier = Modifier.size(6.dp).background(if (isDark) Color(0xFF00E699) else Color(0xFF059669), CircleShape))
                    }
                    Text(
                        text = upstreamBranch ?: "origin/$currentBranch",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer { scaleX = cloudPulse; scaleY = cloudPulse }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF0C4A6E).copy(alpha = 0.50f) else Color(0xFFE0F2FE))
                        .border(1.dp, if (isDark) Color(0x6000E5FF) else Color(0x400284C7), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), modifier = Modifier.size(22.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val tileBg = if (isDark) Color(0xFF0F1424).copy(alpha = 0.85f) else Color(0xFFF8FAFC)
                val tileBorder = if (isDark) Brush.horizontalGradient(listOf(Color(0x3500E699), Color(0x15818CF8)))
                                 else Brush.horizontalGradient(listOf(Color(0x3010B981), Color(0x206366F1)))
                // Ahead Tile
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(tileBg).border(1.dp, tileBorder, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = if (isDark) Color(0xFF00E699) else Color(0xFF059669), modifier = Modifier.size(13.dp))
                            Text("AHEAD", fontSize = 10.5.sp, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "+$aheadCount commits",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (aheadCount > 0) (if (isDark) Color(0xFF00E699) else Color(0xFF059669)) else (if (isDark) Color.White else Color(0xFF0F172A))
                        )
                        Text(if (aheadCount > 0) "Local to push" else "Nothing to push", fontSize = 10.sp, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                    }
                }

                // Behind Tile
                val behindBorder = if (isDark) Brush.horizontalGradient(listOf(Color(0x3500E5FF), Color(0x15818CF8)))
                                   else Brush.horizontalGradient(listOf(Color(0x300284C7), Color(0x206366F1)))
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(tileBg).border(1.dp, behindBorder, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), modifier = Modifier.size(13.dp))
                            Text("BEHIND", fontSize = 10.5.sp, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = if (behindCount > 0) "-$behindCount commits" else "0 commits",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (behindCount > 0) Color(0xFFF43F5E) else (if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7))
                        )
                        Text(if (behindCount > 0) "Pull available" else "Synchronized", fontSize = 10.sp, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                    }
                }
            }
        }
    }
}
