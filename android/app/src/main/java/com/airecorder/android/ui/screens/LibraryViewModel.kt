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
import kotlin.math.min

sealed class LibraryUiState {
    data object Loading : LibraryUiState()
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
    private var consecutiveFailures = 0
    private var initialLoadDone = false
    
    init {
        observeFilters()
        // 先确保有初始数据再启动轮询，避免重复请求
        loadRecordings()
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
                    consecutiveFailures = 0 // 重置失败计数器
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
                    // 首次加载完成后启动轮询
                    if (!initialLoadDone) {
                        initialLoadDone = true
                        startPolling()
                    }
                },
                onFailure = { exception ->
                    consecutiveFailures++
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
                repository.transcribe(id)
            }
            deselectAllRecordings()
            loadRecordings(showLoading = false)
        }
    }
    
    fun batchSummarize() {
        viewModelScope.launch {
            val selectedIds = _selectedRecordingIds.value
            selectedIds.forEach { id ->
                repository.summarize(id)
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
    
    /**
     * 优化后的轮询逻辑：
     * - 仅在 Success 状态且有处理中的录音时轮询（5 秒间隔）
     * - Success 状态无处理中的录音：延长到 30 秒静默轮询
     * - Error 状态：使用指数退避（5s → 10s → 20s → 40s → 60s max）
     * - Loading 状态不轮询，首次加载后由 loadRecordings 回调启动
     */
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                when (val currentState = _uiState.value) {
                    is LibraryUiState.Success -> {
                        val hasProcessing = currentState.recordings.any { it.isProcessing }
                        if (hasProcessing) {
                            loadRecordings(showLoading = false)
                            delay(5_000L) // 有处理中的任务：5 秒轮询
                        } else {
                            delay(30_000L) // 无处理中的任务：30 秒静默轮询
                            loadRecordings(showLoading = false)
                        }
                    }
                    is LibraryUiState.Error -> {
                        // 指数退避：min(2^failures * 5s, 60s)
                        val backoffSeconds = min((1 shl consecutiveFailures.coerceAtMost(4)) * 5L, 60L)
                        delay(backoffSeconds * 1_000L)
                        loadRecordings(showLoading = false)
                    }
                    is LibraryUiState.Loading -> {
                        // Loading 状态不主动轮询
                        delay(5_000L)
                    }
                }
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
