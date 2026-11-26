package app.monitor.feature.gitworkspace.sections

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors

/**
 * Compact Executive Obsidian Terminal Quick Bar (< 160 lines).
 * Follows the unified Obsidian glass design system.
 */
@Composable
fun WorkspaceTerminalQuickCard(
    activeSessionsCount: Int,
    isRunning: Boolean,
    onOpenTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalMonitorColors.current.isDark
    val pulse = rememberInfiniteTransition(label = "terminalPulse")
    val scale by pulse.animateFloat(
        initialValue = 0.85f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val cardShape = RoundedCornerShape(14.dp)
    val cardBg = if (isDark) Color(0xFF0D1611).copy(alpha = 0.94f) else Color(0xFFF8FAFC)
    val borderBrush = if (isRunning) {
        Brush.horizontalGradient(listOf(Color(0xFF00FF87), Color(0xFF00E5FF)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF1B2E22), Color(0xFF132219)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBg)
            .border(1.dp, borderBrush, cardShape)
            .clickable(onClick = onOpenTerminal)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF060A08))
                        .border(1.dp, if (isRunning) Color(0xFFFFB300) else Color(0xFF00FF87).copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Terminal",
                        tint = if (isRunning) Color(0xFFFFB300) else Color(0xFF00FF87),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Project Terminal",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .scale(if (isRunning) scale else 1f)
                                .background(
                                    if (isRunning) Color(0xFFFFB300) else Color(0xFF00FF87),
                                    CircleShape
                                )
                        )
                    }

                    Text(
                        text = if (isRunning) "Process active in CWD" else "$activeSessionsCount session(s) · Ready",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isRunning) Color(0xFFFFB300) else Color(0xFF64748B),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF060A08))
                    .border(1.dp, Color(0xFF1B2E22), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Launch >_",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF87),
                        maxLines = 1,
                        softWrap = false
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = Color(0xFF00FF87),
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }
    }
}
