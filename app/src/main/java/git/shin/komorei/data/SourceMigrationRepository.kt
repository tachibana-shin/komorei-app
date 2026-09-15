package git.shin.komorei.data

import git.shin.komorei.data.local.dao.AnimeDao
import git.shin.komorei.data.local.entity.AnimeEntity
import git.shin.komorei.data.local.entity.WatchHistoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How many records a migration pass looked at vs actually rewrote.
 */
data class MigrationReport(
    val examinedAnimes: Int,
    val examinedEpisodes: Int,
    val migratedAnimes: Int,
    val migratedEpisodes: Int,
)

/**
 * Aidoku-style library migration: for one source, re-key every stored anime
 * and episode (bookmarks + watch history) through the source's
 * `handle_anime_migration` / `handle_episode_migration` exports, then rewrite
 * the Room rows that moved. This is what keeps saved library items working
 * after a source changes its id scheme.
 *
 * Passes are idempotent: identity sources (nothing changes) touch nothing, and
 * requesting the migration again is a no-op. The remapper lambdas are seams so
 * the rewrite logic can be tested without a wasm source.
 */
@Singleton
class SourceMigrationRepository @Inject constructor(
    private val animeDao: AnimeDao,
    private val animeRepository: AnimeRepository,
) {

    /** Seam: maps an anime key to its current form (default: the source API). */
    internal var migrateAnimeKey: suspend (sourceId: String, key: String) -> String? =
        { sourceId, key -> animeRepository.migrateAnime(sourceId, key) }

    /** Seam: maps an episode key under its (old) anime key. */
    internal var migrateEpisodeKey:
        suspend (sourceId: String, animeKey: String, episodeKey: String) -> String? =
        { sourceId, animeKey, episodeKey ->
            animeRepository.migrateEpisode(sourceId, animeKey, episodeKey)
        }

    /**
     * Migrates every stored key for [sourceId] (bookmarks + watch history).
     * Returns a report; a failed source load (null result) is treated as
     * identity for that key, so a half-loaded source can't corrupt the library.
     */
    suspend fun migrateLibrary(sourceId: String): MigrationReport {
        val animeRows = animeDao.getAnimesForSource(sourceId)
        val historyRows = animeDao.getWatchHistoryForSource(sourceId)

        // Distinct anime keys across the library + history tables.
        val distinctKeys =
            (animeRows.map { it.anime.id } + historyRows.map { it.animeId }).distinct()
        val animeRemap = mutableMapOf<String, String>()
        for (key in distinctKeys) {
            val newKey = migrateAnimeKey(sourceId, key)
            if (newKey != null && newKey != key) animeRemap[key] = newKey
        }

        // Episode keys — asked with the OLD (anime, episode) keys, per the
        // source contract, for every distinct stored episode.
        val episodeRemap = mutableMapOf<Pair<String, String>, String>()
        for (row in historyRows) {
            val newEp = migrateEpisodeKey(sourceId, row.animeId, row.episodeId)
            if (newEp != null && newEp != row.episodeId) {
                episodeRemap[row.animeId to row.episodeId] = newEp
            }
        }

        // ── build the plan ──────────────────────────────────────────────
        val deleteAnimeIds = animeRemap.keys.toList()
        val insertAnimes: List<AnimeEntity> = animeRows
            .filter { it.anime.id in animeRemap }
            .map { row ->
                val oldId = row.anime.id
                row.copy(anime = row.anime.copy(id = animeRemap.getValue(oldId)))
            }

        val deleteHistoryByAnime = deleteAnimeIds
        // Rows whose ANIME id stayed but whose EPISODE id moved are deleted
        // individually (the whole-anime delete would miss them).
        val deleteHistoryEntries = historyRows.filter { row ->
            row.animeId !in animeRemap && (row.animeId to row.episodeId) in episodeRemap
        }
        val insertHistory: List<WatchHistoryEntity> = historyRows.mapNotNull { row ->
            val newAnimeId = animeRemap[row.animeId] ?: row.animeId
            val newEpisodeId = episodeRemap[row.animeId to row.episodeId] ?: row.episodeId
            if (newAnimeId == row.animeId && newEpisodeId == row.episodeId) null
            else row.copy(animeId = newAnimeId, episodeId = newEpisodeId)
        }

        animeDao.applyLibraryMigration(
            sourceId = sourceId,
            deleteAnimeIds = deleteAnimeIds,
            insertAnimes = insertAnimes,
            deleteHistoryByAnimeIds = deleteHistoryByAnime,
            deleteHistoryEntries = deleteHistoryEntries,
            insertHistory = insertHistory,
        )

        return MigrationReport(
            examinedAnimes = distinctKeys.size,
            examinedEpisodes = historyRows.size,
            migratedAnimes = animeRemap.size,
            migratedEpisodes = insertHistory.size,
        )
    }
}