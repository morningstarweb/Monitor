package app.monitor.feature.dashboard.dialogs

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.dashboard.components.SettingsDetailRow

private val PrimaryPurple = Color(0xFF8B5CF6)
private val BorderSubtle = Color(0x30818CF8)
private val SuccessGreen = Color(0xFF10B981)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondaryLight = Color(0xFFA1A1AA)
private val TextMutedDark = Color(0xFF71717A)

@Composable
fun AppearanceDialog(
    selectedThemeName: String,
    onThemeSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Appearance", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System default", "Dark", "Light").forEach { themeOption ->
                    val isSelected = selectedThemeName == themeOption
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0x268B5CF6) else Color(0xFF1E1D36))
                            .border(1.dp, if (isSelected) PrimaryPurple else BorderSubtle, RoundedCornerShape(12.dp))
                            .clickable { onThemeSelect(themeOption) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = themeOption,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFFC084FC) else TextWhite
                            )
                            if (isSelected) {
                                Box(modifier = Modifier.size(18.dp).background(SuccessGreen, CircleShape))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = TextSecondaryLight) } },
        containerColor = Color(0xFF151426)
    )
}

@Composable
fun AppLockDialog(
    appLockEnabled: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Lock Security", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Require Android Biometric or device lock credentials each time Monitor HQ opens.",
                    color = TextSecondaryLight,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Enable App Lock", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextWhite)
                    Switch(
                        checked = appLockEnabled,
                        onCheckedChange = onAppLockChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryPurple,
                            uncheckedThumbColor = TextMutedDark,
                            uncheckedTrackColor = BorderSubtle
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)) {
                Text("Done", color = TextWhite)
            }
        },
        containerColor = Color(0xFF151426)
    )
}

@Composable
fun AboutAppDialog(
    installedVersion: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Monitor HQ", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Monitor HQ connects your mobile device securely to your development environment with live Git control and real-time telemetry.",
                    fontSize = 13.sp,
                    color = TextSecondaryLight
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1D36))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SettingsDetailRow(label = "Application Version", value = installedVersion)
                    SettingsDetailRow(label = "Developer Studio", value = "Morning Star Web")
                    SettingsDetailRow(label = "Headquarters", value = "Kolkata, WB, India")
                    SettingsDetailRow(label = "Statutory Compliance", value = "India DPDP Act 2023 / GDPR")
                    SettingsDetailRow(label = "Contact Email", value = "contact@morningstarweb.space")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "• Terms of Service",
                        fontSize = 12.sp,
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://morningstarweb.space/terms")))
                        }
                    )
                    Text(
                        text = "• Privacy Policy",
                        fontSize = 12.sp,
                        color = Color(0xFF06B6D4),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://morningstarweb.space/privacy")))
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = TextSecondaryLight) } },
        containerColor = Color(0xFF151426)
    )
}
