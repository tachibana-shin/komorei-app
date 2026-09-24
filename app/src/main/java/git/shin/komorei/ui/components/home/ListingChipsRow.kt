package git.shin.komorei.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.model.Listing
import git.shin.komorei.ui.components.shimmerEffect
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.tv.tvFocus

/**
 * The Aidoku "listings header" — a horizontal rail of pill chips above a
 * source's home content: `[Trang chủ] listings[0] listings[1] …`, driven by
 * the runner's `listings()` (`get_dynamic_listings`). Tapping a listing chip
 * swaps the content below to that listing; the HOME chip restores the home.
 *
 * `selectedIndex` is 0-based over `[HOME] + listings` (0 = home), mirroring
 * Aidoku's `ListingsHeaderView`.
 */
@Composable
fun ListingChipsRow(
    listings: List<Listing>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (listings.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
    ) {
        item(key = "_home") {
            ListingChip(
                label = stringResource(R.string.tab_home),
                active = selectedIndex == 0,
                onClick = { onSelect(0) },
            )
        }
        itemsIndexed(listings, key = { _, listing -> listing.id }) { index, listing ->
            ListingChip(
                label = listing.name,
                active = selectedIndex == index + 1,
                onClick = { onSelect(index + 1) },
            )
        }
    }
}

@Composable
private fun ListingChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(100)
    // Plain clickable Box/Text — NO Material `Surface(onClick)`, whose
    // `minimumInteractiveComponentSize()` touch-target floor pins every pill to
    // 48dp regardless of padding (that's what made the chips look "fixed
    // height" and too tall).
    Text(
        text = label,
        color = if (active) Color.White else TextPrimary,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            // TV focus highlight (no-op on phones); ring drawn around the pill.
            .tvFocus(shape = RoundedCornerShape(100), scale = 1.08f)
            .clip(shape)
            .background(if (active) AnimeRed else SurfaceDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Grey placeholder pills shown while `listings()` is still loading. */
@Composable
fun ListingChipsSkeleton(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        repeat(3) {
            Row(
                modifier = Modifier
                    .width(64.dp)
                    .height(32.dp)
                    .shimmerEffect(RoundedCornerShape(100)),
            ) {}
        }
    }
}