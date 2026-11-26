package app.monitor.feature.gitworkspace.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val EmeraldSignal = Color(0xFF00FF87)
private val ErrorSignal = Color(0xFFFF453A)
private val PillBackground = Color(0xF00D1611)

private fun sanitizeMessage(raw: String): String {
    val firstLine = raw.lines().firstOrNull { it.isNotBlank() }?.trim() ?: raw.trim()
    return when {
        // Fetch / Pull / Push
        firstLine.contains("fetch", true) && (firstLine.contains("complete", true) || firstLine.contains("success", true)) -> "Fetch complete"
        firstLine.contains("pull", true) && (firstLine.contains("complete", true) || firstLine.contains("success", true)) -> "Pull complete"
        firstLine.contains("push", true) && (firstLine.contains("complete", true) || firstLine.contains("success", true)) -> "Push complete"
        firstLine.contains("Already up to date", true) || firstLine.contains("Everything up-to-date", true) || firstLine.contains("up to date", true) -> "Up to date"

        // Pull Requests
        firstLine.contains("merged pr", true) || firstLine.contains("merge pr", true) -> {
            val prNum = Regex("""#(\d+)""").find(firstLine)?.value ?: ""
            if (prNum.isNotEmpty()) "PR $prNum merged" else "PR merged"
        }
        firstLine.contains("conflict in pr", true) || (firstLine.contains("conflict", true) && firstLine.contains("pr", true)) -> {
            val prNum = Regex("""#(\d+)""").find(firstLine)?.value ?: ""
            if (prNum.isNotEmpty()) "Conflict in PR $prNum" else "PR conflict"
        }

        // Branch Operations
        firstLine.contains("switched to", true) || firstLine.contains("created and switched", true) -> {
            val branch = Regex("""'([^']+)'""").find(firstLine)?.groupValues?.getOrNull(1)
            if (!branch.isNullOrBlank()) "Switched to '$branch'" else "Switched"
        }
        firstLine.contains("created branch", true) || firstLine.contains("created '", true) -> {
            val branch = Regex("""'([^']+)'""").find(firstLine)?.groupValues?.getOrNull(1)
            if (!branch.isNullOrBlank()) "Created '$branch'" else "Branch created"
        }
        firstLine.contains("merged branch", true) || firstLine.contains("merged '", true) -> {
            val branch = Regex("""'([^']+)'""").find(firstLine)?.groupValues?.getOrNull(1)
            if (!branch.isNullOrBlank()) "Merged '$branch'" else "Branch merged"
        }
        firstLine.contains("deleted branch", true) || firstLine.contains("deleted '", true) -> {
            val branch = Regex("""'([^']+)'""").find(firstLine)?.groupValues?.getOrNull(1)
            if (!branch.isNullOrBlank()) "Deleted '$branch'" else "Branch deleted"
        }

        // Changes & Commits
        firstLine.contains("staged", true) -> "Changes staged"
        firstLine.contains("unstaged", true) -> "Changes unstaged"
        firstLine.contains("committed", true) -> {
            if (firstLine.contains("pushed", true)) "Committed & pushed" else "Committed"
        }

        // Errors & Warnings
        firstLine.contains("NEEDS_FORCE_CONFIRMATION", true) -> "Unmerged: force delete needed"
        firstLine.contains("cannot switch branch while merging", true) -> "Cannot switch: merge active"
        firstLine.contains("blocked by desktop tray policy", true) -> "Blocked by desktop tray"
        firstLine.contains("branch 'origin' not found", true) -> "Cannot delete remote ref"

        else -> {
            var clean = firstLine
            val prefixes = listOf("Internal error: git ", "Internal error: ", "fatal: ", "error: ", "hint: ")
            for (p in prefixes) {
                if (clean.startsWith(p, ignoreCase = true)) clean = clean.substring(p.length).trim()
            }
            if (clean.length > 72) clean.take(69) + "..." else clean
        }
    }
}

@Composable
fun GitFeedbackPill(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isErrorHint: Boolean = false
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(3500)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        if (message != null) {
            val isError = isErrorHint ||
                message.contains("error", ignoreCase = true) ||
                message.contains("failed", ignoreCase = true) ||
                message.contains("fatal", ignoreCase = true) ||
                message.contains("conflict", ignoreCase = true) ||
                message.contains("blocked", ignoreCase = true) ||
                message.contains("cannot", ignoreCase = true) ||
                message.contains("denied", ignoreCase = true) ||
                message.contains("protected", ignoreCase = true) ||
                message.contains("invalid", ignoreCase = true) ||
                message.contains("unable", ignoreCase = true) ||
                message.contains("not found", ignoreCase = true)

            val accentColor = if (isError) ErrorSignal else EmeraldSignal
            val displayMessage = sanitizeMessage(message)

            Box(
                modifier = Modifier
                    .background(PillBackground, RoundedCornerShape(20.dp))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = displayMessage,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
