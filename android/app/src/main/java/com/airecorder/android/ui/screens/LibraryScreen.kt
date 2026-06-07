package com.airecorder.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.airecorder.android.data.local.PreferencesManager
import com.airecorder.android.data.model.Recording
import com.airecorder.android.ui.animation.SharedElementState
import com.airecorder.android.ui.components.*
import com.airecorder.android.ui.theme.*
import com.airecorder.android.ui.util.rememberHapticFeedback
import com.airecorder.android.util.ErrorUtils
import com.airecorder.android.util.FormatUtils

/**
 * 录音库首页 —「声音档案馆」
 * 底栏常驻，FAB 呼吸动画
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    preferencesManager: PreferencesManager,
    onNavigateToDetail: (String) -> Unit,
    onUploadClick: () -> Unit,
    sharedElementState: SharedElementState? = null,
    onRecordItemClick: ((String, String, String, LayoutCoordinates?) -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        viewModel.loadRecordings()
    }

    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedStatuses by viewModel.selectedStatuses.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val selectedRecordingIds by viewModel.selectedRecordingIds.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var isSearchExpanded by remember { mutableStateOf(false) }
    var isOverviewExpanded by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = rememberHapticFeedback()

    LaunchedEffect(Unit) {
        preferencesManager.overviewExpanded.collect { expanded ->
            isOverviewExpanded = expanded
        }
    }

    val toggleOverview: () -> Unit = {
        val newState = !isOverviewExpanded
        isOverviewExpanded = newState
        if (newState) isSearchExpanded = false
        coroutineScope.launch { preferencesManager.setOverviewExpanded(newState) }
        Unit
    }

    val isSelectionMode = selectedRecordingIds.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (isSelectionMode) {
                    BatchOperationBar(
                        selectedCount = selectedRecordingIds.size,
                        onTranscribe = { hapticFeedback.performHeavyClick(); viewModel.batchTranscribe() },
                        onSummarize = { hapticFeedback.performHeavyClick(); viewModel.batchSummarize() },
                        onDelete = { hapticFeedback.performConfirm(); viewModel.batchDelete() },
                        onDeselectAll = { viewModel.deselectAllRecordings() }
                    )
                } else {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "录音库",
                                    style = TopBarTitleStyle,
                                    color = TextPrimary
                                )
                                if (uiState is LibraryUiState.Success) {
                                    val count = (uiState as LibraryUiState.Success).recordings.size
                                    Text(
                                        text = count.toString(),
                                        style = CountStyle
                                    )
                                    Text(
                                        text = "首",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextTertiary
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = toggleOverview) {
                                Icon(
                                    imageVector = if (isOverviewExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isOverviewExpanded) "收起概览" else "展开概览",
                                    tint = TextSecondary
                                )
                            }
                            IconButton(onClick = {
                                isSearchExpanded = !isSearchExpanded
                                if (isSearchExpanded) {
                                    isOverviewExpanded = false
                                    coroutineScope.launch { preferencesManager.setOverviewExpanded(false) }
                                }
                                Unit
                            }) {
                                Icon(
                                    imageVector = if (isSearchExpanded) Icons.Default.FilterListOff else Icons.Default.FilterList,
                                    contentDescription = "搜索与筛选",
                                    tint = if (isSearchExpanded) Primary else TextSecondary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Background,
                            scrolledContainerColor = Surface
                        )
                    )
                }
            },
            containerColor = Background,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (val state = uiState) {
                    is LibraryUiState.Loading -> {
                        SkeletonLoading()
                    }
                    is LibraryUiState.Success -> {
                        if (state.recordings.isEmpty() && searchQuery.isEmpty() &&
                            selectedStatuses.isEmpty() && selectedSource == null
                        ) {
                            EmptyLibraryView(onUploadClick = onUploadClick)
                        } else {
                            LibraryContent(
                                recordings = state.recordings,
                                searchQuery = searchQuery,
                                selectedStatuses = selectedStatuses,
                                selectedSource = selectedSource,
                                sortOption = sortOption,
                                selectedRecordingIds = selectedRecordingIds,
                                isRefreshing = isRefreshing,
                                isSelectionMode = isSelectionMode,
                                isSearchExpanded = isSearchExpanded,
                                isOverviewExpanded = isOverviewExpanded,
                                onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                                onStatusToggle = { viewModel.toggleStatusFilter(it) },
                                onSourceSelect = { viewModel.setSourceFilter(it) },
                                onSortSelect = { viewModel.setSortOption(it) },
                                onClearAllFilters = { viewModel.clearAllFilters() },
                                onRemoveStatus = { viewModel.toggleStatusFilter(it) },
                                onRemoveSource = { viewModel.setSourceFilter(null) },
                                onRecordingClick = { id, coords ->
                                    onRecordItemClick?.invoke(
                                        id,
                                        state.recordings.find { it.id == id }?.filename ?: "",
                                        FormatUtils.formatDuration(state.recordings.find { it.id == id }?.durationSeconds ?: 0.0),
                                        coords
                                    )
                                },
                                onRecordingLongClick = {
                                    hapticFeedback.performLongPress()
                                    viewModel.toggleRecordingSelection(it)
                                },
                                onRefresh = { viewModel.refresh() }
                            )
                        }
                    }
                    is LibraryUiState.Error -> {
                        ErrorState(
                            error = ErrorUtils.getFriendlyErrorMessage(state.message),
                            onRetry = { viewModel.refresh() }
                        )
                    }
                }
            }
        }

        // 呼吸动画 FAB — 右下角悬浮
        if (!isSelectionMode) {
            UploadFAB(
                onClick = onUploadClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun LibraryContent(
    recordings: List<Recording>,
    searchQuery: String,
    selectedStatuses: Set<String>,
    selectedSource: String?,
    sortOption: SortOption,
    selectedRecordingIds: Set<String>,
    isRefreshing: Boolean,
    isSelectionMode: Boolean,
    isSearchExpanded: Boolean,
    isOverviewExpanded: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onStatusToggle: (String) -> Unit,
    onSourceSelect: (String?) -> Unit,
    onSortSelect: (SortOption) -> Unit,
    onClearAllFilters: () -> Unit,
    onRemoveStatus: (String) -> Unit,
    onRemoveSource: () -> Unit,
    onRecordingClick: (String, LayoutCoordinates?) -> Unit,
    onRecordingLongClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 概览指标卡片
        MetricCardsRow(
            recordings = recordings,
            isExpanded = isOverviewExpanded
        )

        // 搜索 + 筛选栏
        AnimatedVisibility(
            visible = isSearchExpanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChanged,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                FilterChips(
                    selectedStatuses = selectedStatuses,
                    onStatusToggle = onStatusToggle,
                    selectedSource = selectedSource,
                    onSourceSelect = onSourceSelect,
                    sortOption = sortOption,
                    onSortSelect = onSortSelect
                )
            }
        }

        // 激活的筛选标签
        ActiveFilterTags(
            selectedStatuses = selectedStatuses,
            selectedSource = selectedSource,
            onRemoveStatus = onRemoveStatus,
            onRemoveSource = onRemoveSource,
            onClearAll = onClearAllFilters
        )

        // 录音列表
        if (recordings.isEmpty()) {
            EmptyFilteredView()
        } else {
            RecordingList(
                recordings = recordings,
                selectedRecordingIds = selectedRecordingIds,
                isSelectionMode = isSelectionMode,
                isRefreshing = isRefreshing,
                onRecordingClick = onRecordingClick,
                onRecordingLongClick = onRecordingLongClick,
                onRefresh = onRefresh
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text("搜索录音、转写内容...", color = TextPlaceholder)
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = TextTertiary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清除搜索",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        shape = TextFieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Surface,
            focusedContainerColor = Surface,
            unfocusedBorderColor = Outline,
            focusedBorderColor = Primary
        ),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun RecordingList(
    recordings: List<Recording>,
    selectedRecordingIds: Set<String>,
    isSelectionMode: Boolean,
    isRefreshing: Boolean,
    onRecordingClick: (String, LayoutCoordinates?) -> Unit,
    onRecordingLongClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val itemPositions = remember { mutableMapOf<String, LayoutCoordinates>() }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(
                items = recordings,
                key = { it.id }
            ) { recording ->
                RecordingItem(
                    recording = recording,
                    isSelected = recording.id in selectedRecordingIds,
                    isSelectionMode = isSelectionMode,
                    onClick = { onRecordingClick(recording.id, itemPositions[recording.id]) },
                    onLongClick = { onRecordingLongClick(recording.id) },
                    onPositioned = { coords -> itemPositions[recording.id] = coords },
                    modifier = Modifier.animateItem(placementSpec = tween(300))
                )
            }
        }
    }
}
