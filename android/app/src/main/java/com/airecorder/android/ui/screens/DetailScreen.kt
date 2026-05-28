package com.airecorder.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
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

@OptIn(ExperimentalMaterial3Api::class)
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
        pageCount = { 5 }
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
            DownloadProgressIndicator(
                downloadState = audioDownloadState,
                onCancel = { viewModel.cancelDownload() },
                onRetry = { viewModel.retryDownload() }
            )
            
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.pagerTabIndicatorOffset(tabPositions, selectedTab),
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
                    title = "播放",
                    icon = Icons.Default.Refresh
                )
                TabItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    title = "转写",
                    icon = Icons.Default.Subtitles
                )
                TabItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    title = "摘要",
                    icon = Icons.Default.Description
                )
                TabItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    title = "信息",
                    icon = Icons.Default.Info
                )
                TabItem(
                    selected = selectedTab == 4,
                    onClick = { viewModel.selectTab(4) },
                    title = "任务",
                    icon = Icons.Default.Description
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
                                    val (currentPos, duration, isPlaying, isBuffering) = when (val playback = audioPlaybackState) {
                                        is AudioPlaybackState.Playing -> Triple(playback.positionMs, playback.durationMs, true, false)
                                        is AudioPlaybackState.Paused -> Triple(playback.positionMs, playback.durationMs, false, false)
                                        is AudioPlaybackState.Buffering -> Triple(0L, 0L, false, true)
                                        else -> Triple(0L, 0L, false, false)
                                    }
                                    
                                    val playTabDownloadState = when (audioDownloadState) {
                                        is AudioDownloadState.Downloading -> com.airecorder.android.ui.screens.detail.AudioDownloadState.Downloading(
                                            (audioDownloadState as AudioDownloadState.Downloading).progress,
                                            (audioDownloadState as AudioDownloadState.Downloading).downloaded,
                                            (audioDownloadState as AudioDownloadState.Downloading).total
                                        )
                                        is AudioDownloadState.Downloaded -> com.airecorder.android.ui.screens.detail.AudioDownloadState.Downloaded
                                        is AudioDownloadState.Error -> com.airecorder.android.ui.screens.detail.AudioDownloadState.Error(
                                            (audioDownloadState as AudioDownloadState.Error).message
                                        )
                                        else -> com.airecorder.android.ui.screens.detail.AudioDownloadState.NotDownloaded
                                    }
                                    
                                    PlayTab(
                                        recordingDetail = state.data,
                                        audioState = playTabDownloadState,
                                        isPlaying = isPlaying,
                                        isBuffering = isBuffering,
                                        currentPosition = currentPos,
                                        duration = duration,
                                        playbackSpeed = playbackSpeed,
                                        currentSegmentIndex = currentSegmentIndex,
                                        onDownload = { viewModel.startDownload() },
                                        onPlayPause = { viewModel.togglePlayPause() },
                                        onSeekTo = { viewModel.seekTo(it) },
                                        onRewind = { viewModel.rewind() },
                                        onForward = { viewModel.forward() },
                                        onChangeSpeed = { viewModel.togglePlaybackSpeed() },
                                        onSegmentClick = { segment ->
                                            segment.startTime?.let { viewModel.jumpToSegment(it) }
                                        }
                                    )
                                }
                                1 -> {
                                    TranscriptTab(
                                        recordingDetail = state.data,
                                        isTranscribing = isTranscribing,
                                        onTranscribe = { viewModel.transcribe(recordingId) }
                                    )
                                }
                                2 -> {
                                    SummaryTab(
                                        summaries = state.data.summaries,
                                        isSummarizing = isSummarizing,
                                        onSummaryClick = { summary -> onNavigateToSummaryDetail(summary) },
                                        onGenerateNew = { viewModel.loadSummaryTemplates() }
                                    )
                                }
                                3 -> {
                                    InfoTab(recording = state.data.recording)
                                }
                                4 -> {
                                    TaskTab(tasks = state.data.tasks)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        },
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun DownloadProgressIndicator(
    downloadState: AudioDownloadState,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    if (downloadState is AudioDownloadState.Downloading) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "下载进度 ${(downloadState.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = AudioUtils.formatProgress(downloadState.downloaded, downloadState.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                
                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

@Composable
fun TranscriptTab(
    recordingDetail: com.airecorder.android.data.model.RecordingDetail,
    isTranscribing: Boolean,
    onTranscribe: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val fullText = recordingDetail.segments.joinToString("\n") {
        val timestamp = FormatUtils.formatDuration(it.startTime ?: 0.0)
        "[$timestamp] ${it.speaker ?: "Speaker"}: ${it.text}"
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProcessingIndicator(
            isProcessing = isTranscribing,
            message = stringResource(R.string.transcribing)
        )
        
        if (recordingDetail.segments.isEmpty() && !isTranscribing) {
            EmptyContentCard(
                icon = Icons.Default.Subtitles,
                title = stringResource(R.string.no_transcript),
                subtitle = stringResource(R.string.no_transcript_hint)
            )
            
            Button(
                onClick = onTranscribe,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("生成转写")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = recordingDetail.segments,
                    key = { it.id ?: it.hashCode() }
                ) { segment ->
                    TranscriptSegmentItem(
                        segment = segment,
                        isPlaying = false,
                        isPlayed = false,
                        onClick = {}
                    )
                }
            }
            
            ActionButtons(
                hasContent = recordingDetail.segments.isNotEmpty(),
                onCopy = { clipboardManager.setText(AnnotatedString(fullText)) },
                onExport = { /* TODO: Export transcript */ },
                copyText = stringResource(R.string.copy_text),
                exportText = stringResource(R.string.export_md)
            )
        }
    }
}

@Composable
fun ProcessingIndicator(
    isProcessing: Boolean,
    message: String
) {
    AnimatedVisibility(visible = isProcessing) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun ActionButtons(
    hasContent: Boolean,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    copyText: String,
    exportText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onCopy,
            modifier = Modifier.weight(1f),
            enabled = hasContent,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(copyText)
        }
        
        Button(
            onClick = onExport,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            enabled = hasContent,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(exportText)
        }
    }
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
