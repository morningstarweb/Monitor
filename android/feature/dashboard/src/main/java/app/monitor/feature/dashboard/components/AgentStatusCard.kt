package app.monitor.feature.dashboard.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.ErrorRed
import app.monitor.core.designsystem.theme.MonitorTypography
import app.monitor.core.designsystem.theme.NeonCyan
import app.monitor.core.designsystem.theme.NeonEmeraldRaw
import app.monitor.core.designsystem.theme.WarningAmber
import app.monitor.core.model.AgentSessionSummary

@Composable
fun AgentStatusCard(
    session: AgentSessionSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sessions: List<AgentSessionSummary> = listOf(session),
    onSelectSession: (String) -> Unit = {},
    onEmergencyStop: () -> Unit = {},
    isSessionMonitorActive: Boolean = false,
    onToggleSessionMonitor: (Boolean) -> Unit = {}
) {
    val activeList = remember(sessions, session) { sessions.ifEmpty { listOf(session) } }
    var menuExpanded by remember { mutableStateOf(false) }

    val state = session.state
    val isStalled = state.equals("Stalled", ignoreCase = true)
    val isActive = state.equals("Active", ignoreCase = true)

    val pulseTransition = rememberInfiniteTransition(label = "AgentPulse")
    val dotScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "agentDotScale"
    )

    val (accentColor, statusText) = when {
        isStalled -> WarningAmber to "STALLED"
        isActive -> NeonEmeraldRaw to "ACTIVE"
        else -> NeonCyan to "QUIET"
    }

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showStopConfirm by remember { mutableStateOf(false) }

    if (showStopConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            containerColor = if (isDark) Color(0xFF0C0E1A) else Color.White,
            titleContentColor = if (isDark) Color.White else Color(0xFF0F172A),
            textContentColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
            title = { Text("Emergency Stop Agent?", fontWeight = FontWeight.Bold) },
            text = { Text("This will terminate the active AI agent session (PID ${session.pid}) immediately.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showStopConfirm = false
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onEmergencyStop()
                    }
                ) { Text("Stop Session", color = ErrorRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showStopConfirm = false }) {
                    Text("Cancel", color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                }
            }
        )
    }

    val borderGlow = if (isStalled) WarningAmber.copy(alpha = 0.40f) else NeonCyan.copy(alpha = 0.32f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f))
            .border(1.dp, Brush.horizontalGradient(listOf(borderGlow, Color(0x30818CF8))), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF14182C) else Color(0xFFF1F5F9))
                            .border(1.dp, borderGlow, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SmartToy, "Agent", tint = if (isDark) accentColor else Color(0xFF0284C7), modifier = Modifier.size(17.dp))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (activeList.size > 1) Modifier.clickable { menuExpanded = true }
                                    else Modifier.clickable(onClick = onClick)
                                ),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = formatModelTitle(session.profileName),
                                    style = MonitorTypography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else Color(0xFF0F172A),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (activeList.size > 1) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isDark) Color(0xFF182039) else Color(0xFFE0E7FF))
                                            .border(1.dp, Color(0x45818CF8), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "${activeList.size}",
                                            color = if (isDark) Color(0xFFC7D2FE) else Color(0xFF4338CA),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            softWrap = false
                                        )
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Switch model",
                                            tint = Color(0xFFC7D2FE),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                            val repoName = session.repoPath
                                ?.substringAfterLast('\\')
                                ?.substringAfterLast('/')
                                ?: "Workspace"
                            Text(
                                text = "$repoName · PID ${session.pid}",
                                style = MonitorTypography.bodySmall,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (activeList.size > 1) {
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(Color(0xFF111528))
                            ) {
                                activeList.forEach { item ->
                                    val isSelected = item.sessionId == session.sessionId
                                    val itemAccent = when {
                                        item.state.equals("Stalled", ignoreCase = true) -> WarningAmber
                                        item.state.equals("Active", ignoreCase = true) -> NeonEmeraldRaw
                                        else -> NeonCyan
                                    }
                                    val itemRepo = item.repoPath
                                        ?.substringAfterLast('\\')
                                        ?.substringAfterLast('/')
                                        ?: "Workspace"
                                    DropdownMenuItem(
                                        onClick = {
                                            menuExpanded = false
                                            onSelectSession(item.sessionId)
                                        },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .background(itemAccent, CircleShape)
                                            )
                                        },
                                        trailingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = NeonCyan,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        } else null,
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                Text(
                                                    text = formatModelTitle(item.profileName),
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$itemRepo · PID ${item.pid}",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(accentColor.copy(alpha = 0.14f))
                        .border(1.dp, accentColor.copy(alpha = 0.38f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .scale(if (isActive || isStalled) dotScale else 1f)
                            .background(accentColor, CircleShape)
                    )
                    Text(
                        text = statusText,
                        style = MonitorTypography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Color(0xFF14182C) else Color(0xFFEEF2FF))
                        .border(1.dp, Color(0x40818CF8), RoundedCornerShape(10.dp))
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.History, "Checkpoints", tint = if (isDark) NeonCyan else Color(0xFF0284C7), modifier = Modifier.size(13.dp))
                        Text(
                            text = "Checkpoints",
                            style = MonitorTypography.labelSmall,
                            color = if (isDark) Color(0xFFC7D2FE) else Color(0xFF3730A3),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(0.95f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background((if (isDark) NeonCyan else Color(0xFF0284C7)).copy(alpha = if (isSessionMonitorActive) 0.18f else 0.10f))
                        .border(1.dp, (if (isDark) NeonCyan else Color(0xFF0284C7)).copy(alpha = 0.42f), RoundedCornerShape(10.dp))
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onToggleSessionMonitor(!isSessionMonitorActive)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSessionMonitorActive) "Watching" else "Keep alive",
                        style = MonitorTypography.labelSmall,
                        color = if (isDark) NeonCyan else Color(0xFF0369A1),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(0.75f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ErrorRed.copy(alpha = 0.14f))
                        .border(1.dp, ErrorRed.copy(alpha = 0.48f), RoundedCornerShape(10.dp))
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            showStopConfirm = true
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.StopCircle, "Stop", tint = ErrorRed, modifier = Modifier.size(13.dp))
                        Text(
                            text = "Stop",
                            style = MonitorTypography.labelSmall,
                            color = ErrorRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

internal fun formatModelTitle(raw: String): String {
    val cleaned = raw
        .trim()
        .removeSuffix(" Agent")
        .removeSuffix(" agent")
        .trim()
    if (cleaned.isEmpty()) return "Workspace"
    return cleaned.replaceFirstChar { it.uppercase() }
}
