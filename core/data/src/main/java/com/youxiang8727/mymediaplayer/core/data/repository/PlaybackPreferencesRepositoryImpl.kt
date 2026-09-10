package com.youxiang8727.mymediaplayer.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.youxiang8727.mymediaplayer.core.domain.model.PlaybackPreferences
import com.youxiang8727.mymediaplayer.core.domain.model.RepeatMode
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaybackPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** App 專用的播放偏好 DataStore（delegate 保證 process 內單例）。 */
private val Context.playbackDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "playback_preferences")

/**
 * 播放偏好持久化實作（DataStore Preferences）。
 *
 * 建構子設計：Hilt 走 `@ApplicationContext` 建構子取 Context delegate；
 * JVM 單元測試以 [PreferenceDataStoreFactory] 建立真實 DataStore 後注入 internal
 * 建構子（preferences-core 為純 JVM artifact，不需 Context）。
 *
 * DataStore 內部自管 IO scope（[androidx.datastore.preferences.core.edit] 與
 * `data.first()` 皆 suspend），呼叫端直接 await 即可，不需額外切 dispatcher。
 *
 * @param context 由 Hilt `@ApplicationContext` 注入，供 DataStore delegate 取得 process 級單例。
 *               Jvm 測試不走此建構子（改以 internal constructor 注入 PreferenceDataStoreFactory 建立的 DataStore）。
 */
@Singleton
class PlaybackPreferencesRepositoryImpl : PlaybackPreferencesRepository {

    private val dataStore: DataStore<Preferences>

    @Inject
    constructor(
        @ApplicationContext context: Context
    ) {
        this.dataStore = context.playbackDataStore
    }

    /** 測試注入點：JVM 測試以 [PreferenceDataStoreFactory] 建立的 DataStore 取代 Context delegate。 */
    internal constructor(dataStore: DataStore<Preferences>) {
        this.dataStore = dataStore
    }

    override suspend fun get(): PlaybackPreferences {
        val prefs = dataStore.data.first()
        val shuffleEnabled = prefs[KEY_SHUFFLE_ENABLED] ?: false
        val repeatMode = prefs[KEY_REPEAT_MODE]
            ?.let { name -> RepeatMode.entries.firstOrNull { it.name == name } }
            ?: RepeatMode.ALL
        return PlaybackPreferences(
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode
        )
    }

    override suspend fun save(preferences: PlaybackPreferences) {
        dataStore.edit { prefs ->
            prefs[KEY_SHUFFLE_ENABLED] = preferences.shuffleEnabled
            prefs[KEY_REPEAT_MODE] = preferences.repeatMode.name
        }
    }

    companion object {
        private val KEY_SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        private val KEY_REPEAT_MODE = stringPreferencesKey("repeat_mode")
    }
}