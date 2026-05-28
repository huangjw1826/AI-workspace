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

sealed class AudioDownloadState {
    object NotDownloaded : AudioDownloadState()
    data class Downloading(val progress: Float, val downloaded: Long, val total: Long) : AudioDownloadState()
    object Downloaded : AudioDownloadState()
    data class Error(val message: String) : AudioDownloadState()
}

sealed class AudioPlaybackState {
    object Idle : AudioPlaybackState()
    object Buffering : AudioPlaybackState()
    data class Playing(val positionMs: Long, val durationMs: Long) : AudioPlaybackState()
    data class Paused(val positionMs: Long, val durationMs: Long) : AudioPlaybackState()
    data class Completed(val durationMs: Long) : AudioPlaybackState()
    data class Error(val message: String) : AudioPlaybackState()
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
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    private val _audioDownloadState = MutableStateFlow<AudioDownloadState>(AudioDownloadState.NotDownloaded)
    val audioDownloadState: StateFlow<AudioDownloadState> = _audioDownloadState.asStateFlow()
    
    private val _audioPlaybackState = MutableStateFlow<AudioPlaybackState>(AudioPlaybackState.Idle)
    val audioPlaybackState: StateFlow<AudioPlaybackState> = _audioPlaybackState.asStateFlow()
    
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    
    private val _currentSegmentIndex = MutableStateFlow(-1)
    val currentSegmentIndex: StateFlow<Int> = _currentSegmentIndex.asStateFlow()
    
    private var currentRecordingId: String? = null
    
    fun loadRecording(id: String) {
        currentRecordingId = id
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
    
    fun refreshRecording() {
        val id = currentRecordingId ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.getRecording(id).fold(
                onSuccess = { detail ->
                    _uiState.value = DetailUiState.Success(detail)
                },
                onFailure = { /* Keep current state on refresh failure */ }
            )
            _isRefreshing.value = false
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
    
    fun startDownload() {
        _audioDownloadState.value = AudioDownloadState.Downloading(0f, 0L, 0L)
        
        viewModelScope.launch {
            var progress = 0f
            while (progress < 1f) {
                progress += 0.1f
                val downloaded = (progress * 1000000).toLong()
                val total = 1000000L
                _audioDownloadState.value = AudioDownloadState.Downloading(
                    progress.coerceAtMost(1f),
                    downloaded,
                    total
                )
                delay(200)
            }
            _audioDownloadState.value = AudioDownloadState.Downloaded
        }
    }
    
    fun cancelDownload() {
        _audioDownloadState.value = AudioDownloadState.NotDownloaded
    }
    
    fun retryDownload() {
        startDownload()
    }
    
    fun togglePlayPause() {
        when (val state = _audioPlaybackState.value) {
            is AudioPlaybackState.Idle -> {
                _audioPlaybackState.value = AudioPlaybackState.Buffering
                viewModelScope.launch {
                    delay(500)
                    _audioPlaybackState.value = AudioPlaybackState.Playing(0L, 60000L)
                }
            }
            is AudioPlaybackState.Playing -> {
                _audioPlaybackState.value = AudioPlaybackState.Paused(state.positionMs, state.durationMs)
            }
            is AudioPlaybackState.Paused -> {
                _audioPlaybackState.value = AudioPlaybackState.Playing(state.positionMs, state.durationMs)
            }
            else -> {
                _audioPlaybackState.value = AudioPlaybackState.Idle
            }
        }
    }
    
    fun seekTo(positionMs: Long) {
        when (val state = _audioPlaybackState.value) {
            is AudioPlaybackState.Playing -> {
                _audioPlaybackState.value = AudioPlaybackState.Playing(positionMs, state.durationMs)
            }
            is AudioPlaybackState.Paused -> {
                _audioPlaybackState.value = AudioPlaybackState.Paused(positionMs, state.durationMs)
            }
            else -> {}
        }
    }
    
    fun rewind() {
        when (val state = _audioPlaybackState.value) {
            is AudioPlaybackState.Playing -> {
                val newPosition = (state.positionMs - 10000).coerceAtLeast(0L)
                _audioPlaybackState.value = AudioPlaybackState.Playing(newPosition, state.durationMs)
            }
            is AudioPlaybackState.Paused -> {
                val newPosition = (state.positionMs - 10000).coerceAtLeast(0L)
                _audioPlaybackState.value = AudioPlaybackState.Paused(newPosition, state.durationMs)
            }
            else -> {}
        }
    }
    
    fun forward() {
        when (val state = _audioPlaybackState.value) {
            is AudioPlaybackState.Playing -> {
                val newPosition = (state.positionMs + 10000).coerceAtMost(state.durationMs)
                _audioPlaybackState.value = AudioPlaybackState.Playing(newPosition, state.durationMs)
            }
            is AudioPlaybackState.Paused -> {
                val newPosition = (state.positionMs + 10000).coerceAtMost(state.durationMs)
                _audioPlaybackState.value = AudioPlaybackState.Paused(newPosition, state.durationMs)
            }
            else -> {}
        }
    }
    
    fun togglePlaybackSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentIndex = speeds.indexOf(_playbackSpeed.value)
        val nextIndex = (currentIndex + 1) % speeds.size
        _playbackSpeed.value = speeds[nextIndex]
    }
    
    fun updateCurrentSegment(index: Int) {
        _currentSegmentIndex.value = index
    }
    
    fun jumpToSegment(startTime: Double) {
        val positionMs = (startTime * 1000).toLong()
        when (val state = _audioPlaybackState.value) {
            is AudioPlaybackState.Playing -> {
                _audioPlaybackState.value = AudioPlaybackState.Playing(positionMs, state.durationMs)
            }
            is AudioPlaybackState.Paused -> {
                _audioPlaybackState.value = AudioPlaybackState.Playing(positionMs, state.durationMs)
            }
            else -> {
                _audioPlaybackState.value = AudioPlaybackState.Playing(positionMs, 60000L)
            }
        }
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
