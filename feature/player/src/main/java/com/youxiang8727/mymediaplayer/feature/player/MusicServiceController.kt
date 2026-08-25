package com.youxiang8727.mymediaplayer.feature.player

import android.content.Context
import com.youxiang8727.mymediaplayer.feature.player.service.MusicService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放服務的啟停控制器。
 * ViewModel 透過此介面操作 Service，不直接依賴 Context/Intent 細節，便於測試替換。
 */
interface MusicServiceController {
    fun start(videoId: String, title: String)
    fun stop()
}

@Singleton
class DefaultMusicServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) : MusicServiceController {

    override fun start(videoId: String, title: String) =
        MusicService.start(context, videoId, title)

    override fun stop() = MusicService.stop(context)
}
