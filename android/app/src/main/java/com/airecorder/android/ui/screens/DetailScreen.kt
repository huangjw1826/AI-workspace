package com.airecorder.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airecorder.android.R
import com.airecorder.android.ui.components.ErrorState
import com.airecorder.android.ui.components.LoadingState
import com.airecorder.android.ui.theme.*
import com.airecorder.android.util.FormatUtils
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.RichText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    recordingId: String,
    viewModel: DetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
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
    val clipboardManager = LocalClipboardManager.current

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
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (uiState is DetailUiState.Success) {
                        IconButton(onClick = { viewModel.refreshRecording() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = TextPrimary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteRecording(recordingId, onNavigateBack) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.delete),
                                tint = StatusError
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    scrolledContainerColor = Surface
                )
            )
        },
        floatingActionButton = {
            if (uiState is DetailUiState.Success) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (selectedTab == 0) {
                            viewModel.transcribe(recordingId)
                        } else if (selectedTab == 1) {
                            viewModel.loadSummaryTemplates()
                        }
                    },
                    containerColor = if (selectedTab == 0) TranscribeStatus else SummarizeStatus,
                    contentColor = OnPrimary,
                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 8.dp
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = if (selectedTab == 0) Icons.Default.Subtitles else Icons.Default.AutoAwesome,
                        contentDescription = if (selectedTab == 0) stringResource(R.string.transcribe) else stringResource(R.string.summarize),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedTab == 0) stringResource(R.string.transcribe) else stringResource(R.string.summarize),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        containerColor = Background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .animateContentSize()
        ) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    LoadingState(message = stringResource(R.string.loading_recording))
                }
                is DetailUiState.Success -> {
                    val data = state.data

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Surface,
                        contentColor = Primary,
                        indicator = { tabPositions ->
                            if (selectedTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = Primary
                                )
                            }
                        },
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { viewModel.selectTab(0) },
                            text = {
                                TabLabel(
                                    icon = Icons.Default.Subtitles,
                                    label = stringResource(R.string.tab_transcript),
                                    selected = selectedTab == 0
                                )
                            },
                            selectedContentColor = Primary,
                            unselectedContentColor = TextTertiary
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { viewModel.selectTab(1) },
                            text = {
                                TabLabel(
                                    icon = Icons.Default.Description,
                                    label = stringResource(R.string.tab_summary),
                                    selected = selectedTab == 1
                                )
                            },
                            selectedContentColor = Primary,
                            unselectedContentColor = TextTertiary
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { viewModel.selectTab(2) },
                            text = {
                                TabLabel(
                                    icon = Icons.Default.Info,
                                    label = stringResource(R.string.tab_info),
                                    selected = selectedTab == 2
                                )
                            },
                            selectedContentColor = Primary,
                            unselectedContentColor = TextTertiary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (selectedTab) {
                            0 -> TranscriptContent(data, clipboardManager, isTranscribing)
                            1 -> SummaryContent(data, clipboardManager, isSummarizing)
                            2 -> InfoContent(data)
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
private fun TabLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun TranscriptContent(
    data: com.airecorder.android.data.model.RecordingDetail,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    isTranscribing: Boolean
) {
    val fullText = data.segments.joinToString("\n") {
        val timestamp = FormatUtils.formatDuration(it.startTime)
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

        if (data.segments.isEmpty() && !isTranscribing) {
            EmptyContentCard(
                icon = Icons.Default.Subtitles,
                title = stringResource(R.string.no_transcript),
                subtitle = stringResource(R.string.no_transcript_hint)
            )
        } else {
            data.segments.forEachIndexed { index, segment ->
                TranscriptSegmentCard(
                    segment = segment,
                    isFirst = index == 0
                )
            }
        }

        ActionButtons(
            hasContent = data.segments.isNotEmpty(),
            onCopy = { clipboardManager.setText(AnnotatedString(fullText)) },
            onExport = { /* TODO: Export transcript */ },
            copyText = stringResource(R.string.copy_text),
            exportText = stringResource(R.string.export_md)
        )
    }
}

@Composable
fun SummaryContent(
    data: com.airecorder.android.data.model.RecordingDetail,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    isSummarizing: Boolean
) {
    val sortedSummaries = data.summaries.sortedByDescending { summary ->
        summary.createdAt?.let { createdAt ->
            parseTimestampToMillis(createdAt)
        } ?: 0L
    }
    val latestId = sortedSummaries.firstOrNull()?.id

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProcessingIndicator(
            isProcessing = isSummarizing,
            message = stringResource(R.string.summarizing)
        )

        if (sortedSummaries.isEmpty() && !isSummarizing) {
            EmptyContentCard(
                icon = Icons.Default.Description,
                title = stringResource(R.string.no_summary),
                subtitle = stringResource(R.string.no_summary_hint)
            )
        } else {
            sortedSummaries.forEachIndexed { index, summary ->
                ExpandableSummaryCard(
                    summary = summary,
                    isLatest = summary.id == latestId,
                    isInitiallyExpanded = summary.id == latestId,
                    onCopy = { clipboardManager.setText(AnnotatedString(summary.content)) }
                )
            }
        }

        if (sortedSummaries.isNotEmpty()) {
            ActionButtons(
                hasContent = true,
                onCopy = { 
                    val allText = sortedSummaries.joinToString("\n\n") { s -> 
                        val title = getTemplateTitle(s.mode)
                        "--- $title ---\n${s.content}" 
                    }
                    clipboardManager.setText(AnnotatedString(allText)) 
                },
                onExport = { /* TODO: Export summary */ },
                copyText = "复制全部摘要",
                exportText = stringResource(R.string.export_md)
            )
        }
    }
}

@Composable
private fun ExpandableSummaryCard(
    summary: com.airecorder.android.data.model.Summary,
    isLatest: Boolean,
    isInitiallyExpanded: Boolean,
    onCopy: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(isInitiallyExpanded) }
    val rotationState by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "rotation")
    val wordCount = summary.content.length 
    val previewText = summary.content.take(60).replace("\n", " ") + "..."
    val summaryTitle = getTemplateTitle(summary.mode)

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.rotate(rotationState).size(20.dp).padding(top = 2.dp),
                        tint = TextTertiary
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = summaryTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                            if (isLatest) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = StatusInfoLight.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "最新",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusInfo,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        Text(
                            text = "${if (summary.createdAt != null) FormatUtils.formatShortDate(summary.createdAt) else "AI Generated"} · 约 $wordCount 字",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        if (!isExpanded) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = PrimaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "预览",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = previewText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    HorizontalDivider(color = DividerLight, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    RichText(modifier = Modifier.fillMaxWidth()) {
                        Markdown(summary.content)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Maps template IDs to human-readable titles.
 */
private fun getTemplateTitle(templateId: String?): String {
    return when (templateId) {
        "meeting" -> "会议纪要"
        "todo" -> "待办事项"
        "structure" -> "结构化摘要"
        "regular" -> "转写内容规整"
        else -> "AI 摘要"
    }
}

@Composable
fun InfoContent(
    data: com.airecorder.android.data.model.RecordingDetail
) {
    val recording = data.recording

    ContentCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoSection(
                title = stringResource(R.string.basic_info),
                items = listOf(
                    stringResource(R.string.filename) to recording.filename,
                    stringResource(R.string.duration) to FormatUtils.formatDuration(recording.durationSeconds),
                    stringResource(R.string.file_size) to FormatUtils.formatFileSize(recording.fileSizeBytes),
                    stringResource(R.string.format) to (recording.format ?: stringResource(R.string.unknown))
                )
            )

            InfoSection(
                title = stringResource(R.string.timing_info),
                items = listOf(
                    stringResource(R.string.created_at) to FormatUtils.formatDate(recording.createdAt),
                    stringResource(R.string.updated_at) to FormatUtils.formatDate(recording.updatedAt)
                )
            )

            InfoSection(
                title = stringResource(R.string.status_info),
                items = listOf(
                    stringResource(R.string.status) to when {
                        recording.isCompleted -> stringResource(R.string.status_completed)
                        recording.isProcessing -> stringResource(R.string.status_processing)
                        recording.isError -> stringResource(R.string.status_error)
                        else -> stringResource(R.string.status_pending)
                    }
                )
            )
        }
    }
}

@Composable
private fun ProcessingIndicator(
    isProcessing: Boolean,
    message: String
) {
    AnimatedVisibility(visible = isProcessing) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = PrimaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Primary,
                    strokeWidth = 2.dp
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Primary
                )
            }
        }
    }
}

@Composable
private fun EmptyContentCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DividerLight.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = TextTertiary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ContentCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        content()
    }
}

@Composable
private fun TranscriptSegmentCard(
    segment: com.airecorder.android.data.model.TranscriptSegment,
    isFirst: Boolean
) {
    Column(
        modifier = Modifier
            .padding(vertical = if (isFirst) 0.dp else 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = segment.speaker ?: "Speaker",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Primary
            )
            Text(
                text = FormatUtils.formatDuration(segment.startTime),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = DividerLight.copy(alpha = 0.4f),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 12.dp,
                bottomEnd = 12.dp,
                bottomStart = 12.dp
            ),
            modifier = Modifier.padding(end = 16.dp)
        ) {
            Text(
                text = segment.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier.padding(16.dp),
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun ActionButtons(
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
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Divider)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = copyText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Button(
            onClick = onExport,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = OnPrimary
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
            Text(
                text = exportText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    items: List<Pair<String, String>>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            letterSpacing = 0.5.sp
        )
        Column {
            items.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (index < items.size - 1) {
                    HorizontalDivider(
                        color = DividerLight,
                        thickness = 1.dp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryTemplateBottomSheet(
    templates: List<Map<String, String>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface,
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
private fun TemplateCard(
    name: String,
    description: String?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    color = TextSecondary
                )
            }
        }
    }
}

private fun parseTimestampToMillis(timestamp: String): Long {
    return try {
        var ts = timestamp
        if (ts.endsWith("Z")) {
            ts = ts.substring(0, ts.length - 1)
        }
        if (ts.contains(".")) {
            ts = ts.substring(0, ts.indexOf("."))
        }
        val parts = ts.split("T")
        if (parts.size == 2) {
            val dateParts = parts[0].split("-")
            val timeParts = parts[1].split(":")
            if (dateParts.size == 3 && timeParts.size >= 2) {
                val year = dateParts[0].toInt()
                val month = dateParts[1].toInt()
                val day = dateParts[2].toInt()
                val hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()
                val second = if (timeParts.size > 2) timeParts[2].toInt() else 0
                val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                calendar.set(year, month - 1, day, hour, minute, second)
                calendar.timeInMillis
            } else {
                0L
            }
        } else {
            0L
        }
    } catch (e: Exception) {
        0L
    }
}
