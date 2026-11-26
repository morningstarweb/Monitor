package app.monitor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GraphEdgeType {
    @SerialName("Straight") Straight,
    @SerialName("PassThrough") PassThrough,
    @SerialName("MergeIn") MergeIn,
    @SerialName("MergeToParent") MergeToParent,
    @SerialName("MergeBranch") MergeBranch,
    @SerialName("BranchOut") BranchOut
}

@Serializable
data class GraphEdge(
    @SerialName("from_lane") val fromLane: Int,
    @SerialName("to_lane") val toLane: Int,
    @SerialName("edge_type") val edgeType: GraphEdgeType,
    @SerialName("color_index") val colorIndex: Int
)

@Serializable
data class CommitNode(
    @SerialName("oid") val oid: String,
    @SerialName("short_oid") val shortOid: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_email") val authorEmail: String,
    @SerialName("timestamp_utc_ms") val timestampUtcMs: Long,
    @SerialName("summary") val summary: String,
    @SerialName("parent_oids") val parentOids: List<String> = emptyList(),
    @SerialName("row") val row: Int,
    @SerialName("lane") val lane: Int,
    @SerialName("color_index") val colorIndex: Int,
    @SerialName("edges") val edges: List<GraphEdge> = emptyList()
)

@Serializable
data class GitGraphPage(
    @SerialName("repo_id") val repoId: String,
    @SerialName("commits") val commits: List<CommitNode> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_skip") val nextSkip: Int? = null
)

