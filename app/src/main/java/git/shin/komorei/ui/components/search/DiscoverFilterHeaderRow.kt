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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import git.shin.komorei.R
import git.shin.komorei.model.ContentRatingFilter
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterValue
import git.shin.komorei.model.Source
import git.shin.komorei.ui.components.search.filters.FilterBottomSheet
import git.shin.komorei.ui.components.search.filters.FilterListSheet
import git.shin.komorei.ui.components.search.filters.FilterPill
import git.shin.komorei.ui.components.search.filters.FilterSheetButton
import git.shin.komorei.ui.components.search.filters.MultiSelectFilterGroup
import git.shin.komorei.ui.components.search.filters.SelectFilterGroup
import git.shin.komorei.ui.components.search.filters.activeFilterCount

private const val RATING_ALL = "all"
private const val RATING_SAFE = "safe"
private const val RATING_NSFW = "nsfw"
private const val ALL_LANGUAGE = "all"

private const val FILTER_RATING = "rating"
private const val FILTER_LANGUAGE = "language"
private const val FILTER_SOURCES = "sources"

/**
 * The Aidoku-style global search filter row of the Tìm Kiếm tab: an aggregate
 * filter-sheet button followed by three pills — **Xếp hạng nội dung** (content rating),
 * **Ngôn ngữ** (language) and **Nguồn** (sources).
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
    var showAll by remember { mutableStateOf(false) }

    val ratingTitle = stringResource(R.string.discover_filter_rating)
    val languageTitle = stringResource(R.string.discover_filter_language)
    val sourceTitle = stringResource(R.string.discover_filter_sources)

    // Language options: "Tất cả ngôn ngữ" + the union of installed sources' codes.
    val languages = sources.flatMap { it.languages }.distinct()
    val allLanguagesLabel = stringResource(R.string.discover_language_all)
    val languageOptions = remember(languages, allLanguagesLabel) { listOf(allLanguagesLabel) + languages.map { it.uppercase() } }
    val languageIds = remember(languages) { listOf(ALL_LANGUAGE) + languages }

    val ratingOptions = listOf(
        stringResource(R.string.discover_rating_all),
        stringResource(R.string.discover_rating_safe),
        stringResource(R.string.discover_rating_nsfw),
    )
    val ratingIds = listOf(RATING_ALL, RATING_SAFE, RATING_NSFW)

    // The three global filters as hand-built Filter instances for the aggregate
    // sheet. Defaults mirror the immediate-commit pills: rating/language default
    // to "all", the sources multi-select defaults to ALL ids selected (emptying
    // the selection is "all sources" — the value is dropped when it matches).
    val allFilters = listOf(
        Filter(
            id = FILTER_RATING,
            title = ratingTitle,
            kind = FilterKind.Select(
                options = ratingOptions,
                ids = ratingIds,
                usesTagStyle = true,
                default = RATING_ALL,
            ),
        ),
        Filter(
            id = FILTER_LANGUAGE,
            title = languageTitle,
            kind = FilterKind.Select(
                options = languageOptions,
                ids = languageIds,
                usesTagStyle = true,
                default = ALL_LANGUAGE,
            ),
        ),
        Filter(
            id = FILTER_SOURCES,
            title = sourceTitle,
            kind = FilterKind.MultiSelect(
                options = sources.map { it.name },
                ids = sources.map { it.id },
                usesTagStyle = true,
                canExclude = false,
                defaultIncluded = sources.map { it.id },
            ),
        ),
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        item(key = "_sheet") {
            FilterSheetButton(
                enabledCount = activeFilterCount(
                    discoverEnabledValues(contentRating, language, includedSourceIds),
                ),
                onClick = { showAll = true },
                modifier = Modifier.testTag("filter_sheet_button"),
            )
        }
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
                testTag = "filter_rating_pill",
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
                testTag = "filter_language_pill",
            )
        }
        item(key = "sources") {
            FilterPill(
                name = sourceTitle,
                active = includedSourceIds.isNotEmpty(),
                badgeCount = includedSourceIds.size,
                onClick = { showSources = true },
                testTag = "filter_sources_pill",
            )
        }
    }

    if (showAll) {
        FilterListSheet(
            filters = allFilters,
            initialEnabled = discoverEnabledValues(contentRating, language, includedSourceIds),
            onApply = { values ->
                applyDiscoverFilterValues(values, onContentRatingChange, onLanguageChange, onSourcesChange)
                showAll = false
            },
            onDismiss = { showAll = false },
        )
    }

    if (showRating) {
        FilterBottomSheet(
            title = ratingTitle,
            onDismiss = { showRating = false },
            onReset = { onContentRatingChange(ContentRatingFilter.ALL) },
        ) {
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

/**
 * The active [FilterValue]s of the three global filters — the aggregate sheet's
 * initial values and its badge count (via [activeFilterCount]).
 */
private fun discoverEnabledValues(
    contentRating: ContentRatingFilter,
    language: String?,
    includedSourceIds: Set<String>,
): List<FilterValue> = buildList {
    if (contentRating != ContentRatingFilter.ALL) {
        add(FilterValue.Select(FILTER_RATING, ratingFilterId(contentRating)))
    }
    if (language != null) add(FilterValue.Select(FILTER_LANGUAGE, language))
    if (includedSourceIds.isNotEmpty()) {
        add(FilterValue.MultiSelect(FILTER_SOURCES, includedSourceIds.toList(), emptyList()))
    }
}

private fun ratingFilterId(rating: ContentRatingFilter): String = when (rating) {
    ContentRatingFilter.ALL -> RATING_ALL
    ContentRatingFilter.SAFE -> RATING_SAFE
    ContentRatingFilter.NSFW -> RATING_NSFW
}

/** Applies the aggregate sheet's committed values back to the three callbacks. */
private fun applyDiscoverFilterValues(
    values: List<FilterValue>,
    onContentRatingChange: (ContentRatingFilter) -> Unit,
    onLanguageChange: (String?) -> Unit,
    onSourcesChange: (Set<String>) -> Unit,
) {
    var rating = ContentRatingFilter.ALL
    var language: String? = null
    var sourceIds = emptySet<String>()
    for (value in values) {
        when (value) {
            is FilterValue.Select -> when (value.id) {
                FILTER_RATING -> rating = when (value.value) {
                    RATING_SAFE -> ContentRatingFilter.SAFE
                    RATING_NSFW -> ContentRatingFilter.NSFW
                    else -> ContentRatingFilter.ALL
                }
                FILTER_LANGUAGE -> language = value.value
            }
            is FilterValue.MultiSelect -> if (value.id == FILTER_SOURCES) {
                sourceIds = value.included.toSet()
            }
            else -> Unit
        }
    }
    onContentRatingChange(rating)
    onLanguageChange(language)
    onSourcesChange(sourceIds)
}