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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airecorder.android.R
import com.airecorder.android.data.model.Summary
import com.airecorder.android.ui.animation.SharedElementState
import com.airecorder.android.ui.components.*
import com.airecorder.android.ui.screens.detail.*
import com.airecorder.android.ui.theme.*
import com.airecorder.android.ui.util.rememberHapticFeedback
import com.airecorder.android.util.ErrorUtils
import com.airecorder.android.util.FormatUtils
import kotlinx.coroutines.launch

/**
 * 录音详情页 — 沉浸式阅读
 * Tab 切换：内容 | 摘要 | 信息
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    recordingId: String,
    viewModel: DetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToSummaryDetail: (Summary) -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSettings: () -> Unit,
    sharedElementState: SharedElementState? = null
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
    val hapticFeedback = rememberHapticFeedback()

    LaunchedEffect(uiState) {
        if (uiState is DetailUiState.Success && sharedElementState?.isTransitioning == true) {
            kotlinx.coroutines.delay(300)
            sharedElementState.endTransition()
        }
    }

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.scrollToPage(selectedTab)
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
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                        else -> {
                            Text(
                                text = "录音详情",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = OnSurface
                        )
                    }
                },
                actions = {
                    if (uiState is DetailUiState.Success) {
                        IconButton(onClick = {
                            hapticFeedback.performClick()
                            viewModel.refreshRecording()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = {
                                hapticFeedback.performConfirm()
                                viewModel.deleteRecording(recordingId, onNavigateBack)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = Error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    scrolledContainerColor = Surface
                )
            )
        },
        containerColor = Background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab 栏
            Surface(
                color = Surface,
                shadowElevation = 1.dp
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Surface,
                    contentColor = Primary,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            Box(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[selectedTab])
                                    .padding(horizontal = 24.dp)
                                    .height(3.dp)
                                    .background(Primary, RoundedCornerShape(2.dp))
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = Outline, thickness = 0.5.dp)
                    }
                ) {
                    DetailTab(
                        selected = selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        title = "内容",
                        icon = Icons.Default.Audiotrack
                    )
                    DetailTab(
                        selected = selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        title = "摘要",
                        icon = Icons.Default.AutoAwesome
                    )
                    DetailTab(
                        selected = selectedTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        title = "信息",
                        icon = Icons.Default.Info
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val state = uiState) {
                    is DetailUiState.Loading -> {
                        SkeletonLoading()
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
                                            ds.progress, ds.downloaded, ds.total
                                        )
                                        is AudioDownloadState.Paused -> AudioState.Paused(
                                            ds.progress, ds.downloaded, ds.total
                                        )
                                        is AudioDownloadState.Downloaded -> AudioState.Downloaded
                                        is AudioDownloadState.Error -> AudioState.Error(ds.message)
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
                            error = ErrorUtils.getFriendlyErrorMessage(state.message),
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
private fun DetailTab(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        selectedContentColor = Primary,
        unselectedContentColor = TextTertiary
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
        containerColor = Surface,
        shape = BottomSheetShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "选择摘要模板",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
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
        shape = SmallCardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
