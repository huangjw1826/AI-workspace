package com.airecorder.android.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airecorder.android.R
import com.airecorder.android.ui.components.EmptyState
import com.airecorder.android.ui.components.ErrorState
import com.airecorder.android.ui.components.LoadingState
import com.airecorder.android.ui.components.RecordingItem
import com.airecorder.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToDetail: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadRecordings()
    }

    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.library_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    scrolledContainerColor = Surface
                )
            )
        },
        containerColor = Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // 已经由外层 Scaffold 处理了 padding
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .animateContentSize()
        ) {
            // Search UI
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text(stringResource(R.string.library_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Surface,
                    focusedContainerColor = Surface,
                    unfocusedBorderColor = DividerLight,
                    focusedBorderColor = Primary
                ),
                singleLine = true
            )

            when (val state = uiState) {
                is LibraryUiState.Loading -> {
                    LoadingState(message = stringResource(R.string.loading_recordings))
                }
                is LibraryUiState.Success -> {
                    if (state.recordings.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.library_empty),
                            subtitle = stringResource(R.string.library_empty_subtitle)
                        )
                    } else {
                        RecordingList(
                            recordings = state.recordings,
                            onItemClick = onNavigateToDetail
                        )
                    }
                }
                is LibraryUiState.Error -> {
                    ErrorState(
                        error = state.message,
                        onRetry = { viewModel.refresh() }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingList(
    recordings: List<com.airecorder.android.data.model.Recording>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            items = recordings,
            key = { it.id }
        ) { recording ->
            RecordingItem(
                recording = recording,
                onClick = { onItemClick(recording.id) }
            )
        }
    }
}
