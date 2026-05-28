package com.airecorder.android.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airecorder.android.data.model.RecordingDetail
import com.airecorder.android.data.model.TranscriptSegment
import com.airecorder.android.ui.components.AudioPlayerBar
import com.airecorder.android.ui.components.EmptyContentCard
import com.airecorder.android.ui.components.TranscriptSegmentItem
import com.airecorder.android.util.AudioUtils
import kotlinx.coroutines.launch

sealed class AudioState {
    object NotDownloaded : AudioState()
    data class Downloading(val progress: Float, val downloaded: Long, val total: Long) : AudioState()
    object Downloaded : AudioState()
    data class Error(val message: String) : AudioState()
}

@Composable
fun PlayTab(
    recordingDetail: RecordingDetail,
    audioState: AudioState,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    currentSegmentIndex: Int,
    onDownload: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onChangeSpeed: () -> Unit,
    onSegmentClick: (TranscriptSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(currentSegmentIndex) {
        if (currentSegmentIndex >= 0 && currentSegmentIndex < recordingDetail.segments.size) {
            scope.launch {
                lazyListState.animateScrollToItem(currentSegmentIndex)
            }
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (audioState) {
            is AudioState.NotDownloaded -> {
                DownloadSection(
                    fileName = recordingDetail.recording.filename,
                    fileSize = recordingDetail.recording.fileSizeBytes ?: 0L,
                    onDownload = onDownload
                )
            }
            is AudioState.Downloading -> {
                DownloadingSection(
                    fileName = recordingDetail.recording.filename,
                    progress = audioState.progress,
                    downloaded = audioState.downloaded,
                    total = audioState.total
                )
            }
            is AudioState.Downloaded -> {
                AudioPlayerBar(
                    title = recordingDetail.recording.filename,
                    currentPosition = currentPosition,
                    duration = duration,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    playbackSpeed = playbackSpeed,
                    onPlayPause = onPlayPause,
                    onSeekTo = onSeekTo,
                    onRewind = onRewind,
                    onForward = onForward,
                    onChangeSpeed = onChangeSpeed
                )
            }
            is AudioState.Error -> {
                ErrorSection(
                    message = audioState.message,
                    onRetry = onDownload
                )
            }
        }
        
        if (recordingDetail.segments.isNotEmpty()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = recordingDetail.segments,
                    key = { it.id ?: it.hashCode() }
                ) { segment ->
                    val index = recordingDetail.segments.indexOf(segment)
                    TranscriptSegmentItem(
                        segment = segment,
                        isPlaying = index == currentSegmentIndex,
                        isPlayed = index < currentSegmentIndex,
                        onClick = { onSegmentClick(segment) }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyContentCard(
                    icon = Icons.Default.Download,
                    title = "暂无转写内容",
                    subtitle = "请先生成转写"
                )
            }
        }
    }
}

@Composable
fun DownloadSection(
    fileName: String,
    fileSize: Long,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = AudioUtils.formatFileSize(fileSize),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("下载音频")
            }
        }
    }
}

@Composable
fun DownloadingSection(
    fileName: String,
    progress: Float,
    downloaded: Long,
    total: Long
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(64.dp),
                strokeWidth = 4.dp
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "下载中...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${(progress * 100).toInt()}% · ${AudioUtils.formatProgress(downloaded, total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    }
}

@Composable
fun ErrorSection(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "下载失败",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorVariant
                )
            }
            
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Text("重试")
            }
        }
    }
}
