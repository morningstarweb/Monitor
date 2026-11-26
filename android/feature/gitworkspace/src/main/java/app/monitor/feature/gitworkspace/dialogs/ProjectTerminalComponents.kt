package app.monitor.feature.gitworkspace.dialogs

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.TerminalLogEntry
import app.monitor.core.model.TerminalSession

val QUICK_COMMANDS = listOf("git status", "git branch -a", "git log -n 5 --oneline", "cargo check", "npm test", "npm run build", "dir")

@Composable
fun TerminalSessionTabBar(
    sessions: List<TerminalSession>,
    activeSessionId: String,
    onSelectSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onNewSession: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sessions.forEach { session ->
            val isActive = session.id == activeSessionId
            val bg = if (isActive) Color(0xFF132219) else Color(0xFF0A0F0D)
            val border = if (isActive) Color(0xFF00FF87) else Color(0xFF1B2E22)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(8.dp))
                    .clickable { onSelectSession(session.id) }
                    .padding(start = 10.dp, end = if (sessions.size > 1) 4.dp else 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (session.isRunning) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFFFFB300), CircleShape))
                }
                Text(
                    text = session.title,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) Color(0xFF00FF87) else Color(0xFF94A3B8),
                    softWrap = false,
                    maxLines = 1
                )
                if (sessions.size > 1) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onCloseSession(session.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close ${session.title}",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0D1611))
                .border(1.dp, Color(0xFF1B2E22), RoundedCornerShape(8.dp))
                .clickable(onClick = onNewSession),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, "New Terminal", tint = Color(0xFF00FF87), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun QuickScriptChipsRow(isRunning: Boolean, onSelectCommand: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QUICK_COMMANDS.forEach { cmd ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D1611))
                    .border(1.dp, Color(0xFF1B2E22), RoundedCornerShape(8.dp))
                    .clickable(enabled = !isRunning) { onSelectCommand(cmd) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(cmd, color = if (isRunning) Color(0xFF475569) else Color(0xFF00E5FF), fontSize = 11.sp, fontFamily = FontFamily.Monospace, softWrap = false)
            }
        }
    }
}

@Composable
fun TerminalLogItem(
    entry: TerminalLogEntry,
    onCopyEntry: ((String) -> Unit)? = null
) {
    val res = entry.result
    val displayBody = buildString {
        if (entry.streamingText.isNotBlank()) {
            append(entry.streamingText.trimEnd())
        } else if (res != null && res.stdout.isNotBlank()) {
            append(res.stdout.trimEnd())
        }
        if (res != null && res.stderr.isNotBlank() && !entry.streamingText.contains(res.stderr.trim())) {
            if (isNotEmpty()) append('\n')
            append(res.stderr.trimEnd())
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "$ ${entry.command}",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (res != null) {
                    val badgeColor = if (res.exitCode == 0) Color(0xFF00FF87) else Color(0xFFFF453A)
                    Text(
                        text = "[exit ${res.exitCode} · ${res.durationMs}ms]",
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false
                    )
                }
            }
            if (onCopyEntry != null && displayBody.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable { onCopyEntry("$ ${entry.command}\n$displayBody") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy command output", tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                }
            }
        }
        if (entry.streamingText.isNotBlank()) {
            Text(entry.streamingText.trimEnd(), color = Color(0xFFE2E8F0), fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
        }
        if (entry.isRunning) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val alpha by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
            Text("⚡ Streaming live output…", color = Color(0xFFFFB300).copy(alpha = alpha), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        } else if (res != null) {
            if (res.stdout.isNotBlank() && entry.streamingText.isBlank()) {
                Text(res.stdout.trimEnd(), color = Color(0xFFE2E8F0), fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
            }
            if (res.stderr.isNotBlank() && !entry.streamingText.contains(res.stderr.trim())) {
                Text(res.stderr.trimEnd(), color = Color(0xFFFF453A), fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
            }
        }
    }
}
