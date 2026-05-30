package com.airecorder.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.airecorder.android.data.local.PreferencesManager
import com.airecorder.android.data.repository.SettingsRepository
import com.airecorder.android.data.model.LLMSettings
import com.airecorder.android.data.model.WatchSettings
import com.airecorder.android.data.model.StorageSettings
import com.airecorder.android.data.model.HealthResponse
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
    data object Loading : SettingsUiState<Nothing>
    data class Success<T : Any>(val data: T) : SettingsUiState<T>
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
    
    private val _llmSettingsState = MutableStateFlow<SettingsUiState<LLMSettings>>(SettingsUiState.Loading)
    val llmSettingsState: StateFlow<SettingsUiState<LLMSettings>> = _llmSettingsState.asStateFlow()
    
    private val _watchSettingsState = MutableStateFlow<SettingsUiState<WatchSettings>>(SettingsUiState.Loading)
    val watchSettingsState: StateFlow<SettingsUiState<WatchSettings>> = _watchSettingsState.asStateFlow()
    
    private val _storageSettingsState = MutableStateFlow<SettingsUiState<StorageSettings>>(SettingsUiState.Loading)
    val storageSettingsState: StateFlow<SettingsUiState<StorageSettings>> = _storageSettingsState.asStateFlow()
    
    private val _useDynamicColor = MutableStateFlow(true)
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()
    
    init {
        viewModelScope.launch {
            preferencesManager.serverUrl.collect { _serverUrl.value = it }
        }
        viewModelScope.launch {
            preferencesManager.apiToken.collect { _apiToken.value = it }
        }
        viewModelScope.launch {
            preferencesManager.dynamicColor.collect { _useDynamicColor.value = it }
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
    
    fun toggleDynamicColor() {
        viewModelScope.launch {
            val newValue = !_useDynamicColor.value
            _useDynamicColor.value = newValue
            preferencesManager.setDynamicColor(newValue)
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null

            try {
                settingsRepository.testConnection(_serverUrl.value, _apiToken.value)
                _connectionTestResult.value = true
            } catch (_: Exception) {
                _connectionTestResult.value = false
            }

            _isTestingConnection.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val apiToken by viewModel.apiToken.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    val healthState by viewModel.healthState.collectAsState()
    val llmSettingsState by viewModel.llmSettingsState.collectAsState()
    val watchSettingsState by viewModel.watchSettingsState.collectAsState()
    val storageSettingsState by viewModel.storageSettingsState.collectAsState()
    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
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
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 1. System Health Dashboard ---
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "系统状态",
                    style = MaterialTheme.typography.titleSmall,
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp)
                )
                
                when (val state = healthState) {
                    is HealthUiState.Loading -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Surface),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Primary)
                            }
                        }
                    }
                    is HealthUiState.Success -> {
                        val data = state.data
                        HealthDashboard(data)
                    }
                    is HealthUiState.Error -> {
                        ErrorCard(message = state.message)
                    }
                }
            }

            // --- 2. Connection Settings ---
            SettingsGroup(title = "连接配置") {
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        onClick = { viewModel.testConnection() },
                        icon = Icons.Default.CheckCircle,
                        text = "测试连接",
                        isLoading = isTestingConnection,
                        enabled = !isTestingConnection && serverUrl.isNotBlank() && apiToken.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    )

                    ActionButton(
                        onClick = {
                            scope.launch {
                                viewModel.saveSettings()
                                viewModel.loadHealth()
                                viewModel.loadSettings()
                            }
                        },
                        icon = Icons.Default.Save,
                        text = "保存配置",
                        containerColor = Primary,
                        contentColor = OnPrimary,
                        enabled = serverUrl.isNotBlank() && apiToken.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    )
                }

                AnimatedVisibility(visible = connectionTestResult != null) {
                    ConnectionResultCard(isSuccess = connectionTestResult == true)
                }
            }

            // --- 3. Processing Engines (ASR & LLM) ---
            SettingsGroup(title = "处理引擎") {
                // LLM Settings
                when (val state = llmSettingsState) {
                    is SettingsUiState.Success -> {
                        val settings = state.data
                        SettingsRow(
                            icon = Icons.Default.Psychology,
                            title = "LLM 提供商",
                            value = settings.provider,
                            status = if (settings.configured) HealthStatus.Good else HealthStatus.Error
                        )
                        SettingsRow(
                            icon = Icons.Default.SmartToy,
                            title = "模型名称",
                            value = settings.model
                        )
                    }
                    else -> {
                        SettingsRow(
                            icon = Icons.Default.Psychology,
                            title = "LLM 设置",
                            value = "点击刷新查看详情"
                        )
                    }
                }
            }

            // --- 4. Storage & Monitoring ---
            SettingsGroup(title = "存储与监控") {
                // Storage
                when (val state = storageSettingsState) {
                    is SettingsUiState.Success -> {
                        val settings = state.data
                        SettingsRow(
                            icon = Icons.Default.Storage,
                            title = "存储状态",
                            value = if (settings.transcriptExists) "正常" else "异常",
                            status = if (settings.transcriptExists) HealthStatus.Good else HealthStatus.Error
                        )
                    }
                    else -> {}
                }

                // Watch
                when (val state = watchSettingsState) {
                    is SettingsUiState.Success -> {
                        val settings = state.data
                        SettingsRow(
                            icon = Icons.Default.Visibility,
                            title = "监控服务",
                            value = if (settings.enabled) "已启用" else "已禁用",
                            status = if (settings.enabled) HealthStatus.Good else HealthStatus.Warning
                        )
                        if (settings.enabled) {
                            SettingsRow(
                                icon = Icons.Default.Folder,
                                title = "监控目录",
                                value = settings.watchDir.split("\\").lastOrNull() ?: settings.watchDir,
                                subtitle = settings.watchDir
                            )
                        }
                    }
                    else -> {}
                }
            }
            
            // --- 5. Appearance ---
            SettingsGroup(title = "外观") {
                SettingsToggle(
                    icon = Icons.Default.Palette,
                    title = "动态取色",
                    subtitle = "跟随系统壁纸自动调整主题色（Android 12+）",
                    checked = useDynamicColor,
                    onCheckedChange = { viewModel.toggleDynamicColor() }
                )
            }
            
            // --- 6. About ---
            SettingsGroup(title = "关于") {
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "版本",
                    value = "1.0.0"
                )
                SettingsRow(
                    icon = Icons.Default.Copyright,
                    title = "版权",
                    value = "AI Recorder"
                )
            }
        }
    }
}

enum class HealthStatus { Good, Warning, Error, Neutral }

@Composable
private fun HealthDashboard(data: com.airecorder.android.data.model.HealthResponse) {
    val isOnline = data.status == "ok"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = (if (isOnline) StatusSuccess else StatusError).copy(alpha = 0.1f),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isOnline) StatusSuccess else StatusError,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isOnline) "服务器在线" else "服务器离线",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isOnline) "所有系统运行正常" else "无法连接到后端服务",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = DividerLight, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                HealthIndicator(
                    modifier = Modifier.weight(1f),
                    label = "ASR 引擎",
                    isGood = data.funasr,
                    icon = Icons.Default.Mic
                )
                HealthIndicator(
                    modifier = Modifier.weight(1f),
                    label = "LLM 配置",
                    isGood = data.llmConfigured,
                    icon = Icons.Default.AutoAwesome
                )
                HealthIndicator(
                    modifier = Modifier.weight(1f),
                    label = "内网穿透",
                    isGood = data.tunnel?.connected == true,
                    icon = Icons.Default.Hub
                )
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Background,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 2
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnPrimary,
                checkedTrackColor = Primary,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = DividerLight
            )
        )
    }
}

@Composable
private fun HealthIndicator(
    modifier: Modifier = Modifier,
    label: String,
    isGood: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isGood) HealthGreen else StatusError,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Surface(
            color = if (isGood) HealthGreen else StatusError,
            shape = CircleShape,
            modifier = Modifier.size(6.dp)
        ) {}
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String? = null,
    subtitle: String? = null,
    status: HealthStatus = HealthStatus.Neutral,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.background(Color.Transparent) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Background,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1
                )
            }
        }
        
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        
        if (status != HealthStatus.Neutral) {
            val color = when (status) {
                HealthStatus.Good -> HealthGreen
                HealthStatus.Warning -> StatusWarning
                HealthStatus.Error -> StatusError
                else -> Color.Transparent
            }
            Surface(
                color = color,
                shape = CircleShape,
                modifier = Modifier.size(8.dp)
            ) {}
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StatusErrorLight.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusError)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = message, color = StatusError, style = MaterialTheme.typography.bodySmall)
        }
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
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Primary)
        },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = OutlineVariant,
            focusedBorderColor = Primary,
            unfocusedContainerColor = Background.copy(alpha = 0.5f),
            focusedContainerColor = Background.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = SecondaryContainer,
    contentColor: Color = Primary
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = DividerLight,
            disabledContentColor = TextTertiary
        ),
        shape = RoundedCornerShape(16.dp),
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
    Surface(
        color = (if (isSuccess) StatusSuccess else StatusError).copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isSuccess) StatusSuccess else StatusError,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isSuccess) stringResource(R.string.settings_connection_success) else stringResource(R.string.settings_connection_failed),
                style = MaterialTheme.typography.bodySmall,
                color = if (isSuccess) StatusSuccess else StatusError,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
