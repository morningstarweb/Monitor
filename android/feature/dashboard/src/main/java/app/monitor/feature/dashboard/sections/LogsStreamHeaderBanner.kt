package app.monitor.feature.dashboard.sections

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.feature.dashboard.model.LogLevel

private val NeonEmerald = Color(0xFF00E699)
private val WarningAmber = Color(0xFFFFB020)
private val ErrorRed = Color(0xFFEF4444)

/**
 * Pure Minimalist, Unboxed System Log Stream Header Banner (< 160 lines).
 * Features live system-synchronized heartbeat indicator and clean filter chips.
 */
@Composable
fun LogsStreamHeaderBanner(
    isOnline: Boolean,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: LogLevel?,
    onSelectFilter: (LogLevel) -> Unit,
    modifier: Modifier = Modifier,
    totalLogsCount: Int = 0
) {
    val isDark = LocalMonitorColors.current.isDark
    val pulseTransition = rememberInfiniteTransition(label = "LogSyncPulse")
    val dotScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.30f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Top Meta: Title + Live Sync Beacon + Pause Toggle + Search Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .scale(if (isOnline && !isPaused) dotScale else 1f)
                        .background(
                            if (isPaused) WarningAmber
                            else if (isOnline) (if (isDark) NeonEmerald else Color(0xFF059669)) else ErrorRed,
                            CircleShape
                        )
                )

                Text(
                    text = "AUDIT STREAM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    softWrap = false
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isPaused) (if (isDark) Color(0x20FFB020) else Color(0x15D97706))
                            else (if (isDark) Color(0x1800E5FF) else Color(0x150284C7))
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isPaused) "PAUSED" else if (isOnline) "LIVE · $totalLogsCount" else "OFFLINE",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) (if (isDark) WarningAmber else Color(0xFFD97706))
                                else if (isOnline) (if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)) else ErrorRed,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Quick Actions: Pause + Search
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isPaused) (if (isDark) Color(0x30FFB020) else Color(0x20D97706))
                            else (if (isDark) Color(0x12FFFFFF) else Color(0xFFF1F5F9))
                        )
                        .clickable(onClick = onTogglePause)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = if (isPaused) (if (isDark) WarningAmber else Color(0xFFD97706)) else (if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isPaused) "Resume" else "Pause",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPaused) (if (isDark) WarningAmber else Color(0xFFD97706)) else (if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569))
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSearchActive) (if (isDark) Color(0x3000E5FF) else Color(0x250284C7))
                            else (if (isDark) Color(0x10FFFFFF) else Color(0xFFF1F5F9))
                        )
                        .clickable(onClick = onToggleSearch),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isSearchActive) (if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7))
                               else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 2. Inline Search Input (if active)
        if (isSearchActive) {
            LogSearchInputField(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                isDark = isDark
            )
        }

        // 3. Filter Chips Row
        LogFilterChipsRow(
            selectedFilter = selectedFilter,
            onSelectFilter = onSelectFilter,
            isDark = isDark
        )

        // Hairline Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(if (isDark) Color(0x12FFFFFF) else Color(0x150F172A))
        )
    }
}
