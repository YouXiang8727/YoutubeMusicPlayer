package com.youxiang8727.mymediaplayer.core.data.di

import android.content.Context
import androidx.room.Room
import com.youxiang8727.mymediaplayer.core.common.DefaultDispatcherProvider
import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import com.youxiang8727.mymediaplayer.core.data.di.StreamProfile
import com.youxiang8727.mymediaplayer.core.data.local.AppDatabase
import com.youxiang8727.mymediaplayer.core.data.local.PlaylistDao
import com.youxiang8727.mymediaplayer.core.data.remote.stream.AudioStreamSource
import com.youxiang8727.mymediaplayer.core.data.remote.stream.FallbackStreamResolver
import com.youxiang8727.mymediaplayer.core.data.remote.stream.InnerTubeStreamSource
import com.youxiang8727.mymediaplayer.core.data.remote.stream.NewPipeStreamSource
import com.youxiang8727.mymediaplayer.core.data.remote.stream.OkHttpStreamHttpTransport
import com.youxiang8727.mymediaplayer.core.data.remote.stream.PipedStreamSource
import com.youxiang8727.mymediaplayer.core.data.remote.stream.StreamClock
import com.youxiang8727.mymediaplayer.core.data.remote.stream.StreamErrorClassifier
import com.youxiang8727.mymediaplayer.core.data.remote.stream.StreamHttpTransport
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
import okhttp3.OkHttpClient

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
        ).fallbackToDestructiveMigration()
            .build()
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

/**
 * 串流解析 fallback 鏈的組裝。
 * 順序即優先序：NewPipe（主路徑）→ InnerTube 直連（bot 封鎖時的替代 client）→ Piped 實例（最後手段）。
 */
@Module
@InstallIn(SingletonComponent::class)
object StreamResolverModule {

    @Provides
    @Singleton
    fun provideStreamClock(): StreamClock = StreamClock { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideStreamHttpTransport(
        @StreamProfile okHttpClient: OkHttpClient,
        dispatchers: DispatcherProvider
    ): StreamHttpTransport = OkHttpStreamHttpTransport(okHttpClient, dispatchers)

    @Provides
    @Singleton
    fun provideAudioStreamSources(
        newPipe: NewPipeStreamSource,
        innerTube: InnerTubeStreamSource,
        piped: PipedStreamSource
    ): List<@JvmSuppressWildcards AudioStreamSource> = listOf(newPipe, innerTube, piped)

    @Provides
    @Singleton
    fun provideFallbackStreamResolver(
        sources: List<@JvmSuppressWildcards AudioStreamSource>,
        classifier: StreamErrorClassifier,
        dispatchers: DispatcherProvider,
        clock: StreamClock
    ): FallbackStreamResolver = FallbackStreamResolver(sources, classifier, dispatchers, clock)
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
