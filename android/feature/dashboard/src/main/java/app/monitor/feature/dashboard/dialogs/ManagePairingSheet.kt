package app.monitor.feature.dashboard.dialogs

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.network.TokenStorage
import app.monitor.feature.dashboard.DashboardUiState
import app.monitor.feature.dashboard.settings.ui.components.TelemetryPulseBeacon

private val NeonEmerald = Color(0xFF00FF87)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonPurple = Color(0xFFA855F7)
private val DestructiveRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePairingBottomSheet(
    hostname: String,
    uiState: DashboardUiState,
    tokenStorage: TokenStorage?,
    onUnpairClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val isWebRtc = uiState.activeRoute.contains("WebRTC", ignoreCase = true)
    val isCloud = !isWebRtc && uiState.activeRoute.contains("Cloud", ignoreCase = true)
    val host = if (isCloud) "your-monitor-broker.workers.dev" else (tokenStorage?.getServerHost()?.takeIf { it.isNotBlank() } ?: "127.0.0.1")
    val port = if (isCloud) 443 else (tokenStorage?.getServerPort() ?: 9480)
    val routeColor = if (isWebRtc) (if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)) else if (isCloud) (if (isDark) NeonCyan else Color(0xFF0284C7)) else (if (isDark) NeonEmerald else Color(0xFF059669))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xFF0A0D18) else Color.White,
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape).background(if (isDark) Color(0x44FFFFFF) else Color(0xFFCBD5E1))
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Station Diagnostics", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0F172A))
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(NeonEmerald.copy(alpha = 0.12f))
                        .border(0.8.dp, NeonEmerald.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    TelemetryPulseBeacon(color = NeonEmerald, size = 5.dp, isPulsing = uiState.isOnline)
                    Text("E2EE Noise IK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                }
            }

            // Hero Station Card
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF13172E).copy(alpha = 0.90f), Color(0xFF090C19).copy(alpha = 0.95f))))
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color(0xFF818CF8).copy(alpha = 0.15f), Color.White.copy(alpha = 0.08f))), RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1B2238)), contentAlignment = Alignment.Center) {
                                Icon(if (isCloud) Icons.Default.Cloud else Icons.Default.Computer, contentDescription = null, tint = routeColor, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(hostname, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (isCloud) "workers.dev : 443" else "$host : $port", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFA1A1AA), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isCloud) Color(0xFF0C2434) else if (isWebRtc) Color(0xFF102838) else Color(0xFF0D281C)).border(0.8.dp, routeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(if (isWebRtc) "WebRTC P2P" else if (isCloud) "Cloud Relay" else "< 5ms LAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = routeColor, softWrap = false)
                        }
                    }

                    // Network Topology Graphic
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A0F1D)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f, fill = false)) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                                Text(android.os.Build.MODEL ?: "Mobile Device", fontSize = 10.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val topoText = if (isWebRtc) "⚡ WebRTC P2P (<18ms)" else if (isCloud) "☁ Cloudflare Relay" else "⚡ Direct Wi-Fi LAN"
                            Text(topoText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = routeColor, modifier = Modifier.padding(horizontal = 6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f, fill = false)) {
                                Text(hostname, fontSize = 10.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.Computer, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // 2x2 Telemetry Matrix
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val routeName = if (isWebRtc) "WebRTC P2P Stream" else if (isCloud) "Cloud Edge Relay" else "Direct LAN Stream"
                GlassMatrixTile(if (isCloud) Icons.Default.Cloud else Icons.Default.Wifi, routeColor, "ROUTE", routeName, Modifier.weight(1f))
                GlassMatrixTile(Icons.Default.Security, NeonPurple, "PROTOCOL", "Noise IK Curve25519", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassMatrixTile(Icons.Default.Lock, NeonCyan, "CIPHER", if (isWebRtc) "DTLS-SRTP / SCTP" else "ChaCha20-Poly1305", Modifier.weight(1f))
                GlassMatrixTile(Icons.Default.Cloud, Color(0xFF38BDF8), "CLOUD SYNC", "Global Edge Active", Modifier.weight(1f))
            }

            // Permissions Badges
            val gitPerm = if (uiState.permissions?.gitAccessAllowed == true) "Authorized" else "Read-Only"
            val opsPerm = if (uiState.permissions?.deleteAccessAllowed == true) "Authorized" else "Protected"
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PermBadge(Icons.Default.CheckCircle, NeonEmerald, "Git Mutations", gitPerm, Modifier.weight(1f))
                PermBadge(Icons.Default.CheckCircle, NeonEmerald, "Destructive Ops", opsPerm, Modifier.weight(1f))
            }

            // Unpair Destructive Button
            Button(
                onClick = onUnpairClick,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF261214)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DestructiveRed.copy(alpha = 0.5f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LinkOff, contentDescription = null, tint = DestructiveRed, modifier = Modifier.size(16.dp))
                    Text("Unpair this workstation", color = DestructiveRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun GlassMatrixTile(icon: ImageVector, tint: Color, label: String, value: String, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0F1424)).border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp)).padding(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA1A1AA))
            }
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun PermBadge(icon: ImageVector, tint: Color, title: String, status: String, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF0E1320)).border(0.8.dp, Color(0x18FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 10.sp, color = Color(0xFFA1A1AA))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
                Text(status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint)
            }
        }
    }
}
