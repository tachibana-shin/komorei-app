package git.shin.komorei.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.AnimeRedContainer
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.NoPaddingTextStyle
import git.shin.komorei.ui.theme.TextPrimary

@Composable
fun DetailPillButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String
) {
    Surface(
        onClick = onClick,
        color = if (isActive) AnimeRedContainer else CardDark,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) AnimeRed else CardBorderDark
        ),
        modifier = modifier
//            .height(36.dp)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) AnimeRed else TextPrimary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (isActive) AnimeRed else TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = NoPaddingTextStyle
            )
        }
    }
}
