package git.shin.komorei.data

import git.shin.komorei.data.remote.SegmentDataInterceptor
import git.shin.komorei.data.remote.SegmentUrlInterceptor
import git.shin.komorei.model.Anime
import git.shin.komorei.model.DeepLinkTarget
import git.shin.komorei.model.Episode
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterValue
import git.shin.komorei.model.Genre
import git.shin.komorei.model.HomeComponent
import git.shin.komorei.model.HomeComponentValue
import git.shin.komorei.model.Listing
import git.shin.komorei.model.Source
import git.shin.komorei.model.SourceSetting
import git.shin.komorei.model.SourceSettingValue
import git.shin.komorei.model.StreamData
import git.shin.komorei.model.StreamInfo
import git.shin.komorei.sdk.KrxPage
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.sdk.toAppModel
import git.shin.komorei.sdk.toAppPage
import git.shin.komorei.sdk.toRunner
import git.shin.komorei.sdk.runner.HostDefaultValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runner-backed repository. Every call hops onto the source's dedicated IO
 * thread via [KrxSourceRegistry.call] / [callAll] — wasm calls NEVER run on the
 * main thread, and a slow source never blocks another source (one thread per
 * source, no shared lock).
 */
@Singleton
class AnimeRepository @Inject constructor(
    private val registry: KrxSourceRegistry,
) {

    private companion object {
        const val AGGREGATOR_ID = "all"
        const val AGGREGATOR_NAME = "Tổng hợp"
        const val AGGREGATOR_ICON = "🌐"
        const val AGGREGATOR_VERSION = "2.4.0"
        const val AGGREGATOR_BADGE = 0xFFFF2A55L
    }

    /** The "all" virtual source + every bundled source. */
    val sources: List<Source> = buildList {
        add(
            Source(
                AGGREGATOR_ID, AGGREGATOR_NAME, AGGREGATOR_ICON,
                AGGREGATOR_VERSION, "", true, true, AGGREGATOR_BADGE
            )
        )
        addAll(registry.sourceAppList())
    }

    /**
     * Reactive variant of [sources] — re-emits when the registry's source set
     * changes (install / uninstall), so Home tabs and the Sources tab stay
     * in sync without process restarts.
     */
    val sourcesFlow: Flow<List<Source>> = registry.sourceAppFlow.map { appSources ->
        buildList {
            add(
                Source(
                    AGGREGATOR_ID, AGGREGATOR_NAME, AGGREGATOR_ICON,
                    AGGREGATOR_VERSION, "", true, true, AGGREGATOR_BADGE
                )
            )
            addAll(appSources)
        }
    }

    val genres: List<Genre> = listOf(
        Genre("action", "Hành Động", "⚔️", 0xFFE53935, 142),
        Genre("isekai", "Chuyển Sinh", "🌀", 0xFF8E24AA, 98),
        Genre("adventure", "Phiêu Lưu", "🧭", 0xFF1E88E5, 115),
        Genre("harem", "Harem", "🌸", 0xFFD81B60, 64),
        Genre("shounen", "Shounen", "🔥", 0xFFFF8F00, 180),
        Genre("romance", "Lãng Mạn", "💖", 0xFFEC407A, 88),
        Genre("supernatural", "Siêu Nhiên", "👁️", 0xFF5E35B1, 76),
        Genre("school", "Học Đường", "🏫", 0xFF43A047, 92),
        Genre("comedy", "Hài Hước", "🤣", 0xFFFDD835, 120),
        Genre("mystery", "Bí Ẩn", "🕵️", 0xFF3949AB, 55),
        Genre("fantasy", "Giả Tưởng", "✨", 0xFF00ACC1, 134),
        Genre("mecha", "Mecha", "🤖", 0xFF546E7A, 42)
    )

    fun getSource(sourceId: String): Source? = sources.find { it.id == sourceId }

    fun getSourceName(sourceId: String): String = getSource(sourceId)?.name ?: sourceId

    // ── per-source media transformers ──────────────────────────────────────

    /**
     * Segment transformers for [sourceId], delegating to the runner's
     * `interceptSegmentUrl` / `interceptSegmentData` exports. Created once per
     * stream resolution and invoked on OkHttp media threads (never main).
     * Returns null when the source hasn't been loaded yet.
     */
    fun segmentUrlInterceptorFor(sourceId: String): SegmentUrlInterceptor? {
        val runner = registry.runnerOrNull(sourceId) ?: return null
        return SegmentUrlInterceptor { streamData, requestUrl ->
            runner.interceptSegmentUrl(streamData?.toRunner(), requestUrl)
        }
    }

    fun segmentDataInterceptorFor(sourceId: String): SegmentDataInterceptor? {
        val runner = registry.runnerOrNull(sourceId) ?: return null
        return SegmentDataInterceptor { streamData, segmentUrl, data ->
            runner.interceptSegmentData(streamData?.toRunner(), segmentUrl, data)
        }
    }

    // ── anime / episodes / streams ─────────────────────────────────────────

    /**
     * The primary API to fetch missing data for an Anime. Upgrades a Lite card
     * (id + sourceId only) to full via the runner's `animeUpdate`.
     * Falls back to the input (unchanged) when the source can't be loaded.
     */
    suspend fun getAnimeUpdate(
        anime: Anime,
        needsDetails: Boolean,
        needsChapters: Boolean
    ): Anime {
        return registry.call(anime.sourceId) { runner ->
            runner.animeUpdate(anime.toRunner(), needsDetails, needsChapters).toAppModel()
        } ?: anime
    }

    suspend fun getStreamList(anime: Anime, episode: Episode): List<StreamInfo> {
        return registry.call(anime.sourceId) { runner ->
            runner.streamList(anime.toRunner(), episode.toRunner()).map { it.toAppModel() }
        } ?: emptyList()
    }

    suspend fun getStream(anime: Anime, episode: Episode, stream: StreamInfo): StreamData {
        return registry.call(anime.sourceId) { runner ->
            runner.stream(anime.toRunner(), episode.toRunner(), stream.toRunner()).toAppModel()
        } ?: throw IllegalStateException("Source unavailable: ${anime.sourceId}")
    }

    // ── deep links ─────────────────────────────────────────────────────────

    /**
     * Asks [sourceId]'s `handle_deep_link` export whether it recognizes [url].
     * Returns the app-model [DeepLinkTarget], or null when the source doesn't
     * handle the URL (or the source can't be loaded).
     */
    suspend fun handleDeepLink(sourceId: String, url: String): DeepLinkTarget? {
        return registry.call(sourceId) { runner ->
            runner.deepLink(url)?.toAppModel()
        }
    }

    /**
     * Asks [sourceId]'s `handle_anime_migration` to map [key] to its current
     * form (null when the source can't be loaded).
     */
    suspend fun migrateAnime(sourceId: String, key: String): String? {
        return registry.call(sourceId) { runner -> runner.migrateAnime(key) }
    }

    /**
     * Asks [sourceId]'s `handle_episode_migration` to map [episodeKey] under
     * the (old) [animeKey] to its current form.
     */
    suspend fun migrateEpisode(sourceId: String, animeKey: String, episodeKey: String): String? {
        return registry.call(sourceId) { runner -> runner.migrateEpisode(animeKey, episodeKey) }
    }

    // ── search ─────────────────────────────────────────────────────────────

    /**
     * The source's search filters — a 1:1 mirror of the runner's `filters()`
     * export. Used by the Search screen and by [searchMultiSource] to translate
     * a genre selection into the source's own filter ids.
     */
    suspend fun getFilters(sourceId: String): List<Filter> {
        return registry.call(sourceId) { it.filters().map { f -> f.toAppModel() } } ?: emptyList()
    }

    /**
     * Paginated per-source search — a 1:1 mirror of the runner's `search()`
     * export. Returns a page of Lite [Anime] cards (upgrade with
     * [getAnimeUpdate] before stream access). Runs on the source's own thread.
     */
    suspend fun search(
        sourceId: String,
        query: String?,
        page: Int,
        selected: List<FilterValue> = emptyList(),
    ): KrxPage<Anime> {
        return registry.call(sourceId) { runner ->
            runner.search(query, page, selected.map { it.toRunner() }).toAppPage()
        } ?: KrxPage(emptyList(), false)
    }

    /**
     * Parallel multi-source search. Each source is queried on its own thread;
     * results keyed by their [Source]. A genre selection is translated into the
     * source's own genre filter id/options (exposed by `filters()`).
     *
     * The candidate sources can be narrowed before querying:
     * [contentRating] restricts by the source's manifest rating (`0` = safe-only,
     * `> 0` = 18+-only, `null` = all), [languages] keeps sources publishing at
     * least one of the codes (empty = all) and [sourceIds] whitelists sources
     * by id (empty = all non-aggregator sources).
     */
    suspend fun searchMultiSource(
        query: String,
        selectedGenreId: String? = null,
        contentRating: Int? = null,
        languages: Set<String> = emptySet(),
        sourceIds: Set<String> = emptySet(),
    ): Map<Source, List<Anime>> {
        val genreName = selectedGenreId?.let { id -> genres.find { it.id == id }?.name }
        val candidateSources = sources.filter { !it.isAggregator }
            .filter { sourceIds.isEmpty() || it.id in sourceIds }
            .filter { contentRating == null || ratingMatches(it.contentRating, contentRating) }
            .filter { languages.isEmpty() || it.languages.any { lang -> lang in languages } }

        return coroutineScope {
            candidateSources.map { source ->
                async {
                    val genreValues = genreName?.let { name -> buildGenreFilter(source.id, name) }
                        ?: emptyList()
                    source to search(source.id, query.ifBlank { null }, 1, genreValues).entries
                }
            }.awaitAll()
                .filter { (_, animes) -> animes.isNotEmpty() }
                .toMap()
        }
    }

    /** [filterRating] `0` = safe-only, anything else = 18+-only. */
    private fun ratingMatches(sourceRating: Int, filterRating: Int): Boolean =
        if (filterRating == 0) sourceRating == 0 else sourceRating > 0

    private suspend fun buildGenreFilter(sourceId: String, genreName: String): List<FilterValue> {
        val genreFilter = getFilters(sourceId).firstOrNull { f ->
            when (val k = f.kind) {
                is git.shin.komorei.model.FilterKind.Select -> k.isGenre
                is git.shin.komorei.model.FilterKind.MultiSelect -> k.isGenre
                else -> false
            }
        } ?: return emptyList()
        return when (val kind = genreFilter.kind) {
            is git.shin.komorei.model.FilterKind.MultiSelect ->
                listOf(FilterValue.MultiSelect(genreFilter.id, listOf(genreName), emptyList()))
            is git.shin.komorei.model.FilterKind.Select ->
                listOf(FilterValue.Select(genreFilter.id, genreName))
            else -> emptyList()
        }
    }

    // ── listings ────────────────────────────────────────────────────────────

    /**
     * The source's dynamic listings — a 1:1 mirror of the runner's `listings()`
     * export. These are the named, filterable catalogs ("Mới nhất", "Phổ biến",
     * "Đang phát", "Hoàn thành", ...) a source can paginate through with
     * [getListing]. Runs on the source's own thread.
     *
     * The "all" aggregator unions every bundled source's listings, deduped by id.
     */
    suspend fun getListings(sourceId: String): List<Listing> {
        if (sourceId == AGGREGATOR_ID) {
            return coroutineScope {
                sources.filter { !it.isAggregator }.map { s ->
                    async { getListings(s.id) }
                }.awaitAll().flatten().distinctBy { it.id }
            }
        }
        return registry.call(sourceId) { it.listings().map { l -> l.toAppModel() } } ?: emptyList()
    }

    /**
     * A single page of a [Listing] — a 1:1 mirror of the runner's
     * `animeList(listing, page)` export. Returns a page of Lite [Anime] cards
     * (upgrade with [getAnimeUpdate] before stream access). Runs on the
     * source's own thread.
     *
     * The "all" aggregator has no runner of its own: it asks every bundled
     * source (in parallel, on their own threads) for the same listing+page and
     * merges the results, deduped by id.
     */
    suspend fun getListing(sourceId: String, listing: Listing, page: Int): KrxPage<Anime> {
        if (sourceId == AGGREGATOR_ID) {
            return coroutineScope {
                sources.filter { !it.isAggregator }.map { s ->
                    async { getListing(s.id, listing, page) }
                }.awaitAll().let { pages ->
                    KrxPage(
                        entries = pages.flatMap { it.entries }.distinctBy { it.id },
                        hasNextPage = pages.any { it.hasNextPage },
                    )
                }
            }
        }
        return registry.call(sourceId) { runner ->
            runner.animeList(listing.toRunner(), page).toAppPage()
        } ?: KrxPage(emptyList(), false)
    }

    // ── settings ────────────────────────────────────────────────────────────

    /**
     * The source's dynamic settings (`get_settings`), with the CURRENT value
     * overlaid onto each description: `get_settings` only describes the
     * settings, while their actual values live in the Krx defaults store
     * ([KrxDefaultsStore], SQLite — the source reads them through its
     * `defaults_get` import).  For every setting key with a persisted value,
     * that value replaces the description's `default` — so a freshly written
     * setting shows up immediately on reload even if the source hardcodes its
     * description default.  Runs on the source's thread.
     */
    suspend fun getSettings(sourceId: String): List<SourceSetting> {
        return registry.call(sourceId) { runner ->
            runner.settings().map { s ->
                s.toAppModel().withPersistedValues { key -> registry.defaultsGet(sourceId, key) }
            }
        } ?: emptyList()
    }

    /** Drops the cached `home()` layout for [sourceId] — the next read re-fetches. */
    fun clearCachedHome(sourceId: String) {
        homeCache.remove(sourceId)
    }

    /**
     * Forwards [notification] to source [sourceId]'s `handle_notification`
     * (the NotificationHandler round-trip). The app sends it after every
     * setting change that declares a `notification` value — Aidoku calls
     * `source.handleNotification(notification)` the same way — so the source
     * can react (e.g. clear caches, resync). No-op when the source does not
     * register the trait (the wasm export simply doesn't exist).
     */
    suspend fun handleNotification(sourceId: String, notification: String) {
        registry.call(sourceId) { it.notify(notification) }
    }

    // Overlays the persisted pref value (if any) onto a setting description,
    // recursing into group/page children. `defaults` is a live read of the
    // host store — the same one the source sees through its `defaults_get`.
    private fun SourceSetting.withPersistedValues(defaults: (String) -> HostDefaultValue?): SourceSetting {
        var value = value.overlayPersisted(defaults(key))
        value = when (value) {
            is SourceSettingValue.Group -> value.copy(items = value.items.map { it.withPersistedValues(defaults) })
            is SourceSettingValue.Page -> value.copy(items = value.items.map { it.withPersistedValues(defaults) })
            else -> value
        }
        return if (value === this.value) this else copy(value = value)
    }

    private fun SourceSettingValue.overlayPersisted(pref: HostDefaultValue?): SourceSettingValue {
        if (pref == null) return this
        return when (this) {
            is SourceSettingValue.Toggle -> {
                val v = (pref as? HostDefaultValue.Bool)?.v1 ?: return this
                if (v == default) this else copy(default = v)
            }
            is SourceSettingValue.Select -> {
                val v = (pref as? HostDefaultValue.String)?.v1 ?: return this
                if (v == default) this else copy(default = v)
            }
            is SourceSettingValue.Picker -> {
                val v = (pref as? HostDefaultValue.String)?.v1 ?: return this
                if (v == default) this else copy(default = v)
            }
            is SourceSettingValue.Segment -> {
                val v = (pref as? HostDefaultValue.Int)?.v1 ?: return this
                if (v == default) this else copy(default = v)
            }
            is SourceSettingValue.MultiSelect -> {
                val v = (pref as? HostDefaultValue.StringArray)?.v1 ?: return this
                if (v == default) this else copy(default = v)
            }
            is SourceSettingValue.Stepper -> {
                val v = (pref as? HostDefaultValue.Float)?.v1?.toDouble() ?: return this
                if (v == default) this else copy(default = v)
            }
            is SourceSettingValue.Text -> {
                val v = (pref as? HostDefaultValue.String)?.v1 ?: return this
                if (v == default) this else copy(default = v)
            }
            is SourceSettingValue.EditableList -> {
                val v = (pref as? HostDefaultValue.StringArray)?.v1 ?: return this
                if (v == default) this else copy(default = v)
            }
            else -> this
        }
    }

    // ── home ───────────────────────────────────────────────────────────────

    /**
     * Home layouts are cached per source so repeated reads reuse ONE `home()`
     * wasm call (Home tab + player related list both consume them).
     */
    private val homeCache = ConcurrentHashMap<String, CompletableDeferred<List<HomeComponent>>>()

    /**
     * The FULL home layout of a source — a lossless mirror of the runner's
     * `home()` (`get_home`) export: every [HomeComponent] row with its original
     * [HomeComponentValue] (BigScroller / AnimeEpisodeList / AnimeList /
     * Scroller / ImageScroller / Filters / Links), in order. "all" merges every
     * source's components (each queried in parallel on its own thread).
     */
    suspend fun getHome(sourceId: String): List<HomeComponent> {
        if (sourceId == AGGREGATOR_ID) {
            return coroutineScope {
                sources.filter { !it.isAggregator }.map { s -> async { getHome(s.id) } }
                    .awaitAll().flatten()
            }
        }
        homeCache[sourceId]?.let { return it.await() }
        val deferred = CompletableDeferred<List<HomeComponent>>()
        val existing = homeCache.putIfAbsent(sourceId, deferred)
        if (existing != null) return existing.await()
        return try {
            val components = registry.call(sourceId) { runner ->
                runner.home().components.map { it.toAppModel() }
            } ?: emptyList()
            deferred.complete(components)
            components
        } catch (e: Exception) {
            homeCache.remove(sourceId, deferred)
            deferred.complete(emptyList()) // degrade: a failing source won't crash the Home tab
            emptyList()
        }
    }

    /**
     * Featured anime for the Home tab — the `BigScroller` components of
     * [getHome]. "all" aggregates every source (in parallel, cached).
     */
    suspend fun getFeaturedAnime(sourceId: String): List<Anime> {
        return getHome(sourceId)
            .flatMap { comp -> (comp.value as? HomeComponentValue.BigScroller)?.entries ?: emptyList() }
            .distinctBy { it.id }
    }

    /**
     * Grouped sections for the Home hub, built from [getHome]: anime rails
     * (AnimeEpisodeList / AnimeList / Scroller) keyed by component title.
     * "all" merges same-named sections across sources.
     */
    suspend fun getSectionsForSource(sourceId: String): Map<String, List<Anime>> {
        val combined = linkedMapOf<String, MutableList<Anime>>()
        getHome(sourceId).forEach { comp ->
            val title = comp.title ?: return@forEach
            val items: List<Anime> = when (val v = comp.value) {
                is HomeComponentValue.AnimeEpisodeList -> v.entries.map { it.anime }
                is HomeComponentValue.AnimeList -> v.entries.mapNotNull { it.anime }
                is HomeComponentValue.Scroller -> v.entries.mapNotNull { it.anime }
                else -> return@forEach
            }
            if (items.isEmpty()) return@forEach
            combined.getOrPut(title) { mutableListOf() }.addAll(items)
        }
        return combined.mapValues { (_, list) -> list.distinctBy { it.id } }
    }

    /** Merged home data across all sources (used for the player's related list). */
    suspend fun allAnimes(): List<Anime> = aggregateSources { sourceId ->
        getFeaturedAnime(sourceId) + getSectionsForSource(sourceId).values.flatten()
    }

    private suspend fun aggregateSources(block: suspend (String) -> List<Anime>): List<Anime> {
        return coroutineScope {
            sources.filter { !it.isAggregator }.map { s ->
                async { block(s.id) }
            }.awaitAll().flatten().distinctBy { it.id }
        }
    }

    /**
     * Locates a Lite anime card by id across all sources (page through each
     * source's `search(null, ...)` until found). Used by process-death restore.
     */
    suspend fun findAnimeById(animeId: String): Anime? {
        val ids = sources.filter { !it.isAggregator }.map { it.id }
        for (id in ids) {
            var page = 1
            while (true) {
                val result = registry.call(id) { runner ->
                    runner.search(null, page, emptyList())
                } ?: break
                val found = result.entries.firstOrNull { it.key == animeId }
                if (found != null) return found.toAppModel()
                if (!result.hasNextPage) break
                page++
            }
        }
        return null
    }
}