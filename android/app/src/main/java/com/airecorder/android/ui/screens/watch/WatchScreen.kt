package com.airecorder.android.ui.screens.watch

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airecorder.android.data.model.WatchEvent
import com.airecorder.android.ui.components.ErrorState
import com.airecorder.android.ui.components.LoadingIndicator
import com.airecorder.android.ui.theme.*
import com.airecorder.android.util.ErrorUtils
import com.airecorder.android.util.FormatUtils
import com.airecorder.android.ui.util.rememberHapticFeedback

/**
 * 目录监控页面 — 从设置页进入
 * 显示文件系统变动事件列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    viewModel: WatchViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val hapticFeedback = rememberHapticFeedback()
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "目录监控",
                        style = TopBarTitleStyle,
                        color = TextPrimary
                    )
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
                    IconButton(
                        onClick = {
                            hapticFeedback.performClick()
                            viewModel.refresh()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = TextSecondary
                        )
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is WatchUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(size = 40)
                    }
                }
                is WatchUiState.Error -> {
                    ErrorState(
                        error = ErrorUtils.getFriendlyErrorMessage(state.message),
                        onRetry = {
                            hapticFeedback.performClick()
                            viewModel.refresh()
                        }
                    )
                }
                is WatchUiState.Success -> {
                    if (state.events.isEmpty()) {
                        WatchEmptyState()
                    } else {
                        WatchEventList(
                            events = state.events,
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.refresh() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = SurfaceVariant,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "暂无监控事件",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "当监控目录有文件变动时将显示在这里",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun WatchEventList(
    events: List<WatchEvent>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(events, key = { it.id }) { event ->
                WatchEventItem(
                    event = event,
                    modifier = Modifier.animateItem(placementSpec = tween(300))
                )
            }
        }
    }
}

@Composable
fun WatchEventItem(
    event: WatchEvent,
    modifier: Modifier = Modifier
) {
    val statusColor = when (event.status) {
        "added" -> Primary
        "processed" -> HealthGreen
        "error" -> Error
        "duplicate" -> TextSecondary
        else -> TextTertiary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (event.status) {
                                    "added" -> Icons.Default.AddCircle
                                    "processed" -> Icons.Default.CheckCircle
                                    "error" -> Icons.Default.Error
                                    "duplicate" -> Icons.Default.FileCopy
                                    else -> Icons.Default.Info
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = event.statusLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
                Text(
                    text = FormatUtils.formatDateTime(event.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            Text(
                text = event.filename,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (event.filePath.isNotBlank()) {
                Text(
                    text = event.filePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (event.fileSize != null) {
                Text(
                    text = FormatUtils.formatFileSize(event.fileSize.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            event.reason?.let { reason ->
                Surface(
                    color = when (event.status) {
                        "error" -> ErrorContainer
                        else -> SecondaryContainer
                    },
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (event.status) {
                            "error" -> Error
                            else -> TextSecondary
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private val WatchEvent.statusLabel: String
    get() = when (status) {
        "added" -> "已添加"
        "processed" -> "已处理"
        "error" -> "处理失败"
        "duplicate" -> "重复文件"
        else -> status
    }
