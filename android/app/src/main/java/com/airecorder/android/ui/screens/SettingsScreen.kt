package com.airecorder.android.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.airecorder.android.R
import com.airecorder.android.data.local.PreferencesManager
import com.airecorder.android.data.repository.SettingsRepository
import com.airecorder.android.ui.components.BottomNavigationBar
import com.airecorder.android.ui.navigation.NavDestinations
import com.airecorder.android.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    
    init {
        viewModelScope.launch {
            preferencesManager.serverUrl.collect { _serverUrl.value = it }
        }
        viewModelScope.launch {
            preferencesManager.apiToken.collect { _apiToken.value = it }
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
    onNavigateToLibrary: () -> Unit,
    onNavigateToHealth: () -> Unit
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val apiToken by viewModel.apiToken.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface
                )
            )
        },
        bottomBar = {
            BottomNavigationBar(
                currentDestination = NavDestinations.Settings,
                onNavigateTo = { destination ->
                    when (destination) {
                        NavDestinations.Library -> onNavigateToLibrary()
                        NavDestinations.Health -> onNavigateToHealth()
                        NavDestinations.Settings -> {}
                        NavDestinations.Detail -> {}
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_server_config),
                style = MaterialTheme.typography.titleMedium
            )
            
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { viewModel.updateServerUrl(it) },
                label = { Text(stringResource(R.string.settings_server_url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Public, contentDescription = null)
                }
            )
            
            OutlinedTextField(
                value = apiToken,
                onValueChange = { viewModel.updateApiToken(it) },
                label = { Text(stringResource(R.string.settings_api_token)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Key, contentDescription = null)
                }
            )
            
            Button(
                onClick = { viewModel.testConnection() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = !isTestingConnection && serverUrl.isNotBlank() && apiToken.isNotBlank()
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_testing_connection))
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_test_connection))
                }
            }
            
            connectionTestResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (result) Success else Error,
                        contentColor = if (result) OnSuccess else OnError
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (result) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (result) OnSuccess else OnError
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (result) {
                                stringResource(R.string.settings_connection_success)
                            } else {
                                stringResource(R.string.settings_connection_failed)
                            },
                            color = if (result) OnSuccess else OnError,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            Button(
                onClick = {
                    scope.launch {
                        viewModel.saveSettings()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = serverUrl.isNotBlank() && apiToken.isNotBlank()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_save))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.titleMedium
            )
            
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text("1.0.0") }
            )
        }
    }
}
