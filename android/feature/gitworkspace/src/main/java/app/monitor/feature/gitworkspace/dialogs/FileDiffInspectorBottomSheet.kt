package app.monitor.feature.gitworkspace.dialogs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.FileChangeCategory
import app.monitor.core.model.FileDiffResponse
import app.monitor.core.model.GitFileChange
import app.monitor.feature.gitworkspace.components.DiffHunksViewer

/**
 * Dedicated File Diff & Code Inspector Bottom Sheet (< 160 lines).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDiffInspectorBottomSheet(
    file: GitFileChange,
    diff: FileDiffResponse? = null,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedToClipboard by remember { mutableStateOf(false) }

    val fileName = file.path.substringAfterLast('/')
    val dirPath = file.path.substringBeforeLast('/', "")

    val (catLabel, catBg, catColor) = when (file.category) {
        FileChangeCategory.Added -> Triple("ADDED", Color(0xFF064E3B).copy(alpha = 0.50f), Color(0xFF00E699))
        FileChangeCategory.Deleted -> Triple("DELETED", Color(0xFF4C0519).copy(alpha = 0.50f), Color(0xFFF43F5E))
        FileChangeCategory.Modified -> Triple("MODIFIED", Color(0xFF0C4A6E).copy(alpha = 0.50f), Color(0xFF00E5FF))
        else -> Triple("MODIFIED", Color(0xFF451A03).copy(alpha = 0.50f), Color(0xFFF59E0B))
    }

    val cardShape = RoundedCornerShape(20.dp)
    val cardBackground = Color(0xFF0C0E1A).copy(alpha = 0.96f)
    val cyberBorder = Brush.linearGradient(
        listOf(Color(0x60818CF8), Color(0x30C084FC), Color(0x5000E5FF))
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0A0D18)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Category Pill + File Name + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(catBg)
                            .border(1.dp, catColor.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(catLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = catColor)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = fileName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (dirPath.isNotBlank()) {
                            Text(text = dirPath, fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x20FFFFFF))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            // File Telemetry & Quick Action Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(cardBackground)
                    .border(1.2.dp, cyberBorder, cardShape)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x2200E699))
                                .border(1.dp, Color(0x6000E699), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("+${file.insertions} additions", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E699))
                        }
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22F43F5E))
                                .border(1.dp, Color(0x60F43F5E), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("-${file.deletions} deletions", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E))
                        }
                    }

                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x18818CF8))
                            .border(1.dp, Color(0x50818CF8), RoundedCornerShape(8.dp))
                            .clickable { clipboardManager.setText(AnnotatedString(file.path)); copiedToClipboard = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(13.dp))
                            Text(if (copiedToClipboard) "Copied!" else "Copy Path", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }

            // Dedicated Diff Code Inspector Body
            DiffHunksViewer(
                file = file,
                diff = diff
            )

            Spacer(Modifier.height(8.dp))

            // Done / Dismiss Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x25818CF8))
                    .border(1.dp, Color(0x60818CF8), RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Close Inspector", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
