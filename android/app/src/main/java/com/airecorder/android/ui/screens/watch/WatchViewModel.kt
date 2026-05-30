package com.airecorder.android.ui.screens.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airecorder.android.data.model.WatchEvent
import com.airecorder.android.data.repository.SettingsRepository
import com.airecorder.android.data.repository.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WatchUiState {
    data object Loading : WatchUiState
    data class Success(val events: List<WatchEvent>) : WatchUiState
    data class Error(val message: String) : WatchUiState
}

@HiltViewModel
class WatchViewModel @Inject constructor(
    private val watchRepository: WatchRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<WatchUiState>(WatchUiState.Loading)
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    init {
        loadWatchEvents()
    }
    
    fun loadWatchEvents() {
        viewModelScope.launch {
            _uiState.value = WatchUiState.Loading
            try {
                val result = watchRepository.getWatchEvents()
                result.fold(
                    onSuccess = { events ->
                        _uiState.value = WatchUiState.Success(events)
                    },
                    onFailure = { error ->
                        _uiState.value = WatchUiState.Error(error.message ?: "加载监控事件失败")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = WatchUiState.Error(e.message ?: "加载监控事件失败")
            }
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val result = watchRepository.getWatchEvents()
                result.fold(
                    onSuccess = { events ->
                        _uiState.value = WatchUiState.Success(events)
                    },
                    onFailure = { error ->
                        _uiState.value = WatchUiState.Error(error.message ?: "加载监控事件失败")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = WatchUiState.Error(e.message ?: "加载监控事件失败")
            }
            _isRefreshing.value = false
        }
    }
    
    fun onRefreshComplete() {
        _isRefreshing.value = false
    }
}
