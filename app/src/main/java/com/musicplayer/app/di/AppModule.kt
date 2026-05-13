package com.musicplayer.app.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import com.musicplayer.app.data.db.MusicDatabase
import com.musicplayer.app.data.db.PlaylistDao
import com.musicplayer.app.data.db.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): MusicDatabase =
        Room.databaseBuilder(ctx, MusicDatabase::class.java, "music_db").build()

    @Provides fun provideSongDao(db: MusicDatabase): SongDao = db.songDao()
    @Provides fun providePlaylistDao(db: MusicDatabase): PlaylistDao = db.playlistDao()

    @Provides @Singleton
    fun provideExoPlayer(@ApplicationContext ctx: Context): ExoPlayer =
        ExoPlayer.Builder(ctx)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
}