package git.shin.komorei.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import git.shin.komorei.model.Anime

@Composable
fun AnimeSection(
    title: String,
    animeList: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    isGrid: Boolean = false,
    icon: ImageVector? = null,
    rightContent: (@Composable () -> Unit)? = null,
    getSourceName: (String) -> String = { it }
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!isGrid) Modifier.padding(vertical = 10.dp) else Modifier)
    ) {
        SectionHeader(
            title = title,
            icon = icon,
            rightContent = rightContent
        )

        if (isGrid) {
            // Responsive columns per width bucket (3 phone / 4 tablet / 5-6
            // TV) so the Library grid never stretches giant cards on a big
            // screen.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = animeGridColumns(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items = animeList, key = { it.id }) { anime ->
                        AnimeCard(
                            anime = anime,
                            onClick = { onAnimeClick(anime) },
                            getSourceName = getSourceName
                        )
                    }
                }
            }
        } else {
            // LazyRow for horizontal anime cards in this category
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(items = animeList, key = { it.id }) { anime ->
                    AnimeCard(
                        anime = anime,
                        onClick = { onAnimeClick(anime) },
                        modifier = Modifier.width(110.dp),
                        getSourceName = getSourceName
                    )
                }
            }
        }
    }
}
