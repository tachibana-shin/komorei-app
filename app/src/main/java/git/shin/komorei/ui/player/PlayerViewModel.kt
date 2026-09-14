package git.shin.komorei.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.KomoreiApplication
import git.shin.komorei.R
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.LibraryRepository
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Episode
import git.shin.komorei.model.StreamData
import git.shin.komorei.model.StreamInfo
import git.shin.komorei.model.StreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import javax.inject.Inject
import java.io.File

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: AnimeRepository,
    private val libraryRepository: LibraryRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Process-death persistence ("savedState"): the open session is mirrored into the
     * SavedStateHandle, so when the app is backgrounded long enough to be recreated
     * (or rotated on config-keyless flows) it restores to the exact episode + sheet
     * state the user left. The playhead itself resumes via Room watch history
     * (restoreWatchTime → LibraryRepository.getWatchTime).
     */
    private companion object {
        const val KEY_ANIME_ID = "player_anime_id"
        const val KEY_EPISODE_ID = "player_episode_id"
        const val KEY_SHEET_VALUE = "player_sheet_value"
    }

    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    val playbackState: StateFlow<PlayerPlaybackState> = _playbackState.asStateFlow()

    /**
     * Related-anime list fed to the detail view. Loaded asynchronously from the
     * sources' home payloads (wasm calls run on per-source IO threads — never
     * main); starts empty and fills in as home data arrives. Initialized here,
     * recomposition updates via [VideoPlayerSheet]'s collectAsState.
     */
    private val _allAnimes = MutableStateFlow<List<Anime>>(emptyList())
    val allAnimes: StateFlow<List<Anime>> = _allAnimes.asStateFlow()

    // Shared OkHttp-backed factory: injects stream headers + optional segment transformers.
    // Lives in the application graph so WebView cookies & the shared HttpClient stay consistent.
    private val dataSourceFactory =
        (appContext as KomoreiApplication).dataSourceFactory

    // The single playback engine, owned by the ViewModel so the UI (sheet, mini player,
    // fullscreen, controls) all drive one source of truth instead of competing players.
    private val exoPlayer = ExoPlayer.Builder(appContext).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
        playWhenReady = true
    }

    val player: ExoPlayer get() = exoPlayer

    private var positionPoller: Job? = null

    // Position polls every 250ms. Autosave policy (user-requested): the first periodic
    // save fires only once playback has advanced >= 3s into the episode (avoids
    // clobbering a good resume point with a sub-second snapshot); later saves are
    // spaced >= 10s apart. Boundary saves (STATE_ENDED / pause / switch / dismiss)
    // write immediately and reset the periodic clock via saveWatchTime(), so a
    // periodic save never fires within 10s of an explicit one.
    private val FIRST_SAVE_POSITION_MS = 3_000L
    private val SAVE_INTERVAL_MS = 10_000L
    private var lastSaveUptimeMs: Long? = null

    // Auto-resume target, applied when the freshly built source becomes READY. seekTo
    // right after prepare() is unreliable: the source/period is created asynchronously
    // with the default position (0), and the seek can be silently dropped — so the
    // restore is deferred until the timeline is actually loaded.
    private var pendingRestorePositionMs: Long? = null

    // Speed to restore after a "hold to fast-forward" gesture ends.
    private var fastForwardBaseSpeed: Float? = null

    init {
        // Mirror real player state (playing / buffering / errors) back into the UI state.
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playbackState.update {
                    it.copy(isLoading = playbackState == Player.STATE_BUFFERING)
                }
                // Apply a pending auto-resume now the timeline is actually loaded —
                // seeking before Media3 has the source prepared can be discarded.
                if (playbackState == Player.STATE_READY) {
                    pendingRestorePositionMs?.let { exoPlayer.seekTo(it) }
                    pendingRestorePositionMs = null
                }
                // Episode finished → mark it watched + auto-play the next one (settings toggle).
                if (playbackState == Player.STATE_ENDED) {
                    saveWatchTime()
                    autoPlayNextEpisode()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playbackState.update {
                    it.copy(error = appContext.getString(R.string.error_source_playback))
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                _playbackState.update { it.copy(availableTracks = tracks) }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _playbackState.update { it.copy(videoSize = videoSize) }
            }
        })

        // Poll position/duration/buffer so the timeline scrubber stays live without
        // depending on UI-driven callbacks (which previously were no-ops).
        positionPoller = viewModelScope.launch {
            while (isActive) {
                _playbackState.update {
                    it.copy(
                        currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                        durationMs = exoPlayer.duration.coerceAtLeast(0L),
                        bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                    )
                }
                // Auto-save watch time (write-side of the watch-history feature) so
                // progress survives crashes/close and the resume position stays
                // accurate. First save only once playback has advanced >= 3s into the
                // piece; later saves at least 10s apart. STATE_ENDED / pause / switch /
                // dismiss save via saveWatchTime() directly (no spacing gate) and reset
                // lastSaveUptimeMs there, so the next periodic save still respects the
                // 10s gap.
                if (exoPlayer.isPlaying) {
                    val last = lastSaveUptimeMs
                    val due = if (last == null) {
                        exoPlayer.currentPosition.coerceAtLeast(0L) >= FIRST_SAVE_POSITION_MS
                    } else {
                        SystemClock.uptimeMillis() - last >= SAVE_INTERVAL_MS
                    }
                    if (due) saveWatchTime()
                }
                delay(250)
            }
        }

        // Preload the merged home catalog for the related-anime list (off-thread).
        viewModelScope.launch { _allAnimes.value = repository.allAnimes() }

        // Reopen the session that was playing when this process died (if any). The
        // engine is rebuilt from scratch; position restore happens via Room watch
        // history once the fresh source becomes READY (restoreWatchTime).
        restoreSession()
    }

    fun openAnime(anime: Anime, episode: Episode? = null) {
        // Persist whatever was playing before switching (periodic saves cover this too,
        // but save at the boundary so the switch is never lossy).
        saveWatchTime()

        val targetEp = episode ?: anime.episodes.firstOrNull() ?: Episode(
            id = "${anime.id}_ep_1",
            animeId = anime.id,
            sourceId = anime.sourceId,
            episodeNumber = "1",
            title = appContext.getString(R.string.episode_fallback_title)
        )

        val durationMs = (targetEp.durationSeconds ?: 1440L) * 1000L

        _playbackState.update { current ->
            current.copy(
                currentAnime = anime,
                currentEpisode = targetEp,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = durationMs,
                sheetValue = PlayerSheetValue.EXPANDED,
                isFullscreen = false,
                streams = emptyList(),
                selectedStreamId = null,
                streamData = null,
                streamError = null,
                error = null
            )
        }

        // Mirror the session into the SavedStateHandle so a process death restores it.
        savedStateHandle[KEY_ANIME_ID] = anime.id
        savedStateHandle[KEY_EPISODE_ID] = targetEp.id
        savedStateHandle[KEY_SHEET_VALUE] = PlayerSheetValue.EXPANDED.name

        // Resolve servers + first stream for the target episode.
        viewModelScope.launch { loadStreams(anime, targetEp) }
    }

    fun setPlayerSheetValue(sheetValue: PlayerSheetValue) {
        _playbackState.update { it.copy(sheetValue = sheetValue) }
        savedStateHandle[KEY_SHEET_VALUE] = sheetValue.name
    }

    /**
     * Play/pause the real engine. The mirrored [PlayerPlaybackState.isPlaying] is
     * updated by the player listener, so the UI can never drift from the engine.
     */
    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            saveWatchTime()
        } else {
            exoPlayer.play()
        }
    }

    /** Seek the real engine; the position poller syncs the timeline state. */
    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
        _playbackState.update { it.copy(playbackSpeed = speed) }
    }

    /**
     * "Hold to fast-forward" (YouTube-style): temporarily play at 2x, remembering
     * the previous speed so [stopFastForward] can restore it on release.
     */
    fun startFastForward() {
        if (fastForwardBaseSpeed == null) {
            fastForwardBaseSpeed = exoPlayer.playbackParameters.speed
        }
        exoPlayer.setPlaybackSpeed(2f)
        _playbackState.update { it.copy(playbackSpeed = 2f) }
    }

    fun stopFastForward() {
        val base = fastForwardBaseSpeed
        if (base != null) {
            exoPlayer.setPlaybackSpeed(base)
            _playbackState.update { it.copy(playbackSpeed = base) }
        }
        fastForwardBaseSpeed = null
    }

    // Orientation the screen was in before entering fullscreen, so exiting can restore it.
    private var orientationBeforeFullscreen: Int? = null

    fun toggleFullscreen() {
        val isFullscreen = _playbackState.value.isFullscreen
        _playbackState.update { it.copy(isFullscreen = !isFullscreen) }

        // YouTube-like: entering fullscreen forces landscape, exiting restores the
        // orientation the user had before (portrait on phones, whatever the device had).
        val activity = (appContext as KomoreiApplication).currentActivity ?: return
        if (!isFullscreen) {
            orientationBeforeFullscreen = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity.requestedOrientation = orientationBeforeFullscreen
                ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            orientationBeforeFullscreen = null
        }
    }

    fun toggleLock() {
        _playbackState.update { it.copy(isLocked = !it.isLocked) }
    }

    fun selectTrack(group: Tracks.Group, trackIndex: Int) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
        when (group.type) {
            C.TRACK_TYPE_VIDEO -> {
                // Remember the forced rendition so the quality picker can tell it from Auto
                // (Tracks.isTrackSelected stays true for MANY renditions in adaptive mode).
                _playbackState.update { it.copy(videoTrackOverride = group.getTrackFormat(trackIndex)) }
            }
            C.TRACK_TYPE_TEXT -> {
                // Picking a subtitle track turns subtitles ON — re-enable the text type if
                // the CC button had disabled it, so the CC icon stays in sync with the pane.
                _playbackState.update { it.copy(isSubtitleEnabled = true) }
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
            }
            else -> Unit
        }
    }

    fun clearTrackType(type: Int) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(type)
            .build()
        when (type) {
            C.TRACK_TYPE_VIDEO -> {
                _playbackState.update { it.copy(videoTrackOverride = null) }
            }
            C.TRACK_TYPE_TEXT -> {
                // "No subtitle" means subtitles OFF (not just "auto"): disable the text
                // type too so no track auto-selects and the CC icon flips off.
                _playbackState.update { it.copy(isSubtitleEnabled = false) }
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
            else -> Unit
        }
    }

    fun toggleSubtitles() {
        val newState = !_playbackState.value.isSubtitleEnabled
        _playbackState.update { it.copy(isSubtitleEnabled = newState) }
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !newState)
            .build()
    }

    /** Toggle auto-play of the next episode when the current one ends. */
    fun setAutoNextEnabled(enabled: Boolean) {
        _playbackState.update { it.copy(autoNextEnabled = enabled) }
    }

    /**
     * Auto-play the next episode after the current one reaches its end. Runs from
     * [Player.Listener.onPlaybackStateChanged] on STATE_ENDED; [fullAnime] carries the
     * current season's episode list (loadStreams fetches with needsChapters = true).
     */
    private fun autoPlayNextEpisode() {
        val state = _playbackState.value
        if (!state.autoNextEnabled) return
        val episodes = state.fullAnime?.episodes ?: return
        val current = state.currentEpisode ?: return
        val index = episodes.indexOfFirst { it.id == current.id }
        if (index in episodes.indices && index < episodes.size - 1) {
            selectEpisode(episodes[index + 1])
        }
    }

    /**
     * Persist the current episode's position + duration as watch history. Values are
     * captured synchronously so callers can save right before clearing/switching state
     * (openAnime, selectEpisode, dismissPlayer); the Room write happens async.
     */
    private fun saveWatchTime() {
        val state = _playbackState.value
        val anime = state.fullAnime ?: state.currentAnime ?: return
        val episode = state.currentEpisode ?: return
        val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        val durationMs = exoPlayer.duration.coerceAtLeast(0L)
        if (durationMs <= 0L) return
        viewModelScope.launch {
            libraryRepository.saveProgress(
                anime, episode,
                positionMs.coerceAtMost(durationMs), durationMs
            )
        }
        // Any save (periodic or boundary) restarts the >= 10s periodic clock so the
        // next autosave is never too close to an explicit one.
        lastSaveUptimeMs = SystemClock.uptimeMillis()
    }

    /**
     * Auto-resume the freshly built media source to the last saved watch position.
     * The actual seek is deferred until the engine reports STATE_READY (a seek issued
     * before the source is prepared can be dropped by Media3). Fully-watched episodes
     * (>= 95%) restart from the beginning instead of nagging. suspend because the
     * progress may later come from a remote watch history
     * (LibraryRepository.getWatchTime is already suspend for that seam).
     */
    private suspend fun restoreWatchTime() {
        val state = _playbackState.value
        val anime = state.fullAnime ?: state.currentAnime ?: return
        val episode = state.currentEpisode ?: return
        val savedMs = libraryRepository.getWatchTime(anime.id, anime.sourceId, episode.id) ?: return
        // null (no restore) when there's nothing to resume or the episode was watched
        // to the end — pending is cleared so no stale seek fires on the next media.
        val durationMs = exoPlayer.duration.coerceAtLeast(0L)
            .coerceAtLeast(state.durationMs)
        pendingRestorePositionMs = savedMs.takeIf {
            it > 0L && it < (durationMs * 0.95f).toLong()
        }
    }

    fun selectEpisode(episode: Episode) {
        // Persist the previous episode's position before switching away from it.
        saveWatchTime()

        val durationMs = (episode.durationSeconds ?: 1440L) * 1000L
        _playbackState.update { current ->
            current.copy(
                currentEpisode = episode,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = durationMs,
                streams = emptyList(),
                selectedStreamId = null,
                streamData = null,
                streamError = null,
                error = null,
                videoTrackOverride = null
            )
        }
        val anime = _playbackState.value.fullAnime ?: _playbackState.value.currentAnime ?: return
        viewModelScope.launch { loadStreams(anime, episode) }

        // Persist the switch target so a process death resumes the newly picked episode.
        savedStateHandle[KEY_EPISODE_ID] = episode.id
        savedStateHandle[KEY_SHEET_VALUE] = PlayerSheetValue.EXPANDED.name
    }

    /**
     * User picks a streaming server for the current episode.
     */
    fun selectStream(stream: StreamInfo) {
        val state = _playbackState.value
        val anime = state.fullAnime ?: state.currentAnime ?: return
        val episode = state.currentEpisode ?: return

        pendingRestorePositionMs = null
        _playbackState.update {
            it.copy(
                selectedStreamId = stream.id,
                streamData = null,
                isLoadingStreams = true,
                streamError = null,
                error = null,
                videoTrackOverride = null
            )
        }

        viewModelScope.launch {
            runCatching { repository.getStream(anime, episode, stream) }
                .onSuccess { resolved ->
                    _playbackState.update {
                        it.copy(
                            streamData = resolved,
                            selectedStreamId = stream.id,
                            isLoadingStreams = false,
                            streamError = null
                        )
                    }
                    // (Re)build the media source on the shared engine so headers &
                    // segment transformers from this stream apply to every request.
                    exoPlayer.playWhenReady = true
                    buildMediaSource(resolved)
                    restoreWatchTime()
                }
                .onFailure { e ->
                    _playbackState.update {
                        it.copy(
                            isLoadingStreams = false,
                            streamError = e.message ?: appContext.getString(R.string.error_source_playback)
                        )
                    }
                }
        }
    }

    /**
     * Resolve the streaming servers for [episode].
     *
     * The list-API [anime] is a Lite card with almost no data, so per source contract we
     * upgrade it via getAnimeUpdate(needsDetails = true, needsChapters = false) before
     * asking the source for servers. The first server is auto-resolved via getStream(...).
     */
    private suspend fun loadStreams(anime: Anime, episode: Episode, restorePosition: Boolean = true) {
        // Drop any stale restore target from a previous build before re-resolving.
        pendingRestorePositionMs = null
        _playbackState.update { it.copy(isLoadingStreams = true, streamError = null, error = null) }
        runCatching {
            val full = repository.getAnimeUpdate(anime, needsDetails = true, needsChapters = true)
            finishLoadStreams(full, episode, restorePosition)
        }.onFailure { e ->
            _playbackState.update {
                it.copy(
                    isLoadingStreams = false,
                    streamError = e.message ?: appContext.getString(R.string.error_source_playback)
                )
            }
        }
    }

    /**
     * Common tail of stream resolution (after the Lite→full upgrade): resolve servers,
     * pick the first as default, build the media source and optionally restore position.
     * Shared by [loadStreams] and [restoreSession] so a process-death restore reuses the
     * exact same pipeline.
     */
    private suspend fun finishLoadStreams(full: Anime, episode: Episode, restorePosition: Boolean) {
        val streams = repository.getStreamList(full, episode)
        val first = streams.firstOrNull()
        val resolved = first?.let { repository.getStream(full, episode, it) }

        _playbackState.update {
            it.copy(
                fullAnime = full,
                streams = streams,
                selectedStreamId = first?.id,
                streamData = resolved,
                isLoadingStreams = false,
                streamError = null,
                segmentUrlInterceptor = repository.segmentUrlInterceptorFor(full.sourceId),
                segmentDataInterceptor = repository.segmentDataInterceptorFor(full.sourceId),
                introRange = resolved?.intro?.let { it.startMs..it.endMs },
                outroRange = resolved?.outro?.let { it.startMs..it.endMs }
            )
        }

        resolved?.let {
            buildMediaSource(it)
            // Resume the episode where the user left off — but only on (re)selection.
            // A manual refresh re-resolves the SAME episode and must not rewind to the
            // older saved progress (the user is already partway through it).
            if (restorePosition) restoreWatchTime()
        }
    }

    /**
     * Configure the shared data source factory for [streamData] and load it on the
     * engine. DefaultMediaSourceFactory auto-detects HLS vs progressive MP4; both go
     * through our factory so headers/transformers apply to every sub-request.
     *
     * Raw-content sources ([StreamData.isContent]) can't be handed to Media3 as a URI
     * ("#EXTM3U..." is not an address), so their text is written to a cache file first
     * and played as a local file — the manifest is read from disk while its segments
     * keep fetching over HTTP through the shared factory (headers/transformers apply).
     */
    private suspend fun buildMediaSource(streamData: StreamData) {
        val state = _playbackState.value
        dataSourceFactory.configure(
            streamData,
            state.segmentUrlInterceptor,
            state.segmentDataInterceptor
        )

        val mediaItemBuilder = MediaItem.Builder()
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(state.currentAnime?.title)
                    .setSubtitle(state.currentEpisode?.title)
                    .setArtworkUri(state.currentAnime?.posterUrl?.let { android.net.Uri.parse(it) })
                    .build()
            )

        if (streamData.isContent) {
            // The url field holds raw content text -> cache file so the player can open it.
            val contentFile = withContext(Dispatchers.IO) { writeContentToCache(streamData) }
            mediaItemBuilder.setUri(android.net.Uri.fromFile(contentFile))
        } else {
            mediaItemBuilder.setUri(streamData.url)
        }

        if (streamData.subtitles.isNotEmpty()) {
            val subtitleConfigurations = streamData.subtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                    .setMimeType(MimeTypes.TEXT_VTT) // Defaulting to VTT for mock, real sources should provide mime
                    .setLanguage(sub.language)
                    .setLabel(sub.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigurations)
        }

        // Content played from a file still routes its segments over HTTP through our
        // header-injecting factory — DefaultDataSource dispatches file:// locally and
        // everything else to the base factory.
        val sourceFactory = DefaultMediaSourceFactory(
            if (streamData.isContent) {
                DefaultDataSource.Factory(appContext, dataSourceFactory)
            } else {
                dataSourceFactory
            }
        )
        exoPlayer.setMediaSource(sourceFactory.createMediaSource(mediaItemBuilder.build()))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    /**
     * Writes [StreamData.url] (the raw media content, e.g. an m3u8 playlist) into the app
     * cache with an extension matching [StreamData.type] so DefaultMediaSourceFactory can
     * infer the container and parse the manifest from disk. One file per content type,
     * re-written every resolve — no unbounded cache growth.
     */
    private fun writeContentToCache(streamData: StreamData): File {
        val extension = when (streamData.type) {
            StreamType.HLS -> "m3u8"
            StreamType.DASH -> "mpd"
            StreamType.MP4, StreamType.OTHER -> "mp4"
        }
        val dir = File(appContext.cacheDir, "komorei_content").apply { mkdirs() }
        return File(dir, "content_${streamData.type.name.lowercase()}.$extension").also { file ->
            file.writeText(streamData.url)
        }
    }

    /**
     * Re-resolves servers + first stream after a failure (streamError shown in the UI).
     * The player top bar "refresh" reuses this the same way.
     */
    fun retryStreams() {
        val state = _playbackState.value
        val anime = state.fullAnime ?: state.currentAnime ?: return
        val episode = state.currentEpisode ?: return
        viewModelScope.launch { loadStreams(anime, episode, restorePosition = false) }
    }

    /**
     * Reopens the session that was playing when this process was recreated (the
     * "savedState" piece): find the anime by id, upgrade it to full, locate the same
     * episode, carry over the sheet value (expanded / mini player) and re-resolve the
     * stream — the playhead resumes from Room watch history once the fresh source is
     * READY (restoreWatchTime). A stale/unknown session (removed anime, cleared keys
     * via dismissPlayer) just leaves the app open fresh.
     */
    private fun restoreSession() {
        val animeId = savedStateHandle.get<String>(KEY_ANIME_ID) ?: return
        val savedEpisodeId = savedStateHandle.get<String>(KEY_EPISODE_ID)
        val savedSheet = savedStateHandle.get<String>(KEY_SHEET_VALUE)
            ?.let { name -> runCatching { PlayerSheetValue.valueOf(name) }.getOrNull() }
            ?: PlayerSheetValue.EXPANDED

        viewModelScope.launch {
            runCatching {
                // Locate the Lite card across sources (id lookup is off-thread via
                // the registry — never on main). A missing source = stale session.
                val lite = repository.findAnimeById(animeId) ?: return@runCatching
                val anime = lite
                val full = repository.getAnimeUpdate(anime, needsDetails = true, needsChapters = true)
                val episode = savedEpisodeId?.let { id -> full.episodes.firstOrNull { it.id == id } }
                    ?: full.episodes.firstOrNull()
                    ?: return@runCatching
                val durationMs = (episode.durationSeconds ?: 1440L) * 1000L
                _playbackState.update {
                    it.copy(
                        currentAnime = anime,
                        currentEpisode = episode,
                        isPlaying = true,
                        currentPositionMs = 0L,
                        durationMs = durationMs,
                        sheetValue = savedSheet,
                        isFullscreen = false,
                        streams = emptyList(),
                        selectedStreamId = null,
                        streamData = null,
                        streamError = null,
                        error = null
                    )
                }
                finishLoadStreams(full, episode, restorePosition = true)
            }.onFailure {
                // Silent: a stale/unknown session simply opens the app normally.
            }
        }
    }

    fun dismissPlayer() {
        // Fully release the engine + model so nothing from the previous session leaks
        // into the next. A bare pause() leaves the old media source prepared on the
        // engine (frozen last frame, timeline + all track/videoSize state retained):
        // on reopen the player renders that stale episode for the ~1s the new stream
        // takes to resolve, and leftover state (streamData, intro/outro ranges, track
        // overrides...) mixes into the new session. stop + clearMediaItems drops the
        // source entirely; the model is reset so each open starts from a clean slate.
        // Persist the final position before releasing everything from the session.
        saveWatchTime()
        pendingRestorePositionMs = null
        // This session is over — drop the restore keys so a process death does NOT
        // reopen a player the user explicitly closed.
        savedStateHandle.remove<String>(KEY_ANIME_ID)
        savedStateHandle.remove<String>(KEY_EPISODE_ID)
        savedStateHandle.remove<String>(KEY_SHEET_VALUE)
        exoPlayer.playWhenReady = false
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        // If the player was closed while fullscreen, restore the previous orientation.
        if (_playbackState.value.isFullscreen) {
            (appContext as KomoreiApplication).currentActivity?.requestedOrientation =
                orientationBeforeFullscreen ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            orientationBeforeFullscreen = null
        }
        _playbackState.update {
            it.copy(
                isPlaying = false,
                sheetValue = PlayerSheetValue.HIDDEN,
                isFullscreen = false,
                currentAnime = null,
                currentEpisode = null,
                fullAnime = null,
                streams = emptyList(),
                selectedStreamId = null,
                streamData = null,
                isLoadingStreams = false,
                streamError = null,
                error = null,
                availableTracks = null,
                videoTrackOverride = null,
                videoSize = VideoSize.UNKNOWN,
                introRange = null,
                outroRange = null,
                currentPositionMs = 0L,
                durationMs = 0L,
                bufferedPositionMs = 0L
            )
        }
    }

    override fun onCleared() {
        positionPoller?.cancel()
        exoPlayer.release()
        super.onCleared()
    }
}