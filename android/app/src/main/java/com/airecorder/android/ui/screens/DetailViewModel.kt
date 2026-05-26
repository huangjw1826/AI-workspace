package com.airecorder.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airecorder.android.data.model.RecordingDetail
import com.airecorder.android.data.model.Task
import com.airecorder.android.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val data: RecordingDetail) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()
    
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()
    
    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()
    
    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()
    
    private val _showSummaryTemplates = MutableStateFlow(false)
    val showSummaryTemplates: StateFlow<Boolean> = _showSummaryTemplates.asStateFlow()
    
    private val _summaryTemplates = MutableStateFlow<List<Map<String, String>>>(emptyList())
    val summaryTemplates: StateFlow<List<Map<String, String>>> = _summaryTemplates.asStateFlow()
    
    fun loadRecording(id: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            repository.getRecording(id).fold(
                onSuccess = { detail ->
                    _uiState.value = DetailUiState.Success(detail)
                },
                onFailure = { exception ->
                    _uiState.value = DetailUiState.Error(exception.message ?: "Unknown error")
                }
            )
        }
    }
    
    fun selectTab(index: Int) {
        _selectedTab.value = index
    }
    
    fun deleteRecording(id: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.deleteRecording(id).fold(
                onSuccess = { onSuccess() },
                onFailure = { /* Handle error */ }
            )
        }
    }
    
    fun transcribe(id: String) {
        viewModelScope.launch {
            _isTranscribing.value = true
            repository.transcribe(id).fold(
                onSuccess = { task ->
                    pollTask(task.id, id)
                },
                onFailure = {
                    _isTranscribing.value = false
                }
            )
        }
    }
    
    fun summarize(id: String, mode: String = "summary") {
        viewModelScope.launch {
            _isSummarizing.value = true
            repository.summarize(id, mode).fold(
                onSuccess = { task ->
                    pollTask(task.id, id)
                },
                onFailure = {
                    _isSummarizing.value = false
                }
            )
        }
    }
    
    fun loadSummaryTemplates() {
        viewModelScope.launch {
            repository.getSummaryTemplates().fold(
                onSuccess = { templates ->
                    _summaryTemplates.value = templates
                    _showSummaryTemplates.value = true
                },
                onFailure = { }
            )
        }
    }
    
    fun dismissSummaryTemplates() {
        _showSummaryTemplates.value = false
    }
    
    private suspend fun pollTask(taskId: String, recordingId: String) {
        var attempts = 0
        val maxAttempts = 120 // 10 minutes (5 second intervals)
        
        while (attempts < maxAttempts) {
            repository.getTask(taskId).fold(
                onSuccess = { task ->
                    when (task.status) {
                        "completed", "success" -> {
                            _isTranscribing.value = false
                            _isSummarizing.value = false
                            loadRecording(recordingId)
                            return
                        }
                        "failed", "error" -> {
                            _isTranscribing.value = false
                            _isSummarizing.value = false
                            return
                        }
                        else -> {
                            // Continue polling
                        }
                    }
                },
                onFailure = {
                    _isTranscribing.value = false
                    _isSummarizing.value = false
                    return
                }
            )
            
            delay(5000)
            attempts++
        }
        
        _isTranscribing.value = false
        _isSummarizing.value = false
    }
}
