package com.musicplayer.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicplayer.app.MainActivity
import com.musicplayer.app.widget.MusicWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var player: ExoPlayer

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val sessionIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionIntent)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                updateWidget()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidget()
            }
            override fun onPlaybackStateChanged(state: Int) {
                updateWidget()
            }
        })
    }

    private fun updateWidget() {
        val title = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Not playing"
        val artist = player.currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
        val isPlaying = player.isPlaying
        MusicWidgetProvider.update(applicationContext, title, artist, isPlaying)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            MusicWidgetProvider.ACTION_TOGGLE -> {
                if (player.isPlaying) player.pause() else player.play()
            }
            MusicWidgetProvider.ACTION_NEXT -> player.seekToNextMediaItem()
            MusicWidgetProvider.ACTION_PREV -> player.seekToPreviousMediaItem()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Stop playback but do NOT release the player — it's a Hilt singleton
        if (!player.isPlaying) {
            // Nothing is playing, safe to fully stop the service
            stopSelf()
        } else {
            player.pause()
        }
        MusicWidgetProvider.update(applicationContext, "Not playing", "", false)
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        MusicWidgetProvider.update(applicationContext, "Not playing", "", false)
        // IMPORTANT: Do NOT call player.release() here.
        // The ExoPlayer is a Hilt @Singleton tied to the Application lifecycle.
        // Releasing it here would invalidate the instance while Hilt still holds
        // the same reference — causing crashes when the service is recreated.
        player.stop()
        player.clearMediaItems()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}