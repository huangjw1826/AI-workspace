package com.airecorder.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class FilterOption(
    val id: String,
    val label: String
)

val statusFilters = listOf(
    FilterOption("uploaded", "待转写"),
    FilterOption("queued", "排队中"),
    FilterOption("normalizing", "处理中"),
    FilterOption("transcribing", "转写中"),
    FilterOption("transcribed", "已转写"),
    FilterOption("completed", "已摘要"),
    FilterOption("cancelled", "已取消"),
    FilterOption("error", "错误")
)

val sourceFilters = listOf(
    FilterOption("upload", "上传"),
    FilterOption("watch", "目录监控")
)

enum class SortOption(val id: String, val label: String) {
    NEWEST("newest", "最新优先"),
    OLDEST("oldest", "最旧优先"),
    LONGEST("longest", "时长最长"),
    LARGEST("largest", "文件最大")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterChips(
    selectedStatuses: Set<String>,
    onStatusToggle: (String) -> Unit,
    selectedSource: String?,
    onSourceSelect: (String?) -> Unit,
    sortOption: SortOption,
    onSortSelect: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            statusFilters.forEach { filter ->
                FilterChip(
                    selected = filter.id in selectedStatuses,
                    onClick = { onStatusToggle(filter.id) },
                    label = { Text(filter.label) }
                )
            }
            
            sourceFilters.forEach { filter ->
                FilterChip(
                    selected = selectedSource == filter.id,
                    onClick = { 
                        if (selectedSource == filter.id) {
                            onSourceSelect(null)
                        } else {
                            onSourceSelect(filter.id)
                        }
                    },
                    label = { Text(filter.label) }
                )
            }
            
            SortDropdown(
                sortOption = sortOption,
                onSortSelect = onSortSelect
            )
        }
    }
}

@Composable
fun SortDropdown(
    sortOption: SortOption,
    onSortSelect: (SortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(sortOption.label)
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSortSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
