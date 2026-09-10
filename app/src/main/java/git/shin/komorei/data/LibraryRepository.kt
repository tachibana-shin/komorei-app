package git.shin.komorei.data

import git.shin.komorei.data.local.dao.AnimeDao
import git.shin.komorei.data.local.entity.AnimeEntity
import git.shin.komorei.data.local.entity.WatchHistoryEntity
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Episode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val animeDao: AnimeDao
) {
    val bookmarkedAnimes: Flow<List<Anime>> = animeDao.getBookmarkedAnimes().map { entities ->
        entities.map { it.anime }
    }

    val historyAnimes: Flow<List<Anime>> = animeDao.getHistoryAnimes().map { entities ->
        entities.map { it.anime }
    }

    suspend fun toggleBookmark(anime: Anime) {
        animeDao.toggleBookmark(anime.id, anime.sourceId) {
            anime.toEntity()
        }
    }

    suspend fun saveProgress(
        anime: Anime, 
        episode: Episode, 
        progressMs: Long, 
        durationMs: Long
    ) {
        val historyEntry = WatchHistoryEntity(
            animeId = anime.id,
            sourceId = anime.sourceId,
            episodeId = episode.id,
            episodeNumber = episode.episodeNumber,
            episodeTitle = episode.title,
            lastWatchedAt = System.currentTimeMillis(),
            progressMs = progressMs,
            durationMs = durationMs
        )
        animeDao.saveEpisodeProgress(anime.toEntity(), historyEntry)
    }
    
    fun getWatchHistoryForAnime(animeId: String, sourceId: String): Flow<List<WatchHistoryEntity>> {
        return animeDao.getWatchHistoryForAnime(animeId, sourceId)
    }

    suspend fun getEpisodeProgress(animeId: String, sourceId: String, episodeId: String): WatchHistoryEntity? {
        return animeDao.getEpisodeHistory(animeId, sourceId, episodeId)
    }

    suspend fun isBookmarked(id: String, sourceId: String): Boolean {
        return animeDao.getAnime(id, sourceId)?.isBookmarked ?: false
    }

    private fun Anime.toEntity(): AnimeEntity {
        return AnimeEntity(
            anime = this
        )
    }
}
