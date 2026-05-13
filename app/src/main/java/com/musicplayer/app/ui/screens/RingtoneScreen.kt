package com.musicplayer.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.musicplayer.app.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun RingtoneScreen(song: SongEntity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val durationSecs = (song.duration / 1000).coerceAtLeast(1)
    var startSec by remember { mutableFloatStateOf(0f) }
    var endSec by remember { mutableFloatStateOf(minOf(30f, durationSecs.toFloat())) }
    var isProcessing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Set as Ringtone", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(song.title, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(32.dp))

        Text("Start: ${startSec.toInt()}s")
        Slider(
            value = startSec,
            onValueChange = { startSec = it.coerceAtMost(endSec - 1f) },
            valueRange = 0f..durationSecs.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Text("End: ${endSec.toInt()}s")
        Slider(
            value = endSec,
            onValueChange = { endSec = it.coerceAtLeast(startSec + 1f) },
            valueRange = 0f..durationSecs.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "Clip duration: ${(endSec - startSec).toInt()}s",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                scope.launch {
                    isProcessing = true
                    val ok = trimAndSetRingtone(context, song, startSec.toLong() * 1000, endSec.toLong() * 1000)
                    isProcessing = false
                    Toast.makeText(
                        context,
                        if (ok) "Ringtone set!" else "Failed — check WRITE_SETTINGS permission",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isProcessing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Set as Ringtone")
        }
    }
}

private suspend fun trimAndSetRingtone(
    ctx: Context, song: SongEntity, startMs: Long, endMs: Long
): Boolean = withContext(Dispatchers.IO) {
    try {
        if (!Settings.System.canWrite(ctx)) return@withContext false

        val extractor = MediaExtractor()
        extractor.setDataSource(ctx, Uri.parse(song.path), null)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return@withContext false

        extractor.selectTrack(trackIndex)
        extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val format = extractor.getTrackFormat(trackIndex)

        val outFile = File(ctx.cacheDir, "ringtone_${System.currentTimeMillis()}.m4a")
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxTrack = muxer.addTrack(format)
        muxer.start()

        val buf = java.nio.ByteBuffer.allocate(1024 * 1024)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            val size = extractor.readSampleData(buf, 0)
            if (size < 0 || extractor.sampleTime > endMs * 1000) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime - startMs * 1000
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(muxTrack, buf, info)
            extractor.advance()
        }
        muxer.stop(); muxer.release(); extractor.release()

        // Insert into MediaStore
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DATA, outFile.absolutePath)
            put(MediaStore.Audio.Media.TITLE, "${song.title} (ringtone)")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, uri)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}