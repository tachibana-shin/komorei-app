package git.shin.komorei.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import git.shin.komorei.model.Source

/**
 * Renders a source's identity icon: the real artwork (a PNG extracted from the
 * source's `.krx` package, whose path lives in [Source.icon]) when available,
 * otherwise the app's per-source vector fallback ([AppIcons.getSourceIcon]).
 *
 * [iconSize] is applied to the image branch; the vector fallback uses
 * [fallbackIconSize] so small chips (tab bar icons) can keep a bigger tap target
 * on the surrounding box without inflating the glyph itself.
 */
@Composable
fun SourceIcon(
    source: Source,
    modifier: Modifier = Modifier,
    iconSize: Dp,
    fallbackIconSize: Dp,
    fallbackTint: Color,
    contentDescription: String? = null,
) {
    if (source.icon.isNotBlank()) {
        AsyncImage(
            model = source.icon,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(iconSize),
        )
    } else {
        Icon(
            imageVector = AppIcons.getSourceIcon(source.id),
            contentDescription = contentDescription,
            tint = fallbackTint,
            modifier = modifier.size(fallbackIconSize),
        )
    }
}

/**
 * Icon for an external repo listing ([ExternalSourceInfo]) — renders the repo's
 * advertised [iconUrl] artwork when present, otherwise the per-source vector
 * fallback. Coil fetches the URL against the same [ImageLoader] used across the
 * app (absolute URLs come from [SourceReposRepository.absolutizeUrl]).
 */
@Composable
fun ExternalSourceIcon(
    sourceId: String,
    iconUrl: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp,
    fallbackIconSize: Dp,
    fallbackTint: Color,
    contentDescription: String? = null,
) {
    if (!iconUrl.isNullOrBlank()) {
        AsyncImage(
            model = iconUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(iconSize),
        )
    } else {
        Icon(
            imageVector = AppIcons.getSourceIcon(sourceId),
            contentDescription = contentDescription,
            tint = fallbackTint,
            modifier = modifier.size(fallbackIconSize),
        )
    }
}
