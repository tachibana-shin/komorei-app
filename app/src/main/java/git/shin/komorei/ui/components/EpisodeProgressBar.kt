package git.shin.komorei.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import git.shin.komorei.ui.theme.AnimeRed

/**
 * A slim progress bar to indicate how much of an episode has been watched.
 */
@Composable
fun EpisodeProgressBar(
    progress: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    color: Color = AnimeRed,
    backgroundColor: Color = Color.Gray.copy(alpha = 0.3f)
) {
    if (progress <= 0f) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color)
        )
    }
}
