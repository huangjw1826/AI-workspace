package com.airecorder.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
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

@Composable
fun SimpleFlowLayout(
    modifier: Modifier = Modifier,
    horizontalSpacing: Int = 8,
    verticalSpacing: Int = 8,
    content: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        
        val horizontalSpacingPx = horizontalSpacing.dp.roundToPx()
        val verticalSpacingPx = verticalSpacing.dp.roundToPx()

        placeables.forEach { placeable ->
            if (currentRowWidth + placeable.width > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + horizontalSpacingPx
        }
        rows.add(currentRow)

        val height = rows.sumOf { row -> row.maxOf { it.height } } + (rows.size - 1) * verticalSpacingPx
        val width = constraints.maxWidth

        layout(width, height) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOf { it.height }
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + horizontalSpacingPx
                }
                y += rowHeight + verticalSpacingPx
            }
        }
    }
}

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
        SimpleFlowLayout(
            modifier = Modifier.fillMaxWidth(),
            horizontalSpacing = 8,
            verticalSpacing = 4
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
