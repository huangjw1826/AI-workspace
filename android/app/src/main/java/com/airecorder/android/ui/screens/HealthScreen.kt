package com.airecorder.android.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.airecorder.android.data.model.HealthResponse
import com.airecorder.android.data.repository.SettingsRepository
import com.airecorder.android.ui.components.*
import com.airecorder.android.ui.theme.*
import com.airecorder.android.util.ErrorUtils
import com.airecorder.android.util.FormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ============================================================
// 健康面板 — 系统仪表盘（底栏 Tab 3）
// ============================================================

sealed class HealthUiState {
    data object Loading : HealthUiState()
    data class Success(val data: HealthResponse) : HealthUiState()
    data class Error(val message: String) : HealthUiState()
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val repository: SettingsRepository
) : androidx.lifecycle.ViewModel() {

    private val _uiState = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun loadHealth() {
        viewModelScope.launch {
            _uiState.value = HealthUiState.Loading
            _isRefreshing.value = true
            repository.getHealth().fold(
                onSuccess = { data ->
                    _uiState.value = HealthUiState.Success(data)
                },
                onFailure = { exception ->
                    _uiState.value = HealthUiState.Error(exception.message ?: "Unknown error")
                }
            )
            _isRefreshing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    viewModel: HealthViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadHealth()
    }

    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonitorHeart,
                            contentDescription = null,
                            tint = HealthGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "系统健康",
                            style = TopBarTitleStyle,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 12.dp),
                            strokeWidth = 2.dp,
                            color = Primary
                        )
                    } else {
                        IconButton(onClick = { viewModel.loadHealth() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = TextSecondary
                            )
                        }
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
        ) {
            when (val state = uiState) {
                is HealthUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
                is HealthUiState.Success -> {
                    HealthDashboardGrid(data = state.data)
                }
                is HealthUiState.Error -> {
                    ErrorState(
                        error = ErrorUtils.getFriendlyErrorMessage(state.message),
                        onRetry = { viewModel.loadHealth() }
                    )
                }
            }
        }
    }
}

/**
 * 2×2 仪表盘网格 + 底部详情
 */
@Composable
fun HealthDashboardGrid(data: HealthResponse) {
    val isOnline = data.status == "ok"
    val tunnelOk = data.tunnel?.connected == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 整体状态横幅
        OverallStatusBanner(isOnline = isOnline, tunnelOk = tunnelOk)

        // 2×2 网格
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DashboardCard(
                modifier = Modifier.weight(1f),
                title = "服务状态",
                icon = Icons.Default.Dns,
                iconTint = if (isOnline) HealthGreen else HealthRed
            ) {
                HealthStatusRow("FastAPI", if (isOnline) "在线" else "离线", if (isOnline) HealthGreen else HealthRed)
                HealthStatusRow("Cloudflare", if (tunnelOk) "已连接" else "断开", if (tunnelOk) HealthGreen else HealthRed)
            }

            DashboardCard(
                modifier = Modifier.weight(1f),
                title = "转写引擎",
                icon = Icons.Default.GraphicEq,
                iconTint = if (data.funasr) HealthGreen else HealthRed
            ) {
                HealthStatusRow("FunASR", if (data.funasr) "已加载" else "未加载", if (data.funasr) HealthGreen else HealthRed)
                HealthStatusRow("FFmpeg", if (data.ffmpeg) "已安装" else "未安装", if (data.ffmpeg) HealthGreen else HealthRed)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DashboardCard(
                modifier = Modifier.weight(1f),
                title = "LLM 配置",
                icon = Icons.Default.Psychology,
                iconTint = if (data.llmConfigured) HealthGreen else HealthRed
            ) {
                HealthStatusRow("API 连接", if (data.llmConfigured) "正常" else "异常", if (data.llmConfigured) HealthGreen else HealthRed)
                InfoRow("当前模型", data.llmModel ?: "—")
            }

            DashboardCard(
                modifier = Modifier.weight(1f),
                title = "系统资源",
                icon = Icons.Default.Memory,
                iconTint = if ((data.system?.cpuPercent ?: 0.0) < 80.0) HealthGreen else HealthOrange
            ) {
                val sys = data.system
                val cpuStr = if (sys != null && sys.cpuPercent >= 0) "${sys.cpuPercent.toInt()}%" else "—"
                val memStr = sys?.memory?.let {
                    "${FormatUtils.formatFileSize(it.used)} / ${FormatUtils.formatFileSize(it.total)}"
                } ?: "—"
                val diskStr = sys?.disk?.let {
                    FormatUtils.formatFileSize(it.free) + " 可用"
                } ?: "—"
                val cpuColor = when {
                    sys == null || sys.cpuPercent < 0 -> TextTertiary
                    sys.cpuPercent > 80 -> HealthRed
                    sys.cpuPercent > 50 -> HealthOrange
                    else -> HealthGreen
                }
                HealthStatusRow("CPU", cpuStr, cpuColor)
                InfoRow("内存", memStr)
                InfoRow("磁盘", diskStr)
            }
        }

        // 底部详情卡片
        DashboardCard(
            modifier = Modifier.fillMaxWidth(),
            title = "运行详情",
            icon = Icons.Default.Info,
            iconTint = Primary
        ) {
            data.system?.let { sys ->
                if (sys.uptimeSeconds > 0) {
                    InfoRow("运行时长", FormatUtils.formatUptime(sys.uptimeSeconds))
                }
            }
            InfoRow("Python", data.python ?: "—")
            InfoRow("ASR 模型", data.asrModel ?: "—")
            InfoRow("LLM 供应商", data.llmProvider ?: "—")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 整体状态横幅
 */
@Composable
private fun OverallStatusBanner(isOnline: Boolean, tunnelOk: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline) StatusSuccessLight.copy(alpha = 0.6f) else ErrorContainer
        ),
        shape = SmallCardShape
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isOnline) HealthGreen else Error,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = if (isOnline) "系统运行正常" else "系统异常",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOnline) TextPrimary else Error
                )
                Text(
                    text = if (isOnline) "所有服务在线" else "请检查 PC 端服务状态",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}

/**
 * 仪表盘小卡片
 */
@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = SmallCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

/**
 * 信息行（标题: 值）
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}
