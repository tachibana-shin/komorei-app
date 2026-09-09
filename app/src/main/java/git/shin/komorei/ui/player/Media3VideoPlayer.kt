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
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun Media3VideoPlayer(
    videoUrl: String,
    isPlaying: Boolean,
    onPositionChanged: (currentMs: Long, durationMs: Long, bufferedMs: Long) -> Unit,
    onPlayerReady: (ExoPlayer) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
        }
    }

    // Set media item when videoUrl changes
    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotBlank()) {
            val mediaItem = MediaItem.fromUri(videoUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            onPlayerReady(exoPlayer)
        }
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
