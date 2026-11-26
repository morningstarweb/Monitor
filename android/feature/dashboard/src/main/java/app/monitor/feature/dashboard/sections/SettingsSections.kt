package app.monitor.feature.dashboard.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.dashboard.components.SettingsActionRow
import app.monitor.feature.dashboard.components.SettingsLaptopIcon
import app.monitor.feature.dashboard.components.SettingsSectionContainer
import app.monitor.feature.dashboard.components.SettingsToggleRow

private val PrimaryPurple = Color(0xFF8B5CF6)
private val SuccessGreen = Color(0xFF10B981)
private val DestructiveRed = Color(0xFFEF4444)
private val BorderSubtle = Color(0x30818CF8)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondaryLight = Color(0xFFA1A1AA)
private val TextMutedDark = Color(0xFF71717A)

@Composable
fun SettingsDeviceSection(
    hostname: String,
    isOnline: Boolean,
    onManagePairingClick: () -> Unit,
    onUnpairClick: () -> Unit
) {
    SettingsSectionContainer(title = "Device") {
        SettingsActionRow(
            icon = { SettingsLaptopIcon(tint = Color(0xFFC084FC), modifier = Modifier.size(20.dp)) },
            title = "Connected desktop",
            subtitle = hostname,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isOnline) Color(0xFF0D2B1D) else Color(0xFF261D1D))
                            .border(1.dp, if (isOnline) SuccessGreen else BorderSubtle, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(if (isOnline) SuccessGreen else TextMutedDark, CircleShape))
                            Text(text = if (isOnline) "Connected" else "Offline", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isOnline) SuccessGreen else TextSecondaryLight)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondaryLight, modifier = Modifier.size(18.dp))
                }
            },
            onClick = onManagePairingClick
        )
        HorizontalDivider(color = Color(0x1F818CF8), thickness = 0.8.dp)
        SettingsActionRow(icon = { Icon(Icons.Default.Link, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(20.dp)) }, title = "Manage pairing", subtitle = "View or manage paired station", onClick = onManagePairingClick)
        HorizontalDivider(color = Color(0x1F818CF8), thickness = 0.8.dp)
        SettingsActionRow(icon = { Icon(Icons.Default.LinkOff, contentDescription = null, tint = DestructiveRed, modifier = Modifier.size(20.dp)) }, title = "Unpair device", subtitle = "Disconnect this station", onClick = onUnpairClick)
    }
}

@Composable
fun SettingsPreferencesSection(
    liveUpdatesEnabled: Boolean,
    onLiveUpdatesChange: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    selectedThemeName: String,
    onAppearanceClick: () -> Unit
) {
    SettingsSectionContainer(title = "Preferences") {
        SettingsToggleRow(
            icon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(20.dp)) },
            title = "Live updates",
            subtitle = "Refresh stats automatically",
            checked = liveUpdatesEnabled,
            onCheckedChange = onLiveUpdatesChange
        )
        HorizontalDivider(color = Color(0x1F818CF8), thickness = 0.8.dp)
        SettingsToggleRow(
            icon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(20.dp)) },
            title = "Notifications",
            subtitle = "Important system & git alerts",
            checked = notificationsEnabled,
            onCheckedChange = onNotificationsChange
        )
        HorizontalDivider(color = Color(0x1F818CF8), thickness = 0.8.dp)
        SettingsActionRow(
            icon = { Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(20.dp)) },
            title = "Appearance",
            subtitle = "App theme",
            trailing = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1D36))
                        .border(1.dp, Color(0xFF44345F), RoundedCornerShape(12.dp))
                        .clickable(onClick = onAppearanceClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = selectedThemeName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextWhite)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondaryLight, modifier = Modifier.size(16.dp))
                    }
                }
            },
            onClick = onAppearanceClick
        )
    }
}
