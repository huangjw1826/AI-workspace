package com.airecorder.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airecorder.android.ui.theme.*

/**
 * 上传底部 Sheet — 对齐 PRD v3 设计
 * 支持多文件选择、串行上传、进度条、失败重试
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadBottomSheet(
    onDismiss: () -> Unit
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val uploadQueue by viewModel.uploadQueue.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val files = uris.mapNotNull { uri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val fileName = context.contentResolver.query(uri, null, null, null, null)
                        ?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            cursor.moveToFirst()
                            cursor.getString(nameIndex)
                        } ?: "unknown_audio_file"

                    val tempFile = java.io.File(context.cacheDir, fileName).apply {
                        outputStream().use { output ->
                            inputStream?.copyTo(output)
                        }
                    }
                    tempFile
                } catch (_: Exception) {
                    null
                }
            }
            viewModel.addToUploadQueue(files)
            viewModel.uploadNext()
        }
    }

    fun selectFiles() {
        filePickerLauncher.launch(arrayOf("audio/*"))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = BottomSheetShape,
        containerColor = Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // 拖拽指示条
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Outline, RoundedCornerShape(2.dp))
            )

            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = PrimaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "上传队列",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        if (uploadQueue.isNotEmpty()) {
                            val completed = uploadQueue.count { it.status == UploadStatus.Success }
                            Text(
                                text = "$completed/${uploadQueue.size} 个已完成",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
                if (uploadQueue.any { it.status != UploadStatus.Uploading }) {
                    TextButton(onClick = { viewModel.clearCompletedUploads() }) {
                        Text("清除已完成", color = TextTertiary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 空状态 / 上传列表
            if (uploadQueue.isEmpty()) {
                UploadEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uploadQueue, key = { it.id }) { item ->
                        UploadItemCard(
                            item = item,
                            onRemove = { viewModel.removeFromUploadQueue(item.id) },
                            onRetry = { viewModel.retryUpload(item.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 选择文件按钮
            Button(
                onClick = { selectFiles() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uploadQueue.isEmpty()) "选择音频文件" else "添加更多文件",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun UploadEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = PrimaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = Primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "支持 wav, mp3, m4a, flac, aac, ogg",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
        Text(
            text = "单文件最大 500 MB",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun UploadItemCard(
    item: UploadItem,
    onRemove: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SmallCardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文件图标
            val iconTint = when (item.status) {
                UploadStatus.Success -> HealthGreen
                UploadStatus.Error -> Error
                UploadStatus.Uploading -> Primary
                else -> TextTertiary
            }
            Surface(
                color = iconTint.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 状态行
                when (item.status) {
                    UploadStatus.Waiting -> {
                        Text("等待中", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }
                    UploadStatus.Uploading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp),
                                color = Primary,
                                trackColor = Primary.copy(alpha = 0.1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${(item.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    UploadStatus.Success -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = HealthGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("已完成", style = MaterialTheme.typography.labelSmall, color = HealthGreen)
                        }
                    }
                    UploadStatus.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = Error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("上传失败", style = MaterialTheme.typography.labelSmall, color = Error)
                        }
                    }
                }
            }

            // 操作按钮
            when (item.status) {
                UploadStatus.Error -> {
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重试",
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                UploadStatus.Waiting, UploadStatus.Success -> {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "移除",
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                else -> {} // Uploading 状态不显示按钮
            }
        }
    }
}
