package git.shin.komorei.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.KomoreiApplication
import git.shin.komorei.R
import git.shin.komorei.data.AnimeRepository
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
    private val repository: AnimeRepository
) : ViewModel() {

    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    val playbackState: StateFlow<PlayerPlaybackState> = _playbackState.asStateFlow()

    val allAnimes: List<Anime> = repository.sources.flatMap { src ->
        repository.getFeaturedAnime(src.id) + repository.getSectionsForSource(src.id).values.flatten()
    }.distinctBy { it.id }

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
                delay(250)
            }
        }
    }

    fun openAnime(anime: Anime, episode: Episode? = null) {
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

        // Resolve servers + first stream for the target episode.
        viewModelScope.launch { loadStreams(anime, targetEp) }
    }

    fun setPlayerSheetValue(sheetValue: PlayerSheetValue) {
        _playbackState.update { it.copy(sheetValue = sheetValue) }
    }

    /**
     * Play/pause the real engine. The mirrored [PlayerPlaybackState.isPlaying] is
     * updated by the player listener, so the UI can never drift from the engine.
     */
    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
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
        if (group.type == C.TRACK_TYPE_VIDEO) {
            // Remember the forced rendition so the quality picker can tell it from Auto
            // (Tracks.isTrackSelected stays true for MANY renditions in adaptive mode).
            _playbackState.update { it.copy(videoTrackOverride = group.getTrackFormat(trackIndex)) }
        }
    }

    fun clearTrackType(type: Int) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(type)
            .build()
        if (type == C.TRACK_TYPE_VIDEO) {
            _playbackState.update { it.copy(videoTrackOverride = null) }
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

    fun selectEpisode(episode: Episode) {
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
    }

    /**
     * User picks a streaming server for the current episode.
     */
    fun selectStream(stream: StreamInfo) {
        val state = _playbackState.value
        val anime = state.fullAnime ?: state.currentAnime ?: return
        val episode = state.currentEpisode ?: return

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
    private suspend fun loadStreams(anime: Anime, episode: Episode) {
        _playbackState.update { it.copy(isLoadingStreams = true, streamError = null, error = null) }
        runCatching {
            val full = repository.getAnimeUpdate(anime, needsDetails = true, needsChapters = false)
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
                    segmentUrlInterceptor = repository.segmentUrlInterceptor,
                    segmentDataInterceptor = repository.segmentDataInterceptor,
                    introRange = resolved?.intro?.let { it.startMs..it.endMs },
                    outroRange = resolved?.outro?.let { it.startMs..it.endMs }
                )
            }

            resolved?.let { buildMediaSource(it) }
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
        viewModelScope.launch { loadStreams(anime, episode) }
    }

    fun dismissPlayer() {
        exoPlayer.pause()
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
                isFullscreen = false
            )
        }
    }

    fun toggleBookmark(animeId: String) {
        // Handled by AnimeDetailViewModel or LibraryViewModel now
    }

    override fun onCleared() {
        positionPoller?.cancel()
        exoPlayer.release()
        super.onCleared()
    }
}