package com.youxiang8727.mymediaplayer.feature.player.di

import com.youxiang8727.mymediaplayer.feature.player.DefaultMusicServiceController
import com.youxiang8727.mymediaplayer.feature.player.MusicServiceController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @Singleton
    abstract fun bindMusicServiceController(
        impl: DefaultMusicServiceController
    ): MusicServiceController
}
