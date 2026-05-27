package com.airecorder.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airecorder.android.data.model.Recording
import com.airecorder.android.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    data class Success(val recordings: List<Recording>) : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}

data class UploadItem(
    val id: String,
    val file: File,
    val fileName: String,
    var status: UploadStatus = UploadStatus.Waiting,
    var progress: Int = 0,
    var error: String? = null
)

enum class UploadStatus {
    Waiting,
    Uploading,
    Success,
    Error
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _uploadQueue = MutableStateFlow<List<UploadItem>>(emptyList())
    val uploadQueue: StateFlow<List<UploadItem>> = _uploadQueue.asStateFlow()
    
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()
    
    private var pollingJob: Job? = null
    
    init {
        loadRecordings()
        startPolling()
    }
    
    fun loadRecordings(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading && _uiState.value !is LibraryUiState.Success) {
                _uiState.value = LibraryUiState.Loading
            }
            repository.getRecordings(_searchQuery.value).fold(
                onSuccess = { result ->
                    // 仅在数据真实变化时更新
                    if (_uiState.value !is LibraryUiState.Success || 
                        (_uiState.value as LibraryUiState.Success).recordings != result.recordings) {
                        _uiState.value = LibraryUiState.Success(result.recordings)
                    }
                },
                onFailure = { exception ->
                    if (_uiState.value !is LibraryUiState.Success) {
                        _uiState.value = LibraryUiState.Error(exception.message ?: "Unknown error")
                    }
                }
            )
        }
    }
    
    private var searchJob: Job? = null
    fun onSearchQueryChanged(query: String) {
        if (_searchQuery.value == query) return
        _searchQuery.value = query
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadRecordings()
        }
    }
    
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                val currentState = _uiState.value
                if (currentState is LibraryUiState.Success) {
                    val hasProcessing = currentState.recordings.any { it.isProcessing }
                    if (hasProcessing) {
                        loadRecordings(showLoading = false)
                    }
                } else if (currentState is LibraryUiState.Loading || currentState is LibraryUiState.Error) {
                    loadRecordings(showLoading = false)
                }
                delay(5000) // Poll every 5 seconds
            }
        }
    }
    
    fun refresh() {
        loadRecordings()
    }
    
    fun addToUploadQueue(files: List<File>) {
        val newItems = files.map { file ->
            UploadItem(
                id = java.util.UUID.randomUUID().toString(),
                file = file,
                fileName = file.name
            )
        }
        _uploadQueue.value = _uploadQueue.value + newItems
    }
    
    fun removeFromUploadQueue(itemId: String) {
        _uploadQueue.value = _uploadQueue.value.filterNot { it.id == itemId }
    }
    
    fun uploadNext() {
        val waitingItem = _uploadQueue.value.firstOrNull { it.status == UploadStatus.Waiting }
        waitingItem?.let { uploadItem(it) }
    }
    
    private fun uploadItem(item: UploadItem) {
        viewModelScope.launch {
            item.status = UploadStatus.Uploading
            _isUploading.value = true
            _uploadQueue.value = _uploadQueue.value.toList()
            
            repository.uploadRecording(item.file, item.fileName).fold(
                onSuccess = { recording ->
                    item.status = UploadStatus.Success
                    _uploadQueue.value = _uploadQueue.value.toList()
                    loadRecordings(showLoading = false)
                },
                onFailure = { error ->
                    item.status = UploadStatus.Error
                    item.error = error.message
                    _uploadQueue.value = _uploadQueue.value.toList()
                }
            )
            
            _isUploading.value = false
            uploadNext()
        }
    }
    
    fun retryUpload(itemId: String) {
        val item = _uploadQueue.value.firstOrNull { it.id == itemId }
        item?.let {
            it.status = UploadStatus.Waiting
            _uploadQueue.value = _uploadQueue.value.toList()
            if (!_isUploading.value) {
                uploadItem(it)
            }
        }
    }
    
    fun clearCompletedUploads() {
        _uploadQueue.value = _uploadQueue.value.filter {
            it.status != UploadStatus.Success && it.status != UploadStatus.Error
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
