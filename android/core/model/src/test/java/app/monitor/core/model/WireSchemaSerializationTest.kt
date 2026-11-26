package app.monitor.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class WireSchemaSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun testSystemSnapshotSerializationRoundTrip() {
        val snapshot = SystemSnapshot(
            schemaVersion = 3,
            observedAtUtcMs = 1700000000000L,
            hostname = "morningstar",
            agentVersion = "0.1.0",
            uptimeSeconds = 3600,
            battery = BatterySnapshot(
                hasBattery = true,
                percentage = 82.0f,
                chargingState = ChargingState.Charging,
                acStatus = AcPowerStatus.Connected
            ),
            cpu = CpuSnapshot(
                totalUsagePercent = 34.2f,
                perCoreUsagePercent = listOf(30.0f, 38.0f),
                frequencyMhz = 3400L,
                topProcesses = listOf(
                    ProcessSnapshot(
                        pid = 4012L,
                        name = "rust-analyzer.exe",
                        cpuPercent = 14.5f,
                        memoryBytes = 1200000000L
                    )
                )
            ),
            ram = MemorySnapshot(
                totalBytes = 17179869184L,
                usedBytes = 11700000000L,
                availableBytes = 5479869184L,
                usagePercent = 68.1f
            )
        )

        val serialized = json.encodeToString(snapshot)
        val deserialized = json.decodeFromString<SystemSnapshot>(serialized)

        assertEquals("morningstar", deserialized.hostname)
        assertEquals(82.0f, deserialized.battery.percentage ?: 0f, 0.01f)
        assertEquals(ChargingState.Charging, deserialized.battery.chargingState)
        assertEquals(34.2f, deserialized.cpu.totalUsagePercent, 0.01f)
        assertEquals("rust-analyzer.exe", deserialized.cpu.topProcesses.first().name)
    }

    @Test
    fun testGitWorkspaceSnapshotSerialization() {
        val workspace = GitWorkspaceSnapshot(
            repoId = "repo-monitor",
            revision = 42L,
            currentBranch = "main",
            upstreamBranch = "origin/main",
            aheadCount = 2,
            behindCount = 0,
            files = listOf(
                GitFileChange(
                    path = "src/main.rs",
                    category = FileChangeCategory.Modified,
                    staged = false,
                    insertions = 12,
                    deletions = 4
                )
            )
        )

        val serialized = json.encodeToString(workspace)
        val deserialized = json.decodeFromString<GitWorkspaceSnapshot>(serialized)

        assertEquals("repo-monitor", deserialized.repoId)
        assertEquals(42L, deserialized.revision)
        assertEquals("main", deserialized.currentBranch)
        assertEquals(1, deserialized.files.size)
        assertEquals(FileChangeCategory.Modified, deserialized.files.first().category)
        assertEquals(12L, deserialized.files.first().insertions)
    }

    @Test
    fun testGitGraphPageSerialization() {
        val graphPage = GitGraphPage(
            repoId = "repo-monitor",
            commits = listOf(
                CommitNode(
                    oid = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                    shortOid = "a1b2c3d",
                    authorName = "Engineer",
                    authorEmail = "dev@monitor.local",
                    timestampUtcMs = 1700000000000L,
                    summary = "feat: implement visual DAG graph",
                    parentOids = listOf("f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5"),
                    row = 0,
                    lane = 0,
                    colorIndex = 0,
                    edges = listOf(
                        GraphEdge(
                            fromLane = 0,
                            toLane = 0,
                            edgeType = GraphEdgeType.Straight,
                            colorIndex = 0
                        )
                    )
                )
            ),
            hasMore = false,
            nextSkip = null
        )

        val serialized = json.encodeToString(graphPage)
        val deserialized = json.decodeFromString<GitGraphPage>(serialized)

        assertEquals("repo-monitor", deserialized.repoId)
        assertEquals(1, deserialized.commits.size)
        val commit = deserialized.commits.first()
        assertEquals("a1b2c3d", commit.shortOid)
        assertEquals(GraphEdgeType.Straight, commit.edges.first().edgeType)
    }

    @Test
    fun testPairingModelsSerialization() {
        val req = PairingRequest(
            pairingCode = "123456",
            deviceName = "Pixel 8 Pro"
        )
        val serializedReq = json.encodeToString(req)
        val deserializedReq = json.decodeFromString<PairingRequest>(serializedReq)
        assertEquals("123456", deserializedReq.pairingCode)

        val resp = PairingResponse(
            deviceToken = "tok_sec_999",
            role = DeviceRole.Operator,
            expiresAtUtcMs = 1700050000000L
        )
        val serializedResp = json.encodeToString(resp)
        val deserializedResp = json.decodeFromString<PairingResponse>(serializedResp)
        assertEquals(DeviceRole.Operator, deserializedResp.role)
        assertEquals("tok_sec_999", deserializedResp.deviceToken)
    }

    @Test
    fun testPermissionsResponseSerialization() {
        val permissions = PermissionsResponse(
            gitAccessAllowed = true,
            deleteAccessAllowed = false,
            cloudAccessAllowed = true,
            localAccessAllowed = true
        )
        val serialized = json.encodeToString(permissions)
        val deserialized = json.decodeFromString<PermissionsResponse>(serialized)
        assertEquals(true, deserialized.gitAccessAllowed)
        assertEquals(false, deserialized.deleteAccessAllowed)
        assertEquals(true, deserialized.cloudAccessAllowed)
        assertEquals(true, deserialized.localAccessAllowed)
    }

    @Test
    fun testPullPushGitOperationSerialization() {
        val pullOp = GitOperation.Pull(remote = "origin", branch = "main")
        val pullReq = GitOperationRequest(requestId = "req-1", expectedRepoRevision = 5L, operation = pullOp)
        val serializedPull = json.encodeToString(pullReq)
        val deserializedPull = json.decodeFromString<GitOperationRequest>(serializedPull)
        assertEquals("req-1", deserializedPull.requestId)
        assertEquals(pullOp, deserializedPull.operation)

        val pushOp = GitOperation.Push(remote = "origin", branch = "main", setUpstream = true)
        val pushReq = GitOperationRequest(requestId = "req-2", expectedRepoRevision = 6L, operation = pushOp)
        val serializedPush = json.encodeToString(pushReq)
        val deserializedPush = json.decodeFromString<GitOperationRequest>(serializedPush)
        assertEquals("req-2", deserializedPush.requestId)
        assertEquals(pushOp, deserializedPush.operation)

        val mergeAllOp = GitOperation.MergeAllBranches(sourceBranches = listOf("cleanup", "thread"))
        val mergeAllReq = GitOperationRequest(requestId = "req-3", expectedRepoRevision = 7L, operation = mergeAllOp)
        val serializedMergeAll = json.encodeToString(mergeAllReq)
        val deserializedMergeAll = json.decodeFromString<GitOperationRequest>(serializedMergeAll)
        assertEquals("req-3", deserializedMergeAll.requestId)
        assertEquals(mergeAllOp, deserializedMergeAll.operation)
    }
}

