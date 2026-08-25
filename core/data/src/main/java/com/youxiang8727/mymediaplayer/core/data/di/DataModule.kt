package com.youxiang8727.mymediaplayer.core.data.di

import android.content.Context
import androidx.room.Room
import com.youxiang8727.mymediaplayer.core.common.DefaultDispatcherProvider
import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import com.youxiang8727.mymediaplayer.core.data.local.AppDatabase
import com.youxiang8727.mymediaplayer.core.data.local.PlaylistDao
import com.youxiang8727.mymediaplayer.core.data.repository.AudioStreamRepositoryImpl
import com.youxiang8727.mymediaplayer.core.data.repository.PlaylistRepositoryImpl
import com.youxiang8727.mymediaplayer.core.data.repository.VideoRepositoryImpl
import com.youxiang8727.mymediaplayer.core.domain.repository.AudioStreamRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "mymediaplayer.db"
        ).build()
    }

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DispatcherModule {

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindAudioStreamRepository(impl: AudioStreamRepositoryImpl): AudioStreamRepository
}
