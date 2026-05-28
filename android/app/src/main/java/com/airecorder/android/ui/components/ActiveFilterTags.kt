package com.airecorder.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveFilterTags(
    selectedStatuses: Set<String>,
    selectedSource: String?,
    onRemoveStatus: (String) -> Unit,
    onRemoveSource: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActiveFilters = selectedStatuses.isNotEmpty() || selectedSource != null
    
    if (!hasActiveFilters) return
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            selectedStatuses.forEach { status ->
                val label = statusFilters.find { it.id == status }?.label ?: status
                InputChip(
                    selected = true,
                    onClick = { onRemoveStatus(status) },
                    label = { Text(label) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "移除筛选",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            
            selectedSource?.let { source ->
                val label = sourceFilters.find { it.id == source }?.label ?: source
                InputChip(
                    selected = true,
                    onClick = { onRemoveSource() },
                    label = { Text(label) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "移除筛选",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
        
        TextButton(onClick = onClearAll) {
            Text("清除全部")
        }
    }
}
