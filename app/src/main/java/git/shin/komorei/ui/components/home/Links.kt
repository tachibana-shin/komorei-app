package git.shin.komorei.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.model.Link
import git.shin.komorei.ui.components.SectionHeader
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary

/**
 * The `Links` home component (mirrors the runner + the Aidoku reference):
 * a vertical list of plain links — title + optional subtitle + chevron, one
 * card per row. Tapping a row dispatches [onLinkClick] with the link, and the
 * caller decides what its [androidx.compose.foundation.clickable] value opens
 * (a Listing → the Listing screen, an Anime → the player, a Url → display-only).
 */
@Composable
fun LinksRow(
    title: String?,
    links: List<Link>,
    modifier: Modifier = Modifier,
    onLinkClick: (Link) -> Unit = {},
) {
    if (links.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title ?: "")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            links.forEach { link ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .clickable { onLinkClick(link) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = link.title,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (link.subtitle != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = link.subtitle,
                                color = TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}