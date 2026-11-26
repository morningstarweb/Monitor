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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.gitworkspace.components.GitBranchVectorIcon
import app.monitor.feature.gitworkspace.components.GraphSearchBar

private val BorderSubtle = Color(0x40818CF8)

/**
 * Clean Executive Header for Git Graph (< 130 lines).
 * Aligns 1:1 with WorkspaceHeaderSection and HomeHeaderSection.
 */
@Composable
fun GitGraphHeaderSection(
    repoDisplayName: String,
    currentBranch: String,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onProjectDropdownClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {}
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val pulseTransition = rememberInfiniteTransition(label = "GitGraphPulse")
    val dotScale by pulseTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.30f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotScale"
    )

    val btnBg = if (isDark) Color(0xFF0E1220).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.92f)
    val btnBorder = if (isDark) Brush.linearGradient(listOf(Color(0x50818CF8), Color(0x30C084FC)))
                    else Brush.linearGradient(listOf(Color(0x30818CF8), Color(0x20CBD5E1)))
    val iconTint = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Living 34dp Git Branch Logo + Clean "Git Graph" + Living Status Dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))))
                        .border(1.dp, Color(0x80C084FC), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    GitBranchVectorIcon(tint = Color.White, modifier = Modifier.size(18.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Git Graph",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        letterSpacing = (-0.4).sp
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(dotScale)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                }
            }

            // Right: 34dp Compact Action Buttons (Projects, Search, Refresh)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Select Project Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(btnBg)
                        .border(1.dp, btnBorder, CircleShape)
                        .clickable(onClick = onProjectDropdownClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Select Project", tint = iconTint, modifier = Modifier.size(16.dp))
                }

                // Search Commits Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isSearchActive) (if (isDark) Color(0x408B5CF6) else Color(0xFFEDE9FE)) else btnBg)
                        .border(1.dp, if (isSearchActive) androidx.compose.ui.graphics.SolidColor(Color(0xFF8B5CF6)) else btnBorder, CircleShape)
                        .clickable(onClick = onToggleSearch),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search Commits",
                        tint = if (isSearchActive) (if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)) else iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Refresh Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(btnBg)
                        .border(1.dp, btnBorder, CircleShape)
                        .clickable(onClick = onRefresh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Graph", tint = iconTint, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Collapsible Search Bar
        GraphSearchBar(
            isVisible = isSearchActive,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange
        )
    }
}
