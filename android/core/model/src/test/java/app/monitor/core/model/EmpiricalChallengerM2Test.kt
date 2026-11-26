package app.monitor.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 2.
 * Validates edge case resilience, empty repository models,
 * process kill boundary responses, and disk telemetry contracts.
 */
class EmpiricalChallengerM2Test {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test
    fun testEmptyGitRepositoryZeroCommitsBranchesFiles() {
        // 1. GitGraphPage with 0 commits
        val emptyGraph = GitGraphPage(
            repoId = "empty-repo",
            commits = emptyList(),
            hasMore = false,
            nextSkip = null
        )
        val serializedGraph = json.encodeToString(emptyGraph)
        val deserializedGraph = json.decodeFromString<GitGraphPage>(serializedGraph)
        assertEquals("empty-repo", deserializedGraph.repoId)
        assertTrue(deserializedGraph.commits.isEmpty())
        assertFalse(deserializedGraph.hasMore)
        assertNull(deserializedGraph.nextSkip)

        // 2. GitWorkspaceSnapshot with 0 files and blank branch
        val emptyWorkspace = GitWorkspaceSnapshot(
            repoId = "empty-repo",
            revision = 0L,
            currentBranch = "",
            upstreamBranch = null,
            aheadCount = 0,
            behindCount = 0,
            files = emptyList()
        )
        val serializedWs = json.encodeToString(emptyWorkspace)
        val deserializedWs = json.decodeFromString<GitWorkspaceSnapshot>(serializedWs)
        assertEquals("empty-repo", deserializedWs.repoId)
        assertEquals(0L, deserializedWs.revision)
        assertEquals("", deserializedWs.currentBranch)
        assertNull(deserializedWs.upstreamBranch)
        assertEquals(0, deserializedWs.aheadCount)
        assertEquals(0, deserializedWs.behindCount)
        assertTrue(deserializedWs.files.isEmpty())

        // 3. Branches empty collection
        val emptyBranches: List<BranchInfo> = emptyList()
        val serializedBranches = json.encodeToString(emptyBranches)
        val deserializedBranches = json.decodeFromString<List<BranchInfo>>(serializedBranches)
        assertTrue(deserializedBranches.isEmpty())
    }

    @Test
    fun testDiskTelemetryEmptyAndOmittedField() {
        // 1. SystemSnapshot with explicit empty disks list
        val snapshotEmptyDisks = SystemSnapshot(
            schemaVersion = 3,
            observedAtUtcMs = 1700000000000L,
            hostname = "desktop-box",
            agentVersion = "1.0.4",
            uptimeSeconds = 120,
            battery = BatterySnapshot(hasBattery = false),
            cpu = CpuSnapshot(totalUsagePercent = 5.0f, frequencyMhz = 3200),
            ram = MemorySnapshot(totalBytes = 16000000000L, usedBytes = 8000000000L, availableBytes = 8000000000L, usagePercent = 50.0f),
            cooling = null,
            disks = emptyList()
        )
        val serialized = json.encodeToString(snapshotEmptyDisks)
        val deserialized = json.decodeFromString<SystemSnapshot>(serialized)
        assertTrue(deserialized.disks.isEmpty())
        assertNull(deserialized.cooling)

        // 2. Backward compatibility: JSON payload completely missing the "disks" key
        val jsonWithoutDisks = """
            {
                "schema_version": 3,
                "observed_at_utc_ms": 1700000000000,
                "hostname": "legacy-box",
                "agent_version": "0.1.0",
                "uptime_seconds": 60,
                "battery": { "has_battery": false },
                "cpu": { "total_usage_percent": 12.0, "frequency_mhz": 3000 },
                "ram": { "total_bytes": 8000000000, "used_bytes": 4000000000, "available_bytes": 4000000000, "usage_percent": 50.0 }
            }
        """.trimIndent()
        val parsedLegacy = json.decodeFromString<SystemSnapshot>(jsonWithoutDisks)
        assertNotNull(parsedLegacy.disks)
        assertTrue(parsedLegacy.disks.isEmpty())
    }

    @Test
    fun testDiskTelemetryBoundaryValues() {
        // Test edge values: 0 total bytes, 0 available bytes, full disk
        val zeroTotalDisk = DiskSnapshot(
            name = "",
            mountPoint = "Z:\\",
            totalBytes = 0L,
            availableBytes = 0L
        )
        val fullDisk = DiskSnapshot(
            name = "Drive C",
            mountPoint = "C:\\",
            totalBytes = 1_000_000_000_000L,
            availableBytes = 0L
        )
        val emptyDisk = DiskSnapshot(
            name = "Drive D",
            mountPoint = "D:\\",
            totalBytes = 500_000_000_000L,
            availableBytes = 500_000_000_000L
        )

        // Simulate UI calculation safely
        fun calculateRatio(disk: DiskSnapshot): Float {
            return if (disk.totalBytes > 0) {
                ((disk.totalBytes - disk.availableBytes).toFloat() / disk.totalBytes).coerceIn(0f, 1f)
            } else 0f
        }

        assertEquals(0f, calculateRatio(zeroTotalDisk), 0.001f)
        assertEquals(1.0f, calculateRatio(fullDisk), 0.001f)
        assertEquals(0f, calculateRatio(emptyDisk), 0.001f)
    }

    @Test
    fun testProcessKillResponseNegativeZeroAndProtectedPids() {
        // PID 0 (Protected)
        val respZero = ProcessKillResponse(success = false, pid = 0L)
        val serZero = json.encodeToString(respZero)
        val deserZero = json.decodeFromString<ProcessKillResponse>(serZero)
        assertFalse(deserZero.success)
        assertEquals(0L, deserZero.pid)

        // PID 4 (Protected Windows System process)
        val respFour = ProcessKillResponse(success = false, pid = 4L)
        val serFour = json.encodeToString(respFour)
        val deserFour = json.decodeFromString<ProcessKillResponse>(serFour)
        assertFalse(deserFour.success)
        assertEquals(4L, deserFour.pid)

        // Negative PID (-1L)
        val respNeg = ProcessKillResponse(success = false, pid = -1L)
        val serNeg = json.encodeToString(respNeg)
        val deserNeg = json.decodeFromString<ProcessKillResponse>(serNeg)
        assertFalse(deserNeg.success)
        assertEquals(-1L, deserNeg.pid)

        // Positive PID successful termination
        val respSuccess = ProcessKillResponse(success = true, pid = 12345L)
        val serSuccess = json.encodeToString(respSuccess)
        val deserSuccess = json.decodeFromString<ProcessKillResponse>(serSuccess)
        assertTrue(deserSuccess.success)
        assertEquals(12345L, deserSuccess.pid)
    }

    @Test
    fun testPullRequestModelsEdgeCases() {
        // Empty PR list
        val emptyPulls: List<PullRequestSummary> = emptyList()
        val serPulls = json.encodeToString(emptyPulls)
        val deserPulls = json.decodeFromString<List<PullRequestSummary>>(serPulls)
        assertTrue(deserPulls.isEmpty())

        // Merge PR requests with valid methods
        val reqMerge = MergePrRequest(method = "merge")
        val serReq = json.encodeToString(reqMerge)
        val deserReq = json.decodeFromString<MergePrRequest>(serReq)
        assertEquals("merge", deserReq.method)

        val respMerge = MergePrResponse(success = true, message = "Merged PR #1 successfully")
        val serResp = json.encodeToString(respMerge)
        val deserResp = json.decodeFromString<MergePrResponse>(serResp)
        assertTrue(deserResp.success)
        assertEquals("Merged PR #1 successfully", deserResp.message)
    }
}
