package com.airecorder.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airecorder.android.util.AudioUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerBar(
    title: String,
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playbackSpeed: Float,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onChangeSpeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = onChangeSpeed,
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "${playbackSpeed}x",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = AudioUtils.formatDuration(currentPosition / 1000.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { value ->
                        onSeekTo((value * duration).toLong())
                    },
                    enabled = duration > 0,
                    modifier = Modifier.weight(1f).height(24.dp),
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            thumbSize = androidx.compose.ui.unit.DpSize(12.dp, 12.dp)
                        )
                    }
                )
                
                Text(
                    text = AudioUtils.formatDuration(duration / 1000.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRewind, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "后退10秒",
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                IconButton(onClick = onForward, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "前进10秒",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
