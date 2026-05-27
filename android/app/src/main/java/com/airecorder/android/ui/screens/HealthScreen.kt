package com.airecorder.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.airecorder.android.R
import com.airecorder.android.data.model.HealthResponse
import com.airecorder.android.data.repository.SettingsRepository
import com.airecorder.android.ui.components.BottomNavigationBar
import com.airecorder.android.ui.components.ErrorState
import com.airecorder.android.ui.components.LoadingState
import com.airecorder.android.ui.navigation.NavDestinations
import com.airecorder.android.ui.theme.*
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
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.health_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadHealth() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    scrolledContainerColor = Surface
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
        containerColor = Background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            when (val state = uiState) {
                is HealthUiState.Loading -> {
                    LoadingState(message = stringResource(R.string.loading_health))
                }
                is HealthUiState.Success -> {
                    HealthContent(state.data)
                }
                is HealthUiState.Error -> {
                    ErrorState(
                        error = state.message,
                        onRetry = { viewModel.loadHealth() }
                    )
                }
            }
        }
    }
}

@Composable
fun HealthContent(data: HealthResponse) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HealthStatusCard(
            isOnline = data.status == "ok",
            tunnelConnected = data.tunnel?.connected == true
        )

        SectionCard(
            title = stringResource(R.string.service_status),
            icon = Icons.Default.Dns
        ) {
            StatusItem(
                title = stringResource(R.string.fastapi_backend),
                isGood = data.status == "ok",
                statusText = if (data.status == "ok") stringResource(R.string.status_ok) else stringResource(R.string.status_error),
                isLast = false
            )
            StatusItem(
                title = stringResource(R.string.cloudflare_tunnel),
                isGood = data.tunnel?.connected == true,
                statusText = if (data.tunnel?.connected == true) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected),
                isLast = true
            )
        }

        SectionCard(
            title = stringResource(R.string.asr_engine),
            icon = Icons.Default.Speaker
        ) {
            StatusItem(
                title = stringResource(R.string.funasr_model),
                isGood = data.funasr,
                statusText = if (data.funasr) stringResource(R.string.status_loaded) else stringResource(R.string.status_error),
                isLast = false
            )
            StatusItem(
                title = stringResource(R.string.ffmpeg),
                isGood = data.ffmpeg,
                statusText = if (data.ffmpeg) stringResource(R.string.status_installed) else stringResource(R.string.status_error),
                isLast = true
            )
        }

        SectionCard(
            title = stringResource(R.string.llm_config_health),
            icon = Icons.Default.Psychology
        ) {
            StatusItem(
                title = stringResource(R.string.api_connection),
                isGood = data.llmConfigured,
                statusText = if (data.llmConfigured) stringResource(R.string.status_ok) else stringResource(R.string.status_error),
                isLast = false
            )
            InfoItem(
                title = stringResource(R.string.current_model),
                value = data.llmModel ?: stringResource(R.string.na)
            )
            InfoItem(
                title = stringResource(R.string.settings_llm_provider),
                value = data.llmProvider ?: stringResource(R.string.na)
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun HealthStatusCard(
    isOnline: Boolean,
    tunnelConnected: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline) StatusSuccessLight else StatusErrorLight
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isOnline) stringResource(R.string.system_online) else stringResource(R.string.system_offline),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOnline) StatusSuccess else StatusError
                )
                Text(
                    text = if (tunnelConnected) stringResource(R.string.tunnel_connected_hint) else stringResource(R.string.tunnel_disconnected_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOnline) TextSecondary else TextTertiary
                )
            }
            Surface(
                color = if (isOnline) StatusSuccess else StatusError,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isOnline) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp).padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            content()
        }
    }
}

@Composable
private fun StatusItem(
    title: String,
    isGood: Boolean,
    statusText: String,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusIndicatorDot(isGood = isGood)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (isGood) HealthGreen else StatusError
        )
    }
    if (!isLast) {
        HorizontalDivider(
            color = DividerLight,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun InfoItem(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
private fun StatusIndicatorDot(isGood: Boolean) {
    Surface(
        color = if (isGood) HealthGreen else StatusError,
        shape = CircleShape,
        modifier = Modifier.size(10.dp)
    ) {}
}
