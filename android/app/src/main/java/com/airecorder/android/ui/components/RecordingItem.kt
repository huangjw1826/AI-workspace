package com.airecorder.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airecorder.android.data.model.Recording
import com.airecorder.android.ui.theme.*
import com.airecorder.android.util.FormatUtils

@Composable
fun RecordingItem(
    recording: Recording,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AudioFile,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = recording.filename,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FormatUtils.formatDuration(recording.durationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    
                    Text(
                        text = FormatUtils.formatFileSize(recording.fileSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            StatusBadge(recording = recording)
        }
    }
}

@Composable
fun StatusBadge(recording: Recording) {
    val (backgroundColor, textColor, text) = when {
        recording.isCompleted -> Triple(PrimaryContainer, Primary, "✓ 已完成")
        recording.isProcessing -> Triple(StatusWarningBg, StatusWarning, "⏳ 转写中")
        recording.isError -> Triple(StatusErrorBg, StatusError, "⚠ 失败")
        else -> Triple(SurfaceVariant, TextTertiary, "--")
    }
    
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(4.dp)),
        color = backgroundColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
