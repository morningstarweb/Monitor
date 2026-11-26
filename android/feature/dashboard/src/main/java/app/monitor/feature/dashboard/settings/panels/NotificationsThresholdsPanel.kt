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
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.dashboard.settings.model.SettingsPreferences
import app.monitor.feature.dashboard.settings.ui.components.ObsidianGlassCard

private val WarningAmber = Color(0xFFFFB300)
private val NeonPurple = Color(0xFFA855F7)
private val DestructiveRed = Color(0xFFEF4444)

@Composable
fun NotificationsThresholdsPanel(
    preferences: SettingsPreferences,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onCpuThresholdSelected: (Int) -> Unit,
    onRamThresholdSelected: (Int) -> Unit,
    onBatteryThresholdSelected: (Int) -> Unit,
    onToggleBackgroundSync: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val chevronRot by animateFloatAsState(targetValue = if (isExpanded) 90f else 0f, label = "chevron")

    ObsidianGlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (isDark) Color(0xFF241C10) else Color(0xFFFEF3C7)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                    }
                    Text("Alerts & Thresholds", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0F172A), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val statusColor = if (preferences.notificationsEnabled) WarningAmber else (if (isDark) Color(0xFF71717A) else Color(0xFF64748B))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(statusColor.copy(alpha = 0.12f))
                            .border(0.8.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = if (preferences.notificationsEnabled) "Active" else "Off", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor, maxLines = 1, softWrap = false)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Expand", tint = if (isDark) Color(0xFFA1A1AA) else Color(0xFF64748B), modifier = Modifier.size(20.dp).rotate(chevronRot))
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                    // Master Alerts Switch moved inside the expanded panel
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Notifications", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0F172A))
                        Switch(checked = preferences.notificationsEnabled, onCheckedChange = onToggleMaster, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = WarningAmber))
                    }

                    if (preferences.notificationsEnabled) {
                        HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                        // CPU Threshold
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("CPU Alert", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569))
                                Text("${preferences.cpuThresholdPercent}% Limit", fontSize = 11.sp, color = WarningAmber, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(80, 85, 90).forEach { th ->
                                    val isSelected = preferences.cpuThresholdPercent == th
                                    Box(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) (if (isDark) WarningAmber.copy(alpha = 0.2f) else Color(0xFFFEF3C7)) else (if (isDark) Color(0xFF1E1710) else Color(0xFFF1F5F9)))
                                            .border(1.dp, if (isSelected) WarningAmber else (if (isDark) Color(0x22FFFFFF) else Color(0x15000000)), RoundedCornerShape(10.dp))
                                            .clickable { onCpuThresholdSelected(th) }.padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "$th%", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) WarningAmber else (if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569)))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                        // RAM Threshold
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("RAM Alert", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569))
                                Text("${preferences.ramThresholdPercent}% Limit", fontSize = 11.sp, color = NeonPurple, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(75, 85, 90).forEach { th ->
                                    val isSelected = preferences.ramThresholdPercent == th
                                    Box(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) (if (isDark) NeonPurple.copy(alpha = 0.22f) else Color(0xFFEDE9FE)) else (if (isDark) Color(0xFF14101E) else Color(0xFFF1F5F9)))
                                            .border(1.dp, if (isSelected) NeonPurple else (if (isDark) Color(0x22FFFFFF) else Color(0x15000000)), RoundedCornerShape(10.dp))
                                            .clickable { onRamThresholdSelected(th) }.padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$th%",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) (if (isDark) NeonPurple else Color(0xFF7C3AED)) else (if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569))
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                        // Battery Threshold
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Low Battery Alert", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569))
                                Text("${preferences.batteryLowThresholdPercent}% Reserve", fontSize = 11.sp, color = DestructiveRed, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(15 to "15% Alert", 20 to "20% Reserve").forEach { (th, label) ->
                                    val isSelected = preferences.batteryLowThresholdPercent == th
                                    Box(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) (if (isDark) DestructiveRed.copy(alpha = 0.22f) else Color(0xFFFEE2E2)) else (if (isDark) Color(0xFF201114) else Color(0xFFF1F5F9)))
                                            .border(1.dp, if (isSelected) DestructiveRed else (if (isDark) Color(0x22FFFFFF) else Color(0x15000000)), RoundedCornerShape(10.dp))
                                            .clickable { onBatteryThresholdSelected(th) }.padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) DestructiveRed else (if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569)))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                        // Background Sync Toggle
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Background Monitoring", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0F172A))
                            Switch(checked = preferences.backgroundSyncEnabled, onCheckedChange = onToggleBackgroundSync, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = WarningAmber))
                        }
                    }
                }
            }
        }
    }
}
