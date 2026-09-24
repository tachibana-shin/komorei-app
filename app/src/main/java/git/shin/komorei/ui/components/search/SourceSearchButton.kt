package git.shin.komorei.ui.components.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import git.shin.komorei.R
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.tv.tvFocus

/**
 * The search entry button on a source's home content — a circular magnifier
 * pill sitting directly before the listing chips (Aidoku sources surface
 * search from the browse header). Opens the per-source search screen.
 */
@Composable
fun SourceSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(32.dp)
            // TV focus highlight (no-op on phones); the small circle magnifier
            // scales noticeably so the ring reads at viewing distance.
            .tvFocus(shape = CircleShape, scale = 1.2f, borderWidth = 2.dp)
            .clip(shape)
            .background(SurfaceDark)
            .border(width = 1.dp, color = CardBorderDark, shape = shape)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = stringResource(R.string.source_search_entry_cd),
            tint = TextPrimary,
            modifier = Modifier.size(17.dp),
        )
    }
}