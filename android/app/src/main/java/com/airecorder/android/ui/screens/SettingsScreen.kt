package com.airecorder.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.airecorder.android.R
import com.airecorder.android.data.local.PreferencesManager
import com.airecorder.android.data.repository.SettingsRepository
import com.airecorder.android.ui.navigation.NavDestinations
import com.airecorder.android.ui.theme.*
import com.airecorder.android.ui.util.rememberHapticFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsUiState<out T> {
    object Loading : SettingsUiState<Nothing>
    data class Success<T>(val data: T) : SettingsUiState<T>
    data class Error(val message: String) : SettingsUiState<Nothing>
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val settingsRepository: SettingsRepository
) : androidx.lifecycle.ViewModel() {
    
    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()
    
    private val _apiToken = MutableStateFlow("")
    val apiToken: StateFlow<String> = _apiToken.asStateFlow()
    
    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()
    
    private val _connectionTestResult = MutableStateFlow<Boolean?>(null)
    val connectionTestResult: StateFlow<Boolean?> = _connectionTestResult.asStateFlow()
    
    private val _healthState = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
    val healthState: StateFlow<HealthUiState> = _healthState.asStateFlow()
    
    private val _llmSettingsState = MutableStateFlow<SettingsUiState<com.airecorder.android.data.model.LLMSettings>>(SettingsUiState.Loading)
    val llmSettingsState: StateFlow<SettingsUiState<com.airecorder.android.data.model.LLMSettings>> = _llmSettingsState.asStateFlow()
    
    private val _watchSettingsState = MutableStateFlow<SettingsUiState<com.airecorder.android.data.model.WatchSettings>>(SettingsUiState.Loading)
    val watchSettingsState: StateFlow<SettingsUiState<com.airecorder.android.data.model.WatchSettings>> = _watchSettingsState.asStateFlow()
    
    private val _storageSettingsState = MutableStateFlow<SettingsUiState<com.airecorder.android.data.model.StorageSettings>>(SettingsUiState.Loading)
    val storageSettingsState: StateFlow<SettingsUiState<com.airecorder.android.data.model.StorageSettings>> = _storageSettingsState.asStateFlow()
    
    init {
        viewModelScope.launch {
            preferencesManager.serverUrl.collect { _serverUrl.value = it }
        }
        viewModelScope.launch {
            preferencesManager.apiToken.collect { _apiToken.value = it }
        }
        loadHealth()
        loadSettings()
    }
    
    fun loadSettings() {
        loadLLMSettings()
        loadWatchSettings()
        loadStorageSettings()
    }
    
    fun loadHealth() {
        viewModelScope.launch {
            _healthState.value = HealthUiState.Loading
            settingsRepository.getHealth().fold(
                onSuccess = { data ->
                    _healthState.value = HealthUiState.Success(data)
                },
                onFailure = { exception ->
                    _healthState.value = HealthUiState.Error(exception.message ?: "Unknown error")
                }
            )
        }
    }
    
    fun loadLLMSettings() {
        viewModelScope.launch {
            _llmSettingsState.value = SettingsUiState.Loading
            settingsRepository.getLLMSettings().fold(
                onSuccess = { data ->
                    _llmSettingsState.value = SettingsUiState.Success(data)
                },
                onFailure = { exception ->
                    _llmSettingsState.value = SettingsUiState.Error(exception.message ?: "加载 LLM 设置失败")
                }
            )
        }
    }
    
    fun loadWatchSettings() {
        viewModelScope.launch {
            _watchSettingsState.value = SettingsUiState.Loading
            settingsRepository.getWatchSettings().fold(
                onSuccess = { data ->
                    _watchSettingsState.value = SettingsUiState.Success(data)
                },
                onFailure = { exception ->
                    _watchSettingsState.value = SettingsUiState.Error(exception.message ?: "加载监控设置失败")
                }
            )
        }
    }
    
    fun loadStorageSettings() {
        viewModelScope.launch {
            _storageSettingsState.value = SettingsUiState.Loading
            settingsRepository.getStorageSettings().fold(
                onSuccess = { data ->
                    _storageSettingsState.value = SettingsUiState.Success(data)
                },
                onFailure = { exception ->
                    _storageSettingsState.value = SettingsUiState.Error(exception.message ?: "加载存储设置失败")
                }
            )
        }
    }

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
    }

    fun updateApiToken(token: String) {
        _apiToken.value = token
    }

    suspend fun saveSettings() {
        preferencesManager.setServerUrl(_serverUrl.value)
        preferencesManager.setApiToken(_apiToken.value)
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null

            try {
                settingsRepository.testConnection(_serverUrl.value, _apiToken.value)
                _connectionTestResult.value = true
            } catch (e: Exception) {
                _connectionTestResult.value = false
            }

            _isTestingConnection.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val apiToken by viewModel.apiToken.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    val healthState by viewModel.healthState.collectAsState()
    val llmSettingsState by viewModel.llmSettingsState.collectAsState()
    val watchSettingsState by viewModel.watchSettingsState.collectAsState()
    val storageSettingsState by viewModel.storageSettingsState.collectAsState()
    val hapticFeedback = rememberHapticFeedback()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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
                    IconButton(onClick = {
                        hapticFeedback.performClick()
                        viewModel.loadHealth()
                        viewModel.loadSettings()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- 1. System Health Sections ---
            SectionHeader(title = stringResource(R.string.health_title), icon = Icons.Default.MonitorHeart)
            
            when (val state = healthState) {
                is HealthUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Primary)
                    }
                }
                is HealthUiState.Success -> {
                    val data = state.data
                    
                    // Health Summary Card
                    HealthSummaryCard(
                        isOnline = data.status == "ok",
                        tunnelConnected = data.tunnel?.connected == true
                    )

                    // Service Status Details
                    SettingsSection(
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

                    // Engine Status Details
                    SettingsSection(
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

                    // LLM Details
                    SettingsSection(
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
                }
                is HealthUiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusErrorLight.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = state.message,
                            color = StatusError,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // --- 2. Server Config Section ---
            SectionHeader(title = stringResource(R.string.settings_server_config), icon = Icons.Default.Settings)
            SettingsSection(
                title = "Endpoint Configuration",
                icon = Icons.Default.NetworkCheck
            ) {
                SettingTextField(
                    value = serverUrl,
                    onValueChange = { viewModel.updateServerUrl(it) },
                    label = stringResource(R.string.settings_server_url),
                    icon = Icons.Default.Public,
                    placeholder = "http://localhost:8000"
                )

                SettingTextField(
                    value = apiToken,
                    onValueChange = { viewModel.updateApiToken(it) },
                    label = stringResource(R.string.settings_api_token),
                    icon = Icons.Default.Key,
                    placeholder = "your-api-token"
                )

                Spacer(modifier = Modifier.height(8.dp))

                ActionButton(
                    onClick = { viewModel.testConnection() },
                    icon = Icons.Default.CheckCircle,
                    text = stringResource(R.string.settings_test_connection),
                    isLoading = isTestingConnection,
                    enabled = !isTestingConnection && serverUrl.isNotBlank() && apiToken.isNotBlank()
                )

                AnimatedVisibility(visible = connectionTestResult != null) {
                    ConnectionResultCard(isSuccess = connectionTestResult == true)
                }

                ActionButton(
                    onClick = {
                        scope.launch {
                            viewModel.saveSettings()
                        }
                    },
                    icon = Icons.Default.Save,
                    text = stringResource(R.string.settings_save),
                    containerColor = Primary,
                    contentColor = OnPrimary,
                    enabled = serverUrl.isNotBlank() && apiToken.isNotBlank()
                )
            }

            // --- 2. LLM Settings ---
            SectionHeader(title = "LLM 设置", icon = Icons.Default.Psychology)
            SettingsSection(
                title = "LLM 配置",
                icon = Icons.Default.SmartToy
            ) {
                when (val state = llmSettingsState) {
                    is SettingsUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Primary
                            )
                        }
                    }
                    is SettingsUiState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusError
                        )
                    }
                    is SettingsUiState.Success -> {
                        val settings = state.data
                        StatusItem(
                            title = "配置状态",
                            isGood = settings.configured,
                            statusText = if (settings.configured) "已配置" else "未配置",
                            isLast = false
                        )
                        InfoItem(
                            title = "提供商",
                            value = settings.provider
                        )
                        InfoItem(
                            title = "模型",
                            value = settings.model
                        )
                        InfoItem(
                            title = "API 密钥",
                            value = settings.apiKeyMasked ?: "******"
                        )
                        settings.maxCompletionTokens?.let { tokens ->
                            InfoItem(
                                title = "最大 Token 数",
                                value = tokens.toString()
                            )
                        }
                        settings.temperature?.let { temp ->
                            InfoItem(
                                title = "温度",
                                value = temp.toString()
                            )
                        }
                        settings.topP?.let { topP ->
                            InfoItem(
                                title = "Top P",
                                value = topP.toString()
                            )
                        }
                    }
                }
            }
            
            // --- 3. Storage Settings ---
            SectionHeader(title = "存储设置", icon = Icons.Default.Storage)
            SettingsSection(
                title = "存储配置",
                icon = Icons.Default.Folder
            ) {
                when (val state = storageSettingsState) {
                    is SettingsUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Primary
                            )
                        }
                    }
                    is SettingsUiState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusError
                        )
                    }
                    is SettingsUiState.Success -> {
                        val settings = state.data
                        InfoItem(
                            title = "转录文件目录",
                            value = settings.transcriptDir
                        )
                        StatusItem(
                            title = "转录目录状态",
                            isGood = settings.transcriptExists,
                            statusText = if (settings.transcriptExists) "存在" else "不存在",
                            isLast = false
                        )
                        InfoItem(
                            title = "摘要文件目录",
                            value = settings.summaryDir
                        )
                        StatusItem(
                            title = "摘要目录状态",
                            isGood = settings.summaryExists,
                            statusText = if (settings.summaryExists) "存在" else "不存在",
                            isLast = true
                        )
                    }
                }
            }
            
            // --- 4. Watch Settings ---
            SectionHeader(title = "监控设置", icon = Icons.Default.Visibility)
            SettingsSection(
                title = "目录监控配置",
                icon = Icons.Default.Visibility
            ) {
                when (val state = watchSettingsState) {
                    is SettingsUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Primary
                            )
                        }
                    }
                    is SettingsUiState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusError
                        )
                    }
                    is SettingsUiState.Success -> {
                        val settings = state.data
                        StatusItem(
                            title = "监控状态",
                            isGood = settings.enabled,
                            statusText = if (settings.enabled) "已启用" else "已禁用",
                            isLast = false
                        )
                        InfoItem(
                            title = "监控目录",
                            value = settings.watchDir
                        )
                        StatusItem(
                            title = "目录存在",
                            isGood = settings.exists,
                            statusText = if (settings.exists) "存在" else "不存在",
                            isLast = false
                        )
                        StatusItem(
                            title = "递归监控",
                            isGood = settings.recursive,
                            statusText = if (settings.recursive) "是" else "否",
                            isLast = false
                        )
                        InfoItem(
                            title = "监控间隔",
                            value = "${settings.intervalSeconds}秒"
                        )
                    }
                }
            }
            
            // --- 5. About Section ---
            SettingsSection(
                title = stringResource(R.string.settings_about),
                icon = Icons.Default.Info
            ) {
                AboutItem(
                    title = stringResource(R.string.settings_version),
                    value = "1.0.0"
                )
                AboutItem(
                    title = stringResource(R.string.settings_copyright),
                    value = "AI Recorder"
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
private fun HealthSummaryCard(
    isOnline: Boolean,
    tunnelConnected: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline) StatusSuccessLight.copy(alpha = 0.5f) else StatusErrorLight.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isOnline) stringResource(R.string.system_online) else stringResource(R.string.system_offline),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOnline) StatusSuccess else StatusError
                )
                Text(
                    text = if (tunnelConnected) stringResource(R.string.tunnel_connected_hint) else stringResource(R.string.tunnel_disconnected_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Surface(
                color = if (isOnline) StatusSuccess else StatusError,
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isOnline) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = if (isGood) HealthGreen else StatusError,
                shape = CircleShape,
                modifier = Modifier.size(8.dp)
            ) {}
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
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
            .padding(vertical = 4.dp),
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
            fontWeight = FontWeight.Bold,
            color = OnSurface
        )
    }
}

@Composable
private fun SettingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = DividerLight,
            focusedBorderColor = Primary,
            unfocusedContainerColor = Background,
            focusedContainerColor = Background
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = SecondaryContainer,
    contentColor: Color = Primary
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = DividerLight,
            disabledContentColor = TextTertiary
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ConnectionResultCard(isSuccess: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (isSuccess) StatusSuccess else StatusError,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (isSuccess) stringResource(R.string.settings_connection_success) else stringResource(R.string.settings_connection_failed),
            style = MaterialTheme.typography.bodySmall,
            color = if (isSuccess) StatusSuccess else StatusError,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AboutItem(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
            fontWeight = FontWeight.Bold,
            color = OnSurface
        )
    }
}
