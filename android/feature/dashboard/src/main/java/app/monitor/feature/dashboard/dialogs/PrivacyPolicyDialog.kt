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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val NeonCyan = Color(0xFF00E5FF)
private val NeonEmerald = Color(0xFF00FF87)
private val CardSurface = Color(0xFF0D141C)
private val BorderStroke = Color(0x3300E5FF)

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(CardSurface)
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(NeonCyan.copy(alpha = 0.4f), Color(0x1A00E5FF), Color.Transparent)),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeonCyan.copy(alpha = 0.15f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text("Privacy Policy", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Zero surveillance & local telemetry", fontSize = 12.sp, color = Color(0xFFA1A1AA))
                    }
                }

                // Scrollable highlights
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrivacyHighlightCard(
                        icon = Icons.Default.Shield,
                        title = "Zero Surveillance & DPDP / GDPR",
                        description = "Morning Star Web (Kolkata, India) complies with India DPDP Act 2023, GDPR Art. 5–6, and UK GDPR. Zero keylogging, zero source-code uploads, and full erasure on unpair."
                    )
                    PrivacyHighlightCard(
                        icon = Icons.Default.Storage,
                        title = "Local-First Architecture",
                        description = "CPU, RAM, disk, and Git DAG telemetry stay on your workstation and flow directly between your paired devices."
                    )
                    PrivacyHighlightCard(
                        icon = Icons.Default.Lock,
                        title = "Encrypted Hardware Keystore",
                        description = "Device pairing tokens are encrypted via hardware-backed AES-256-GCM Direct AndroidKeyStore (SecureStorage) and TLS 1.3 / Noise IK."
                    )
                    PrivacyHighlightCard(
                        icon = Icons.Default.Sync,
                        title = "Cloud Broker & Google OAuth",
                        description = "Optional Google OAuth sign-in stores your subject ID, device pairing metadata, and transient relay state on Cloudflare Workers/D1 for out-of-home sync. Grievance: contact@morningstarweb.space | +91 78649 23844."
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF141D26))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://morningstarweb.space/privacy"))
                                runCatching { context.startActivity(intent) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color(0xFFA1A1AA), modifier = Modifier.size(16.dp))
                            Text("Open Web", fontSize = 13.sp, color = Color(0xFFA1A1AA), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeonCyan)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Close", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF060A08))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyHighlightCard(icon: ImageVector, title: String, description: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F1722))
            .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp).padding(top = 2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(description, fontSize = 11.sp, color = Color(0xFFA1A1AA), lineHeight = 16.sp)
            }
        }
    }
}
