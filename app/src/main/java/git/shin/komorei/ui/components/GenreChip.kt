package git.shin.komorei.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.model.Genre
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.tv.tvFocus

@Composable
fun GenreGridCard(
    genre: Genre,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = Color(genre.accentColorHex)
    val backgroundBrush =
        if (isSelected) {
            Brush.horizontalGradient(
                colors = listOf(AnimeRed, accentColor),
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(CardDark, CardDark.copy(alpha = 0.85f)),
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(72.dp)
                // TV focus highlight (no-op on phones); small scale keeps the
                // chunked genre grid from visually overlapping neighbors.
                .tvFocus(shape = RoundedCornerShape(14.dp), scale = 1.03f)
                .clip(RoundedCornerShape(14.dp))
                .background(backgroundBrush)
                .border(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) Color.White.copy(alpha = 0.7f) else CardBorderDark,
                    shape = RoundedCornerShape(14.dp),
                ).clickable(onClick = onClick)
                .padding(12.dp)
                .testTag("genre_${genre.id}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = genre.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.anime_count_format, genre.count),
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextMuted,
                    fontSize = 11.sp,
                )
            }

            // Genre Icon Widget
            Icon(
                imageVector = AppIcons.getGenreIcon(genre.id),
                contentDescription = genre.name,
                tint = if (isSelected) Color.White else accentColor,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun GenreChipCompact(
    genre: Genre,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipBackground = if (isSelected) AnimeRed else CardDark
    val borderColor = if (isSelected) AnimeRed else CardBorderDark

    Row(
        modifier =
            modifier
                // TV focus highlight (no-op on phones) — pill ring.
                .tvFocus(shape = RoundedCornerShape(20.dp), scale = 1.08f)
                .clip(RoundedCornerShape(20.dp))
                .background(chipBackground)
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag("genre_chip_${genre.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.getGenreIcon(genre.id),
            contentDescription = genre.name,
            tint = if (isSelected) Color.White else AnimeRed,
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = genre.name,
            color = if (isSelected) Color.White else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
