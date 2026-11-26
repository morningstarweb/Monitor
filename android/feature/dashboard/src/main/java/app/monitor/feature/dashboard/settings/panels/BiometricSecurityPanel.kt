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
import androidx.compose.material.icons.filled.Fingerprint
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
import app.monitor.feature.dashboard.settings.model.SecuritySettingsState
import app.monitor.feature.dashboard.settings.model.SettingsPreferences
import app.monitor.feature.dashboard.settings.ui.components.ObsidianGlassCard

private val NeonPurple = Color(0xFFA855F7)
private val NeonEmerald = Color(0xFF00FF87)
private val WarningAmber = Color(0xFFFFB300)

@Composable
fun BiometricSecurityPanel(
    preferences: SettingsPreferences,
    securityState: SecuritySettingsState,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleAppLock: (Boolean) -> Unit,
    onToggleBiometricOnResume: (Boolean) -> Unit,
    onToggleDestructiveGating: (Boolean) -> Unit,
    onToggleRemoteTerminal: (Boolean) -> Unit,
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
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (isDark) Color(0xFF1B1428) else Color(0xFFEDE9FE)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = if (isDark) NeonPurple else Color(0xFF7C3AED), modifier = Modifier.size(20.dp))
                    }
                    Text("Security & Permissions", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0F172A), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val statusColor = if (securityState.isBiometricHardwareAvailable) (if (isDark) NeonEmerald else Color(0xFF059669)) else WarningAmber
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(statusColor.copy(alpha = 0.15f))
                            .border(0.8.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(text = if (securityState.isBiometricHardwareAvailable) "Ready" else "No Sensor", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor, maxLines = 1, softWrap = false)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Expand", tint = if (isDark) Color(0xFFA1A1AA) else Color(0xFF64748B), modifier = Modifier.size(20.dp).rotate(chevronRot))
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("App Lock", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0F172A))
                        Switch(checked = preferences.appLockEnabled, onCheckedChange = onToggleAppLock, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeonPurple))
                    }

                    if (preferences.appLockEnabled) {
                        HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Lock on Resume", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0F172A))
                            Switch(checked = preferences.biometricPromptOnResume, onCheckedChange = onToggleBiometricOnResume, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeonPurple))
                        }

                        HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gate Destructive Actions", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0F172A))
                            Switch(checked = preferences.biometricGatingDestructiveOps, onCheckedChange = onToggleDestructiveGating, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeonPurple))
                        }
                    }

                    HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Remote Terminal Access", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0F172A))
                            Text("Allow executing commands in repo directory", fontSize = 11.sp, color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF64748B))
                        }
                        Switch(
                            checked = preferences.remoteTerminalEnabled,
                            onCheckedChange = onToggleRemoteTerminal,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeonEmerald)
                        )
                    }
                }
            }
        }
    }
}
