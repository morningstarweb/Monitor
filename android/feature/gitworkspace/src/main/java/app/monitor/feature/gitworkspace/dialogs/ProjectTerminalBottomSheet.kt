package app.monitor.feature.gitworkspace.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.TerminalSession

private val COMMON_COMPLETIONS = listOf(
    "git status", "git branch -a", "git log -n 10 --oneline", "git diff", "git pull", "git push",
    "npm install", "npm run build", "npm test", "npm run dev", "cargo check", "cargo test", "dir", "clear"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectTerminalBottomSheet(
    repoDisplayName: String,
    repoPath: String,
    sessions: List<TerminalSession>,
    activeSessionId: String,
    onSelectSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onRunCommand: (String) -> Unit,
    onStopCommand: () -> Unit,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputField by remember(activeSessionId) { mutableStateOf(TextFieldValue("")) }
    var historyCursor by remember(activeSessionId) { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val currentSession = sessions.find { it.id == activeSessionId } ?: sessions.firstOrNull()
    val logs = currentSession?.logs.orEmpty()
    val history = currentSession?.commandHistory.orEmpty()
    val isRunning = currentSession?.isRunning ?: false

    LaunchedEffect(Unit) {
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(logs.size, logs.lastOrNull()?.streamingText?.length) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    val submitCommand: (String) -> Unit = { rawCmd ->
        val trimmed = rawCmd.trim()
        if (trimmed.isNotEmpty()) {
            if (trimmed.equals("clear", ignoreCase = true) || trimmed.equals("cls", ignoreCase = true)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClearLogs()
                inputField = TextFieldValue("")
                historyCursor = -1
            } else if (!isRunning) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRunCommand(trimmed)
                inputField = TextFieldValue("")
                historyCursor = -1
            }
        }
        runCatching { focusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF060A08),
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFF0D1611), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF1B2E22), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Terminal, "Terminal", tint = Color(0xFF00FF87), modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Terminal · $repoDisplayName",
                            color = Color(0xFFF8FAFC),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = repoPath,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isRunning) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF7F1D1D))
                                .border(1.dp, Color(0xFFFF453A), RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStopCommand()
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Stop, "Stop", tint = Color(0xFFFF453A), modifier = Modifier.size(14.dp))
                                Text("STOP", color = Color(0xFFFF453A), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, softWrap = false)
                            }
                        }
                    }
                    if (logs.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0D1611))
                                .border(1.dp, Color(0xFF1B2E22), CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val fullText = logs.joinToString("\n\n") { entry ->
                                        buildString {
                                            append("$ ").append(entry.command)
                                            if (entry.streamingText.isNotBlank()) {
                                                append('\n').append(entry.streamingText.trimEnd())
                                            }
                                        }
                                    }
                                    clipboard.setText(AnnotatedString(fullText))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy all output", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D1611))
                            .border(1.dp, Color(0xFF1B2E22), CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClearLogs()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DeleteSweep, "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D1611))
                            .border(1.dp, Color(0xFF1B2E22), CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                    }
                }
            }

            TerminalSessionTabBar(
                sessions = sessions,
                activeSessionId = activeSessionId,
                onSelectSession = onSelectSession,
                onCloseSession = onCloseSession,
                onNewSession = onNewSession
            )

            QuickScriptChipsRow(isRunning = isRunning, onSelectCommand = submitCommand)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF040608))
                    .border(1.dp, Color(0xFF132219), RoundedCornerShape(12.dp))
                    .clickable {
                        runCatching {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }
                    .padding(12.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "$repoPath>\n# Real-time streaming terminal ready.\n# Output streams live as packages compile (npm, cargo, git, etc.).",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(logs, key = { it.id }) { entry ->
                                TerminalLogItem(
                                    entry = entry,
                                    onCopyEntry = { text ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        clipboard.setText(AnnotatedString(text))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Terminal Accessory Bar (Ctrl+C, History Up/Down, Tab, Esc, Pipe/Flags)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccessoryKeyChip(
                    label = "Ctrl+C",
                    tint = Color(0xFFFF453A),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isRunning) {
                            onStopCommand()
                        } else {
                            inputField = TextFieldValue("")
                            historyCursor = -1
                        }
                    }
                )
                AccessoryKeyChip(
                    label = "↑",
                    onClick = {
                        if (history.isNotEmpty()) {
                            val nextIdx = if (historyCursor == -1) history.lastIndex else (historyCursor - 1).coerceAtLeast(0)
                            historyCursor = nextIdx
                            val cmd = history[nextIdx]
                            inputField = TextFieldValue(cmd, TextRange(cmd.length))
                        }
                    }
                )
                AccessoryKeyChip(
                    label = "↓",
                    onClick = {
                        if (history.isNotEmpty() && historyCursor != -1) {
                            if (historyCursor >= history.lastIndex) {
                                historyCursor = -1
                                inputField = TextFieldValue("")
                            } else {
                                val nextIdx = historyCursor + 1
                                historyCursor = nextIdx
                                val cmd = history[nextIdx]
                                inputField = TextFieldValue(cmd, TextRange(cmd.length))
                            }
                        }
                    }
                )
                AccessoryKeyChip(
                    label = "Tab",
                    onClick = {
                        val cur = inputField.text.trimStart()
                        val match = COMMON_COMPLETIONS.firstOrNull { it.startsWith(cur, ignoreCase = true) && it != cur }
                        val next = match ?: (inputField.text + "  ")
                        inputField = TextFieldValue(next, TextRange(next.length))
                    }
                )
                AccessoryKeyChip(
                    label = "Esc",
                    onClick = {
                        inputField = TextFieldValue("")
                        historyCursor = -1
                    }
                )
                listOf("|", "-", "--", "/", "&&", "clear").forEach { sym ->
                    AccessoryKeyChip(
                        label = sym,
                        onClick = {
                            if (sym == "clear") {
                                onClearLogs()
                            } else {
                                val cur = inputField.text
                                val next = cur + sym
                                inputField = TextFieldValue(next, TextRange(next.length))
                            }
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0D1611))
                    .border(1.dp, if (isRunning) Color(0xFF334155) else Color(0xFF1B2E22), RoundedCornerShape(14.dp))
                    .clickable {
                        runCatching {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(">", color = Color(0xFF00FF87), fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = inputField,
                    onValueChange = {
                        inputField = it
                        historyCursor = -1
                    },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(Color(0xFF00FF87)),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { submitCommand(inputField.text) }),
                    decorationBox = { innerTextField ->
                        if (inputField.text.isEmpty()) {
                            Text(
                                text = if (isRunning) "Streaming live output… (Ctrl+C to stop)" else "Type command…",
                                color = Color(0xFF64748B),
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                )
                Spacer(Modifier.width(8.dp))
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFFFFB300), strokeWidth = 2.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00FF87).copy(alpha = if (inputField.text.isNotBlank()) 0.18f else 0.08f))
                            .border(1.dp, Color(0xFF00FF87).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable(enabled = inputField.text.isNotBlank()) { submitCommand(inputField.text) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Execute", tint = Color(0xFF00FF87), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessoryKeyChip(
    label: String,
    tint: Color = Color(0xFFCBD5E1),
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF0D1611))
            .border(1.dp, Color(0xFF1B2E22), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            softWrap = false
        )
    }
}
