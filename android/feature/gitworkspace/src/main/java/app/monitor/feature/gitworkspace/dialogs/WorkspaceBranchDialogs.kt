package app.monitor.feature.gitworkspace.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.monitor.core.model.BranchInfo

/**
 * Automatically sanitizes any input string into a valid Git branch name.
 * Converts spaces and prohibited characters to hyphens and cleans up duplicate slashes/dots.
 */
fun sanitizeBranchName(name: String): String {
    return name.trim()
        .replace(Regex("[\\s~^:?*\\[\\]\\\\@{}\\<\\>|\"']+"), "-")
        .replace(Regex("/+"), "/")
        .replace(Regex("-+"), "-")
        .replace(Regex("/-+"), "/")
        .replace(Regex("-+/"), "/")
        .replace(Regex("\\.{2,}"), ".")
        .trim('-', '.', '/')
        .removeSuffix(".lock")
        .trim('-', '.', '/')
}

@Composable
fun CreateBranchDialog(
    isOperating: Boolean,
    onCreateBranch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newBranchName by remember { mutableStateOf("") }
    val formatted = remember(newBranchName) { sanitizeBranchName(newBranchName) }
    val hasAutoFix = newBranchName.isNotBlank() && newBranchName.trim() != formatted && formatted.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create New Branch", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Spawn a new working branch from current HEAD.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                OutlinedTextField(
                    value = newBranchName,
                    onValueChange = { newBranchName = it },
                    placeholder = { Text("e.g. feature/auth-flow", color = Color(0xFF64748B)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0x35818CF8),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF090D1A),
                        unfocusedContainerColor = Color(0xFF090D1A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasAutoFix) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF00FF87), CircleShape))
                        Text(
                            text = "Auto-formatted: $formatted",
                            color = Color(0xFF00FF87),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetBranch = formatted.ifBlank { sanitizeBranchName(newBranchName) }
                    if (targetBranch.isNotBlank()) {
                        onCreateBranch(targetBranch)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                shape = RoundedCornerShape(12.dp),
                enabled = formatted.isNotBlank() && !isOperating
            ) {
                Text("Create Branch", color = Color(0xFF090D1A), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFA1A1AA))
            }
        },
        containerColor = Color(0xFF0F1426),
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun SwitchBranchDialog(
    branchName: String,
    isOperating: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch Branch?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = { Text("Your workspace will checkout branch '$branchName'.", color = Color(0xFF94A3B8), fontSize = 13.sp) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(12.dp),
                enabled = !isOperating
            ) { Text("Switch", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFA1A1AA))
            }
        },
        containerColor = Color(0xFF0F1426),
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun DeleteBranchDialog(
    branchName: String,
    isOperating: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Branch?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to delete branch '$branchName'?", color = Color(0xFF94A3B8), fontSize = 13.sp) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(12.dp),
                enabled = !isOperating
            ) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFA1A1AA))
            }
        },
        containerColor = Color(0xFF0F1426),
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun SmartMergeAllDialog(
    targetBranch: String,
    unmergedBranches: List<BranchInfo>,
    isOperating: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF00FF87), CircleShape)
                )
                Text(
                    text = "Smart Merge All",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Merge all unmerged branches into active branch '$targetBranch'?",
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "The following branches will be merged in optimal order:",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070A14))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    unmergedBranches.forEach { b ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF00E5FF), CircleShape)
                                )
                                Text(
                                    text = b.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (b.unmergedCommits > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x3000E5FF))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "+${b.unmergedCommits} commits",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "External changes are automatically fast-forwarded or merged into '$targetBranch' without manual steps.",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                shape = RoundedCornerShape(12.dp),
                enabled = !isOperating
            ) {
                Text(
                    text = if (isOperating) "Merging..." else "Merge All Now",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFA1A1AA))
            }
        },
        containerColor = Color(0xFF0F1426),
        shape = RoundedCornerShape(22.dp)
    )
}
