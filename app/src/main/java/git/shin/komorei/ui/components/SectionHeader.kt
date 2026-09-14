package git.shin.komorei.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.TextPrimary

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    rightContent: (@Composable () -> Unit)? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        // When the section links to a listing, the WHOLE header is the tap
        // target (clip+clickable sit OUTSIDE the padding so the ripple covers
        // the full strip) and a chevron floats at the right end — no "Xem tất
        // cả" label. Without a link the header is inert and trailing-empty.
        modifier = modifier
            .fillMaxWidth()
            .let { m ->
                if (onSeeAll != null) {
                    m.clip(RoundedCornerShape(6.dp)).clickable(onClick = onSeeAll)
                } else {
                    m
                }
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon ?: AppIcons.getCategoryIcon(title),
            contentDescription = title,
            tint = AnimeRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        if (rightContent != null) {
            Spacer(modifier = Modifier.weight(1f))
            rightContent()
        } else if (onSeeAll != null) {
            // Chevron-only affordance: the whole header row already navigates.
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.section_see_all),
                tint = AnimeRed,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
