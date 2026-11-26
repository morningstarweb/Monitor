package app.monitor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TrustProfile {
    @SerialName("Restricted") Restricted,
    @SerialName("Trusted") Trusted
}

@Serializable
enum class FileChangeCategory {
    @SerialName("Modified") Modified,
    @SerialName("Added") Added,
    @SerialName("Deleted") Deleted,
    @SerialName("Renamed") Renamed,
    @SerialName("Copied") Copied,
    @SerialName("Untracked") Untracked,
    @SerialName("Conflict") Conflict
}

@Serializable
data class GitFileChange(
    @SerialName("path") val path: String,
    @SerialName("original_path") val originalPath: String? = null,
    @SerialName("category") val category: FileChangeCategory,
    @SerialName("staged") val staged: Boolean,
    @SerialName("insertions") val insertions: Long = 0,
    @SerialName("deletions") val deletions: Long = 0
)

@Serializable
data class RepoSummary(
    @SerialName("repo_id") val repoId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("path") val path: String,
    @SerialName("current_branch") val currentBranch: String,
    @SerialName("is_clean") val isClean: Boolean,
    @SerialName("modified_count") val modifiedCount: Int,
    @SerialName("staged_count") val stagedCount: Int,
    @SerialName("untracked_count") val untrackedCount: Int,
    @SerialName("ahead_count") val aheadCount: Int,
    @SerialName("behind_count") val behindCount: Int,
    @SerialName("last_activity_utc_ms") val lastActivityUtcMs: Long,
    @SerialName("watch_enabled") val watchEnabled: Boolean,
    @SerialName("trust_profile") val trustProfile: TrustProfile
)

@Serializable
data class GitWorkspaceSnapshot(
    @SerialName("repo_id") val repoId: String,
    @SerialName("revision") val revision: Long,
    @SerialName("current_branch") val currentBranch: String,
    @SerialName("upstream_branch") val upstreamBranch: String? = null,
    @SerialName("ahead_count") val aheadCount: Int = 0,
    @SerialName("behind_count") val behindCount: Int = 0,
    @SerialName("files") val files: List<GitFileChange> = emptyList(),
    @SerialName("is_merging") val isMerging: Boolean = false,
    @SerialName("is_rebasing") val isRebasing: Boolean = false
)

@Serializable
data class BranchInfo(
    @SerialName("name") val name: String,
    @SerialName("is_current") val isCurrent: Boolean,
    @SerialName("is_remote") val isRemote: Boolean,
    @SerialName("upstream") val upstream: String? = null,
    @SerialName("ahead_count") val aheadCount: Int = 0,
    @SerialName("behind_count") val behindCount: Int = 0,
    @SerialName("is_merged") val isMerged: Boolean = false,
    @SerialName("unmerged_commits") val unmergedCommits: Int = 0,
    @SerialName("changed_files") val changedFiles: List<String> = emptyList()
)

@Serializable
sealed interface GitOperation {
    @Serializable
    @SerialName("StageFiles")
    data class StageFiles(val paths: List<String>) : GitOperation

    @Serializable
    @SerialName("StageAll")
    data object StageAll : GitOperation

    @Serializable
    @SerialName("UnstageFiles")
    data class UnstageFiles(val paths: List<String>) : GitOperation

    @Serializable
    @SerialName("UnstageAll")
    data object UnstageAll : GitOperation

    @Serializable
    @SerialName("DiscardFile")
    data class DiscardFile(
        val path: String,
        @SerialName("confirmation_token") val confirmationToken: String
    ) : GitOperation

    @Serializable
    @SerialName("Commit")
    data class Commit(
        @SerialName("message_summary") val messageSummary: String,
        @SerialName("message_body") val messageBody: String? = null,
        @SerialName("stage_all_tracked") val stageAllTracked: Boolean = false,
        val paths: List<String> = emptyList()
    ) : GitOperation

    @Serializable
    @SerialName("CommitAndPush")
    data class CommitAndPush(
        @SerialName("message_summary") val messageSummary: String,
        @SerialName("message_body") val messageBody: String? = null,
        @SerialName("stage_all_tracked") val stageAllTracked: Boolean = false,
        val paths: List<String> = emptyList()
    ) : GitOperation

    @Serializable
    @SerialName("Fetch")
    data class Fetch(val remote: String? = null) : GitOperation

    @Serializable
    @SerialName("Pull")
    data class Pull(val remote: String? = null, val branch: String? = null) : GitOperation

    @Serializable
    @SerialName("Push")
    data class Push(
        val remote: String? = null,
        val branch: String? = null,
        @SerialName("set_upstream") val setUpstream: Boolean = false
    ) : GitOperation

    @Serializable
    @SerialName("CreateBranch")
    data class CreateBranch(
        @SerialName("branch_name") val branchName: String,
        @SerialName("start_point") val startPoint: String? = null
    ) : GitOperation

    @Serializable
    @SerialName("SwitchBranch")
    data class SwitchBranch(
        @SerialName("branch_name") val branchName: String
    ) : GitOperation

    @Serializable
    @SerialName("DeleteBranch")
    data class DeleteBranch(
        @SerialName("branch_name") val branchName: String,
        val force: Boolean = false
    ) : GitOperation

    @Serializable
    @SerialName("MergeBranch")
    data class MergeBranch(
        @SerialName("source_branch") val sourceBranch: String
    ) : GitOperation

    @Serializable
    @SerialName("MergeAllBranches")
    data class MergeAllBranches(
        @SerialName("source_branches") val sourceBranches: List<String> = emptyList()
    ) : GitOperation
}

@Serializable
data class GitOperationRequest(
    @SerialName("request_id") val requestId: String,
    @SerialName("expected_repo_revision") val expectedRepoRevision: Long,
    @SerialName("operation") val operation: GitOperation
)

@Serializable
enum class OperationStatus {
    @SerialName("Succeeded") Succeeded,
    @SerialName("PartialSuccess") PartialSuccess,
    @SerialName("Failed") Failed,
    @SerialName("StaleConflict") StaleConflict
}

@Serializable
data class GitOperationResult(
    @SerialName("request_id") val requestId: String,
    @SerialName("operation_id") val operationId: String,
    @SerialName("status") val status: OperationStatus,
    @SerialName("new_repo_revision") val newRepoRevision: Long,
    @SerialName("commit_oid") val commitOid: String? = null,
    @SerialName("push_completed") val pushCompleted: Boolean = false,
    @SerialName("user_message") val userMessage: String,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("started_at_utc_ms") val startedAtUtcMs: Long,
    @SerialName("finished_at_utc_ms") val finishedAtUtcMs: Long
)

