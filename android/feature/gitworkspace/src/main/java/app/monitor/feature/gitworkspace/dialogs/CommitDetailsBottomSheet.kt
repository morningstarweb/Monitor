package app.monitor.feature.gitworkspace.dialogs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.CommitNode
import app.monitor.core.model.FileDiffResponse
import app.monitor.core.model.GitFileChange
import app.monitor.feature.gitworkspace.components.CommitFilesChangedSection
import app.monitor.feature.gitworkspace.components.CommitTelemetryCard
import app.monitor.feature.gitworkspace.model.formatRelativeTime
import app.monitor.feature.gitworkspace.model.getLaneColor

/**
 * Ultra-Luxury Frosted Glass Commit Inspector Studio Sheet (< 110 lines).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailsBottomSheet(
    commit: CommitNode,
    workspaceFiles: List<GitFileChange> = emptyList(),
    selectedDiff: FileDiffResponse? = null,
    onFileClick: ((GitFileChange, String) -> Unit)? = null,
    onViewFullDiff: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val effectiveFiles = workspaceFiles
    val laneColor = getLaneColor(commit.colorIndex)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF090C16)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar: Modern Tag + Relative Time + 1-Tap SHA Copy Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(laneColor.copy(alpha = 0.20f))
                            .border(1.dp, laneColor.copy(alpha = 0.60f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("COMMIT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = laneColor)
                    }
                    Text(formatRelativeTime(commit.timestampUtcMs), fontSize = 12.sp, color = Color(0xFF94A3B8))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x2838BDF8))
                        .border(1.dp, Color(0x5038BDF8), RoundedCornerShape(8.dp))
                        .clickable { clipboardManager.setText(AnnotatedString(commit.oid)) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                        Text(commit.shortOid, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Commit Message
            Text(text = commit.summary, fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 24.sp)

            // Author, Parent OID & Stats Telemetry Card
            CommitTelemetryCard(
                commit = commit,
                effectiveFiles = effectiveFiles,
                laneColor = laneColor
            )

            // Changed Files Section (Tapping any file triggers diff fetch & dedicated modal)
            CommitFilesChangedSection(
                files = effectiveFiles,
                selectedDiff = selectedDiff,
                onFileClick = { file -> onFileClick?.invoke(file, commit.oid) }
            )
        }
    }
}
