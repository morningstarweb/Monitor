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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.SystemSnapshot
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimalist Clean Connected Station Card (< 160 lines).
 * Zero clutter: Hostname, single short status line, living terminal, and subtle chevron.
 */
@Composable
fun ConnectedDeviceCard(
    snapshot: SystemSnapshot?,
    isOnline: Boolean,
    onPairDevice: () -> Unit,
    modifier: Modifier = Modifier,
    requiresPairing: Boolean = false,
    errorMessage: String? = null,
    onRefresh: () -> Unit = onPairDevice
) {
    val cardShape = RoundedCornerShape(24.dp)
    val transition = rememberInfiniteTransition(label = "StationRadar")
    val borderPhase by transition.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Restart), label = "borderPhase")
    val pulseGlow by transition.animateFloat(0.85f, 1.25f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulseGlow")

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val cardBg = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color(0xFFFFFFFF).copy(alpha = 0.92f)
    val borderColors = if (isDark) listOf(Color(0xFF00E5FF), Color(0xFF818CF8), Color(0xFFC084FC), Color(0x35FFFFFF), Color(0xFF00E5FF))
    else listOf(Color(0xFF0284C7), Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0x25000000), Color(0xFF0284C7))

    val cardClick = { onRefresh() }

    Box(modifier = modifier.fillMaxWidth().clip(cardShape).background(cardBg).clickable(onClick = cardClick)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val p = borderPhase.toDouble(); val w = size.width; val h = size.height; val r = w * 0.6f
            val startX = w * 0.5f + r * cos(p).toFloat(); val startY = h * 0.5f + r * sin(p).toFloat()
            val endX = w * 0.5f - r * cos(p).toFloat(); val endY = h * 0.5f - r * sin(p).toFloat()
            drawRoundRect(
                brush = Brush.linearGradient(borderColors, start = Offset(startX, startY), end = Offset(endX, endY)),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()), style = Stroke(width = 1.3.dp.toPx())
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(if (isDark) listOf(Color(0xFF1E1738), Color(0xFF0E1122)) else listOf(Color(0xFFEDE9FE), Color(0xFFE0E7FF)))),
                    contentAlignment = Alignment.Center
                ) { LivingDesktopTerminalCanvas(isOnline = isOnline, isDark = isDark) }

                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = snapshot?.hostname?.takeIf { it.isNotBlank() } ?: (if (isOnline) "PC Online" else "PC Offline"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val lastSeen = androidx.compose.runtime.remember(snapshot?.observedAtUtcMs) {
                            snapshot?.observedAtUtcMs?.takeIf { it > 0 }?.let {
                                java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(it))
                            }
                        }
                        val dotColor = if (isOnline) Color(0xFF10B981) else if (requiresPairing) Color(0xFFEF4444) else Color(0xFF71717A)
                        val txtColor = when {
                            isOnline -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                            requiresPairing -> if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
                            else -> if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569)
                        }
                        val status = when {
                            isOnline -> "Online"
                            requiresPairing -> "Session expired"
                            lastSeen != null -> "Offline · $lastSeen"
                            else -> "Offline"
                        }

                        Box(modifier = Modifier.size(7.dp).scale(pulseGlow).background(dotColor, CircleShape))
                        Text(
                            text = status,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = txtColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isOnline) {
                            Text("•", color = Color(0xFF64748B), fontSize = 11.sp)
                            Text(
                                text = "Live Sync",
                                fontSize = 12.sp,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (isOnline) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(if (isDark) Color(0xFF13172E).copy(alpha = 0.8f) else Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChevronRight, null, tint = if (isDark) Color(0xFF818CF8) else Color(0xFF6366F1), modifier = Modifier.size(16.dp))
                }
            } else {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF13172E).copy(alpha = 0.90f) else Color(0xFFECFDF5))
                        .border(1.dp, if (isDark) Brush.linearGradient(listOf(Color(0xFF10B981).copy(0.6f), Color(0xFF00E5FF).copy(0.4f))) else Brush.linearGradient(listOf(Color(0xFF10B981).copy(0.4f), Color(0xFF059669).copy(0.3f))), RoundedCornerShape(12.dp))
                        .clickable { if (requiresPairing) onPairDevice() else onRefresh() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = if (requiresPairing) "Pair" else "Check", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF10B981) else Color(0xFF059669), softWrap = false, maxLines = 1)
                        Icon(Icons.Default.ChevronRight, null, tint = if (isDark) Color(0xFF10B981) else Color(0xFF059669), modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LivingDesktopTerminalCanvas(isOnline: Boolean, isDark: Boolean = true) {
    val sweep by rememberInfiniteTransition(label = "TerminalSweep").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "sweep"
    )

    Canvas(modifier = Modifier.size(30.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(
            color = if (isOnline) (if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)) else (if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)),
            topLeft = Offset(w * 0.16f, h * 0.16f), size = Size(w * 0.68f, h * 0.50f),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()), style = Stroke(width = 1.6.dp.toPx())
        )
        drawRoundRect(
            color = if (isDark) Color(0xFF070B18) else Color(0xFFF1F5F9),
            topLeft = Offset(w * 0.20f, h * 0.20f), size = Size(w * 0.60f, h * 0.42f), cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        )

        if (isOnline) {
            val cursorX = w * (0.24f + 0.40f * sweep)
            drawLine(Color(0xFF00E5FF), Offset(w * 0.24f, h * 0.32f), Offset(cursorX, h * 0.32f), strokeWidth = 1.3.dp.toPx())
            drawLine(Color(0xFFC084FC).copy(0.7f), Offset(w * 0.24f, h * 0.44f), Offset(w * 0.50f, h * 0.44f), strokeWidth = 1.3.dp.toPx())
            drawCircle(Color(0xFF10B981), radius = 1.dp.toPx(), center = Offset(w * 0.72f, h * 0.54f))
        }

        val base = Path().apply {
            moveTo(w * 0.08f, h * 0.76f); lineTo(w * 0.92f, h * 0.76f)
            lineTo(w * 0.86f, h * 0.84f); lineTo(w * 0.14f, h * 0.84f); close()
        }
        drawPath(base, if (isOnline) Color(0xFF818CF8) else Color(0xFF64748B))
    }
}
