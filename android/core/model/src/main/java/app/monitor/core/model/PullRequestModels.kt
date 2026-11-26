package app.monitor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Summary of a Pull Request matching Rust desktop backend monitor-git (< 50 lines).
 */
@Serializable
data class PullRequestSummary(
    @SerialName("number") val number: Long,
    @SerialName("title") val title: String,
    @SerialName("author") val author: String,
    @SerialName("head_branch") val headBranch: String,
    @SerialName("base_branch") val baseBranch: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("url") val url: String = "",
    @SerialName("state") val state: String = "OPEN",
    @SerialName("is_draft") val isDraft: Boolean = false,
    @SerialName("changed_files_count") val changedFilesCount: Int = 0,
    @SerialName("additions") val additions: Int = 0,
    @SerialName("deletions") val deletions: Int = 0
)

enum class PullRequestStateFilter {
    All, Open, Merged, Closed
}

/**
 * Request payload for merging a Pull Request.
 */
@Serializable
data class MergePrRequest(
    @SerialName("method") val method: String? = "merge"
)

/**
 * Response payload after merging a Pull Request.
 */
@Serializable
data class MergePrResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("message") val message: String = ""
)
