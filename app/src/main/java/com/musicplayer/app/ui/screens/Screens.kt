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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.musicplayer.app.data.db.PlaylistEntity
import com.musicplayer.app.data.db.SongEntity
import com.musicplayer.app.ui.PlayerViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.concurrent.TimeUnit

// ─── Library ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: PlayerViewModel, onSongClick: (SongEntity) -> Unit) {
    val songs by vm.allSongs.collectAsState()
    val playlists by vm.allPlaylists.collectAsState()
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
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

    var sortByArtist by remember { mutableStateOf(false) }
    val filtered = remember(songs, search, sortByArtist) {
        val list = if (search.isEmpty()) songs
        else songs.filter {
            it.title.contains(search, true) || it.artist.contains(search, true)
        }
        if (sortByArtist) list.sortedBy { it.artist.lowercase() } else list
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
            } else {
                TopAppBar(
                    title = { Text("Library") },
                    actions = {
                        IconButton(onClick = { sortByArtist = !sortByArtist }) {
                            Icon(
                                Icons.Default.SortByAlpha,
                                contentDescription = "Sort by Artist",
                                tint = if (sortByArtist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(
                                if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                if (showSearch) "Close search" else "Search"
                            )
                        }
                    }
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
// Filter chips removed as per user request

            if (showSearch) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("Search songs…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50)
                )
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No songs found", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Import audio files or copy them to Music/ or Downloads/.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { filePicker.launch(arrayOf("audio/*")) }) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Import")
                            }
                        }
                    }
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
    onAddToQueue: () -> Unit // Using this as the 'more' action for now
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ) else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 8.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        AlbumArtImage(
            albumArtUri = song.albumArtUri,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            crossfade = true
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            song.duration.toTimeString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
        if (!inSelectionMode) {
            IconButton(onClick = onLongClick) { // Reusing onLongClick for the more menu
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
            }
        }
    }
}

// ─── Now Playing ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
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

    val isFavorite by remember(song) {
        if (song != null) vm.isFavoriteFlow(song.id) else kotlinx.coroutines.flow.flowOf(false)
    }.collectAsState(initial = false)

    val context = LocalContext.current
    var dominantColor by remember { mutableStateOf<Color?>(null) }
    var mutedColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(song?.albumArtUri) {
        val uri = song?.albumArtUri
        if (uri != null) {
            val request = coil.request.ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false) // Hardware bitmaps cannot be used with Palette
                .build()
            val result = context.imageLoader.execute(request)
            if (result is coil.request.SuccessResult) {
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                        dominantColor = palette?.dominantSwatch?.rgb?.let { Color(it) }
                            ?: palette?.mutedSwatch?.rgb?.let { Color(it) }
                        mutedColor = palette?.darkMutedSwatch?.rgb?.let { Color(it) }
                            ?: palette?.darkVibrantSwatch?.rgb?.let { Color(it) }
                    }
                }
            }
        } else {
            dominantColor = null
            mutedColor = null
        }
    }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor ?: MaterialTheme.colorScheme.background,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
    )
    val animatedMutedColor by animateColorAsState(
        targetValue = mutedColor ?: MaterialTheme.colorScheme.background,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
    )

    val backgroundBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            animatedDominantColor.copy(alpha = 0.7f),
            animatedMutedColor.copy(alpha = 0.9f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar area
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.weight(1f))

            // Hero Album Art
            AlbumArtImage(
                albumArtUri = song?.albumArtUri,
                modifier = Modifier
                    .size(320.dp)
                    .shadow(32.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                crossfade = true
            )

            Spacer(Modifier.height(56.dp))

            // Track Info & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "No song playing",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = song?.artist ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { song?.let { vm.toggleFavorite(it) } }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF4081) else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Scrubber
            Slider(
                value = if (state.duration > 0) progress.toFloat() / state.duration else 0f,
                onValueChange = { vm.seekTo((it * state.duration).toLong()) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onBackground,
                    activeTrackColor = MaterialTheme.colorScheme.onBackground,
                    inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                )
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp), 
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(progress.toTimeString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                Text(state.duration.toTimeString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }

            Spacer(Modifier.height(24.dp))

            // Main Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle, "Shuffle",
                        tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f)
                    )
                }
                IconButton(onClick = { vm.skipPrev() }) {
                    Icon(Icons.Default.SkipPrevious, "Prev", modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onBackground)
                }
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { vm.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play/Pause",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }
                IconButton(onClick = { vm.skipNext() }) {
                    Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onBackground)
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
                        else MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f)
                    )
                }
            }

            Spacer(Modifier.weight(0.5f))
        }
    }
}

// ─── Playlists ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = { Text("Playlists", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, "New Playlist", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No playlists yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Tap + to create a new playlist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistDesignCard(
                        playlist = playlist,
                        vm = vm,
                        onClick = { onPlaylistClick(playlist) },
                        onDelete = { vm.deletePlaylist(playlist) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDesignCard(playlist: PlaylistEntity, vm: PlayerViewModel, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val playlistWithSongs by vm.getPlaylistWithSongs(playlist.id).collectAsState(initial = null)
    val songCount = playlistWithSongs?.songs?.size ?: 0
    
    var showMenu by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // Save image to internal storage to persist access
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        // Delete old cover art if it exists
                        playlist.coverArtUri?.let { oldPath ->
                            val oldFile = File(oldPath)
                            if (oldFile.exists()) oldFile.delete()
                        }
                        
                        // Generate a unique file name to bypass Coil's image cache
                        val uniqueId = System.currentTimeMillis()
                        val file = File(context.filesDir, "playlist_cover_${playlist.id}_$uniqueId.jpg")
                        file.outputStream().use { out -> inputStream.copyTo(out) }
                        inputStream.close()
                        
                        // Update playlist in DB
                        vm.updatePlaylist(playlist.copy(coverArtUri = file.absolutePath))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover Art Image (Clickable to change)
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { 
                        launcher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
                    },
                contentAlignment = Alignment.Center
            ) {
                if (playlist.coverArtUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(playlist.coverArtUri))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Cover Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Add Cover",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(24.dp))
            
            // Text Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$songCount songs",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            
            // Overflow Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete Playlist", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }
    }
}

// ─── Queue Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    if (queue.isNotEmpty()) {
                        IconButton(onClick = { vm.clearQueue() }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Queue", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Queue is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            state = reorderState.listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .reorderable(reorderState)
                .detectReorderAfterLongPress(reorderState),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(queue, key = { i, s -> "$i-${s.id}" }) { index, song ->
                val isPlaying = state.currentSong?.id == song.id

                ReorderableItem(reorderState, key = "$index-${song.id}") { isDragging ->
                    val elevation = if (isDragging) 8.dp else 0.dp
                    val backgroundColor = if (isPlaying) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    } else if (isDragging) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    } else {
                        Color.Transparent
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = backgroundColor,
                        shadowElevation = elevation
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hamburger (drag handle)
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Drag",
                                modifier = Modifier.padding(end = 12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Cover Art
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                AlbumArtImage(
                                    albumArtUri = song.albumArtUri,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Text Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = song.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Playing Indicator or X Button
                            if (isPlaying) {
                                AudioEqualizerAnim(
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    queue.removeAt(index)
                                    vm.removeFromQueue(index)
                                }
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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

// ─── Animations ───────────────────────────────────────────────────────────────

@Composable
fun AudioEqualizerAnim(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    
    val height1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier.size(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight(height1)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight(height2)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight(height3)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
