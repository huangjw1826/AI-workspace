package com.airecorder.android.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airecorder.android.R
import com.airecorder.android.ui.components.BottomNavigationBar
import com.airecorder.android.ui.components.RecordingItem
import com.airecorder.android.ui.navigation.NavDestinations
import com.airecorder.android.ui.theme.Background
import com.airecorder.android.ui.theme.Primary
import com.airecorder.android.ui.theme.Surface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHealth: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadRecordings()
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var searchFieldValue by remember { mutableStateOf(TextFieldValue(searchQuery)) }
    var showUploadSheet by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface
                ),
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                currentDestination = NavDestinations.Library,
                onNavigateTo = { dest ->
                    when (dest) {
                        NavDestinations.Settings -> onNavigateToSettings()
                        NavDestinations.Health -> onNavigateToHealth()
                        NavDestinations.Library -> {}
                        NavDestinations.Detail -> {}
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showUploadSheet = true },
                containerColor = Primary
            ) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = stringResource(R.string.upload_select_more),
                    tint = Surface
                )
            }
        },
        containerColor = Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = searchFieldValue,
                onValueChange = { 
                    searchFieldValue = it
                    viewModel.onSearchQueryChanged(it.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp)),
                placeholder = { Text(stringResource(R.string.library_search)) },
                leadingIcon = { 
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface
                )
            )
            
            when (val state = uiState) {
                is LibraryUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is LibraryUiState.Success -> {
                    if (state.recordings.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.library_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = state.recordings,
                                key = { it.id }
                            ) { recording ->
                                RecordingItem(
                                    recording = recording,
                                    onClick = { onNavigateToDetail(recording.id) }
                                )
                            }
                        }
                    }
                }
                is LibraryUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.refresh() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showUploadSheet) {
        UploadBottomSheet(
            onDismiss = { showUploadSheet = false }
        )
    }
}
