package app.monitor.feature.dashboard.settings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.components.MonitorGlobalBackgroundCanvas

private val NeonPurple = Color(0xFFA855F7)
private val NeonCyan = Color(0xFF00E5FF)

@Composable
fun AppLockOverlay(
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxSize()
            // Fully opaque: protected content must not be visible underneath (AUD-02).
            .background(if (isDark) Color(0xFF07080E) else Color(0xFFF3F6F8))
            // Consume pointer input across the whole overlay so touches cannot
            // reach the protected content composed beneath it.
            .clickable(interactionSource = null, indication = null, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        MonitorGlobalBackgroundCanvas(isDarkTheme = isDark)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(NeonPurple.copy(alpha = if (isDark) 0.3f else 0.16f), NeonCyan.copy(alpha = if (isDark) 0.3f else 0.16f))
                        )
                    )
                    .border(1.5.dp, if (isDark) Color(0x44FFFFFF) else Color(0x406366F1), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isDark) Color.White else Color(0xFF0F172A),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Monitor Locked",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = errorMessage ?: "Authenticate with Face, Fingerprint, or PIN",
                fontSize = 13.sp,
                color = if (errorMessage != null) {
                    if (isDark) Color(0xFFFF453A) else Color(0xFFDC2626)
                } else {
                    if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569)
                }
            )

            Spacer(modifier = Modifier.height(26.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color(0xFF1B1B2A) else Color.White)
                    .border(1.dp, NeonPurple.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onUnlockClick()
                    }
                    .padding(horizontal = 24.dp, vertical = 13.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = if (isDark) NeonCyan else Color(0xFF7C3AED),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Unlock App",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A)
                    )
                }
            }
        }
    }
}
