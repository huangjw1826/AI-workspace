package com.airecorder.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airecorder.android.data.model.Recording
import com.airecorder.android.data.repository.RecordingRepository
import com.airecorder.android.ui.components.SortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    
    private val _allRecordings = MutableStateFlow<List<Recording>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _selectedStatuses = MutableStateFlow<Set<String>>(emptySet())
    val selectedStatuses: StateFlow<Set<String>> = _selectedStatuses.asStateFlow()
    
    private val _selectedSource = MutableStateFlow<String?>(null)
    val selectedSource: StateFlow<String?> = _selectedSource.asStateFlow()
    
    private val _sortOption = MutableStateFlow(SortOption.NEWEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()
    
    private val _selectedRecordingIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedRecordingIds: StateFlow<Set<String>> = _selectedRecordingIds.asStateFlow()
    
    private val _uploadQueue = MutableStateFlow<List<UploadItem>>(emptyList())
    val uploadQueue: StateFlow<List<UploadItem>> = _uploadQueue.asStateFlow()
    
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    private var pollingJob: Job? = null
    
    init {
        loadRecordings()
        observeFilters()
        startPolling()
    }
    
    private fun observeFilters() {
        viewModelScope.launch {
            combine(
                _allRecordings,
                _searchQuery,
                _selectedStatuses,
                _selectedSource,
                _sortOption
            ) { recordings, search, statuses, source, sort ->
                applyFilters(recordings, search, statuses, source, sort)
            }.collect { filtered ->
                if (_allRecordings.value.isNotEmpty()) {
                    _uiState.value = LibraryUiState.Success(filtered)
                }
            }
        }
    }
    
    fun loadRecordings(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading && _uiState.value !is LibraryUiState.Success) {
                _uiState.value = LibraryUiState.Loading
            }
            repository.getRecordings(_searchQuery.value).fold(
                onSuccess = { result ->
                    _allRecordings.value = result.recordings
                    if (_uiState.value !is LibraryUiState.Success) {
                        val filtered = applyFilters(
                            result.recordings,
                            _searchQuery.value,
                            _selectedStatuses.value,
                            _selectedSource.value,
                            _sortOption.value
                        )
                        _uiState.value = LibraryUiState.Success(filtered)
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
    
    private fun applyFilters(
        recordings: List<Recording>,
        search: String,
        statuses: Set<String>,
        source: String?,
        sort: SortOption
    ): List<Recording> {
        var filtered = recordings
        
        if (search.isNotEmpty()) {
            filtered = filtered.filter { 
                it.filename.contains(search, ignoreCase = true)
            }
        }
        
        if (statuses.isNotEmpty()) {
            filtered = filtered.filter { it.status in statuses }
        }
        
        if (source != null) {
            filtered = filtered.filter { 
                when (source) {
                    "upload" -> it.sourceType == "upload" || it.sourceType == null
                    "watch" -> it.sourceType == "watch"
                    else -> true
                }
            }
        }
        
        filtered = when (sort) {
            SortOption.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SortOption.OLDEST -> filtered.sortedBy { it.createdAt }
            SortOption.LONGEST -> filtered.sortedByDescending { it.durationSeconds }
            SortOption.LARGEST -> filtered.sortedByDescending { it.fileSizeBytes }
        }
        
        return filtered
    }
    
    private var searchJob: Job? = null
    fun onSearchQueryChanged(query: String) {
        if (_searchQuery.value == query) return
        _searchQuery.value = query
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadRecordings(showLoading = false)
        }
    }
    
    fun toggleStatusFilter(status: String) {
        _selectedStatuses.value = if (status in _selectedStatuses.value) {
            _selectedStatuses.value - status
        } else {
            _selectedStatuses.value + status
        }
    }
    
    fun setSourceFilter(source: String?) {
        _selectedSource.value = source
    }
    
    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }
    
    fun clearAllFilters() {
        _selectedStatuses.value = emptySet()
        _selectedSource.value = null
        _searchQuery.value = ""
    }
    
    fun toggleRecordingSelection(recordingId: String) {
        _selectedRecordingIds.value = if (recordingId in _selectedRecordingIds.value) {
            _selectedRecordingIds.value - recordingId
        } else {
            _selectedRecordingIds.value + recordingId
        }
    }
    
    fun selectAllRecordings(recordings: List<Recording>) {
        _selectedRecordingIds.value = recordings.map { it.id }.toSet()
    }
    
    fun deselectAllRecordings() {
        _selectedRecordingIds.value = emptySet()
    }
    
    fun isRecordingSelected(recordingId: String): Boolean {
        return recordingId in _selectedRecordingIds.value
    }
    
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadRecordings(showLoading = false)
            delay(500)
            _isRefreshing.value = false
        }
    }
    
    fun batchTranscribe() {
        viewModelScope.launch {
            val selectedIds = _selectedRecordingIds.value
            selectedIds.forEach { id ->
                repository.startTranscription(id)
            }
            deselectAllRecordings()
            loadRecordings(showLoading = false)
        }
    }
    
    fun batchSummarize() {
        viewModelScope.launch {
            val selectedIds = _selectedRecordingIds.value
            selectedIds.forEach { id ->
                repository.startSummary(id)
            }
            deselectAllRecordings()
            loadRecordings(showLoading = false)
        }
    }
    
    fun batchDelete() {
        viewModelScope.launch {
            val selectedIds = _selectedRecordingIds.value
            selectedIds.forEach { id ->
                repository.deleteRecording(id)
            }
            deselectAllRecordings()
            loadRecordings(showLoading = false)
        }
    }
    
    fun deleteRecording(recordingId: String) {
        viewModelScope.launch {
            repository.deleteRecording(recordingId)
            loadRecordings(showLoading = false)
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
                delay(5000)
            }
        }
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
