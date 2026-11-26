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
import androidx.compose.material.icons.filled.Speed
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
import app.monitor.feature.dashboard.settings.model.ProcessFilterMode
import app.monitor.feature.dashboard.settings.model.SamplingInterval
import app.monitor.feature.dashboard.settings.model.SettingsPreferences
import app.monitor.feature.dashboard.settings.model.TemperatureUnit
import app.monitor.feature.dashboard.settings.ui.components.ObsidianGlassCard

private val NeonEmerald = Color(0xFF00FF87)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonPurple = Color(0xFFA855F7)

@Composable
fun TelemetryPreferencesPanel(
    preferences: SettingsPreferences,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSamplingRateChange: (SamplingInterval) -> Unit,
    onTemperatureUnitChange: (TemperatureUnit) -> Unit,
    onProcessFilterChange: (ProcessFilterMode) -> Unit,
    onShowFanChange: (Boolean) -> Unit,
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
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (isDark) Color(0xFF121B16) else Color(0xFFE6FBF2)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = if (isDark) NeonEmerald else Color(0xFF059669), modifier = Modifier.size(20.dp))
                    }
                    Text("Display & Units", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0F172A), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(NeonEmerald.copy(alpha = 0.12f))
                            .border(0.8.dp, NeonEmerald.copy(alpha = 0.35f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = preferences.temperatureUnit.symbol, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) NeonEmerald else Color(0xFF059669), maxLines = 1, softWrap = false)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Expand", tint = if (isDark) Color(0xFFA1A1AA) else Color(0xFF64748B), modifier = Modifier.size(20.dp).rotate(chevronRot))
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)

                    // Temperature Unit
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Temperature Unit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0F172A), maxLines = 1, softWrap = false)
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (isDark) Color(0xFF0F1420) else Color(0xFFF1F5F9))
                                .border(1.dp, if (isDark) Color(0x22FFFFFF) else Color(0x15000000), RoundedCornerShape(10.dp)).padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TemperatureUnit.entries.forEach { unit ->
                                val isSelected = preferences.temperatureUnit == unit
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSelected) NeonCyan else Color.Transparent)
                                        .clickable { onTemperatureUnitChange(unit) }.padding(horizontal = 14.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = unit.symbol, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color(0xFF060A08) else (if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
