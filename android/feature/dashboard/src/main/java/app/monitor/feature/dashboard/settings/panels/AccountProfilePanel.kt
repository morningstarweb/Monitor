package app.monitor.feature.dashboard.settings.panels

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.dashboard.components.GoogleGLogo
import app.monitor.feature.dashboard.settings.model.AccountProfileState
import app.monitor.feature.dashboard.settings.ui.components.ObsidianGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val GlassBorderStroke = Color(0x33FFFFFF)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonEmerald = Color(0xFF00FF87)
private val NeonPurple = Color(0xFFA855F7)
private val DestructiveRed = Color(0xFFEF4444)

@Composable
fun AccountProfilePanel(
    state: AccountProfileState,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val chevronRot by animateFloatAsState(targetValue = if (isExpanded) 90f else 0f, label = "chevron")

    ObsidianGlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.8f), NeonPurple.copy(alpha = 0.8f))))
                                .border(1.5.dp, GlassBorderStroke, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            ProfileAvatarContent(state.photoUrl, state.displayName.take(2).uppercase())
                        }
                        if (state.hasGoogleAuth) {
                            Box(
                                modifier = Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape)
                                    .background(if (isDark) Color(0xFF0E1016) else Color.White)
                                    .border(1.2.dp, if (isDark) GlassBorderStroke else Color(0x30CBD5E1), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                GoogleGLogo(modifier = Modifier.size(11.dp))
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                        Text(text = state.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0F172A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = state.userEmail ?: "Local Peer", fontSize = 12.sp, color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "Expand", tint = if (isDark) Color(0xFFA1A1AA) else Color(0xFF64748B), modifier = Modifier.size(20.dp).rotate(chevronRot))
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x15000000), thickness = 0.8.dp)
                    val expiration = if (!state.isSessionActive) "Inactive" else (state.daysUntilExpiration?.let { "${it}d left" } ?: "Active")
                    AccountDetailRow(Icons.Default.Schedule, NeonCyan, "Session", expiration, NeonEmerald, isDark)
                    AccountDetailRow(Icons.Default.Security, NeonPurple, "Security", "Encrypted Storage", if (isDark) Color.White else Color(0xFF0F172A), isDark)
                    AccountDetailRow(Icons.Default.Key, Color(0xFF71717A), "Account", if (state.hasGoogleAuth) "Cloud Verified" else "Local Pair", if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569), isDark)
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF261214) else Color(0xFFFEE2E2))
                            .border(1.dp, DestructiveRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable(enabled = !state.isRevokingSession, onClick = onSignOutClick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isRevokingSession) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DestructiveRed, strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = DestructiveRed, modifier = Modifier.size(16.dp))
                                Text(text = if (state.hasGoogleAuth) "Sign Out" else "Unpair Workstation", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DestructiveRed, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatarContent(photoUrl: String?, initials: String) {
    var avatarBitmap by remember(photoUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photoUrl) {
        if (!photoUrl.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(photoUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 4000; conn.readTimeout = 4000; conn.doInput = true; conn.connect()
                    conn.inputStream.use { stream -> BitmapFactory.decodeStream(stream)?.asImageBitmap() }
                }.getOrNull()?.let { avatarBitmap = it }
            }
        }
    }
    val bitmap = avatarBitmap
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = "Profile", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        Text(text = initials, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun AccountDetailRow(icon: ImageVector, iconTint: Color, label: String, value: String, valueColor: Color, isDark: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            Text(label, fontSize = 12.sp, color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
