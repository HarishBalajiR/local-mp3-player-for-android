package com.musicplayer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.musicplayer.app.MainActivity
import com.musicplayer.app.R
import com.musicplayer.app.player.PlaybackService

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(ctx, mgr, it) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_TOGGLE, ACTION_NEXT, ACTION_PREV -> {
                val serviceIntent = Intent(ctx, PlaybackService::class.java).apply {
                    action = intent.action
                }
                ctx.startForegroundService(serviceIntent)
            }
            ACTION_UPDATE -> {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, MusicWidgetProvider::class.java))
                ids.forEach { updateWidget(ctx, mgr, it) }
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.musicplayer.WIDGET_TOGGLE"
        const val ACTION_NEXT   = "com.musicplayer.WIDGET_NEXT"
        const val ACTION_PREV   = "com.musicplayer.WIDGET_PREV"
        const val ACTION_UPDATE = "com.musicplayer.WIDGET_UPDATE"

        // Called from PlaybackService whenever state changes
        fun update(ctx: Context, title: String = "Not playing", artist: String = "", isPlaying: Boolean = false) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, MusicWidgetProvider::class.java))
            ids.forEach { updateWidget(ctx, mgr, it, title, artist, isPlaying) }
        }

        private fun updateWidget(
            ctx: Context,
            mgr: AppWidgetManager,
            widgetId: Int,
            title: String = "Not playing",
            artist: String = "",
            isPlaying: Boolean = false
        ) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_music)

            // Open app on tap
            val openIntent = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, openIntent)

            // Set text
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)

            // Show "Now Playing" label only when actually playing
            views.setViewVisibility(
                R.id.widget_now_playing,
                if (isPlaying || title != "Not playing") android.view.View.VISIBLE
                else android.view.View.INVISIBLE
            )

            // Set play/pause icon
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )

            // Control buttons
            views.setOnClickPendingIntent(R.id.widget_prev, buildPendingIntent(ctx, ACTION_PREV, 1))
            views.setOnClickPendingIntent(R.id.widget_play_pause, buildPendingIntent(ctx, ACTION_TOGGLE, 2))
            views.setOnClickPendingIntent(R.id.widget_next, buildPendingIntent(ctx, ACTION_NEXT, 3))

            mgr.updateAppWidget(widgetId, views)
        }

        private fun buildPendingIntent(ctx: Context, action: String, reqCode: Int): PendingIntent {
            val intent = Intent(ctx, MusicWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                ctx, reqCode, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}