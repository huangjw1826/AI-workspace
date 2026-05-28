package com.airecorder.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airecorder.android.data.model.Recording
import com.airecorder.android.ui.theme.*
import com.airecorder.android.util.FormatUtils

@Composable
fun RecordingItem(
    recording: Recording,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Surface
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = if (isSelectionMode) onLongClick else onClick,
        onLongClick = onLongClick,
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                RadioButton(
                    selected = isSelected,
                    onClick = onLongClick,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    PrimaryContainer
                }
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = OnPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = recording.filename,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            OnSurface
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = recording.statusColor.copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = recording.statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = recording.statusColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = FormatUtils.formatDuration(recording.durationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            TextSecondary
                        },
                        fontWeight = FontWeight.Medium
                    )
                    
                    Surface(
                        modifier = Modifier.size(4.dp),
                        shape = CircleShape,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                        } else {
                            Outline
                        }
                    ) {}
                    
                    Text(
                        text = FormatUtils.formatFileSize(recording.fileSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        } else {
                            TextTertiary
                        }
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    recording.sourceType?.let { source ->
                        val sourceLabel = when (source) {
                            "upload" -> "上传"
                            "watch" -> "目录监控"
                            else -> source
                        }
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                            } else {
                                TextTertiary
                            }
                        )
                    }
                }
                
                if (recording.status == "error") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "处理失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = Error
                    )
                }
            }
        }
    }
}
