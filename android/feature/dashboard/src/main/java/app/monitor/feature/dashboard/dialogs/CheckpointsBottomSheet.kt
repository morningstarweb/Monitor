package app.monitor.feature.dashboard.dialogs

import androidx.compose.foundation.layout.navigationBarsPadding

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.ErrorRed
import app.monitor.core.designsystem.theme.MonitorTypography
import app.monitor.core.designsystem.theme.NeonCyan
import app.monitor.core.designsystem.theme.NeonPink
import app.monitor.core.model.AgentSessionSummary
import app.monitor.core.model.CheckpointItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckpointsBottomSheet(
    checkpoints: List<CheckpointItem>,
    activeSession: AgentSessionSummary? = null,
    onRollback: (String) -> Unit,
    onCreateCheckpoint: () -> Unit = {},
    errorMessage: String? = null,
    onEmergencyStop: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingRollbackId by remember { mutableStateOf<String?>(null) }
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDark) Color(0xFF0C0E1A) else Color.White,
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF161B30) else Color(0xFFEEF2FF))
                            .border(1.dp, Color(0x40818CF8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.History, "Checkpoints", tint = if (isDark) NeonCyan else Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Git Checkpoints", style = MonitorTypography.labelLarge, color = if (isDark) Color.White else Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        val countStr = if (checkpoints.size == 1) "1 safety snapshot" else "${checkpoints.size} safety snapshots"
                        Text("$countStr · 1-Tap restore", style = MonitorTypography.bodySmall, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background((if (isDark) NeonCyan else Color(0xFF0284C7)).copy(alpha = 0.16f))
                            .border(1.dp, (if (isDark) NeonCyan else Color(0xFF0284C7)).copy(alpha = 0.50f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onCreateCheckpoint)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+ Save",
                            color = if (isDark) NeonCyan else Color(0xFF0284C7),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ErrorRed.copy(alpha = 0.14f))
                        .border(1.dp, ErrorRed.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (activeSession != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A121E))
                        .border(1.dp, ErrorRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeSession.profileName.replaceFirstChar { it.uppercase() }} · PID ${activeSession.pid}",
                        style = MonitorTypography.labelSmall,
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ErrorRed.copy(alpha = 0.20f))
                            .border(1.dp, ErrorRed.copy(alpha = 0.60f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onEmergencyStop)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.StopCircle, "Stop", tint = ErrorRed, modifier = Modifier.size(13.dp))
                            Text(
                                text = "Stop",
                                style = MonitorTypography.labelSmall,
                                color = ErrorRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (checkpoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF14182C).copy(alpha = 0.94f))
                        .border(1.dp, Color(0x30818CF8), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No Checkpoints Recorded Yet", style = MonitorTypography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Shadow snapshots are saved automatically while an agent is modifying files.", style = MonitorTypography.bodySmall, color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    items(checkpoints, key = { it.checkpointId }) { item ->
                        CheckpointRowItem(item = item, onRequestRollback = { pendingRollbackId = item.checkpointId })
                    }
                }
            }
        }
    }

    pendingRollbackId?.let { chkId ->
        AlertDialog(
            onDismissRequest = { pendingRollbackId = null },
            title = { Text("Confirm 1-Tap Rollback", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Restore working copy to this snapshot? An automated pre-rollback safety snapshot will be taken first so you can always revert.", color = Color(0xFFCBD5E1), fontSize = 12.sp) },
            containerColor = Color(0xFF14182C),
            confirmButton = {
                TextButton(onClick = { onRollback(chkId); pendingRollbackId = null }) {
                    Text("Rollback Working Copy", color = NeonPink, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRollbackId = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}
