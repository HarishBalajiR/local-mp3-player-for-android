package com.musicplayer.app.ui

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.musicplayer.app.data.db.MusicRepository
import com.musicplayer.app.data.db.SongEntity
import com.musicplayer.app.player.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentSong: SongEntity? = null,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val queue: List<SongEntity> = emptyList()
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    app: Application,
    val repository: MusicRepository
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    val allSongs = repository.allSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allPlaylists = repository.allPlaylists.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val songMap = mutableMapOf<String, SongEntity>()
    private var pendingPlayRequest: Pair<List<SongEntity>, Int>? = null

    init {
        connectToService()
        viewModelScope.launch { repository.cleanDirtyTags() }
        viewModelScope.launch { repository.scanMediaStore() }
        viewModelScope.launch {
            allSongs.collect { songs -> songs.forEach { songMap[it.id.toString()] = it } }
        }
        // Periodically check connection and reconnect if lost
        viewModelScope.launch {
            while (true) {
                delay(3000)
                if (controller == null || !controller!!.isConnected) {
                    connectToService()
                }
            }
        }
    }

    private fun connectToService() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.addListener(playerListener)
                syncState()
                pendingPlayRequest?.let { (songs, startIndex) ->
                    pendingPlayRequest = null
                    playSongs(songs, startIndex)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                controller = null
            }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { syncState() }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) { syncState() }
        override fun onRepeatModeChanged(mode: Int) { syncState() }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            // Most users expect shuffle to keep going; enable looping when shuffle is turned on.
            val c = controller
            if (enabled && c != null && c.repeatMode == Player.REPEAT_MODE_OFF) {
                c.repeatMode = Player.REPEAT_MODE_ALL
            }
            syncState()
        }
        override fun onPlaybackStateChanged(state: Int) {
            // Safety net: if shuffle is on and repeat is off, restart when the queue ends.
            val c = controller
            if (state == Player.STATE_ENDED && c != null &&
                c.shuffleModeEnabled && c.repeatMode == Player.REPEAT_MODE_OFF &&
                c.mediaItemCount > 0
            ) {
                c.seekToDefaultPosition(0)
                c.prepare()
                c.play()
            }
            syncState()
        }
    }

    private fun syncState() {
        val c = controller ?: return
        val mediaId = c.currentMediaItem?.mediaId
        val currentSong = mediaId?.let { songMap[it] }
        val queue = (0 until c.mediaItemCount).mapNotNull { i ->
            songMap[c.getMediaItemAt(i).mediaId]
        }
        _state.value = PlayerState(
            isPlaying = c.isPlaying,
            currentSong = currentSong,
            progress = c.currentPosition,
            duration = c.duration.coerceAtLeast(0L),
            repeatMode = c.repeatMode,
            shuffleEnabled = c.shuffleModeEnabled,
            queue = queue
        )
    }

    // ── Playback controls ─────────────────────────────────────────────────────

    fun playSongs(songs: List<SongEntity>, startIndex: Int = 0) {
        val c = controller ?: run {
            // After being swiped away from recents, the controller may not be connected yet.
            // Queue the user's request and execute it once the controller reconnects.
            pendingPlayRequest = songs to startIndex
            connectToService()
            return
        }
        val items = songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.path)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(artworkUriFromStored(song.albumArtUri))
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, startIndex, 0L)
        if (c.shuffleModeEnabled && c.repeatMode == Player.REPEAT_MODE_OFF) {
            c.repeatMode = Player.REPEAT_MODE_ALL
        }
        c.prepare()
        c.play()
        syncState()
    }

    fun togglePlayPause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun skipNext() { controller?.seekToNextMediaItem() }
    fun skipPrev() { controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun toggleShuffle() {
        controller?.let {
            val enable = !it.shuffleModeEnabled
            it.shuffleModeEnabled = enable
            if (enable && it.repeatMode == Player.REPEAT_MODE_OFF) {
                it.repeatMode = Player.REPEAT_MODE_ALL
            }
        }
        syncState()
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
        syncState()
    }

    // ── Queue ─────────────────────────────────────────────────────────────────

    fun addToQueue(song: SongEntity) {
        val c = controller ?: return
        val item = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.path)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(artworkUriFromStored(song.albumArtUri))
                    .build()
            )
            .build()
        c.addMediaItem(item)
        syncState()
    }

    fun removeFromQueue(index: Int) {
        controller?.removeMediaItem(index)
        syncState()
    }

    fun moveQueueItem(from: Int, to: Int) {
        controller?.moveMediaItem(from, to)
        syncState()
    }

    fun getProgress(): Long = controller?.currentPosition ?: 0L

    // ── Playlists ─────────────────────────────────────────────────────────────

    fun createPlaylist(name: String) = viewModelScope.launch { repository.createPlaylist(name) }

    fun addSongToPlaylist(playlistId: Long, songId: Long) =
        viewModelScope.launch { repository.addSongToPlaylist(playlistId, songId) }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        viewModelScope.launch { repository.removeSongFromPlaylist(playlistId, songId) }

    fun deletePlaylist(playlist: com.musicplayer.app.data.db.PlaylistEntity) =
        viewModelScope.launch { repository.deletePlaylist(playlist) }

    fun getPlaylistWithSongs(id: Long) = repository.getPlaylistWithSongs(id)

    fun deleteSong(song: SongEntity) = viewModelScope.launch { repository.deleteSong(song) }

    fun deleteSongs(songs: List<SongEntity>) = viewModelScope.launch {
        songs.forEach { repository.deleteSong(it) }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) =
        viewModelScope.launch { songIds.forEach { repository.addSongToPlaylist(playlistId, it) } }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}

private fun artworkUriFromStored(value: String?): android.net.Uri? {
    if (value.isNullOrBlank()) return null
    return when {
        value.startsWith("content:") || value.startsWith("file:") -> android.net.Uri.parse(value)
        value.startsWith("/") -> android.net.Uri.fromFile(File(value))
        else -> android.net.Uri.parse(value)
    }
}
