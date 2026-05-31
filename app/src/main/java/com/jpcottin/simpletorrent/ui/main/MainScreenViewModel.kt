package com.jpcottin.simpletorrent.ui.main

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpcottin.simpletorrent.data.CreateTorrentResult
import com.jpcottin.simpletorrent.data.DataRepository
import com.jpcottin.simpletorrent.data.TorrentInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainScreenViewModel(
    private val repository: DataRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    val uiState: StateFlow<MainScreenUiState> =
        repository.torrents
            .map<List<TorrentInfo>, MainScreenUiState>(MainScreenUiState::Success)
            .catch { emit(MainScreenUiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainScreenUiState.Loading)

    private val _createState = MutableStateFlow<CreateState>(CreateState.Idle)
    val createState: StateFlow<CreateState> = _createState.asStateFlow()

    fun addMagnet(uri: String) { viewModelScope.launch { repository.addMagnet(uri.trim()) } }
    fun pause(infoHash: String) { repository.pauseTorrent(infoHash) }
    fun resume(infoHash: String) { repository.resumeTorrent(infoHash) }
    fun remove(infoHash: String, deleteFiles: Boolean) { repository.removeTorrent(infoHash, deleteFiles) }
    fun setSequentialDownload(infoHash: String, enabled: Boolean) { repository.setSequentialDownload(infoHash, enabled) }

    fun createTorrent(sourcePath: String, outputDir: String) {
        _createState.value = CreateState.Creating
        viewModelScope.launch(ioDispatcher) {
            val result = repository.createTorrentFrom(sourcePath, outputDir)
            _createState.value = when (result) {
                is CreateTorrentResult.Success -> CreateState.Done(result)
                is CreateTorrentResult.Failure -> CreateState.Failed(result.error)
            }
        }
    }

    // Copies the picked URI to internal staging storage (bypasses FUSE path issues),
    // then creates and seeds the torrent from the stable internal path.
    fun createTorrentFromUri(contentResolver: ContentResolver, uri: Uri, stagingDir: String, outputDir: String) {
        _createState.value = CreateState.Creating
        viewModelScope.launch(ioDispatcher) {
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "torrent_source"

            val stageDir = File(stagingDir).also { it.mkdirs() }
            val stagedFile = File(stageDir, name)
            val result = runCatching {
                contentResolver.openInputStream(uri)!!.use { input ->
                    stagedFile.outputStream().use { input.copyTo(it) }
                }
                repository.createTorrentFrom(stagedFile.absolutePath, outputDir)
            }.getOrElse { CreateTorrentResult.Failure(it.message ?: "copy failed") }

            _createState.value = when (result) {
                is CreateTorrentResult.Success -> CreateState.Done(result)
                is CreateTorrentResult.Failure -> CreateState.Failed(result.error)
            }
        }
    }

    fun dismissCreateResult() { _createState.value = CreateState.Idle }
}

sealed interface CreateState {
    object Idle : CreateState
    object Creating : CreateState
    data class Done(val result: CreateTorrentResult.Success) : CreateState
    data class Failed(val error: String) : CreateState
}

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val torrents: List<TorrentInfo>) : MainScreenUiState
}
