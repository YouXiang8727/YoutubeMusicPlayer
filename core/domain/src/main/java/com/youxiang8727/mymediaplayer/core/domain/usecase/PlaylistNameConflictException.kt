package com.youxiang8727.mymediaplayer.core.domain.usecase

/**
 * 歌單名稱與既有歌單重複時拋出。
 * message 為可直接顯示給使用者的中文訊息。
 */
class PlaylistNameConflictException(val name: String) :
    Exception("已存在同名歌單「$name」")