package app.monitor.feature.dashboard.settings.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.dashboard.settings.model.DevicePairingState
import app.monitor.feature.dashboard.settings.ui.components.ObsidianGlassCard
import app.monitor.feature.dashboard.settings.ui.components.TelemetryPulseBeacon

private val NeonEmerald = Color(0xFF00FF87)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonPurple = Color(0xFFA855F7)
private val DestructiveRed = Color(0xFFEF4444)

@Composable
fun DeviceStationPanel(
    state: DevicePairingState,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onManagePairingClick: () -> Unit,
    onUnpairClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val chevronRot by animateFloatAsState(targetValue = if (isExpanded) 90f else 0f, label = "chevron")
    val isWebRtc = state.connectionType.contains("WebRTC", ignoreCase = true)
    val isLan = state.isOnline && !isWebRtc && !state.connectionType.contains("Cloud", ignoreCase = true)
    val statusColor = if (state.isOnline) (if (isLan) NeonEmerald else if (isWebRtc) NeonCyan else Color(0xFF38BDF8)) else Color(0xFF71717A)

    ObsidianGlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Full Hostname and Clean Monospace Port
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(statusColor.copy(alpha = 0.22f), NeonPurple.copy(alpha = 0.15f))))
                            .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Computer, contentDescription = null, tint = statusColor, modifier = Modifier.size(24.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                        Text(text = state.hostname, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0F172A), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        val endpointText = if (state.serverPort == 443) "Cloud Connected" else "Wi-Fi · ${state.serverHost}"
                        Text(text = endpointText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF64748B), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "Expand", tint = if (isDark) Color(0xFFA1A1AA) else Color(0xFF64748B), modifier = Modifier.size(20.dp).rotate(chevronRot))
            }

            // Dedicated Full-Width Telemetry Ribbon: Never cropped or crowded
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF0B101D) else Color(0xFFF1F5F9))
                    .border(0.8.dp, if (isDark) Color(0x22FFFFFF) else Color(0x10000000), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TelemetryPulseBeacon(color = statusColor, size = 4.dp, isPulsing = state.isOnline)
                    val routePillText = when {
                        !state.isOnline -> "Station Offline"
                        isLan -> "Direct Wi-Fi"
                        isWebRtc -> "WebRTC P2P"
                        else -> "Cloud Relay"
                    }
                    Text(text = routePillText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor, maxLines = 1, softWrap = false)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(11.dp))
                    Text(text = "End-to-End Encrypted", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonPurple, maxLines = 1, softWrap = false)
                }
            }

            // Expanded Animated Diagnostics & Controls
            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF0A0F1A) else Color(0xFFF8FAFC))
                            .border(0.8.dp, if (isDark) Color(0x18FFFFFF) else Color(0x10000000), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                                Text(android.os.Build.MODEL ?: "Phone", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (isDark) Color.White else Color(0xFF0F172A), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Text(if (isLan) "Wi-Fi" else if (isWebRtc) "P2P" else "Cloud", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor, maxLines = 1, softWrap = false)
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                                Text(state.hostname, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (isDark) Color.White else Color(0xFF0F172A), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(end = 5.dp))
                                Icon(Icons.Default.Computer, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val routeTitle = if (isLan) "Direct Wi-Fi" else if (isWebRtc) "WebRTC P2P" else "Cloud Relay"
                        StationMetricChip(icon = if (isLan) Icons.Default.Wifi else Icons.Default.Cloud, iconTint = statusColor, title = "CONNECTION", value = routeTitle, modifier = Modifier.weight(1f), isDark = isDark)
                        StationMetricChip(icon = Icons.Default.Security, iconTint = NeonPurple, title = "SECURITY", value = "E2EE Active", modifier = Modifier.weight(1f), isDark = isDark)
                    }

                    HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color(0xFF131F2E) else Color(0xFFE0F2FE))
                                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable(onClick = onManagePairingClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = if (isDark) NeonCyan else Color(0xFF0284C7), modifier = Modifier.size(15.dp))
                                Text("Diagnostics", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isDark) NeonCyan else Color(0xFF0284C7), maxLines = 1, softWrap = false)
                            }
                        }
                        Box(
                            modifier = Modifier.height(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color(0xFF261214) else Color(0xFFFEE2E2))
                                .border(1.dp, DestructiveRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable(onClick = onUnpairClick).padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, tint = DestructiveRed, modifier = Modifier.size(15.dp))
                                Text("Unpair", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DestructiveRed, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StationMetricChip(icon: ImageVector, iconTint: Color, title: String, value: String, modifier: Modifier = Modifier, isDark: Boolean = true) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF0D121F) else Color(0xFFF8FAFC))
            .border(0.8.dp, if (isDark) Color(0x1FFFFFFF) else Color(0x10000000), RoundedCornerShape(12.dp)).padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(13.dp))
                Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF64748B), letterSpacing = 0.5.sp)
            }
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0F172A), softWrap = false)
        }
    }
}