package git.shin.komorei.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import git.shin.komorei.KomoreiApplication
import git.shin.komorei.data.remote.SegmentDataInterceptor
import git.shin.komorei.data.remote.SegmentUrlInterceptor
import git.shin.komorei.model.StreamData
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun Media3VideoPlayer(
    streamData: StreamData?,
    isPlaying: Boolean,
    onPositionChanged: (currentMs: Long, durationMs: Long, bufferedMs: Long) -> Unit,
    onPlayerReady: (ExoPlayer) -> Unit = {},
    segmentUrlInterceptor: SegmentUrlInterceptor? = null,
    segmentDataInterceptor: SegmentDataInterceptor? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Shared OkHttp backed factory: injects stream headers + optional segment transformers.
    val dataSourceFactory = remember {
        (context.applicationContext as KomoreiApplication).dataSourceFactory
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
        }
    }

    // (Re)build the media source whenever the resolved stream changes.
    LaunchedEffect(streamData, segmentUrlInterceptor, segmentDataInterceptor) {
        val sd = streamData
        if (sd == null || sd.url.isBlank() || !sd.isContent) {
            // Nothing playable yet: URL needs source-side resolution (isContent == false)
            // or we're still waiting for the first getStream(...) result.
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            return@LaunchedEffect
        }

        // Headers (Referer, Cookie, User-Agent...) + optional segment transformers.
        dataSourceFactory.configure(sd, segmentUrlInterceptor, segmentDataInterceptor)

        // DefaultMediaSourceFactory auto-detects HLS vs progressive; both go through
        // our data source factory so headers/transformers apply to every sub-request.
        val sourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        exoPlayer.setMediaSource(sourceFactory.createMediaSource(MediaItem.fromUri(sd.url)))
        exoPlayer.prepare()
        exoPlayer.play()
        onPlayerReady(exoPlayer)
    }

    // Sync play/pause state
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Periodic progress updates
    LaunchedEffect(exoPlayer) {
        while (true) {
            val curPos = exoPlayer.currentPosition
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            val buf = exoPlayer.bufferedPosition
            onPositionChanged(curPos, dur, buf)
            delay(400)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false // Custom overlay is used
                player = exoPlayer
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { playerView ->
            if (playerView.player != exoPlayer) {
                playerView.player = exoPlayer
            }
        },
        modifier = modifier.fillMaxSize()
    )
}