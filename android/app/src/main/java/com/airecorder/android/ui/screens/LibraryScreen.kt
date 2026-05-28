package com.airecorder.android.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import com.airecorder.android.data.local.PreferencesManager
import kotlinx.coroutines.launch
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airecorder.android.R
import com.airecorder.android.data.model.Recording
import com.airecorder.android.ui.components.*
import com.airecorder.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    preferencesManager: PreferencesManager,
    onNavigateToDetail: (String) -> Unit,
    onUploadClick: () -> Unit
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
    
    val coroutineScope = rememberCoroutineScope()
    
    var isOverviewExpanded by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        preferencesManager.overviewExpanded.collect { expanded ->
            isOverviewExpanded = expanded
        }
    }
    
    val toggleOverview = {
        val newState = !isOverviewExpanded
        isOverviewExpanded = newState
        coroutineScope.launch {
            preferencesManager.setOverviewExpanded(newState)
        }
        Unit
    }
    
    val isSelectionMode = selectedRecordingIds.isNotEmpty()
    
    Scaffold(
        topBar = {
            if (isSelectionMode) {
                BatchOperationBar(
                    selectedCount = selectedRecordingIds.size,
                    onTranscribe = { viewModel.batchTranscribe() },
                    onSummarize = { viewModel.batchSummarize() },
                    onDelete = { viewModel.batchDelete() },
                    onDeselectAll = { viewModel.deselectAllRecordings() }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "录音库",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = toggleOverview) {
                            Icon(
                                imageVector = if (isOverviewExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isOverviewExpanded) "收起概览" else "展开概览",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                            Icon(
                                imageVector = if (isSearchExpanded) Icons.Default.FilterListOff else Icons.Default.FilterList,
                                contentDescription = "搜索与筛选",
                                tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onUploadClick) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "上传音频",
                                tint = MaterialTheme.colorScheme.primary
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                        selectedStatuses.isEmpty() && selectedSource == null) {
                        EmptyLibraryView()
                    } else {
                        ContentView(
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
                            onRecordingClick = { onNavigateToDetail(it) },
                            onRecordingLongClick = { viewModel.toggleRecordingSelection(it) },
                            onRefresh = { viewModel.refresh() }
                        )
                    }
                }
                is LibraryUiState.Error -> {
                    ErrorState(
                        error = state.message,
                        onRetry = { viewModel.refresh() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentView(
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
    onRecordingClick: (String) -> Unit,
    onRecordingLongClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        MetricCardsRow(
            recordings = recordings,
            isExpanded = isOverviewExpanded
        )
        
        if (isSearchExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
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
        
        ActiveFilterTags(
            selectedStatuses = selectedStatuses,
            selectedSource = selectedSource,
            onRemoveStatus = onRemoveStatus,
            onRemoveSource = onRemoveSource,
            onClearAll = onClearAllFilters
        )
        
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
        modifier = modifier
            .fillMaxWidth(),
        placeholder = { Text("搜索文件名") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = TextSecondary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Surface,
            focusedContainerColor = Surface,
            unfocusedBorderColor = Outline,
            focusedBorderColor = Primary
        ),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingList(
    recordings: List<Recording>,
    selectedRecordingIds: Set<String>,
    isSelectionMode: Boolean,
    isRefreshing: Boolean,
    onRecordingClick: (String) -> Unit,
    onRecordingLongClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            onRefresh()
        }
    }
    
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
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
                    onClick = { onRecordingClick(recording.id) },
                    onLongClick = { onRecordingLongClick(recording.id) }
                )
            }
        }
        
        if (pullToRefreshState.progress > 0.01f || isRefreshing) {
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EmptyLibraryView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MicNone,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = TextTertiary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "还没有录音",
            style = MaterialTheme.typography.titleLarge,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击右上角 + 上传音频文件",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun EmptyFilteredView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = TextTertiary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "没有找到匹配的录音",
            style = MaterialTheme.typography.titleLarge,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "尝试调整筛选条件",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary
        )
    }
}

@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "加载失败",
            style = MaterialTheme.typography.titleLarge,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}
