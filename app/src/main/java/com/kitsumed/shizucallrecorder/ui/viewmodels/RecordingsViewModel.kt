/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.viewmodels

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.call.RecordingFile
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecordingsUiState(
    val recordings: List<RecordingFile> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedUris: Set<Uri> = emptySet(),
    val errorMessage: String? = null,
    val noFolderConfigured: Boolean = false
)

/**
 * Manages the state for the Recordings screen.
 *
 * Reads audio files from the SAF folder configured in [AppPreferences] and exposes
 * operations for delete, rename, share, and multi-selection.
 *
 * The recordings list is always sorted by [RecordingFile.lastModified] descending
 * (most recent first) and can be further filtered by [RecordingsUiState.searchQuery]
 * via [filteredRecordings].
 */
class RecordingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = AppPreferences(appContext)

    private val _uiState = MutableStateFlow(RecordingsUiState())
    val uiState: StateFlow<RecordingsUiState> = _uiState.asStateFlow()

    /**
     * Recordings filtered by the current [RecordingsUiState.searchQuery].
     * Returns the full list when the query is blank.
     */
    val filteredRecordings: List<RecordingFile>
        get() {
            val q = _uiState.value.searchQuery.trim().lowercase()
            return if (q.isEmpty()) _uiState.value.recordings
            else _uiState.value.recordings.filter { it.name.lowercase().contains(q) }
        }

    init {
        loadRecordings()
    }

    /**
     * Reads files from the SAF recording folder and populates [uiState].
     * Sorted by [RecordingFile.lastModified] descending (most recent first).
     * Clears the selection on each reload.
     */
    fun loadRecordings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val folderUri = preferences.getRecordingFolderUri()
            if (folderUri == null) {
                _uiState.update { it.copy(isLoading = false, noFolderConfigured = true) }
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val folder = DocumentFile.fromTreeUri(appContext, folderUri)
                        ?: return@runCatching emptyList<RecordingFile>()

                    folder.listFiles()
                        .filter { it.isFile && it.name != null }
                        .map { doc ->
                            val fullName = doc.name!!
                            val dotIndex = fullName.lastIndexOf('.')
                            val nameWithoutExt = if (dotIndex > 0) fullName.substring(0, dotIndex) else fullName
                            val ext = if (dotIndex > 0) fullName.substring(dotIndex + 1) else ""
                            val direction = inferDirection(nameWithoutExt)
                            val duration = readDuration(doc.uri)
                            RecordingFile(
                                uri = doc.uri,
                                name = nameWithoutExt,
                                extension = ext,
                                sizeBytes = doc.length(),
                                lastModified = doc.lastModified(),
                                durationMs = duration,
                                direction = direction
                            )
                        }
                        .sortedByDescending { it.lastModified }
                }
            }

            result.fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            recordings = list,
                            isLoading = false,
                            noFolderConfigured = false,
                            selectedUris = emptySet()
                        )
                    }
                },
                onFailure = { e ->
                    AppLogger.e("RecordingsViewModel: failed to load recordings: ${e.message}")
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
                }
            )
        }
    }

    /**
     * Deletes a single recording file by [uri].
     * Shows an error message in state on failure, then reloads.
     */
    fun deleteRecording(uri: Uri) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    DocumentFile.fromSingleUri(appContext, uri)?.delete() ?: false
                }.getOrElse { false }
            }
            if (!success) {
                _uiState.update { it.copy(errorMessage = "Unable to delete the recording.") }
            }
            loadRecordings()
        }
    }

    /**
     * Deletes all recordings whose URIs are in [RecordingsUiState.selectedUris].
     * Failures on individual files are silently logged; the list is reloaded afterwards.
     */
    fun deleteSelected() {
        val toDelete = _uiState.value.selectedUris.toList()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                toDelete.forEach { uri ->
                    runCatching { DocumentFile.fromSingleUri(appContext, uri)?.delete() }
                        .onFailure { AppLogger.e("RecordingsViewModel: failed to delete $uri: ${it.message}") }
                }
            }
            loadRecordings()
        }
    }

    /**
     * Renames a recording file, preserving its original extension.
     * @param uri     SAF URI of the file to rename.
     * @param newName New base name without extension.
     */
    fun renameRecording(uri: Uri, newName: String) {
        viewModelScope.launch {
            val current = _uiState.value.recordings.find { it.uri == uri }
                ?: return@launch
            val newFullName = "$newName.${current.extension}"
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    DocumentFile.fromSingleUri(appContext, uri)?.renameTo(newFullName) ?: false
                }.getOrElse { false }
            }
            if (!success) {
                _uiState.update { it.copy(errorMessage = "Unable to rename the recording.") }
            }
            loadRecordings()
        }
    }

    /** Adds [uri] to the selection set if absent, removes it if present. */
    fun toggleSelection(uri: Uri) {
        _uiState.update { state ->
            val newSet = state.selectedUris.toMutableSet()
            if (uri in newSet) newSet.remove(uri) else newSet.add(uri)
            state.copy(selectedUris = newSet)
        }
    }

    /** Selects all currently filtered recordings. */
    fun selectAll() {
        _uiState.update { state ->
            val allFilteredUris = filteredRecordings.map { it.uri }.toSet()
            state.copy(selectedUris = allFilteredUris)
        }
    }

    /** Clears all selections, exiting multi-selection mode. */
    fun clearSelection() {
        _uiState.update { it.copy(selectedUris = emptySet()) }
    }

    /** Updates the search query used to filter [filteredRecordings]. */
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /** Clears the displayed error message. */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Infers [CallDirection] from the filename by looking for "_in_" or "_out_" substrings,
     * matching the naming convention produced by `RecordingFileNameFormatter` (direction placeholder
     * produces "in" for incoming, "out" for outgoing).
     */
    private fun inferDirection(name: String): CallDirection? {
        val lower = name.lowercase()
        return when {
            "_in_" in lower || lower.endsWith("_in") -> CallDirection.INCOMING
            "_out_" in lower || lower.endsWith("_out") -> CallDirection.OUTGOING
            else -> null
        }
    }

    /**
     * Reads the audio duration from [uri] using [MediaMetadataRetriever].
     * Returns null if the duration cannot be determined (unsupported format, I/O error, etc.).
     * Must be called on a background thread.
     */
    private fun readDuration(uri: Uri): Long? {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(appContext, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            }
        } catch (e: Exception) {
            AppLogger.v("RecordingsViewModel: could not read duration for $uri: ${e.message}")
            null
        }
    }
}
