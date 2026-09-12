package com.youxiang8727.mymediaplayer.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaylistEntity::class, PlaylistItemEntity::class, SearchHistoryEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    abstract fun searchHistoryDao(): SearchHistoryDao
}
