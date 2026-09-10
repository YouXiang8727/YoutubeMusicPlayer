package com.youxiang8727.mymediaplayer.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.youxiang8727.mymediaplayer.core.domain.model.PlaybackPreferences
import com.youxiang8727.mymediaplayer.core.domain.model.RepeatMode
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 團隊規範要求：core:data Repository 對 Fake 資料源至少一組測試。
 * 本測試以 `PreferenceDataStoreFactory.create()` 建立**真實** DataStore
 * （preferences-core 為純 JVM artifact，不需 Context），經 [PlaybackPreferencesRepositoryImpl]
 * 的 internal 建構子注入（同 module，unit test 可存取）。
 * 每次測試使用獨立 temp file，避免跨測試互相污染。
 */
class PlaybackPreferencesRepositoryImplTest {

    private fun createRepo(
        testScope: TestScope,
        file: File
    ): Pair<DataStore<Preferences>, PlaybackPreferencesRepositoryImpl> {
        val dataStore = PreferenceDataStoreFactory.create(scope = testScope.backgroundScope) { file }
        return dataStore to PlaybackPreferencesRepositoryImpl(dataStore)
    }

    @Test
    fun `空 store 回傳預設值（shuffle=false repeatMode=ALL）`() = runTest {
        val file = File.createTempFile("playback_prefs_test_", ".preferences_pb")
        try {
            val (_, repo) = createRepo(this, file)
            val prefs = repo.get()
            assertEquals(false, prefs.shuffleEnabled)
            assertEquals(RepeatMode.ALL, prefs.repeatMode)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `save shuffle=true repeatMode=ALL 後 get 回讀一致`() = runTest {
        val file = File.createTempFile("playback_prefs_test_", ".preferences_pb")
        try {
            val (_, repo) = createRepo(this, file)
            repo.save(PlaybackPreferences(shuffleEnabled = true, repeatMode = RepeatMode.ALL))
            val prefs = repo.get()
            assertEquals(true, prefs.shuffleEnabled)
            assertEquals(RepeatMode.ALL, prefs.repeatMode)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `save shuffle=true repeatMode=ONE 後 get 回讀一致`() = runTest {
        val file = File.createTempFile("playback_prefs_test_", ".preferences_pb")
        try {
            val (_, repo) = createRepo(this, file)
            repo.save(PlaybackPreferences(shuffleEnabled = true, repeatMode = RepeatMode.ONE))
            val prefs = repo.get()
            assertEquals(true, prefs.shuffleEnabled)
            assertEquals(RepeatMode.ONE, prefs.repeatMode)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `unknown repeat_mode 字串 fallback 至 ALL`() = runTest {
        val file = File.createTempFile("playback_prefs_test_", ".preferences_pb")
        try {
            val (dataStore, repo) = createRepo(this, file)
            // 模擬資料檔被寫入無法辨識的字串（防禦：unknown 一律 fallback 預設）
            dataStore.edit { it[KEY_REPEAT_MODE] = "unexpected_mode" }
            val prefs = repo.get()
            assertEquals(RepeatMode.ALL, prefs.repeatMode)
            assertEquals(false, prefs.shuffleEnabled)
        } finally {
            file.delete()
        }
    }

    companion object {
        /** 與實作端 key 名稱同步（contract：`repeat_mode` 存 `RepeatMode.name`）。 */
        private val KEY_REPEAT_MODE = stringPreferencesKey("repeat_mode")
    }
}