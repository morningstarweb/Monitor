package app.monitor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DiffLineType {
    @SerialName("Context") Context,
    @SerialName("Addition") Addition,
    @SerialName("Deletion") Deletion
}

@Serializable
data class DiffLine(
    @SerialName("line_type") val lineType: DiffLineType,
    @SerialName("old_line_num") val oldLineNum: Long? = null,
    @SerialName("new_line_num") val newLineNum: Long? = null,
    @SerialName("content") val content: String
)

@Serializable
data class DiffHunk(
    @SerialName("old_start") val oldStart: Long,
    @SerialName("old_lines") val oldLines: Long,
    @SerialName("new_start") val newStart: Long,
    @SerialName("new_lines") val newLines: Long,
    @SerialName("header") val header: String,
    @SerialName("lines") val lines: List<DiffLine> = emptyList()
)

@Serializable
data class FileDiffResponse(
    @SerialName("path") val path: String,
    @SerialName("is_binary") val isBinary: Boolean = false,
    @SerialName("is_truncated") val isTruncated: Boolean = false,
    @SerialName("old_size_bytes") val oldSizeBytes: Long? = null,
    @SerialName("new_size_bytes") val newSizeBytes: Long? = null,
    @SerialName("insertions") val insertions: Long = 0,
    @SerialName("deletions") val deletions: Long = 0,
    @SerialName("hunks") val hunks: List<DiffHunk> = emptyList()
)

