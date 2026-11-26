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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.dashboard.settings.model.BrokerHealthTier
import app.monitor.feature.dashboard.settings.model.BrokerSyncFrequency
import app.monitor.feature.dashboard.settings.model.BrokerSyncState
import app.monitor.feature.dashboard.settings.ui.components.ObsidianGlassCard
import app.monitor.feature.dashboard.settings.ui.components.TelemetryPulseBeacon

private val NeonEmerald = Color(0xFF00FF87)
private val NeonCyan = Color(0xFF00E5FF)
private val WarningAmber = Color(0xFFFFB300)
private val DestructiveRed = Color(0xFFEF4444)

@Composable
fun CloudflareBrokerSyncPanel(
    state: BrokerSyncState,
    isExpanded: Boolean,
    isCloudSyncEnabled: Boolean,
    onToggleExpand: () -> Unit,
    onUrlChange: (String) -> Unit,
    onToggleCloudSync: (Boolean) -> Unit,
    selectedFrequency: BrokerSyncFrequency,
    onFrequencySelected: (BrokerSyncFrequency) -> Unit,
    onPingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val chevronRot by animateFloatAsState(targetValue = if (isExpanded) 90f else 0f, label = "chevron")
    val tierColor = when (state.healthTier) {
        BrokerHealthTier.Optimal -> NeonEmerald
        BrokerHealthTier.Moderate -> NeonCyan
        BrokerHealthTier.Degraded -> WarningAmber
        BrokerHealthTier.Offline -> DestructiveRed
        BrokerHealthTier.Idle -> Color(0xFF71717A)
    }

    ObsidianGlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (isDark) Color(0xFF131A26) else Color(0xFFE0F2FE)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = if (isDark) NeonCyan else Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                        }
                        Text("Cloud Relay", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0F172A), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(tierColor.copy(alpha = 0.12f)).border(0.8.dp, tierColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            TelemetryPulseBeacon(color = tierColor, size = 5.dp, isPulsing = state.healthTier != BrokerHealthTier.Idle && state.healthTier != BrokerHealthTier.Offline)
                            val badge = state.latencyMs?.let { "${it}ms" }
                                ?: if (state.healthTier == BrokerHealthTier.Offline) "Offline" else "Standby"
                            Text(text = badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tierColor, softWrap = false, maxLines = 1)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Expand", tint = if (isDark) Color(0xFFA1A1AA) else Color(0xFF64748B), modifier = Modifier.size(20.dp).rotate(chevronRot))
                    }
                }

                AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Cloud Sync", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0F172A), maxLines = 1, softWrap = false)
                                Text("Allow remote connection when outside Wi-Fi", fontSize = 11.sp, color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Switch(checked = isCloudSyncEnabled, onCheckedChange = onToggleCloudSync, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeonCyan))
                        }

                        HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val status = state.errorMessage ?: if (state.latencyMs != null) "Cloud Connected (${state.latencyMs}ms)" else "Monitor Cloud · Ready"
                            Text(text = status, fontSize = 12.sp, color = if (state.errorMessage != null) DestructiveRed else if (isDark) Color(0xFFA1A1AA) else Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF161F2E) else Color(0xFFE0F2FE))
                                    .border(0.8.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable(enabled = !state.isSyncing, onClick = onPingClick).padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (state.isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = NeonCyan)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = if (isDark) NeonCyan else Color(0xFF0284C7), modifier = Modifier.size(13.dp))
                                        Text("Test", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) NeonCyan else Color(0xFF0284C7), maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}
