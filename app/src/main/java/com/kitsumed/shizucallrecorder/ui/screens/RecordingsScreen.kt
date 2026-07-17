/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.call.RecordingFile
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Main screen for managing recorded audio files.
 *
 * Allows the user to browse, search, play, rename, share, and delete recordings
 * stored in the SAF folder configured in AppPreferences.
 *
 * Features:
 * - Search/filter by filename
 * - Swipe right → rename dialog; swipe left → delete confirmation
 * - Long-press → multi-selection mode (bulk delete / share)
 * - Tap → opens [RecordingPlayerSheet] media player
 * - Overflow menu per item (rename, share, delete)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecordingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val recordings = viewModel.filteredRecordings
    val isSelectionMode = uiState.selectedUris.isNotEmpty()

    // Dialog state
    var recordingToDelete by remember { mutableStateOf<RecordingFile?>(null) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var recordingToRename by remember { mutableStateOf<RecordingFile?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Player state
    var playerRecording by remember { mutableStateOf<RecordingFile?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Reload when the screen resumes (file may have been added/deleted externally)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.loadRecordings() }

    // Show errors as Snackbar
    val errorMessage = uiState.errorMessage
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.recordings_selected_count, uiState.selectedUris.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.recordings_action_cancel)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(
                                Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.recordings_action_select_all)
                            )
                        }
                        IconButton(onClick = {
                            shareRecordings(context, uiState.selectedUris.toList())
                        }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.recordings_action_share)
                            )
                        }
                        IconButton(onClick = { showDeleteSelectedConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.recordings_action_delete)
                            )
                        }
                    }
                )
            } else {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.recordings_screen_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search bar — always visible below the TopAppBar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.recordings_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.noFolderConfigured -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.recordings_no_folder_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(R.string.recordings_no_folder_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Card(
                                onClick = onNavigateBack,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.recordings_go_to_settings),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                recordings.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.MicOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.recordings_empty_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(R.string.recordings_empty_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(recordings, key = { it.uri.toString() }) { recording ->
                            SwipeableRecordingItem(
                                recording = recording,
                                isSelected = recording.uri in uiState.selectedUris,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(recording.uri)
                                    } else {
                                        playerRecording = recording
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(recording.uri) },
                                onSwipeDelete = { recordingToDelete = recording },
                                onSwipeRename = {
                                    recordingToRename = recording
                                    renameText = recording.name
                                },
                                onMenuDelete = { recordingToDelete = recording },
                                onMenuRename = {
                                    recordingToRename = recording
                                    renameText = recording.name
                                },
                                onMenuShare = { shareRecording(context, recording.uri) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    // Delete single
    recordingToDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            title = { Text(stringResource(R.string.recordings_delete_confirm_title)) },
            text = { Text(stringResource(R.string.recordings_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecording(rec.uri)
                    recordingToDelete = null
                }) { Text(stringResource(R.string.recordings_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) {
                    Text(stringResource(R.string.recordings_action_cancel))
                }
            }
        )
    }

    // Delete multiple
    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text(stringResource(R.string.recordings_delete_multi_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.recordings_delete_multi_confirm_body,
                        uiState.selectedUris.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showDeleteSelectedConfirm = false
                }) { Text(stringResource(R.string.recordings_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) {
                    Text(stringResource(R.string.recordings_action_cancel))
                }
            }
        )
    }

    // Rename
    recordingToRename?.let { rec ->
        AlertDialog(
            onDismissRequest = { recordingToRename = null },
            title = { Text(stringResource(R.string.recordings_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.recordings_rename_placeholder)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameRecording(rec.uri, renameText.trim())
                            recordingToRename = null
                        }
                    },
                    enabled = renameText.isNotBlank()
                ) { Text(stringResource(R.string.recordings_action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { recordingToRename = null }) {
                    Text(stringResource(R.string.recordings_action_cancel))
                }
            }
        )
    }

    // Media Player Bottom Sheet
    playerRecording?.let { rec ->
        RecordingPlayerSheet(
            recording = rec,
            onDismiss = { playerRecording = null }
        )
    }
}

// ── Swipeable recording item ──────────────────────────────────────────────────

/**
 * Wraps [RecordingListItem] in a [SwipeToDismissBox]:
 *  - Swipe right (StartToEnd) → rename
 *  - Swipe left  (EndToStart) → delete
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRecordingItem(
    recording: RecordingFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeDelete: () -> Unit,
    onSwipeRename: () -> Unit,
    onMenuDelete: () -> Unit,
    onMenuRename: () -> Unit,
    onMenuShare: () -> Unit
) {
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart  -> { onSwipeDelete(); false }
                SwipeToDismissBoxValue.StartToEnd  -> { onSwipeRename(); false }
                else -> false
            }
        },
        positionalThreshold = { it * 0.35f }
    )

    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            val direction = swipeState.dismissDirection
            val isEndToStart = direction == SwipeToDismissBoxValue.EndToStart
            val isStartToEnd = direction == SwipeToDismissBoxValue.StartToEnd

            val bgColor by animateColorAsState(
                targetValue = when {
                    isEndToStart -> MaterialTheme.colorScheme.errorContainer
                    isStartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    else         -> Color.Transparent
                },
                label = "swipeBg"
            )
            val iconScale by animateFloatAsState(
                targetValue = if (swipeState.targetValue == SwipeToDismissBoxValue.Settled) 0.75f else 1f,
                label = "swipeIconScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (isEndToStart) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Icon(
                    imageVector = if (isEndToStart) Icons.Default.Delete else Icons.Default.Edit,
                    contentDescription = null,
                    tint = if (isEndToStart) MaterialTheme.colorScheme.onErrorContainer
                           else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.scale(iconScale)
                )
            }
        }
    ) {
        RecordingListItem(
            recording = recording,
            isSelected = isSelected,
            isSelectionMode = isSelectionMode,
            onClick = onClick,
            onLongClick = onLongClick,
            onMenuDelete = onMenuDelete,
            onMenuRename = onMenuRename,
            onMenuShare = onMenuShare
        )
    }
}

/** Single list item showing direction icon, filename, date/duration/size and an overflow menu. */
@Composable
private fun RecordingListItem(
    recording: RecordingFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuDelete: () -> Unit,
    onMenuRename: () -> Unit,
    onMenuShare: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        leadingContent = {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            } else {
                Icon(
                    imageVector = when (recording.direction) {
                        CallDirection.INCOMING -> Icons.AutoMirrored.Filled.ArrowForward
                        CallDirection.OUTGOING -> Icons.Default.ArrowUpward
                        null                  -> Icons.Default.Mic
                    },
                    contentDescription = when (recording.direction) {
                        CallDirection.INCOMING -> stringResource(R.string.recordings_direction_incoming)
                        CallDirection.OUTGOING -> stringResource(R.string.recordings_direction_outgoing)
                        null                  -> null
                    },
                    tint = when (recording.direction) {
                        CallDirection.INCOMING -> MaterialTheme.colorScheme.primary
                        CallDirection.OUTGOING -> MaterialTheme.colorScheme.tertiary
                        null                  -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        },
        headlineContent = {
            Text(
                text = recording.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            val dateStr = remember(recording.lastModified) {
                SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())
                    .format(Date(recording.lastModified))
            }
            val durationStr = recording.durationMs?.let { ms ->
                val m = TimeUnit.MILLISECONDS.toMinutes(ms)
                val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
                "%d:%02d".format(m, s)
            } ?: ""
            val sizeStr = formatSize(recording.sizeBytes)
            val meta = listOf(dateStr, durationStr, sizeStr)
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (!isSelectionMode) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.recordings_action_rename)) },
                            onClick = { menuExpanded = false; onMenuRename() },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.recordings_action_share)) },
                            onClick = { menuExpanded = false; onMenuShare() },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.recordings_action_delete)) },
                            onClick = { menuExpanded = false; onMenuDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }
            }
        }
    )
}

// ── Media Player Bottom Sheet ─────────────────────────────────────────────────

/**
 * Modal bottom sheet with a minimal media player for [recording].
 *
 * Uses Android's built-in [android.media.MediaPlayer].
 * The player is released via [DisposableEffect] when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingPlayerSheet(
    recording: RecordingFile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    val durationMs = recording.durationMs ?: 0L

    // Build and prepare the MediaPlayer eagerly (it will be released in DisposableEffect)
    val player = remember {
        android.media.MediaPlayer().apply {
            try {
                context.contentResolver.openFileDescriptor(recording.uri, "r")?.use { pfd ->
                    setDataSource(pfd.fileDescriptor)
                    prepare()
                }
            } catch (_: Exception) {
                // Player stays in an un-prepared state; play button will simply not work.
            }
        }
    }

    // Poll playback position at ~2 Hz while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.currentPosition.toLong()
            kotlinx.coroutines.delay(500)
        }
    }

    // Release the player when the sheet leaves composition
    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            player.release()
        }
    }

    player.setOnCompletionListener {
        isPlaying = false
        positionMs = 0L
        player.seekTo(0)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // File name
            Text(
                text = recording.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Seek slider
            Slider(
                value = if (durationMs > 0) positionMs / durationMs.toFloat() else 0f,
                onValueChange = { fraction ->
                    val seekTo = (fraction * durationMs).toInt()
                    player.seekTo(seekTo)
                    positionMs = seekTo.toLong()
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Time labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(positionMs), style = MaterialTheme.typography.labelSmall)
                Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall)
            }

            // Playback controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rewind −15s
                IconButton(onClick = {
                    val newPos = (player.currentPosition - 15_000).coerceAtLeast(0)
                    player.seekTo(newPos)
                    positionMs = newPos.toLong()
                }) {
                    Icon(Icons.Default.Replay, contentDescription = stringResource(R.string.player_rewind))
                }

                // Play / Pause
                FilledIconButton(
                    onClick = {
                        if (isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            player.start()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying)
                            stringResource(R.string.player_pause)
                        else
                            stringResource(R.string.player_play),
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Forward +15s
                IconButton(onClick = {
                    val newPos = (player.currentPosition + 15_000).coerceAtMost(durationMs.toInt())
                    player.seekTo(newPos)
                    positionMs = newPos.toLong()
                }) {
                    Icon(Icons.Default.Forward10, contentDescription = stringResource(R.string.player_forward))
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun shareRecording(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private fun shareRecordings(context: android.content.Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    if (uris.size == 1) { shareRecording(context, uris.first()); return }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "audio/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000     -> "%.0f KB".format(bytes / 1_000.0)
    else               -> "$bytes B"
}

private fun formatDuration(ms: Long): String {
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(m, s)
}
