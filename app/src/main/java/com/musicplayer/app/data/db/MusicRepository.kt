package com.musicplayer.app.data.db

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) {

    // ── Songs ──────────────────────────────────────────────────────────────────

    val allSongs: Flow<List<SongEntity>> = songDao.getAllSongs()

    /** Scans device MediaStore and syncs with Room DB */
    suspend fun scanMediaStore() = withContext(Dispatchers.IO) {
        val songs = mutableListOf<SongEntity>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                val songId = cursor.getLong(idCol)
                val path = cursor.getString(dataCol)?.takeIf { it.isNotBlank() }
                    ?: ContentUris.withAppendedId(uri, songId).toString()
                val albumId = cursor.getLong(albumIdCol)
                val fallbackArt = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                ).toString()
                val cover = resolveAlbumArt(songId, path, fallbackArt)
                songs.add(
                    SongEntity(
                        id = songId,
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown Artist",
                        album = cursor.getString(albumCol) ?: "Unknown Album",
                        duration = cursor.getLong(durationCol),
                        path = path,
                        albumArtUri = cover
                    )
                )
            }
        }
        songDao.upsertSongs(songs)
    }

    /** Import a single MP3 picked via file picker */
    suspend fun importSongFromUri(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).removeSuffix(".mp3") else null
                } ?: "Unknown"
            val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Unknown Album"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong() ?: 0L
            val embedded = retriever.embeddedPicture
            retriever.release()

            // Clean tags at import time
            val cleaned = TagCleaner.clean(rawTitle, rawArtist)

            val id = uri.toString().hashCode().toLong().let {
                if (it < 0) -it else it
            }
            val artPath = embedded?.let { persistEmbeddedAlbumArt(id, it) }
            songDao.upsertSong(
                SongEntity(
                    id = id,
                    title = cleaned.title,
                    artist = cleaned.artist,
                    album = album,
                    duration = duration,
                    path = uri.toString(),
                    albumArtUri = artPath
                )
            )
            Log.d("MusicRepository", "Imported: '${cleaned.title}' by '${cleaned.artist}'")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Retroactively clean dirty tags for all existing songs in the DB */
    suspend fun cleanDirtyTags() = withContext(Dispatchers.IO) {
        val songs = allSongs.first()
        var fixedCount = 0
        songs.forEach { song ->
            val cleaned = TagCleaner.clean(song.title, song.artist)
            if (cleaned.wasModified) {
                songDao.upsertSong(
                    song.copy(
                        title = cleaned.title,
                        artist = cleaned.artist
                    )
                )
                fixedCount++
                Log.d("MusicRepository", "Fixed tags for: '${song.title}' → '${cleaned.title}'")
            }
        }
        Log.d("MusicRepository", "Tag cleanup done. Fixed $fixedCount songs.")
    }

    suspend fun deleteSong(song: SongEntity) = withContext(Dispatchers.IO) {
        // Remove cached art in every possible format
        listOf("jpg", "png", "webp").forEach { ext ->
            File(embeddedArtDir(), "${song.id}.$ext").delete()
        }
        songDao.deleteSong(song)
    }

    // ── Playlists ──────────────────────────────────────────────────────────────

    val allPlaylists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    fun getPlaylistWithSongs(id: Long): Flow<PlaylistWithSongs> = playlistDao.getPlaylistWithSongs(id)

    suspend fun createPlaylist(name: String): Long = playlistDao.insertPlaylist(PlaylistEntity(name = name))

    suspend fun deletePlaylist(playlist: PlaylistEntity) = playlistDao.deletePlaylist(playlist)

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long, position: Int = 0) =
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId, position))

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        playlistDao.removeSong(playlistId, songId)

    // ── Embedded album art (ID3 / APIC) ───────────────────────────────────────

    private fun embeddedArtDir(): File =
        File(context.filesDir, "album_art").apply { mkdirs() }

    /**
     * Sniff the actual image format from the first 12 bytes of raw embedded-art data.
     * MediaMetadataRetriever.embeddedPicture() can return JPEG, PNG, or WebP bytes.
     * Saving with the wrong extension (e.g. WebP bytes as ".jpg") causes
     * BitmapFactory to silently fail on some Android versions.
     */
    private fun detectImageExtension(bytes: ByteArray): String {
        if (bytes.size < 12) return "jpg"
        // WebP: "RIFF" at [0..3] + "WEBP" at [8..11]
        if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) return "webp"
        // PNG: \x89PNG
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return "png"
        // JPEG: FF D8
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpg"
        return "jpg" // safe fallback
    }

    /** Writes raw embedded picture bytes; returns absolute path for Coil / metadata.
     *  WebP images are converted to JPEG for compatibility with devices (e.g. Xiaomi/MIUI)
     *  whose BitmapFactory has issues decoding WebP from file paths. */
    private fun persistEmbeddedAlbumArt(songId: Long, bytes: ByteArray): String {
        val detectedExt = detectImageExtension(bytes)

        // Convert WebP → JPEG for maximum device compatibility
        val (saveBytes, ext) = if (detectedExt == "webp") {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                bmp.recycle()
                out.toByteArray() to "jpg"
            } else {
                bytes to detectedExt   // decode failed, save original
            }
        } else {
            bytes to detectedExt
        }

        // Delete any previously cached art that may have a different extension
        listOf("jpg", "png", "webp")
            .filter { it != ext }
            .forEach { old -> File(embeddedArtDir(), "$songId.$old").delete() }
        val file = File(embeddedArtDir(), "$songId.$ext")
        file.outputStream().use { it.write(saveBytes) }
        Log.d("MusicRepository", "Persisted album art for $songId as .$ext (original: $detectedExt)")
        return file.absolutePath
    }

    /**
     * Prefer embedded cover from the audio file; otherwise use MediaStore album art URI.
     * [path] may be a filesystem path or a content URI string.
     */
    private fun resolveAlbumArt(songId: Long, path: String?, fallbackContentUri: String): String {
        val bytes = readEmbeddedAlbumArt(path) ?: return fallbackContentUri
        return persistEmbeddedAlbumArt(songId, bytes)
    }

    private fun readEmbeddedAlbumArt(path: String?): ByteArray? {
        if (path.isNullOrBlank()) return null
        val r = MediaMetadataRetriever()
        return try {
            when {
                path.startsWith("content:") -> r.setDataSource(context, Uri.parse(path))
                else -> r.setDataSource(path)
            }
            r.embeddedPicture
        } catch (e: Exception) {
            Log.d("MusicRepository", "Embedded art: ${e.message}")
            null
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }
}