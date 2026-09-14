package git.shin.komorei.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The green quality corner label (FHD / 4K / BD ...), overlaid **top-right on
 * the poster**, styled exactly like [AnimeCard]'s quality tag. Call it inside
 * a `Box` that renders the poster/thumb image.
 */
@Composable
fun BoxScope.QualityTagBadge(
    qualityTag: String?,
    inset: Dp = 6.dp,
) {
    if (qualityTag.isNullOrBlank()) return
    Text(
        text = qualityTag,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        style = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        ),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(inset)
            .background(Color(0xFF00C853).copy(alpha = .85f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}