package com.musicplayer.app.ui.screens

import com.musicplayer.app.R
import androidx.compose.ui.res.painterResource
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.musicplayer.app.data.db.PlaylistEntity
import com.musicplayer.app.data.db.SongEntity
import com.musicplayer.app.ui.PlayerViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

// ─── Library ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: PlayerViewModel, onSongClick: (SongEntity) -> Unit) {
    val songs by vm.allSongs.collectAsState()
    val playlists by vm.allPlaylists.collectAsState()
    var search by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Multi-select state ────────────────────────────────────────────────────
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val inSelectionMode = selectedIds.isNotEmpty()

    // ── Dialogs ───────────────────────────────────────────────────────────────
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Single-song context menu (only when NOT in selection mode)
    var contextSong by remember { mutableStateOf<SongEntity?>(null) }
    var showSingleAddToPlaylist by remember { mutableStateOf(false) }
    var showSingleDeleteConfirm by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            scope.launch { vm.repository.importSongFromUri(uri) }
        }
    }

    val filtered = remember(songs, search) {
        if (search.isEmpty()) songs
        else songs.filter {
            it.title.contains(search, true) || it.artist.contains(search, true)
        }
    }

    // Clear selection when the song list changes (e.g. after delete)
    LaunchedEffect(songs) {
        selectedIds = selectedIds.filter { id -> songs.any { it.id == id } }.toSet()
    }

    // ── Bulk "Add to Playlist" dialog ─────────────────────────────────────────
    if (showAddToPlaylist && selectedIds.isNotEmpty()) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylist = false },
            onSelect = { playlist ->
                vm.addSongsToPlaylist(playlist.id, selectedIds.toList())
                showAddToPlaylist = false
                selectedIds = emptySet()
            }
        )
    }

    // ── Bulk "Delete" confirmation ────────────────────────────────────────────
    if (showDeleteConfirm && selectedIds.isNotEmpty()) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove $count song${if (count > 1) "s" else ""}") },
            text = { Text("Remove ${if (count == 1) "this song" else "these $count songs"} from your library?") },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = songs.filter { it.id in selectedIds }
                    vm.deleteSongs(toDelete)
                    showDeleteConfirm = false
                    selectedIds = emptySet()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // ── Single-song "Add to Playlist" (from context menu) ─────────────────────
    if (showSingleAddToPlaylist && contextSong != null) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showSingleAddToPlaylist = false; contextSong = null },
            onSelect = { playlist ->
                vm.addSongToPlaylist(playlist.id, contextSong!!.id)
                showSingleAddToPlaylist = false
                contextSong = null
            }
        )
    }

    // ── Single-song "Delete" confirmation (from context menu) ─────────────────
    if (showSingleDeleteConfirm && contextSong != null) {
        AlertDialog(
            onDismissRequest = { showSingleDeleteConfirm = false; contextSong = null },
            title = { Text("Remove Song") },
            text = { Text("Remove \"${contextSong!!.title}\" from your library?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSong(contextSong!!)
                    showSingleDeleteConfirm = false
                    contextSong = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showSingleDeleteConfirm = false; contextSong = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Single-song context menu dialog ───────────────────────────────────────
    if (contextSong != null && !showSingleAddToPlaylist && !showSingleDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { contextSong = null },
            title = { Text(contextSong!!.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Add to Playlist") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                        modifier = Modifier.clickable { showSingleAddToPlaylist = true }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Select Multiple") },
                        leadingContent = { Icon(Icons.Default.CheckBox, null) },
                        modifier = Modifier.clickable {
                            selectedIds = setOf(contextSong!!.id)
                            contextSong = null
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = {
                            Text("Remove from Library", color = MaterialTheme.colorScheme.error)
                        },
                        leadingContent = {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier.clickable { showSingleDeleteConfirm = true }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextSong = null }) { Text("Cancel") }
            }
        )
    }

    // Back handler: pressing back while in selection mode clears selection
    androidx.activity.compose.BackHandler(enabled = inSelectionMode) {
        selectedIds = emptySet()
    }

    Scaffold(
        topBar = {
            if (inSelectionMode) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, "Clear selection")
                        }
                    },
                    title = { Text("${selectedIds.size} selected") },
                    actions = {
                        // Select all / deselect all
                        val allSelected = filtered.isNotEmpty() && filtered.all { it.id in selectedIds }
                        IconButton(onClick = {
                            selectedIds = if (allSelected) emptySet()
                            else filtered.map { it.id }.toSet()
                        }) {
                            Icon(
                                if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                if (allSelected) "Deselect All" else "Select All"
                            )
                        }
                        // Add to playlist
                        IconButton(onClick = { showAddToPlaylist = true }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist")
                        }
                        // Delete
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete, "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        },
        floatingActionButton = {
            if (!inSelectionMode) {
                FloatingActionButton(onClick = { filePicker.launch(arrayOf("audio/*")) }) {
                    Icon(Icons.Default.Add, "Import MP3")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Search songs…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(50)
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tap + to import MP3 files", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    itemsIndexed(filtered, key = { _, s -> s.id }) { index, song ->
                        val isSelected = song.id in selectedIds
                        SongRow(
                            song = song,
                            isPlaying = false,
                            vm = vm,
                            inSelectionMode = inSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (inSelectionMode) {
                                    selectedIds = if (isSelected)
                                        selectedIds - song.id
                                    else
                                        selectedIds + song.id
                                } else {
                                    vm.playSongs(filtered, index)
                                    onSongClick(song)
                                }
                            },
                            onLongClick = {
                                if (!inSelectionMode) {
                                    contextSong = song
                                } else {
                                    // In selection mode, long press also toggles
                                    selectedIds = if (isSelected)
                                        selectedIds - song.id
                                    else
                                        selectedIds + song.id
                                }
                            },
                            onAddToQueue = { vm.addToQueue(song) }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

// ─── Add to Playlist Dialog ───────────────────────────────────────────────────

@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onSelect: (PlaylistEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            if (playlists.isEmpty()) {
                Text("No playlists yet. Create one in the Playlists tab.")
            } else {
                LazyColumn {
                    itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            modifier = Modifier.clickable { onSelect(playlist) }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─── Song Row ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: SongEntity,
    isPlaying: Boolean,
    vm: PlayerViewModel,
    inSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onAddToQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ) else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null, // handled by row click
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        AlbumArtImage(
            albumArtUri = song.albumArtUri,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            crossfade = true
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            song.duration.toTimeString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!inSelectionMode) {
            IconButton(onClick = onAddToQueue) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to queue", modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ─── Now Playing ──────────────────────────────────────────────────────────────

@Composable
fun NowPlayingScreen(vm: PlayerViewModel) {
    val state by vm.state.collectAsState()
    val song = state.currentSong

    var progress by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            progress = vm.getProgress()
            kotlinx.coroutines.delay(500)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        AlbumArtImage(
            albumArtUri = song?.albumArtUri,
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            crossfade = true
        )

        Spacer(Modifier.height(32.dp))

        Text(
            song?.title ?: "No song playing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            song?.artist ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Slider(
            value = if (state.duration > 0) progress.toFloat() / state.duration else 0f,
            onValueChange = { vm.seekTo((it * state.duration).toLong()) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(progress.toTimeString(), style = MaterialTheme.typography.labelSmall)
            Text(state.duration.toTimeString(), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.toggleShuffle() }) {
                Icon(
                    Icons.Default.Shuffle, "Shuffle",
                    tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { vm.skipPrev() }) {
                Icon(Icons.Default.SkipPrevious, "Prev", modifier = Modifier.size(36.dp))
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { vm.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play/Pause",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = { vm.skipNext() }) {
                Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { vm.cycleRepeat() }) {
                Icon(
                    when (state.repeatMode) {
                        androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    "Repeat",
                    tint = if (state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ─── Playlists ────────────────────────────────────────────────────────────────

@Composable
fun PlaylistsScreen(vm: PlayerViewModel, onPlaylistClick: (PlaylistEntity) -> Unit) {
    val playlists by vm.allPlaylists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        vm.createPlaylist(newName)
                        newName = ""
                        showCreate = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, "New Playlist")
            }
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tap + to create a playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                        trailingContent = {
                            IconButton(onClick = { vm.deletePlaylist(playlist) }) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        },
                        modifier = Modifier.clickable { onPlaylistClick(playlist) }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

// ─── Queue Screen ─────────────────────────────────────────────────────────────

@Composable
fun QueueScreen(vm: PlayerViewModel) {
    val state by vm.state.collectAsState()
    val queue = remember { mutableStateListOf<SongEntity>().also { it.addAll(state.queue) } }

    LaunchedEffect(state.queue) {
        queue.clear()
        queue.addAll(state.queue)
    }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            queue.add(to.index, queue.removeAt(from.index))
            vm.moveQueueItem(from.index, to.index)
        }
    )

    if (queue.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Queue is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        state = reorderState.listState,
        modifier = Modifier.reorderable(reorderState).detectReorderAfterLongPress(reorderState)
    ) {
        itemsIndexed(queue, key = { i, s -> "$i-${s.id}" }) { index, song ->
            ReorderableItem(reorderState, key = "$index-${song.id}") { isDragging ->
                val elevation = if (isDragging) 8.dp else 0.dp
                Surface(shadowElevation = elevation) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Drag",
                            modifier = Modifier.padding(end = 12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                song.artist, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            queue.removeAt(index)
                            vm.removeFromQueue(index)
                        }) {
                            Icon(Icons.Default.Close, "Remove")
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

// ─── Album art (embedded / MediaStore or music-note placeholder) ──────────────

@Composable
private fun AlbumArtImage(
    albumArtUri: String?,
    modifier: Modifier = Modifier,
    crossfade: Boolean = true
) {
    val context = LocalContext.current
    val artData = remember(albumArtUri) { songAlbumArtData(albumArtUri) }
    val notePainter = painterResource(R.drawable.ic_music_note)
    if (artData == null) {
        Image(
            painter = notePainter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(artData)
                .crossfade(crossfade)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = notePainter,
            error = notePainter,
            fallback = notePainter,
            modifier = modifier
        )
    }
}

// ─── Util ─────────────────────────────────────────────────────────────────────

fun Long.toTimeString(): String {
    val mins = TimeUnit.MILLISECONDS.toMinutes(this)
    val secs = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return "%d:%02d".format(mins, secs)
}

/** Coil model: content/file/http strings, or app-local absolute path from embedded art. */
private fun songAlbumArtData(uriOrPath: String?): Any? {
    if (uriOrPath.isNullOrBlank()) return null
    return when {
        uriOrPath.startsWith("content:") ||
            uriOrPath.startsWith("file:") ||
            uriOrPath.startsWith("http://") ||
            uriOrPath.startsWith("https://") -> uriOrPath
        uriOrPath.startsWith("/") -> File(uriOrPath)
        else -> uriOrPath
    }
}