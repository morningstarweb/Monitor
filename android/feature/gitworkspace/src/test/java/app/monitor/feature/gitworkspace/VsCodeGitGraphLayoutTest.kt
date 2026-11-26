package app.monitor.feature.gitworkspace

import app.monitor.core.model.CommitNode
import app.monitor.feature.gitworkspace.model.GraphDrawSegment
import app.monitor.feature.gitworkspace.model.computeVsCodeGitGraphLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VsCodeGitGraphLayoutTest {

    @Test
    fun testLinearHistory() {
        val commits = listOf(
            CommitNode(oid = "c3", shortOid = "c3", authorName = "Dev", authorEmail = "", timestampUtcMs = 300, summary = "c3", parentOids = listOf("c2"), row = 0, lane = 0, colorIndex = 0),
            CommitNode(oid = "c2", shortOid = "c2", authorName = "Dev", authorEmail = "", timestampUtcMs = 200, summary = "c2", parentOids = listOf("c1"), row = 1, lane = 0, colorIndex = 0),
            CommitNode(oid = "c1", shortOid = "c1", authorName = "Dev", authorEmail = "", timestampUtcMs = 100, summary = "c1", parentOids = emptyList(), row = 2, lane = 0, colorIndex = 0)
        )

        val result = computeVsCodeGitGraphLayout(commits)
        assertEquals(3, result.rows.size)
        assertEquals(1, result.maxLanes)

        // c3: tip, no incoming trunk, outgoing trunk to c2
        assertEquals(0, result.rows[0].commitLane)
        assertTrue(result.rows[0].segments.any { it is GraphDrawSegment.OutgoingTrunk })

        // c2: incoming trunk from c3, outgoing trunk to c1
        assertEquals(0, result.rows[1].commitLane)
        assertTrue(result.rows[1].segments.any { it is GraphDrawSegment.IncomingTrunk })
        assertTrue(result.rows[1].segments.any { it is GraphDrawSegment.OutgoingTrunk })

        // c1: root, incoming trunk from c2, no outgoing trunk
        assertEquals(0, result.rows[2].commitLane)
        assertTrue(result.rows[2].segments.any { it is GraphDrawSegment.IncomingTrunk })
        assertFalse(result.rows[2].segments.any { it is GraphDrawSegment.OutgoingTrunk })
    }

    @Test
    fun testMergeCommitCurvesAndLanes() {
        // Scenario matching user screenshot:
        // c0 (HEAD) -> parent: c1
        // c1 (merge commit) -> parents: [c2 (main), b1 (branch tip)]
        // b1 (branch commit) -> parent: c2 (branch base)
        // c2 (main commit / base) -> parent: empty
        val commits = listOf(
            CommitNode(oid = "c0", shortOid = "c0", authorName = "Dev", authorEmail = "", timestampUtcMs = 400, summary = "head", parentOids = listOf("c1"), row = 0, lane = 0, colorIndex = 0),
            CommitNode(oid = "c1", shortOid = "c1", authorName = "Dev", authorEmail = "", timestampUtcMs = 300, summary = "Merge pull request #39", parentOids = listOf("c2", "b1"), row = 1, lane = 0, colorIndex = 0),
            CommitNode(oid = "b1", shortOid = "b1", authorName = "Dev", authorEmail = "", timestampUtcMs = 200, summary = "feat: branch commit", parentOids = listOf("c2"), row = 2, lane = 1, colorIndex = 1),
            CommitNode(oid = "c2", shortOid = "c2", authorName = "Dev", authorEmail = "", timestampUtcMs = 100, summary = "base", parentOids = emptyList(), row = 3, lane = 0, colorIndex = 0)
        )

        val result = computeVsCodeGitGraphLayout(commits)
        assertEquals(4, result.rows.size)
        assertTrue(result.maxLanes >= 2)

        // Row 1 (Merge commit) must be flagged as merge
        assertTrue(result.rows[1].isMerge)
        assertEquals(0, result.rows[1].commitLane)
        // Merge commit must branch out/curve to lane 1 for secondary parent b1
        assertTrue(result.rows[1].segments.any { it is GraphDrawSegment.CurveOut && it.toLane == 1 })
        // Merge commit must have trunk going down for primary parent c2
        assertTrue(result.rows[1].segments.any { it is GraphDrawSegment.OutgoingTrunk && it.lane == 0 })

        // Row 2 (b1) must be in lane 1
        assertEquals(1, result.rows[2].commitLane)
        // Row 2 must have pass-through on lane 0 for c2
        assertTrue(result.rows[2].segments.any { it is GraphDrawSegment.PassThrough && it.lane == 0 })
        // Row 2 has parent c2 which is tracked on lane 0, so it curves back into lane 0
        assertTrue(result.rows[2].segments.any { it is GraphDrawSegment.CurveOut && it.toLane == 0 })

        // Row 3 (c2) must be on lane 0
        assertEquals(0, result.rows[3].commitLane)
        assertFalse(result.rows[3].isMerge)
    }

    @Test
    fun testMultiLaneHistory() {
        // Multi-lane scenario matching user's 5-lane project screenshot:
        // Base commit with multiple parallel feature branches
        val commits = listOf(
            CommitNode(oid = "m1", shortOid = "m1", authorName = "Dev", authorEmail = "", timestampUtcMs = 500, summary = "merge 1", parentOids = listOf("base", "b1", "b2", "b3"), row = 0, lane = 0, colorIndex = 0),
            CommitNode(oid = "b1", shortOid = "b1", authorName = "Dev", authorEmail = "", timestampUtcMs = 400, summary = "feat 1", parentOids = listOf("base"), row = 1, lane = 1, colorIndex = 1),
            CommitNode(oid = "b2", shortOid = "b2", authorName = "Dev", authorEmail = "", timestampUtcMs = 300, summary = "feat 2", parentOids = listOf("base"), row = 2, lane = 2, colorIndex = 2),
            CommitNode(oid = "b3", shortOid = "b3", authorName = "Dev", authorEmail = "", timestampUtcMs = 200, summary = "feat 3", parentOids = listOf("base"), row = 3, lane = 3, colorIndex = 3),
            CommitNode(oid = "base", shortOid = "base", authorName = "Dev", authorEmail = "", timestampUtcMs = 100, summary = "root base", parentOids = emptyList(), row = 4, lane = 0, colorIndex = 0)
        )

        val result = computeVsCodeGitGraphLayout(commits)
        assertEquals(5, result.rows.size)
        // Must allocate multiple distinct lanes without clamping to 3
        assertTrue("Max lanes should be at least 4, was ${result.maxLanes}", result.maxLanes >= 4)
        assertTrue(result.rows[0].isMerge)
    }

    @Test
    fun testEmptyHistoryIsStable() {
        val result = computeVsCodeGitGraphLayout(emptyList())
        assertTrue(result.rows.isEmpty())
        // The layout helper always exposes at least one lane to renderers.
        assertEquals(1, result.maxLanes)
    }

    @Test
    fun testForkFromSingleRootUsesDistinctLanes() {
        val commits = listOf(
            CommitNode(oid = "tipA", shortOid = "tipA", authorName = "Dev", authorEmail = "", timestampUtcMs = 300, summary = "A", parentOids = listOf("root"), row = 0, lane = 0, colorIndex = 0),
            CommitNode(oid = "tipB", shortOid = "tipB", authorName = "Dev", authorEmail = "", timestampUtcMs = 200, summary = "B", parentOids = listOf("root"), row = 1, lane = 1, colorIndex = 1),
            CommitNode(oid = "root", shortOid = "root", authorName = "Dev", authorEmail = "", timestampUtcMs = 100, summary = "root", parentOids = emptyList(), row = 2, lane = 0, colorIndex = 0)
        )
        val result = computeVsCodeGitGraphLayout(commits)
        assertEquals(3, result.rows.size)
        assertTrue("fork must allocate at least two lanes, was ${result.maxLanes}", result.maxLanes >= 2)
        assertFalse(result.rows[0].isMerge)
    }

    @Test
    fun testDisconnectedRootsDoNotCrash() {
        val commits = listOf(
            CommitNode(oid = "r1", shortOid = "r1", authorName = "Dev", authorEmail = "", timestampUtcMs = 200, summary = "root1", parentOids = emptyList(), row = 0, lane = 0, colorIndex = 0),
            CommitNode(oid = "r2", shortOid = "r2", authorName = "Dev", authorEmail = "", timestampUtcMs = 100, summary = "root2", parentOids = emptyList(), row = 1, lane = 1, colorIndex = 1)
        )
        val result = computeVsCodeGitGraphLayout(commits)
        assertEquals(2, result.rows.size)
        assertFalse(result.rows[0].isMerge)
        assertFalse(result.rows[1].isMerge)
    }

    @Test
    fun testLargeLinearHistoryIsDeterministic() {
        // Git log order is newest-first; the layout tracks parents downward.
        val commits = (500 downTo 1).map { i ->
            CommitNode(
                oid = "c$i", shortOid = "c$i", authorName = "Dev", authorEmail = "",
                timestampUtcMs = i.toLong(), summary = "commit $i",
                parentOids = if (i == 1) emptyList() else listOf("c${i - 1}"),
                row = 500 - i, lane = 0, colorIndex = 0
            )
        }
        val first = computeVsCodeGitGraphLayout(commits)
        val second = computeVsCodeGitGraphLayout(commits)
        assertEquals(500, first.rows.size)
        assertEquals(1, first.maxLanes)
        assertEquals(
            first.rows.map { it.segments.toString() },
            second.rows.map { it.segments.toString() }
        )
    }
}
