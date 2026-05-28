package com.airecorder.android.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchOperationBar(
    selectedCount: Int,
    onTranscribe: () -> Unit,
    onSummarize: () -> Unit,
    onDelete: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        TopAppBar(
            title = {
                Text("已选择 $selectedCount 条")
            },
            navigationIcon = {
                IconButton(onClick = onDeselectAll) {
                    Icon(Icons.Default.Close, contentDescription = "取消选择")
                }
            },
            actions = {
                IconButton(onClick = onTranscribe) {
                    Icon(Icons.Default.EditNote, contentDescription = "批量转写")
                }
                IconButton(onClick = onSummarize) {
                    Icon(Icons.Default.Summarize, contentDescription = "批量摘要")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "批量删除")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}
