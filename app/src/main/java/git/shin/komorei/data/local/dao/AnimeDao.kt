package git.shin.komorei.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import git.shin.komorei.data.local.entity.AnimeEntity
import git.shin.komorei.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {

    // --- Anime Library & Bookmarks ---

    @Query("SELECT * FROM anime_library WHERE isBookmarked = 1 ORDER BY bookmarkAddedAt DESC")
    fun getBookmarkedAnimes(): Flow<List<AnimeEntity>>

    @Query("SELECT * FROM anime_library WHERE id = :id AND sourceId = :sourceId")
    suspend fun getAnime(id: String, sourceId: String): AnimeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnime(anime: AnimeEntity)

    @Query("UPDATE anime_library SET isBookmarked = :isBookmarked, bookmarkAddedAt = :timestamp WHERE id = :id AND sourceId = :sourceId")
    suspend fun updateBookmark(id: String, sourceId: String, isBookmarked: Boolean, timestamp: Long?)

    @Transaction
    suspend fun toggleBookmark(id: String, sourceId: String, animeProvider: suspend () -> AnimeEntity) {
        val existing = getAnime(id, sourceId)
        if (existing == null) {
            val newAnime = animeProvider().copy(isBookmarked = true, bookmarkAddedAt = System.currentTimeMillis())
            upsertAnime(newAnime)
        } else {
            val newStatus = !existing.isBookmarked
            updateBookmark(id, sourceId, newStatus, if (newStatus) System.currentTimeMillis() else null)
        }
    }

    // --- Watch History ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchHistory(history: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE animeId = :animeId AND sourceId = :sourceId AND episodeId = :episodeId")
    suspend fun getEpisodeHistory(animeId: String, sourceId: String, episodeId: String): WatchHistoryEntity?

    /**
     * Lấy danh sách Anime từ thư viện dựa trên lịch sử xem (JOIN).
     * Sắp xếp theo tập phim được xem gần đây nhất của bộ đó.
     */
    @Query("""
        SELECT a.* FROM anime_library a 
        INNER JOIN (
            SELECT animeId, sourceId, MAX(lastWatchedAt) as lastWatched 
            FROM watch_history 
            GROUP BY animeId, sourceId
        ) h ON a.id = h.animeId AND a.sourceId = h.sourceId 
        ORDER BY h.lastWatched DESC
    """)
    fun getHistoryAnimes(): Flow<List<AnimeEntity>>

    /**
     * Lấy toàn bộ lịch sử xem của một bộ phim cụ thể (để hiển thị tick xanh trên danh sách tập)
     */
    @Query("SELECT * FROM watch_history WHERE animeId = :animeId AND sourceId = :sourceId")
    fun getWatchHistoryForAnime(animeId: String, sourceId: String): Flow<List<WatchHistoryEntity>>

    @Transaction
    suspend fun saveEpisodeProgress(anime: AnimeEntity, history: WatchHistoryEntity) {
        // Đảm bảo metadata của anime có trong DB trước khi lưu lịch sử (để phục vụ JOIN)
        if (getAnime(anime.anime.id, anime.anime.sourceId) == null) {
            upsertAnime(anime)
        }
        upsertWatchHistory(history)
    }
}
