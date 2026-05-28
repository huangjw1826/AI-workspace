package com.airecorder.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airecorder.android.R
import com.airecorder.android.data.model.Summary
import com.airecorder.android.data.model.TranscriptSegment
import com.airecorder.android.ui.components.*
import com.airecorder.android.ui.screens.detail.*
import com.airecorder.android.ui.theme.DividerLight
import com.airecorder.android.ui.theme.TextSecondary
import com.airecorder.android.ui.theme.TextTertiary
import com.airecorder.android.util.AudioUtils
import com.airecorder.android.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    recordingId: String,
    viewModel: DetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToSummaryDetail: (Summary) -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    LaunchedEffect(recordingId) {
        viewModel.loadRecording(recordingId)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val isSummarizing by viewModel.isSummarizing.collectAsState()
    val showSummaryTemplates by viewModel.showSummaryTemplates.collectAsState()
    val summaryTemplates by viewModel.summaryTemplates.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val audioDownloadState by viewModel.audioDownloadState.collectAsState()
    val audioPlaybackState by viewModel.audioPlaybackState.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val currentSegmentIndex by viewModel.currentSegmentIndex.collectAsState()
    
    val pagerState = rememberPagerState(
        initialPage = selectedTab,
        pageCount = { 3 }
    )
    
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }
    
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedTab) {
            viewModel.selectTab(pagerState.currentPage)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val state = uiState) {
                        is DetailUiState.Success -> {
                            Text(
                                text = state.data.recording.filename,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        else -> {
                            Text(stringResource(R.string.detail_title))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (uiState is DetailUiState.Success) {
                        IconButton(onClick = { viewModel.refreshRecording() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteRecording(recordingId, onNavigateBack) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                }
            ) {
                TabItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    title = "内容",
                    icon = Icons.Default.Audiotrack
                )
                TabItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    title = "摘要",
                    icon = Icons.Default.AutoAwesome
                )
                TabItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    title = "信息",
                    icon = Icons.Default.Info
                )
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val state = uiState) {
                    is DetailUiState.Loading -> {
                        LoadingState(message = stringResource(R.string.loading_recording))
                    }
                    is DetailUiState.Success -> {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true
                        ) { page ->
                            when (page) {
                                0 -> {
                                    val playback = audioPlaybackState
                                    val currentPos = when (playback) {
                                        is AudioPlaybackState.Playing -> playback.positionMs
                                        is AudioPlaybackState.Paused -> playback.positionMs
                                        else -> 0L
                                    }
                                    val duration = when (playback) {
                                        is AudioPlaybackState.Playing -> playback.durationMs
                                        is AudioPlaybackState.Paused -> playback.durationMs
                                        else -> 0L
                                    }
                                    val isPlaying = playback is AudioPlaybackState.Playing
                                    val isBuffering = playback is AudioPlaybackState.Buffering
                                    
                                    val playTabAudioState = when (val ds = audioDownloadState) {
                                        is AudioDownloadState.Downloading -> AudioState.Downloading(
                                            ds.progress,
                                            ds.downloaded,
                                            ds.total
                                        )
                                        is AudioDownloadState.Paused -> AudioState.Paused(
                                            ds.progress,
                                            ds.downloaded,
                                            ds.total
                                        )
                                        is AudioDownloadState.Downloaded -> AudioState.Downloaded
                                        is AudioDownloadState.Error -> AudioState.Error(
                                            ds.message
                                        )
                                        else -> AudioState.NotDownloaded
                                    }
                                    
                                    PlayTab(
                                        recordingDetail = state.data,
                                        audioState = playTabAudioState,
                                        isPlaying = isPlaying,
                                        isBuffering = isBuffering,
                                        currentPosition = currentPos,
                                        duration = duration,
                                        playbackSpeed = playbackSpeed,
                                        currentSegmentIndex = currentSegmentIndex,
                                        isTranscribing = isTranscribing,
                                        onDownload = { viewModel.startDownload() },
                                        onPauseDownload = { viewModel.pauseDownload() },
                                        onCancelDownload = { viewModel.cancelDownload() },
                                        onDeleteAudio = { viewModel.deleteAudio() },
                                        onPlayPause = { viewModel.togglePlayPause() },
                                        onSeekTo = { viewModel.seekTo(it) },
                                        onRewind = { viewModel.rewind() },
                                        onForward = { viewModel.forward() },
                                        onChangeSpeed = { viewModel.togglePlaybackSpeed() },
                                        onSegmentClick = { segment ->
                                            segment.startTime?.let { viewModel.jumpToSegment(it) }
                                        },
                                        onTranscribe = { viewModel.transcribe(recordingId) }
                                    )
                                }
                                1 -> {
                                    SummaryTab(
                                        summaries = state.data.summaries,
                                        isSummarizing = isSummarizing,
                                        onSummaryClick = { summary -> onNavigateToSummaryDetail(summary) },
                                        onGenerateNew = { viewModel.loadSummaryTemplates() }
                                    )
                                }
                                2 -> {
                                    InfoTab(recording = state.data.recording)
                                }
                            }
                        }
                    }
                    is DetailUiState.Error -> {
                        ErrorState(
                            error = state.message,
                            onRetry = { viewModel.loadRecording(recordingId) }
                        )
                    }
                }
            }
        }
    }
    
    if (showSummaryTemplates) {
        SummaryTemplateBottomSheet(
            templates = summaryTemplates,
            onSelect = { templateId ->
                viewModel.summarize(recordingId, templateId)
                viewModel.dismissSummaryTemplates()
            },
            onDismiss = { viewModel.dismissSummaryTemplates() }
        )
    }
}

@Composable
fun TabItem(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        },
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryTemplateBottomSheet(
    templates: List<Map<String, String>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.select_summary_template),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(templates.size) { index ->
                    val template = templates[index]
                    TemplateCard(
                        name = template["name"] ?: "",
                        description = template["description"],
                        onClick = { onSelect(template["id"] ?: "summary") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TemplateCard(
    name: String,
    description: String?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
