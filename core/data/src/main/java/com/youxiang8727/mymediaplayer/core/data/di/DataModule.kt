package com.youxiang8727.mymediaplayer.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.youxiang8727.mymediaplayer.core.common.DefaultDispatcherProvider
import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import com.youxiang8727.mymediaplayer.core.data.di.StreamProfile
import com.youxiang8727.mymediaplayer.core.data.local.AppDatabase
import com.youxiang8727.mymediaplayer.core.data.local.PlaylistDao
import com.youxiang8727.mymediaplayer.core.data.local.SearchHistoryDao
import com.youxiang8727.mymediaplayer.core.data.remote.SearchSuggestionDataSource
import com.youxiang8727.mymediaplayer.core.data.remote.YoutubeSearchSuggestionDataSource
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
import com.youxiang8727.mymediaplayer.core.data.repository.PlaybackPreferencesRepositoryImpl
import com.youxiang8727.mymediaplayer.core.data.repository.PlaylistRepositoryImpl
import com.youxiang8727.mymediaplayer.core.data.repository.RecommendationRepositoryImpl
import com.youxiang8727.mymediaplayer.core.data.repository.SearchHistoryRepositoryImpl
import com.youxiang8727.mymediaplayer.core.data.repository.SearchSuggestionRepositoryImpl
import com.youxiang8727.mymediaplayer.core.data.repository.VideoRepositoryImpl
import com.youxiang8727.mymediaplayer.core.domain.repository.AudioStreamRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaybackPreferencesRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.PlaylistRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.RecommendationRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.SearchHistoryRepository
import com.youxiang8727.mymediaplayer.core.domain.repository.SearchSuggestionRepository
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

    /** v2 → v3：playlist_items 新增可空 duration 欄位（顯示用長度字串）。 */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN duration TEXT")
        }
    }

    /** v3 → v4：新增 search_history 表（搜尋紀錄，query PK + searchedAt）。 */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS search_history (" +
                    "query TEXT NOT NULL PRIMARY KEY, " +
                    "searchedAt INTEGER NOT NULL)"
            )
        }
    }

    /** v4 → v5：playlist_items 新增可空 streamFailedAt 欄位（播放失敗標記，null = 無失敗）。 */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN streamFailedAt INTEGER")
        }
    }

    /**
     * v5 → v6：playlists.name 建立唯一索引（歌單名稱防呆）。
     *
     * 既有資料可能已有重複名稱，直接建唯一索引會失敗，故先歸一去重：
     * 每組重複名稱保留 **id 最小** 的一筆，其餘改名（後綴含原 id），
     * 改名結果會確保不與任何既有/已指派名稱重複（撞名時再加 `-N` 後綴）。
     * 不刪除任何歌單、不失敗。最後建立唯一索引（名稱與 Room 依 Entity
     * `Index(value=["name"], unique=true)` 自動推導的 `index_playlists_name` 一致）。
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val rows = mutableListOf<Pair<Long, String>>() // id → name
            db.query("SELECT id, name FROM playlists").use { cursor ->
                while (cursor.moveToNext()) {
                    rows.add(cursor.getLong(0) to cursor.getString(1))
                }
            }
            if (rows.isNotEmpty()) {
                val usedNames = rows.map { it.second }.toMutableSet()
                // 每組名稱保留 id 最小的一筆：其餘（minId 不等於自己）才需要改名
                val minIdByName = rows.groupBy { it.second }
                    .mapValues { (_, group) -> group.minOf { it.first } }
                rows.forEach { (id, name) ->
                    val minId = minIdByName.getValue(name)
                    if (minId != id) {
                        var candidate = "$name (重複 $id)"
                        var suffix = 2
                        while (candidate in usedNames) {
                            candidate = "$name (重複 $id-$suffix)"
                            suffix++
                        }
                        usedNames += candidate
                        db.execSQL(
                            "UPDATE playlists SET name = ? WHERE id = ?",
                            arrayOf<Any>(candidate, id)
                        )
                    }
                }
            }
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_playlists_name ON playlists(name)")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "mymediaplayer.db"
        ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigration()  // 對不可預期版本仍是防禦
            .build()
    }

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()
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
    abstract fun bindRecommendationRepository(
        impl: RecommendationRepositoryImpl
    ): RecommendationRepository

    @Binds
    @Singleton
    abstract fun bindAudioStreamRepository(impl: AudioStreamRepositoryImpl): AudioStreamRepository

    @Binds
    @Singleton
    abstract fun bindSearchSuggestionRepository(
        impl: SearchSuggestionRepositoryImpl
    ): SearchSuggestionRepository

    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(
        impl: SearchHistoryRepositoryImpl
    ): SearchHistoryRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackPreferencesRepository(
        impl: PlaybackPreferencesRepositoryImpl
    ): PlaybackPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindSearchSuggestionDataSource(
        impl: YoutubeSearchSuggestionDataSource
    ): SearchSuggestionDataSource
}
