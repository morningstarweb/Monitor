package app.monitor.feature.gitworkspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.monitor.core.designsystem.components.MonitorGlobalBackgroundCanvas
import app.monitor.core.model.CommitNode
import app.monitor.core.model.PullRequestSummary
import app.monitor.core.model.RepoSummary
import app.monitor.feature.gitworkspace.components.GitDagTreeCard
import app.monitor.feature.gitworkspace.dialogs.GitGraphDialogHost
import app.monitor.feature.gitworkspace.model.GitWorkspaceUiState
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import app.monitor.feature.gitworkspace.components.GitFeedbackPill
import app.monitor.feature.gitworkspace.sections.BranchesManagementSection
import app.monitor.feature.gitworkspace.sections.GitBranchHeroCard
import app.monitor.feature.gitworkspace.sections.GitGraphHeaderSection
import app.monitor.feature.gitworkspace.sections.GitSegment
import app.monitor.feature.gitworkspace.sections.GitWorkspaceSegmentRow
import app.monitor.feature.gitworkspace.sections.PullRequestsListSection
import app.monitor.feature.gitworkspace.sections.RemoteSyncSection
import kotlin.math.max

/**
 * Executive Living Git Hub & Topological DAG Screen (< 180 lines).
 */
@Composable
fun GitGraphScreen(
    uiState: GitWorkspaceUiState,
    repoDisplayName: String = "Monitor",
    allProjects: List<RepoSummary> = emptyList(),
    onSelectProject: (String) -> Unit = {}, onBackClick: () -> Unit = {},
    onCreateBranch: (String) -> Unit = {}, onSwitchBranch: (String) -> Unit = {},
    onDeleteBranch: (String) -> Unit = {}, onMergeBranch: (String) -> Unit = {},
    onMergeAllBranches: (List<String>) -> Unit = {}, onMergePr: (Long) -> Unit = {},
    onPullClick: () -> Unit = {}, onPushClick: () -> Unit = {}, onFetchClick: () -> Unit = {},
    onFileClick: ((String, Boolean, String?) -> Unit)? = null,
    onCommitSelect: ((String) -> Unit)? = null,
    onViewDiff: ((String) -> Unit)? = null,
    onRefresh: () -> Unit = {}, onLoadMore: (Int) -> Unit = {}, onClearFeedback: () -> Unit = {}
) {
    var selectedSegment by remember { mutableStateOf(GitSegment.Graph) }
    var showProjectSelector by remember { mutableStateOf(false) }
    var showCreateBranchDialog by remember { mutableStateOf(false) }
    var branchToSwitch by remember { mutableStateOf<String?>(null) }
    var branchToDelete by remember { mutableStateOf<String?>(null) }
    var selectedCommitForDetails by remember { mutableStateOf<CommitNode?>(null) }
    var selectedPrForDetails by remember { mutableStateOf<PullRequestSummary?>(null) }
    var showCompareSheet by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val rawCommits = uiState.graphPage?.commits.orEmpty()
    val branches = uiState.branches
    val pullRequests = uiState.pullRequests
    val currentBranch = uiState.snapshot?.currentBranch?.takeIf { it.isNotBlank() } ?: "main"
    val aheadCount = uiState.snapshot?.aheadCount ?: 0
    val behindCount = uiState.snapshot?.behindCount ?: 0

    val displayedCommits = remember(rawCommits, searchQuery) {
        if (searchQuery.isBlank()) rawCommits
        else rawCommits.filter {
            it.summary.contains(searchQuery, ignoreCase = true) ||
            it.shortOid.contains(searchQuery, ignoreCase = true) ||
            it.authorName.contains(searchQuery, ignoreCase = true)
        }
    }

    val maxLanes = remember(displayedCommits) {
        var highest = 1
        displayedCommits.forEach { c ->
            highest = max(highest, c.lane)
            c.edges.forEach { e -> highest = max(highest, max(e.fromLane, e.toLane)) }
        }
        max(3, highest + 1)
    }

    val listState = rememberLazyListState()
    val shouldLoadMore = remember(selectedSegment, rawCommits.size, uiState.graphPage?.hasMore, uiState.isLoadingMore) {
        derivedStateOf {
            selectedSegment == GitSegment.Graph &&
                rawCommits.size >= 20 &&
                !listState.canScrollForward &&
                listState.layoutInfo.totalItemsCount > 0 &&
                uiState.graphPage?.hasMore == true &&
                !uiState.isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMore.value) { if (shouldLoadMore.value) onLoadMore(rawCommits.size) }

    GitGraphDialogHost(
        showProjectSelector = showProjectSelector, onDismissProjectSelector = { showProjectSelector = false },
        allProjects = allProjects, currentRepoDisplayName = repoDisplayName, currentRepoId = uiState.repoId,
        onSelectProject = onSelectProject, showCreateBranchDialog = showCreateBranchDialog,
        onDismissCreateBranchDialog = { showCreateBranchDialog = false }, isOperating = uiState.isOperating,
        onCreateBranch = onCreateBranch, branchToSwitch = branchToSwitch,
        onDismissSwitchBranchDialog = { branchToSwitch = null }, onConfirmSwitchBranch = { onSwitchBranch(it); branchToSwitch = null },
        branchToDelete = branchToDelete, onDismissDeleteBranchDialog = { branchToDelete = null },
        onConfirmDeleteBranch = { onDeleteBranch(it); branchToDelete = null },
        selectedCommitForDetails = selectedCommitForDetails,
        selectedDiff = uiState.selectedDiff,
        onFileClick = { file, commitOid -> onFileClick?.invoke(file.path, file.staged, commitOid) },
        selectedPrForDetails = selectedPrForDetails,
        onDismissPrDetails = { selectedPrForDetails = null }, onMergePr = { onMergePr(it.number) },
        workspaceFiles = selectedCommitForDetails?.let { uiState.commitFilesMap[it.oid] } ?: emptyList(),
        onViewFullDiff = onViewDiff,
        onDismissCommitDetails = { selectedCommitForDetails = null }, showCompareSheet = showCompareSheet,
        onDismissCompareSheet = { showCompareSheet = false }, currentBranch = currentBranch,
        aheadCount = aheadCount, behindCount = behindCount
    )

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark

    Box(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF07080E) else Color.Transparent)) {
        MonitorGlobalBackgroundCanvas(isDarkTheme = isDark)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 144.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GitGraphHeaderSection(
                    repoDisplayName = repoDisplayName, currentBranch = currentBranch, isSearchActive = isSearchActive,
                    onToggleSearch = { isSearchActive = !isSearchActive; if (!isSearchActive) searchQuery = "" },
                    searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it },
                    onRefresh = onRefresh, onProjectDropdownClick = { showProjectSelector = true }
                )
            }
            item {
                GitWorkspaceSegmentRow(
                    selectedSegment = selectedSegment, onSelectSegment = { selectedSegment = it },
                    prCount = pullRequests.count { it.state.equals("OPEN", ignoreCase = true) }
                )
            }
            when (selectedSegment) {
                GitSegment.Graph -> {
                    item {
                        GitBranchHeroCard(
                            currentBranch = currentBranch, upstreamBranch = uiState.snapshot?.upstreamBranch,
                            aheadCount = aheadCount, behindCount = behindCount, totalCommits = rawCommits.size,
                            onNewBranchClick = { showCreateBranchDialog = true }, onPullClick = onPullClick,
                            onPushClick = onPushClick, onFetchClick = onFetchClick, onCompareClick = { showCompareSheet = true }
                        )
                    }
                    item {
                        GitDagTreeCard(
                            displayedCommits = displayedCommits,
                            maxLanes = maxLanes,
                            currentBranch = currentBranch,
                            isLoading = uiState.isLoading,
                            isLoadingMore = uiState.isLoadingMore,
                            hasMoreCommits = (uiState.graphPage?.hasMore == true),
                            searchQuery = searchQuery,
                            errorMessage = uiState.errorMessage,
                            onCommitClick = {
                                selectedCommitForDetails = it
                                onCommitSelect?.invoke(it.oid)
                            },
                            onLoadMore = { onLoadMore(rawCommits.size) }
                        )
                    }
                }
                GitSegment.Branches -> {
                    item {
                        BranchesManagementSection(
                            branches = branches, currentBranch = currentBranch,
                            onNewBranchClick = { showCreateBranchDialog = true }, onSwitchBranch = { branchToSwitch = it },
                            onMergeBranch = onMergeBranch, onDeleteBranch = { branchToDelete = it },
                            onMergeAllBranches = onMergeAllBranches
                        )
                    }
                }
                GitSegment.PullRequests -> {
                    item {
                        PullRequestsListSection(pullRequests = pullRequests, onSelectPr = { selectedPrForDetails = it })
                    }
                }
                GitSegment.Sync -> {
                    item {
                        RemoteSyncSection(
                            currentBranch = currentBranch, upstreamBranch = uiState.snapshot?.upstreamBranch,
                            aheadCount = aheadCount, behindCount = behindCount, isOperating = uiState.isOperating,
                            onPullClick = onPullClick, onPushClick = onPushClick, onFetchClick = onFetchClick,
                            onCompareClick = { showCompareSheet = true }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(110.dp)) }
        }
        GitFeedbackPill(
            message = uiState.errorMessage ?: uiState.userMessage,
            onDismiss = onClearFeedback,
            isErrorHint = uiState.errorMessage != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        )
    }
}
