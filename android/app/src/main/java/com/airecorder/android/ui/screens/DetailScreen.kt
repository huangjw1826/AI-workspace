package com.airecorder.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airecorder.android.R
import com.airecorder.android.ui.components.BottomNavigationBar
import com.airecorder.android.ui.navigation.NavDestinations
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
    onNavigateToSettings: () -> Unit,
    onNavigateToHealth: () -> Unit
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
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        else -> {
                            Text(stringResource(R.string.detail_title))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is DetailUiState.Success) {
                        IconButton(onClick = {
                            viewModel.deleteRecording(recordingId, onNavigateBack)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface
                )
            )
        },
        bottomBar = {
            BottomNavigationBar(
                currentDestination = NavDestinations.Detail,
                onNavigateTo = { dest ->
                    when (dest) {
                        NavDestinations.Library -> onNavigateToLibrary()
                        NavDestinations.Settings -> onNavigateToSettings()
                        NavDestinations.Health -> onNavigateToHealth()
                        NavDestinations.Detail -> {}
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState is DetailUiState.Success) {
                FloatingActionButton(
                    onClick = { 
                        if (selectedTab == 0) {
                            viewModel.transcribe(recordingId)
                        } else if (selectedTab == 1) {
                            viewModel.loadSummaryTemplates()
                        }
                    },
                    containerColor = Primary
                ) {
                    Icon(
                        imageVector = if (selectedTab == 0) Icons.Default.Edit else Icons.Default.AutoAwesome,
                        contentDescription = if (selectedTab == 0) "Transcribe" else "Summarize"
                    )
                }
            }
        },
        containerColor = Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is DetailUiState.Success -> {
                    val data = state.data
                    
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Surface
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { viewModel.selectTab(0) },
                            text = { Text(stringResource(R.string.tab_transcript)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { viewModel.selectTab(1) },
                            text = { Text(stringResource(R.string.tab_summary)) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { viewModel.selectTab(2) },
                            text = { Text(stringResource(R.string.tab_info)) }
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadRecording(recordingId) }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showSummaryTemplates) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissSummaryTemplates() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_summary_template),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                summaryTemplates.forEach { template ->
                    Card(
                        onClick = {
                            viewModel.summarize(recordingId, template["id"] ?: "summary")
                            viewModel.dismissSummaryTemplates()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = template["name"] ?: "",
                                style = MaterialTheme.typography.titleMedium
                            )
                            template["description"]?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
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
            .padding(16.dp)
    ) {
        if (isTranscribing) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.transcribing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Primary
                    )
                }
            }
        }
        
        if (data.segments.isEmpty() && !isTranscribing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_transcript),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }
        } else {
            data.segments.forEach { segment ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = segment.speaker ?: "Speaker",
                            style = MaterialTheme.typography.labelLarge,
                            color = Primary
                        )
                        Text(
                            text = FormatUtils.formatDuration(segment.startTime),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = SurfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
                        modifier = Modifier.padding(end = 24.dp)
                    ) {
                        Text(
                            text = segment.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(fullText))
                },
                modifier = Modifier.weight(1f),
                enabled = data.segments.isNotEmpty(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.copy_text))
            }
            
            Button(
                onClick = { /* TODO: Export transcript */ },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = data.segments.isNotEmpty(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.export_md))
            }
        }
    }
}

@Composable
fun SummaryContent(
    data: com.airecorder.android.data.model.RecordingDetail,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    isSummarizing: Boolean
) {
    val summaryText = data.summaries.firstOrNull()?.content ?: stringResource(R.string.no_summary)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isSummarizing) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.summarizing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Primary
                    )
                }
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceVariant)
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                if (data.summaries.isEmpty()) {
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                } else {
                    RichText(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Markdown(summaryText)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(summaryText))
                },
                modifier = Modifier.weight(1f),
                enabled = data.summaries.isNotEmpty(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.copy_text))
            }
            
            Button(
                onClick = { /* TODO: Export summary */ },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = data.summaries.isNotEmpty(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.export_md))
            }
        }
    }
}

@Composable
fun InfoContent(
    data: com.airecorder.android.data.model.RecordingDetail
) {
    val recording = data.recording
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            InfoItem("文件名", recording.filename)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            InfoItem("时长", FormatUtils.formatDuration(recording.durationSeconds))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            InfoItem("文件大小", FormatUtils.formatFileSize(recording.fileSizeBytes))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            InfoItem("格式", recording.format ?: "未知")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            InfoItem("创建时间", FormatUtils.formatDate(recording.createdAt))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            InfoItem("状态", when {
                recording.isCompleted -> "✓ 已完成"
                recording.isProcessing -> "⏳ 转写中"
                recording.isError -> "⚠ 失败"
                else -> "--"
            })
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}
