package git.shin.komorei.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Episode
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary

@Composable
fun MiniPlayer(
    anime: Anime,
    episode: Episode,
    isPlaying: Boolean,
    progressFraction: Float,
    onExpand: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(SurfaceDark)
            .clickable(onClick = onExpand)
            .testTag("mini_player")
    ) {
        // Subtle red progress bar line along the top edge of the mini player
        LinearProgressIndicator(
            progress = { progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp),
            color = AnimeRed,
            trackColor = Color(0x33FFFFFF)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Thumbnail / small preview on the left (16:9 ratio)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 6.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CardDark)
            ) {
                AsyncImage(
                    model = anime.bannerUrl.ifBlank { anime.posterUrl },
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Playing badge
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(AnimeRed)
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.badge_live),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Center: Anime Title + Episode title
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = anime.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = episode.title,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: Play/Pause Button
            IconButton(
                onClick = onPlayPauseToggle,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("mini_player_play_pause")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) {
                        stringResource(R.string.cd_pause)
                    } else {
                        stringResource(R.string.cd_play)
                    },
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Right: Close Button (X) to dismiss completely
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("mini_player_close")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_close_player),
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
