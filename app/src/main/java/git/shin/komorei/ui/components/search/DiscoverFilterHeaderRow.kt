package git.shin.komorei.ui.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import git.shin.komorei.R
import git.shin.komorei.model.ContentRatingFilter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.Source
import git.shin.komorei.ui.components.search.filters.FilterBottomSheet
import git.shin.komorei.ui.components.search.filters.FilterPill
import git.shin.komorei.ui.components.search.filters.MultiSelectFilterGroup
import git.shin.komorei.ui.components.search.filters.SelectFilterGroup

private const val RATING_ALL = "all"
private const val RATING_SAFE = "safe"
private const val RATING_NSFW = "nsfw"
private const val ALL_LANGUAGE = "all"

/**
 * The Aidoku-style global search filter row of the Khám Phá tab: three pills —
 * **Xếp hạng nội dung** (content rating), **Ngôn ngữ** (language) and
 * **Nguồn** (sources) — each opening a [FilterBottomSheet] (single-select tag
 * chips for rating/language, multi-select chips for sources). Value changes
 * commit immediately and flow back through the callbacks.
 *
 * Reuses the per-source filter chrome ([FilterPill], [FilterBottomSheet],
 * [SelectFilterGroup], [MultiSelectFilterGroup]) with hand-built
 * [FilterKind] instances so no source `filters()` request is needed.
 */
@Composable
fun DiscoverFilterHeaderRow(
    contentRating: ContentRatingFilter,
    language: String?,
    includedSourceIds: Set<String>,
    sources: List<Source>,
    onContentRatingChange: (ContentRatingFilter) -> Unit,
    onLanguageChange: (String?) -> Unit,
    onSourcesChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRating by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }

    val ratingTitle = stringResource(R.string.discover_filter_rating)
    val languageTitle = stringResource(R.string.discover_filter_language)
    val sourceTitle = stringResource(R.string.discover_filter_sources)

    // Language options: "Tất cả ngôn ngữ" + the union of installed sources' codes.
    val languages = sources.flatMap { it.languages }.distinct()
    val allLanguagesLabel = stringResource(R.string.discover_language_all)
    val languageOptions = remember(languages) { listOf(allLanguagesLabel) + languages.map { it.uppercase() } }
    val languageIds = remember(languages) { listOf(ALL_LANGUAGE) + languages }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        item(key = "rating") {
            FilterPill(
                name = when (contentRating) {
                    ContentRatingFilter.ALL -> ratingTitle
                    ContentRatingFilter.SAFE -> stringResource(
                        R.string.discover_filter_value_format,
                        ratingTitle,
                        stringResource(R.string.discover_rating_safe),
                    )
                    ContentRatingFilter.NSFW -> stringResource(
                        R.string.discover_filter_value_format,
                        ratingTitle,
                        stringResource(R.string.discover_rating_nsfw),
                    )
                },
                active = contentRating != ContentRatingFilter.ALL,
                onClick = { showRating = true },
            )
        }
        item(key = "language") {
            FilterPill(
                name = if (language == null) languageTitle
                else stringResource(
                    R.string.discover_filter_value_format,
                    languageTitle,
                    language.uppercase(),
                ),
                active = language != null,
                onClick = { showLanguage = true },
            )
        }
        item(key = "sources") {
            FilterPill(
                name = sourceTitle,
                active = includedSourceIds.isNotEmpty(),
                badgeCount = includedSourceIds.size,
                onClick = { showSources = true },
            )
        }
    }

    if (showRating) {
        FilterBottomSheet(
            title = ratingTitle,
            onDismiss = { showRating = false },
            onReset = { onContentRatingChange(ContentRatingFilter.ALL) },
        ) {
            val ratingOptions = listOf(
                stringResource(R.string.discover_rating_all),
                stringResource(R.string.discover_rating_safe),
                stringResource(R.string.discover_rating_nsfw),
            )
            SelectFilterGroup(
                kind = FilterKind.Select(
                    options = ratingOptions,
                    ids = listOf(RATING_ALL, RATING_SAFE, RATING_NSFW),
                    usesTagStyle = true,
                ),
                selected = when (contentRating) {
                    ContentRatingFilter.ALL -> RATING_ALL
                    ContentRatingFilter.SAFE -> RATING_SAFE
                    ContentRatingFilter.NSFW -> RATING_NSFW
                },
                onSelect = { value ->
                    onContentRatingChange(
                        when (value) {
                            RATING_SAFE -> ContentRatingFilter.SAFE
                            RATING_NSFW -> ContentRatingFilter.NSFW
                            else -> ContentRatingFilter.ALL
                        },
                    )
                    showRating = false
                },
            )
        }
    }

    if (showLanguage) {
        FilterBottomSheet(
            title = languageTitle,
            onDismiss = { showLanguage = false },
            onReset = { onLanguageChange(null) },
        ) {
            SelectFilterGroup(
                kind = FilterKind.Select(
                    options = languageOptions,
                    ids = languageIds,
                    usesTagStyle = true,
                ),
                selected = language ?: ALL_LANGUAGE,
                onSelect = { value ->
                    onLanguageChange(value.takeIf { it != ALL_LANGUAGE })
                    showLanguage = false
                },
            )
        }
    }

    if (showSources) {
        FilterBottomSheet(
            title = sourceTitle,
            onDismiss = { showSources = false },
            onReset = { onSourcesChange(emptySet()) },
        ) {
            MultiSelectFilterGroup(
                kind = FilterKind.MultiSelect(
                    options = sources.map { it.name },
                    ids = sources.map { it.id },
                    usesTagStyle = true,
                    canExclude = false,
                ),
                // Empty selection means "all sources" → render every source as
                // selected in the sheet so the default state reads correctly.
                included = if (includedSourceIds.isEmpty()) sources.map { it.id }
                else includedSourceIds.toList(),
                excluded = emptyList(),
                onToggle = { newIncluded, _ ->
                    onSourcesChange(newIncluded.toSet())
                },
            )
        }
    }
}