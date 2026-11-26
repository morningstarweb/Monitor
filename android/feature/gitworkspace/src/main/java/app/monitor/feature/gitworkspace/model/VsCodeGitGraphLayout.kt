package app.monitor.feature.gitworkspace.model

import app.monitor.core.model.CommitNode
import app.monitor.core.model.GraphEdgeType
import kotlin.math.max

/**
 * Visual elements to draw for an individual commit row in the Git DAG.
 */
sealed interface GraphDrawSegment {
    val colorIndex: Int

    /** Vertical straight line from top to bottom (pass-through of another active branch). */
    data class PassThrough(val lane: Int, override val colorIndex: Int) : GraphDrawSegment

    /** Straight vertical line from top of row to centerY on commit's lane. */
    data class IncomingTrunk(val lane: Int, override val colorIndex: Int) : GraphDrawSegment

    /** Straight vertical line from centerY to bottom of row on commit's lane. */
    data class OutgoingTrunk(val lane: Int, override val colorIndex: Int) : GraphDrawSegment

    /** S-curve connecting fromLane at top of row into toLane at centerY (branch convergence/merge). */
    data class CurveIn(val fromLane: Int, val toLane: Int, override val colorIndex: Int) : GraphDrawSegment

    /** S-curve connecting fromLane at centerY downwards into toLane at bottom of row (branch out / merge parent). */
    data class CurveOut(val fromLane: Int, val toLane: Int, override val colorIndex: Int) : GraphDrawSegment

    /** S-curve passing through row from fromLane at top to toLane at bottom. */
    data class CurvePass(val fromLane: Int, val toLane: Int, override val colorIndex: Int) : GraphDrawSegment
}

/**
 * Precomputed layout metadata for an individual commit row.
 */
data class GitRowLayout(
    val row: Int,
    val commitLane: Int,
    val commitColorIndex: Int,
    val isMerge: Boolean,
    val isHead: Boolean,
    val segments: List<GraphDrawSegment>,
    val maxLane: Int = commitLane
)

data class GitGraphLine(
    val fromLane: Int,
    val fromRow: Int,
    val toLane: Int,
    val toRow: Int,
    val lockedFirst: Boolean
)

data class GitGraphBranch(
    val colorIndex: Int,
    val lines: List<GitGraphLine>
)

data class GitGraphVertex(
    val row: Int,
    val lane: Int,
    val colorIndex: Int,
    val isMerge: Boolean,
    val isHead: Boolean
)

/**
 * Complete result of the VS Code-style Git Graph layout calculation.
 */
data class GitGraphLayoutResult(
    val rows: List<GitRowLayout>,
    val maxLanes: Int,
    val branches: List<GitGraphBranch> = emptyList(),
    val vertices: List<GitGraphVertex> = emptyList()
)

private const val NULL_VERTEX_ID = -1

private data class GraphPoint(val x: Int, val y: Int)

private data class GraphLine(
    val p1: GraphPoint,
    val p2: GraphPoint,
    val lockedFirst: Boolean
)

private class GraphBranch(val colour: Int) {
    val lines = mutableListOf<GraphLine>()
    var end: Int = 0

    fun addLine(p1: GraphPoint, p2: GraphPoint, isCommitted: Boolean, lockedFirst: Boolean) {
        lines.add(GraphLine(p1, p2, lockedFirst))
    }
}

private class UnavailablePoint(
    val connectsTo: GraphVertex?,
    val onBranch: GraphBranch
)

private class GraphVertex(
    val id: Int,
    val isStash: Boolean = false
) {
    var x: Int = 0
    val children = mutableListOf<GraphVertex>()
    val parents = mutableListOf<GraphVertex>()
    var nextParent: Int = 0
    var onBranch: GraphBranch? = null
    var isCommitted: Boolean = true
    var nextX: Int = 0
    val connections = mutableMapOf<Int, UnavailablePoint>()

    fun addChild(v: GraphVertex) = children.add(v)
    fun addParent(v: GraphVertex) = parents.add(v)
    fun getNextParent(): GraphVertex? = if (nextParent < parents.size) parents[nextParent] else null
    fun registerParentProcessed() { nextParent++ }
    fun isMerge(): Boolean = parents.size > 1

    fun addToBranch(branch: GraphBranch, xPos: Int) {
        if (onBranch == null) {
            onBranch = branch
            this.x = xPos
        }
    }

    fun isNotOnBranch(): Boolean = (onBranch == null)
    fun getPoint(): GraphPoint = GraphPoint(x, id)
    fun getNextPoint(): GraphPoint = GraphPoint(nextX, id)

    fun getPointConnectingTo(vertex: GraphVertex?, onBranch: GraphBranch): GraphPoint? {
        for ((idx, conn) in connections) {
            if (conn.connectsTo == vertex && conn.onBranch == onBranch) {
                return GraphPoint(idx, id)
            }
        }
        return null
    }

    fun registerUnavailablePoint(xPos: Int, connectsToVertex: GraphVertex?, onBranch: GraphBranch) {
        if (xPos == nextX) {
            nextX = xPos + 1
            connections[xPos] = UnavailablePoint(connectsToVertex, onBranch)
        }
    }

    fun getColour(): Int = onBranch?.colour ?: 0
}

private data class SwimlaneNode(val id: String, val colorIndex: Int)

/**
 * 100% Authentic VS Code Git Graph Layout Engine.
 * Direct 1:1 mathematical port of Microsoft VS Code's native SCM History Graph (scmHistory.ts).
 */
fun computeVsCodeGitGraphLayout(commits: List<CommitNode>): GitGraphLayoutResult {
    if (commits.isEmpty()) {
        return GitGraphLayoutResult(emptyList(), maxLanes = 1)
    }

    val hasParentOids = commits.any { it.parentOids.isNotEmpty() }
    if (!hasParentOids) {
        return fallbackFromCommitEdges(commits)
    }

    val resultRows = ArrayList<GitRowLayout>(commits.size)
    var previousOutputSwimlanes = emptyList<SwimlaneNode>()
    var colorIndex = -1
    var maxLaneEncountered = 0

    val layoutVertices = ArrayList<GitGraphVertex>(commits.size)

    for (rowIdx in commits.indices) {
        val commit = commits[rowIdx]
        val inputSwimlanes = previousOutputSwimlanes.map { it.copy() }
        val outputSwimlanes = mutableListOf<SwimlaneNode>()
        var firstParentAdded = false

        // Add first parent to output
        if (commit.parentOids.isNotEmpty()) {
            for (node in inputSwimlanes) {
                if (node.id == commit.oid) {
                    if (!firstParentAdded) {
                        val firstParent = commit.parentOids[0]
                        val alreadyPresent = outputSwimlanes.any { it.id == firstParent }
                        if (!alreadyPresent) {
                            outputSwimlanes.add(
                                SwimlaneNode(
                                    id = firstParent,
                                    colorIndex = node.colorIndex
                                )
                            )
                        }
                        firstParentAdded = true
                    }
                    continue
                }
                outputSwimlanes.add(node.copy())
            }
        }

        // Add unprocessed parent(s) to output
        val startP = if (firstParentAdded) 1 else 0
        for (i in startP until commit.parentOids.size) {
            val pId = commit.parentOids[i]
            if (outputSwimlanes.none { it.id == pId }) {
                colorIndex = (colorIndex + 1) % GraphPalette.size
                outputSwimlanes.add(
                    SwimlaneNode(
                        id = pId,
                        colorIndex = colorIndex
                    )
                )
            }
        }

        // Find current commit in input swimlanes
        val inputIndex = inputSwimlanes.indexOfFirst { it.id == commit.oid }
        val circleLane = if (inputIndex != -1) inputIndex else inputSwimlanes.size
        val commitColor = if (circleLane < outputSwimlanes.size) {
            outputSwimlanes[circleLane].colorIndex
        } else if (circleLane < inputSwimlanes.size) {
            inputSwimlanes[circleLane].colorIndex
        } else {
            0
        }

        maxLaneEncountered = max(maxLaneEncountered, max(circleLane + 1, max(inputSwimlanes.size, outputSwimlanes.size)))

        val segments = mutableListOf<GraphDrawSegment>()

        // 1. Incoming trunk from top of row to commit circle
        if (inputIndex != -1) {
            segments.add(GraphDrawSegment.IncomingTrunk(circleLane, inputSwimlanes[inputIndex].colorIndex))
        }

        // 2. Outgoing trunk from commit circle to bottom of row (first parent)
        if (commit.parentOids.isNotEmpty()) {
            val firstParentId = commit.parentOids[0]
            val parentLane = outputSwimlanes.indexOfFirst { it.id == firstParentId }
            if (parentLane != -1) {
                if (parentLane == circleLane) {
                    segments.add(GraphDrawSegment.OutgoingTrunk(circleLane, commitColor))
                } else {
                    segments.add(
                        GraphDrawSegment.CurveOut(
                            fromLane = circleLane,
                            toLane = parentLane,
                            colorIndex = outputSwimlanes[parentLane].colorIndex
                        )
                    )
                }
            } else {
                segments.add(GraphDrawSegment.OutgoingTrunk(circleLane, commitColor))
            }
        }

        // 3. Branches converging into this commit (merge in)
        for (idx in inputSwimlanes.indices) {
            val node = inputSwimlanes[idx]
            if (node.id == commit.oid && idx != circleLane) {
                segments.add(
                    GraphDrawSegment.CurveIn(
                        fromLane = idx,
                        toLane = circleLane,
                        colorIndex = node.colorIndex
                    )
                )
            }
        }

        // 4. Remaining parents branching out from this commit
        for (i in 1 until commit.parentOids.size) {
            val parentId = commit.parentOids[i]
            val parentOutputIndex = outputSwimlanes.indexOfLast { it.id == parentId }
            if (parentOutputIndex != -1) {
                segments.add(
                    GraphDrawSegment.CurveOut(
                        fromLane = circleLane,
                        toLane = parentOutputIndex,
                        colorIndex = outputSwimlanes[parentOutputIndex].colorIndex
                    )
                )
            }
        }

        // 5. Pass-through swimlanes and lane shifts (VS Code scmHistory.ts lines 167-200)
        var outputSwimlaneIndex = 0
        for (idx in inputSwimlanes.indices) {
            val node = inputSwimlanes[idx]
            if (node.id == commit.oid) {
                if (idx == circleLane) {
                    outputSwimlaneIndex++
                }
            } else {
                if (outputSwimlaneIndex < outputSwimlanes.size &&
                    node.id == outputSwimlanes[outputSwimlaneIndex].id
                ) {
                    if (idx == outputSwimlaneIndex) {
                        segments.add(GraphDrawSegment.PassThrough(idx, node.colorIndex))
                    } else {
                        segments.add(
                            GraphDrawSegment.CurvePass(
                                fromLane = idx,
                                toLane = outputSwimlaneIndex,
                                colorIndex = node.colorIndex
                            )
                        )
                    }
                    outputSwimlaneIndex++
                }
            }
        }

        val isMergeCommit = commit.parentOids.size > 1 || commit.summary.startsWith("merge", ignoreCase = true)
        val isHeadCommit = (rowIdx == 0)

        val rowMaxLane = max(
            circleLane,
            segments.maxOfOrNull { seg ->
                when (seg) {
                    is GraphDrawSegment.PassThrough -> seg.lane
                    is GraphDrawSegment.IncomingTrunk -> seg.lane
                    is GraphDrawSegment.OutgoingTrunk -> seg.lane
                    is GraphDrawSegment.CurveIn -> max(seg.fromLane, seg.toLane)
                    is GraphDrawSegment.CurveOut -> max(seg.fromLane, seg.toLane)
                    is GraphDrawSegment.CurvePass -> max(seg.fromLane, seg.toLane)
                }
            } ?: circleLane
        )

        resultRows.add(
            GitRowLayout(
                row = rowIdx,
                commitLane = circleLane,
                commitColorIndex = commitColor,
                isMerge = isMergeCommit,
                isHead = isHeadCommit,
                segments = segments,
                maxLane = rowMaxLane
            )
        )

        layoutVertices.add(
            GitGraphVertex(
                row = rowIdx,
                lane = circleLane,
                colorIndex = commitColor,
                isMerge = isMergeCommit,
                isHead = isHeadCommit
            )
        )

        previousOutputSwimlanes = outputSwimlanes
    }

    return GitGraphLayoutResult(
        rows = resultRows,
        maxLanes = max(1, maxLaneEncountered),
        branches = emptyList(),
        vertices = layoutVertices
    )
}

private fun determinePath(
    startAt: Int,
    vertices: List<GraphVertex>,
    branches: MutableList<GraphBranch>,
    availableColours: MutableList<Int>
) {
    var i = startAt
    var vertex = vertices[i]
    var parentVertex = vertex.getNextParent()
    var curVertex: GraphVertex
    var lastPoint = if (vertex.isNotOnBranch()) vertex.getNextPoint() else vertex.getPoint()
    var curPoint: GraphPoint

    if (parentVertex != null && parentVertex.id != NULL_VERTEX_ID && vertex.isMerge() && !vertex.isNotOnBranch() && !parentVertex.isNotOnBranch()) {
        var foundPointToParent = false
        val parentBranch = parentVertex.onBranch!!
        for (idx in (startAt + 1) until vertices.size) {
            curVertex = vertices[idx]
            val connecting = curVertex.getPointConnectingTo(parentVertex, parentBranch)
            if (connecting != null) {
                foundPointToParent = true
                curPoint = connecting
            } else {
                curPoint = curVertex.getNextPoint()
            }
            parentBranch.addLine(
                lastPoint,
                curPoint,
                vertex.isCommitted,
                if (!foundPointToParent && curVertex != parentVertex) (lastPoint.x < curPoint.x) else true
            )
            curVertex.registerUnavailablePoint(curPoint.x, parentVertex, parentBranch)
            lastPoint = curPoint

            if (foundPointToParent) {
                vertex.registerParentProcessed()
                break
            }
        }
    } else {
        val colour = getAvailableColour(startAt, availableColours)
        val branch = GraphBranch(colour)
        vertex.addToBranch(branch, lastPoint.x)
        vertex.registerUnavailablePoint(lastPoint.x, vertex, branch)

        var lastIdx = startAt
        for (idx in (startAt + 1) until vertices.size) {
            lastIdx = idx
            curVertex = vertices[idx]
            curPoint = if (parentVertex == curVertex && !parentVertex.isNotOnBranch()) {
                curVertex.getPoint()
            } else {
                curVertex.getNextPoint()
            }
            branch.addLine(lastPoint, curPoint, vertex.isCommitted, lastPoint.x < curPoint.x)
            curVertex.registerUnavailablePoint(curPoint.x, parentVertex, branch)
            lastPoint = curPoint

            if (parentVertex == curVertex) {
                vertex.registerParentProcessed()
                val parentVertexOnBranch = !parentVertex.isNotOnBranch()
                parentVertex.addToBranch(branch, curPoint.x)
                vertex = parentVertex
                parentVertex = vertex.getNextParent()
                if (parentVertex == null || parentVertexOnBranch) {
                    break
                }
            }
        }

        if (parentVertex != null && parentVertex.id == NULL_VERTEX_ID) {
            vertex.registerParentProcessed()
        }

        branch.end = lastIdx
        branches.add(branch)
        if (colour < availableColours.size) {
            availableColours[colour] = lastIdx
        } else {
            while (availableColours.size <= colour) availableColours.add(0)
            availableColours[colour] = lastIdx
        }
    }
}

private fun getAvailableColour(startAt: Int, availableColours: MutableList<Int>): Int {
    for (i in availableColours.indices) {
        if (startAt > availableColours[i]) {
            return i
        }
    }
    availableColours.add(0)
    return availableColours.size - 1
}

/**
 * Fallback layout generator when only commit.edges and commit.lane are available.
 */
private fun fallbackFromCommitEdges(commits: List<CommitNode>): GitGraphLayoutResult {
    var maxLane = 0
    val rows = commits.mapIndexed { rowIdx, commit ->
        maxLane = max(maxLane, commit.lane)
        val segments = mutableListOf<GraphDrawSegment>()
        val commitColor = commit.colorIndex % GraphPalette.size

        segments.add(GraphDrawSegment.IncomingTrunk(commit.lane, commitColor))

        for (edge in commit.edges) {
            maxLane = max(maxLane, max(edge.fromLane, edge.toLane))
            val edgeColor = edge.colorIndex % GraphPalette.size
            when (edge.edgeType) {
                GraphEdgeType.PassThrough -> {
                    segments.add(GraphDrawSegment.PassThrough(edge.fromLane, edgeColor))
                }
                GraphEdgeType.Straight -> {
                    if (edge.fromLane == edge.toLane) {
                        segments.add(GraphDrawSegment.OutgoingTrunk(edge.fromLane, edgeColor))
                    } else {
                        segments.add(GraphDrawSegment.CurveOut(edge.fromLane, edge.toLane, edgeColor))
                    }
                }
                GraphEdgeType.MergeIn -> {
                    segments.add(GraphDrawSegment.CurveIn(edge.fromLane, edge.toLane, edgeColor))
                }
                GraphEdgeType.BranchOut, GraphEdgeType.MergeBranch, GraphEdgeType.MergeToParent -> {
                    segments.add(GraphDrawSegment.CurveOut(edge.fromLane, edge.toLane, edgeColor))
                }
            }
        }

        val isMerge = commit.edges.any {
            it.edgeType == GraphEdgeType.MergeIn || it.edgeType == GraphEdgeType.MergeBranch
        } || commit.summary.startsWith("merge", ignoreCase = true)

        val fallbackMaxLane = max(
            commit.lane,
            segments.maxOfOrNull { seg ->
                when (seg) {
                    is GraphDrawSegment.PassThrough -> seg.lane
                    is GraphDrawSegment.IncomingTrunk -> seg.lane
                    is GraphDrawSegment.OutgoingTrunk -> seg.lane
                    is GraphDrawSegment.CurveIn -> max(seg.fromLane, seg.toLane)
                    is GraphDrawSegment.CurveOut -> max(seg.fromLane, seg.toLane)
                    is GraphDrawSegment.CurvePass -> max(seg.fromLane, seg.toLane)
                }
            } ?: commit.lane
        )

        GitRowLayout(
            row = rowIdx,
            commitLane = commit.lane,
            commitColorIndex = commitColor,
            isMerge = isMerge,
            isHead = rowIdx == 0,
            segments = segments,
            maxLane = fallbackMaxLane
        )
    }
    val fallbackLines = mutableListOf<GitGraphLine>()
    for (rowIdx in commits.indices) {
        val commit = commits[rowIdx]
        for (edge in commit.edges) {
            when (edge.edgeType) {
                GraphEdgeType.Straight, GraphEdgeType.BranchOut, GraphEdgeType.MergeBranch, GraphEdgeType.MergeToParent -> {
                    fallbackLines.add(
                        GitGraphLine(
                            fromLane = edge.fromLane,
                            fromRow = rowIdx,
                            toLane = edge.toLane,
                            toRow = rowIdx + 1,
                            lockedFirst = false
                        )
                    )
                }
                GraphEdgeType.PassThrough -> {
                    fallbackLines.add(
                        GitGraphLine(
                            fromLane = edge.fromLane,
                            fromRow = rowIdx,
                            toLane = edge.toLane,
                            toRow = rowIdx + 1,
                            lockedFirst = false
                        )
                    )
                }
                GraphEdgeType.MergeIn -> {
                    if (rowIdx > 0) {
                        fallbackLines.add(
                            GitGraphLine(
                                fromLane = edge.fromLane,
                                fromRow = rowIdx - 1,
                                toLane = edge.toLane,
                                toRow = rowIdx,
                                lockedFirst = false
                            )
                        )
                    }
                }
            }
        }
    }

    val fallbackBranches = fallbackLines.groupBy { it.fromLane }.map { (lane, lines) ->
        GitGraphBranch(colorIndex = lane % GraphPalette.size, lines = lines)
    }

    val fallbackVertices = commits.mapIndexed { idx, c ->
        GitGraphVertex(
            row = idx,
            lane = c.lane,
            colorIndex = c.colorIndex % GraphPalette.size,
            isMerge = c.edges.any { it.edgeType == GraphEdgeType.MergeIn || it.edgeType == GraphEdgeType.MergeBranch } || c.summary.startsWith("merge", ignoreCase = true),
            isHead = (idx == 0)
        )
    }

    return GitGraphLayoutResult(
        rows = rows,
        maxLanes = maxLane + 1,
        branches = fallbackBranches,
        vertices = fallbackVertices
    )
}
