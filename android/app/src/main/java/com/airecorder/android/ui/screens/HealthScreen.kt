package com.airecorder.android.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.airecorder.android.R
import com.airecorder.android.data.model.HealthResponse
import com.airecorder.android.data.repository.SettingsRepository
import com.airecorder.android.ui.components.BottomNavigationBar
import com.airecorder.android.ui.navigation.NavDestinations
import com.airecorder.android.ui.theme.*
import com.airecorder.android.util.FormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HealthUiState {
    object Loading : HealthUiState()
    data class Success(val data: HealthResponse) : HealthUiState()
    data class Error(val message: String) : HealthUiState()
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val repository: SettingsRepository
) : androidx.lifecycle.ViewModel() {
    
    private val _uiState = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()
    
    fun loadHealth() {
        viewModelScope.launch {
            _uiState.value = HealthUiState.Loading
            repository.getHealth().fold(
                onSuccess = { data ->
                    _uiState.value = HealthUiState.Success(data)
                },
                onFailure = { exception ->
                    _uiState.value = HealthUiState.Error(exception.message ?: "Unknown error")
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    viewModel: HealthViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadHealth()
    }
    
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.health_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadHealth() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface
                )
            )
        },
        bottomBar = {
            BottomNavigationBar(
                currentDestination = NavDestinations.Health,
                onNavigateTo = { dest ->
                    when (dest) {
                        NavDestinations.Library -> onNavigateToLibrary()
                        NavDestinations.Settings -> onNavigateToSettings()
                        NavDestinations.Health -> {}
                        NavDestinations.Detail -> {}
                    }
                }
            )
        },
        containerColor = Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            when (val state = uiState) {
                is HealthUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is HealthUiState.Success -> {
                    HealthContent(state.data)
                }
                is HealthUiState.Error -> {
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
                            Button(onClick = { viewModel.loadHealth() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthContent(data: HealthResponse) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        SectionHeader(stringResource(R.string.service_status))
        
        HealthCard {
            StatusItem(
                title = stringResource(R.string.fastapi_backend),
                isGood = data.status == "ok",
                statusText = if (data.status == "ok") stringResource(R.string.status_ok) else stringResource(R.string.status_error)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            StatusItem(
                title = stringResource(R.string.cloudflare_tunnel),
                isGood = data.tunnel?.connected == true,
                statusText = if (data.tunnel?.connected == true) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected)
            )
        }
        
        SectionHeader(stringResource(R.string.asr_engine))
        
        HealthCard {
            StatusItem(
                title = stringResource(R.string.funasr_model),
                isGood = data.funasr,
                statusText = if (data.funasr) stringResource(R.string.status_loaded) else stringResource(R.string.status_error)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            StatusItem(
                title = stringResource(R.string.ffmpeg),
                isGood = data.ffmpeg,
                statusText = if (data.ffmpeg) stringResource(R.string.status_installed) else stringResource(R.string.status_error)
            )
        }
        
        SectionHeader(stringResource(R.string.llm_config_health))
        
        HealthCard {
            StatusItem(
                title = stringResource(R.string.api_connection),
                isGood = data.llmConfigured,
                statusText = if (data.llmConfigured) stringResource(R.string.status_ok) else stringResource(R.string.status_error)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            HealthInfoItem(
                title = stringResource(R.string.current_model),
                value = data.llmModel ?: "N/A"
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = TextSecondary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun HealthCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun StatusItem(
    title: String,
    isGood: Boolean,
    statusText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(isGood = isGood)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isGood) HealthGreen else StatusError
        )
    }
}

@Composable
fun StatusDot(isGood: Boolean) {
    val color = if (isGood) HealthGreen else StatusError
    Canvas(
        modifier = Modifier.size(8.dp)
    ) {
        drawCircle(
            color = color,
            radius = 4.dp.toPx()
        )
    }
}

@Composable
fun HealthInfoItem(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}
