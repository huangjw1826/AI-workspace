package com.airecorder.android.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airecorder.android.data.model.Summary
import com.airecorder.android.ui.components.EmptyContentCard
import com.airecorder.android.ui.components.SummaryListItem
import com.airecorder.android.util.AudioUtils

@Composable
fun SummaryTab(
    summaries: List<Summary>,
    isSummarizing: Boolean,
    onSummaryClick: (Summary) -> Unit,
    onGenerateNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedSummaries = summaries.sortedByDescending { 
        it.createdAt?.let { timestamp ->
            parseTimestampToMillis(timestamp)
        } ?: 0L
    }
    val latestId = sortedSummaries.firstOrNull()?.id
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (sortedSummaries.isEmpty() && !isSummarizing) {
            EmptyState(
                modifier = Modifier.align(Alignment.Center),
                onGenerateNew = onGenerateNew
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSummarizing) {
                    item {
                        ProcessingBanner()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                items(
                    items = sortedSummaries,
                    key = { it.id ?: it.hashCode() }
                ) { summary ->
                    SummaryListItem(
                        summary = summary,
                        isLatest = summary.id == latestId,
                        onClick = { onSummaryClick(summary) }
                    )
                }
            }
        }
        
        FloatingActionButton(
            onClick = onGenerateNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "生成新摘要"
            )
        }
    }
}

@Composable
fun EmptyState(
    onGenerateNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EmptyContentCard(
            icon = Icons.Default.AutoAwesome,
            title = "暂无摘要",
            subtitle = "点击下方按钮生成音频摘要"
        )
        
        Button(
            onClick = onGenerateNew,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("生成摘要")
        }
    }
}

@Composable
fun ProcessingBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
            Text(
                text = "正在生成摘要...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
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
