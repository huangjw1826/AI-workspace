package com.airecorder.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airecorder.android.data.model.RecordingDetail
import com.airecorder.android.data.repository.RecordingRepository
import com.airecorder.android.util.AudioPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    data class Paused(val progress: Float, val downloaded: Long, val total: Long) : AudioDownloadState()
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
    private val repository: RecordingRepository,
    private val audioPlayerManager: AudioPlayerManager
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
    
    val playbackSpeed: StateFlow<Float> = audioPlayerManager.playbackSpeed
    
    private val _currentSegmentIndex = MutableStateFlow(-1)
    val currentSegmentIndex: StateFlow<Int> = _currentSegmentIndex.asStateFlow()
    
    private var currentRecordingId: String? = null
    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            audioPlayerManager.playbackState.collectLatest { state ->
                _audioPlaybackState.value = when (state) {
                    is AudioPlayerManager.PlaybackState.Idle -> AudioPlaybackState.Idle
                    is AudioPlayerManager.PlaybackState.Buffering -> AudioPlaybackState.Buffering
                    is AudioPlayerManager.PlaybackState.Playing -> {
                        updateSegmentIndex(state.positionMs)
                        AudioPlaybackState.Playing(state.positionMs, state.durationMs)
                    }
                    is AudioPlayerManager.PlaybackState.Paused -> {
                        updateSegmentIndex(state.positionMs)
                        AudioPlaybackState.Paused(state.positionMs, state.durationMs)
                    }
                    is AudioPlayerManager.PlaybackState.Completed -> AudioPlaybackState.Completed(state.durationMs)
                    is AudioPlayerManager.PlaybackState.Error -> AudioPlaybackState.Error(state.message)
                }
            }
        }
    }

    private fun updateSegmentIndex(positionMs: Long) {
        val detail = (uiState.value as? DetailUiState.Success)?.data ?: return
        val seconds = positionMs / 1000.0
        val index = detail.segments.findLast { (it.startTime ?: 0.0) <= seconds }?.let { segment ->
            detail.segments.indexOf(segment)
        } ?: -1
        _currentSegmentIndex.value = index
    }
    
    fun loadRecording(id: String) {
        currentRecordingId = id
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            repository.getRecording(id).fold(
                onSuccess = { detail ->
                    _uiState.value = DetailUiState.Success(detail)
                    checkCache(id)
                },
                onFailure = { exception ->
                    _uiState.value = DetailUiState.Error(exception.message ?: "Unknown error")
                }
            )
        }
    }

    private fun checkCache(id: String) {
        if (repository.isAudioCached(id)) {
            _audioDownloadState.value = AudioDownloadState.Downloaded
        } else {
            val detail = (uiState.value as? DetailUiState.Success)?.data
            val format = detail?.recording?.format ?: "m4a"
            val tempSize = repository.getTempFileSize(id, format)
            if (tempSize > 0) {
                val totalSize = detail?.recording?.fileSizeBytes ?: 0L
                val progress = if (totalSize > 0) tempSize.toFloat() / totalSize else 0f
                _audioDownloadState.value = AudioDownloadState.Paused(progress, tempSize, totalSize)
            } else {
                _audioDownloadState.value = AudioDownloadState.NotDownloaded
            }
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
        val id = currentRecordingId ?: return
        val format = (uiState.value as? DetailUiState.Success)?.data?.recording?.format ?: "m4a"
        
        val startByte = if (_audioDownloadState.value is AudioDownloadState.Paused) {
            (_audioDownloadState.value as AudioDownloadState.Paused).downloaded
        } else {
            0L
        }
        
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            repository.downloadAudio(id, format, startByte).collect { status ->
                _audioDownloadState.value = when (status) {
                    is RecordingRepository.DownloadStatus.Started -> {
                        if (startByte > 0) _audioDownloadState.value
                        else AudioDownloadState.Downloading(0f, 0, 0)
                    }
                    is RecordingRepository.DownloadStatus.Progress -> AudioDownloadState.Downloading(status.progress, status.downloaded, status.total)
                    is RecordingRepository.DownloadStatus.Success -> AudioDownloadState.Downloaded
                    is RecordingRepository.DownloadStatus.Error -> AudioDownloadState.Error(status.message)
                }
            }
        }
    }
    
    fun pauseDownload() {
        val currentState = _audioDownloadState.value
        if (currentState is AudioDownloadState.Downloading) {
            downloadJob?.cancel()
            _audioDownloadState.value = AudioDownloadState.Paused(
                currentState.progress,
                currentState.downloaded,
                currentState.total
            )
        }
    }
    
    fun cancelDownload() {
        val id = currentRecordingId ?: return
        val format = (uiState.value as? DetailUiState.Success)?.data?.recording?.format ?: "m4a"
        downloadJob?.cancel()
        repository.deleteTempFile(id, format)
        _audioDownloadState.value = AudioDownloadState.NotDownloaded
    }

    fun deleteAudio() {
        val id = currentRecordingId ?: return
        repository.clearAudioCache(id)
        _audioDownloadState.value = AudioDownloadState.NotDownloaded
        audioPlayerManager.stop()
        _audioPlaybackState.value = AudioPlaybackState.Idle
    }
    
    fun retryDownload() {
        startDownload()
    }
    
    fun togglePlayPause() {
        if (_audioDownloadState.value == AudioDownloadState.Downloaded) {
            val file = repository.getCachedAudioFile(currentRecordingId!!)
            if (file != null) {
                if (_audioPlaybackState.value == AudioPlaybackState.Idle) {
                    audioPlayerManager.play(file)
                } else {
                    audioPlayerManager.togglePlayPause()
                }
            }
        }
    }
    
    fun seekTo(positionMs: Long) {
        audioPlayerManager.seekTo(positionMs)
    }
    
    fun rewind() {
        audioPlayerManager.rewind()
    }
    
    fun forward() {
        audioPlayerManager.forward()
    }
    
    fun togglePlaybackSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentIndex = speeds.indexOf(playbackSpeed.value)
        val nextIndex = (currentIndex + 1) % speeds.size
        audioPlayerManager.setSpeed(speeds[nextIndex])
    }
    
    fun updateCurrentSegment(index: Int) {
        _currentSegmentIndex.value = index
    }
    
    fun jumpToSegment(startTime: Double) {
        val positionMs = (startTime * 1000).toLong()
        if (_audioDownloadState.value == AudioDownloadState.Downloaded) {
            val file = repository.getCachedAudioFile(currentRecordingId!!)
            if (file != null) {
                if (_audioPlaybackState.value == AudioPlaybackState.Idle) {
                    audioPlayerManager.play(file)
                }
                audioPlayerManager.seekTo(positionMs)
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

    override fun onCleared() {
        super.onCleared()
        audioPlayerManager.stop()
    }
}
