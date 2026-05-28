package com.airecorder.android.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    data class Paused(val progress: Float, val downloaded: Long, val total: Long) : AudioState()
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
    isTranscribing: Boolean,
    onDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteAudio: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onChangeSpeed: () -> Unit,
    onSegmentClick: (TranscriptSegment) -> Unit,
    onTranscribe: () -> Unit,
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
        modifier = modifier.fillMaxSize()
    ) {
        // Compact Audio Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when (audioState) {
                is AudioState.NotDownloaded -> {
                    CompactDownloadSection(
                        fileName = recordingDetail.recording.filename,
                        fileSize = recordingDetail.recording.fileSizeBytes ?: 0L,
                        onDownload = onDownload
                    )
                }
                is AudioState.Downloading -> {
                    CompactDownloadingSection(
                        progress = audioState.progress,
                        onPause = onPauseDownload,
                        onCancel = onCancelDownload
                    )
                }
                is AudioState.Paused -> {
                    CompactPausedSection(
                        progress = audioState.progress,
                        onResume = onDownload,
                        onCancel = onCancelDownload
                    )
                }
                is AudioState.Downloaded -> {
                    Column {
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
                        TextButton(
                            onClick = onDeleteAudio,
                            modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("删除音频", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                is AudioState.Error -> {
                    CompactErrorSection(
                        message = audioState.message,
                        onRetry = onDownload
                    )
                }
            }
        }

        // Processing Indicator for Transcription
        AnimatedVisibility(
            visible = isTranscribing,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("正在生成转写...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        // Transcript List
        if (recordingDetail.segments.isNotEmpty()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
        } else if (!isTranscribing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyContentCard(
                        icon = Icons.Default.Subtitles,
                        title = "暂无转写内容",
                        subtitle = "点击下方按钮开始转写"
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onTranscribe,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("生成转写")
                    }
                }
            }
        }
    }
}

@Composable
fun CompactDownloadSection(
    fileName: String,
    fileSize: Long,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = AudioUtils.formatFileSize(fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onDownload,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("下载", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun CompactDownloadingSection(
    progress: Float,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp
                )
                Text(
                    text = "正在下载 ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun CompactPausedSection(
    progress: Float,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = "下载暂停 ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onResume,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("继续", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun CompactErrorSection(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "下载出错",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("重试", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
