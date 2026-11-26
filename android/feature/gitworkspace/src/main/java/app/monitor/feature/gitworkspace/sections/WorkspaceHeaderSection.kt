package app.monitor.feature.gitworkspace.sections

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.feature.gitworkspace.components.LivingFolderLogo

/**
 * Clean Minimalist Header for Projects / Workspace Screen (< 120 lines).
 * Aligns 1:1 with HomeHeaderSection: Living Logo, clean title, living status dot, and 34dp action buttons.
 */
@Composable
fun WorkspaceHeaderSection(
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onProjectsMenuClick: () -> Unit = {},
    onAuthorProfileClick: () -> Unit = {}
) {
    val isDark = LocalMonitorColors.current.isDark
    val pulseTransition = rememberInfiniteTransition(label = "WorkspaceHeaderPulse")
    val dotScale by pulseTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.30f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotScale"
    )

    val buttonBg = if (isDark) Color(0xFF0E1220).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.90f)
    val buttonBorder = if (isDark) Brush.linearGradient(listOf(Color(0x50818CF8), Color(0x30C084FC)))
                       else Brush.linearGradient(listOf(Color(0x30818CF8), Color(0x20CBD5E1)))
    val iconTint = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LivingFolderLogo(size = 34.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Workspaces",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(dotScale)
                        .background(if (isDark) Color(0xFF10B981) else Color(0xFF059669), CircleShape)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(buttonBg)
                    .border(1.dp, buttonBorder, CircleShape)
                    .clickable(onClick = onAuthorProfileClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, "Git Author Profile", tint = iconTint, modifier = Modifier.size(16.dp))
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(buttonBg)
                    .border(1.dp, buttonBorder, CircleShape)
                    .clickable(onClick = onSearchClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, "Search", tint = iconTint, modifier = Modifier.size(16.dp))
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(buttonBg)
                    .border(1.dp, buttonBorder, CircleShape)
                    .clickable(onClick = onProjectsMenuClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FolderOpen, "Select Project", tint = iconTint, modifier = Modifier.size(16.dp))
            }
        }
    }
}
