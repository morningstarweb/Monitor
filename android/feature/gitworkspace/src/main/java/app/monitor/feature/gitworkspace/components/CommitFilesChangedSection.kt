package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.monitor.core.model.FileChangeCategory
import app.monitor.core.model.FileDiffResponse
import app.monitor.core.model.GitFileChange
import app.monitor.feature.gitworkspace.dialogs.FileDiffInspectorBottomSheet
import kotlin.math.max

/**
 * Clean Changed Files Section with Fast Search, Category Filtering, and High-Speed Pagination (< 180 lines).
 */
@Composable
fun CommitFilesChangedSection(
    files: List<GitFileChange>,
    modifier: Modifier = Modifier,
    selectedDiff: FileDiffResponse? = null,
    onFileClick: ((GitFileChange) -> Unit)? = null
) {
    var fileForInspector by remember { mutableStateOf<GitFileChange?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<FileChangeCategory?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var showAll by remember { mutableStateOf(false) }

    val filteredFiles = remember(files, searchQuery, selectedCategory) {
        files.filter { file ->
            val matchesCat = selectedCategory == null || file.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                file.path.contains(searchQuery, ignoreCase = true) ||
                file.path.substringAfterLast('/').contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    val pageSize = 20
    val totalPages = max(1, (filteredFiles.size + pageSize - 1) / pageSize)
    val safePage = page.coerceIn(0, totalPages - 1)
    val displayedFiles = if (showAll || filteredFiles.size <= pageSize) {
        filteredFiles
    } else {
        val start = safePage * pageSize
        filteredFiles.subList(start, minOf(filteredFiles.size, start + pageSize))
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                Text("Changed Files", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x2000E5FF))
                    .border(1.dp, Color(0x5000E5FF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("${files.size} files", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
            }
        }

        if (files.size > 8) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; page = 0 },
                placeholder = { Text("Filter ${files.size} files by name or path...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).clickable { searchQuery = "" }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color(0x3500E5FF),
                    focusedContainerColor = Color(0xFF080B16), unfocusedContainerColor = Color(0xFF080B16)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryFilterPill("All (${files.size})", selectedCategory == null, Color(0xFF00E5FF)) { selectedCategory = null; page = 0 }
                CategoryFilterPill("Modified (${files.count { it.category == FileChangeCategory.Modified }})", selectedCategory == FileChangeCategory.Modified, Color(0xFF00E5FF)) {
                    selectedCategory = if (selectedCategory == FileChangeCategory.Modified) null else FileChangeCategory.Modified; page = 0
                }
                CategoryFilterPill("Added (${files.count { it.category == FileChangeCategory.Added }})", selectedCategory == FileChangeCategory.Added, Color(0xFF00E699)) {
                    selectedCategory = if (selectedCategory == FileChangeCategory.Added) null else FileChangeCategory.Added; page = 0
                }
                CategoryFilterPill("Deleted (${files.count { it.category == FileChangeCategory.Deleted }})", selectedCategory == FileChangeCategory.Deleted, Color(0xFFF43F5E)) {
                    selectedCategory = if (selectedCategory == FileChangeCategory.Deleted) null else FileChangeCategory.Deleted; page = 0
                }
            }
        }

        if (displayedFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF090D1A)).padding(20.dp), contentAlignment = Alignment.Center) {
                Text("No changed files match \"$searchQuery\"", fontSize = 12.5.sp, color = Color(0xFF94A3B8))
            }
        }

        displayedFiles.forEach { file ->
            CommitFileItemCard(
                file = file,
                onClick = {
                    fileForInspector = file
                    onFileClick?.invoke(file)
                }
            )
        }

        if (totalPages > 1 && !showAll) {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF080C18)).border(1.dp, Color(0x30818CF8), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                FilePagingButton("< Prev", safePage > 0) { page = max(0, safePage - 1) }
                Text("Page ${safePage + 1} of $totalPages (${filteredFiles.size} files)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF00E5FF))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilePagingButton("Next >", safePage < totalPages - 1) { page = minOf(totalPages - 1, safePage + 1) }
                    FilePagingButton("All", true) { showAll = true }
                }
            }
        } else if (showAll && filteredFiles.size > pageSize) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x18818CF8)).border(1.dp, Color(0x40818CF8), RoundedCornerShape(10.dp)).clickable { showAll = false }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text("Switch to Paged View (${filteredFiles.size} files)", fontSize = 11.5.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
            }
        }
    }

    fileForInspector?.let { inspectedFile ->
        FileDiffInspectorBottomSheet(
            file = inspectedFile,
            diff = selectedDiff?.takeIf { it.path == inspectedFile.path },
            onDismiss = { fileForInspector = null }
        )
    }
}

@Composable
private fun CategoryFilterPill(label: String, isSelected: Boolean, activeColor: Color, onClick: () -> Unit) {
    val bg = if (isSelected) activeColor.copy(alpha = 0.22f) else Color(0x10FFFFFF)
    val border = if (isSelected) activeColor.copy(alpha = 0.65f) else Color(0x20FFFFFF)
    val text = if (isSelected) activeColor else Color(0xFF94A3B8)
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).border(1.dp, border, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = text)
    }
}

@Composable
private fun FilePagingButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) Color(0x22818CF8) else Color(0x0C818CF8)
    val text = if (enabled) Color.White else Color(0xFF475569)
    Box(modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(bg).border(1.dp, if (enabled) Color(0x50818CF8) else Color(0x15818CF8), RoundedCornerShape(7.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = text)
    }
}
