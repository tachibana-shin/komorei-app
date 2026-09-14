package git.shin.komorei.sdk

import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeSeason
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.model.AnimeWithEpisode
import git.shin.komorei.model.CategoryLink
import git.shin.komorei.model.Episode
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterItem
import git.shin.komorei.model.HomeComponent
import git.shin.komorei.model.HomeComponentValue
import git.shin.komorei.model.Link
import git.shin.komorei.model.LinkValue
import git.shin.komorei.model.Listing
import git.shin.komorei.model.ListingKind
import git.shin.komorei.model.RangeLong
import git.shin.komorei.model.StreamData
import git.shin.komorei.model.StreamInfo
import git.shin.komorei.model.SubtitleInfo
import git.shin.komorei.sdk.runner.Anime as RunnerAnime
import git.shin.komorei.sdk.runner.AnimePageResult as RunnerAnimePageResult
import git.shin.komorei.sdk.runner.AnimeSeason as RunnerAnimeSeason
import git.shin.komorei.sdk.runner.AnimeStatus as RunnerAnimeStatus
import git.shin.komorei.sdk.runner.AnimeWithEpisode as RunnerAnimeWithEpisode
import git.shin.komorei.sdk.runner.CategoryLink as RunnerCategoryLink
import git.shin.komorei.sdk.runner.Episode as RunnerEpisode
import git.shin.komorei.sdk.runner.Filter as RunnerFilter
import git.shin.komorei.sdk.runner.FilterItem as RunnerFilterItem
import git.shin.komorei.sdk.runner.FilterKind as RunnerFilterKind
import git.shin.komorei.sdk.runner.FilterValue as RunnerFilterValue
import git.shin.komorei.sdk.runner.HomeComponent as RunnerHomeComponent
import git.shin.komorei.sdk.runner.HomeComponentValue as RunnerHomeComponentValue
import git.shin.komorei.sdk.runner.Link as RunnerLink
import git.shin.komorei.sdk.runner.LinkValue as RunnerLinkValue
import git.shin.komorei.sdk.runner.Listing as RunnerListing
import git.shin.komorei.sdk.runner.ListingKind as RunnerListingKind
import git.shin.komorei.sdk.runner.RangeLong as RunnerRangeLong
import git.shin.komorei.sdk.runner.SortFilterDefault as RunnerSortFilterDefault
import git.shin.komorei.sdk.runner.StreamData as RunnerStreamData
import git.shin.komorei.sdk.runner.StreamInfo as RunnerStreamInfo
import git.shin.komorei.sdk.runner.StreamType as RunnerStreamType
import git.shin.komorei.sdk.runner.SubtitleInfo as RunnerSubtitleInfo

/**
 * Maps the uniffi-generated SDK records (package `git.shin.komorei.sdk.runner`,
 * produced from the Rust `komorei_lib` structs) to the app's model classes
 * (`git.shin.komorei.model`). Field-for-field: the SDK is the wire format, the
 * model classes are the app's data layer.
 */

fun RunnerAnime.toAppModel(): Anime {
    val appStatus = when (status) {
        RunnerAnimeStatus.ONGOING -> git.shin.komorei.model.AnimeStatus.ONGOING
        RunnerAnimeStatus.COMPLETED -> git.shin.komorei.model.AnimeStatus.COMPLETED
        else -> git.shin.komorei.model.AnimeStatus.UNKNOWN
    }
    return Anime(
        id = key,
        sourceId = sourceId,
        title = title,
        originalTitle = originalTitle,
        posterUrl = cover,
        bannerUrl = banner ?: "",
        description = description ?: "",
        episodeCount = episodeCount,
        currentEpisode = currentEpisode,
        rating = rating,
        ratingCount = ratingCount,
        status = appStatus,
        releaseYear = releaseYear?.toAppModel(),
        genres = genres.map { it.toAppModel() },
        authors = authors.map { it.toAppModel() },
        studio = studio?.toAppModel(),
        seasonOf = seasonOf?.toAppModel(),
        countries = countries.map { it.toAppModel() },
        episodes = episodes.orEmpty().map { it.toAppModel(animeId = key, sourceId = sourceId) },
        seasons = seasons.map { it.toAppModel() },
        isFeatured = isFeatured,
        views = views,
        nextEpisodeAirInfo = nextEpisodeAirInfo,
        qualityTag = qualityTag,
    )
}

fun RunnerAnimeSeason.toAppModel(): AnimeSeason = AnimeSeason(animeId, title, id)

fun RunnerEpisode.toAppModel(animeId: String, sourceId: String): Episode = Episode(
    id = key,
    animeId = animeId,
    sourceId = sourceId,
    episodeNumber = episodeNumber,
    title = title ?: "",
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnail ?: "",
    quality = quality ?: DEFAULT_QUALITY,
    dateUploaded = dateUploaded,
)

fun RunnerStreamInfo.toAppModel(): StreamInfo = StreamInfo(
    id = key,
    name = name,
    quality = quality,
)

fun RunnerStreamData.toAppModel(): StreamData = StreamData(
    url = url,
    type = when (streamType) {
        git.shin.komorei.sdk.runner.StreamType.HLS -> git.shin.komorei.model.StreamType.HLS
        git.shin.komorei.sdk.runner.StreamType.MP4 -> git.shin.komorei.model.StreamType.MP4
        git.shin.komorei.sdk.runner.StreamType.DASH -> git.shin.komorei.model.StreamType.DASH
        git.shin.komorei.sdk.runner.StreamType.OTHER -> git.shin.komorei.model.StreamType.OTHER
    },
    headers = headers,
    isContent = isContent,
    subtitles = subtitles.map { it.toAppModel() },
    intro = intro?.toAppModel(),
    outro = outro?.toAppModel(),
)

fun RunnerSubtitleInfo.toAppModel(): SubtitleInfo = SubtitleInfo(
    url = url,
    language = language,
    label = label,
    headers = headers,
)

fun RunnerRangeLong.toAppModel(): RangeLong = RangeLong(startMs, endMs)

fun RunnerCategoryLink.toAppModel(): CategoryLink = CategoryLink(
    name = name,
    filters = filters.map { it.toAppModel() },
)

fun RunnerFilterValue.toAppModel(): git.shin.komorei.model.FilterValue = when (this) {
    is RunnerFilterValue.Text -> git.shin.komorei.model.FilterValue.Text(id, value)
    is RunnerFilterValue.Sort -> git.shin.komorei.model.FilterValue.Sort(id, index, ascending)
    is RunnerFilterValue.Check -> git.shin.komorei.model.FilterValue.Check(id, value)
    is RunnerFilterValue.Select -> git.shin.komorei.model.FilterValue.Select(id, value)
    is RunnerFilterValue.MultiSelect -> git.shin.komorei.model.FilterValue.MultiSelect(id, included, excluded)
    is RunnerFilterValue.Range -> git.shin.komorei.model.FilterValue.Range(id, from, to)
}

fun RunnerFilter.toAppModel(): Filter = Filter(
    id = id,
    title = title,
    hideFromHeader = hideFromHeader,
    kind = kind.toAppModel(),
)

fun RunnerFilterKind.toAppModel(): git.shin.komorei.model.FilterKind = when (this) {
    is RunnerFilterKind.Text -> git.shin.komorei.model.FilterKind.Text(placeholder)
    is RunnerFilterKind.Sort -> git.shin.komorei.model.FilterKind.Sort(
        canAscend = canAscend,
        options = options,
        default = default?.toAppModel(),
    )
    is RunnerFilterKind.Check -> git.shin.komorei.model.FilterKind.Check(
        name = name,
        canExclude = canExclude,
        default = default,
    )
    is RunnerFilterKind.Select -> git.shin.komorei.model.FilterKind.Select(
        isGenre = isGenre,
        usesTagStyle = usesTagStyle,
        options = options,
        ids = ids,
        default = default,
    )
    is RunnerFilterKind.MultiSelect -> git.shin.komorei.model.FilterKind.MultiSelect(
        isGenre = isGenre,
        canExclude = canExclude,
        usesTagStyle = usesTagStyle,
        options = options,
        ids = ids,
        defaultIncluded = defaultIncluded,
        defaultExcluded = defaultExcluded,
    )
    is RunnerFilterKind.Note -> git.shin.komorei.model.FilterKind.Note(text = v1)
    is RunnerFilterKind.Range -> git.shin.komorei.model.FilterKind.Range(
        min = min,
        max = max,
        decimal = decimal,
    )
}

fun RunnerSortFilterDefault.toAppModel(): git.shin.komorei.model.SortFilterDefault =
    git.shin.komorei.model.SortFilterDefault(index, ascending)

// ── home components (lossless mirror of `get_home`) ────────────────────────

fun RunnerListingKind.toAppModel(): ListingKind = when (this) {
    RunnerListingKind.DEFAULT -> ListingKind.DEFAULT
    RunnerListingKind.LIST -> ListingKind.LIST
}

fun RunnerListing.toAppModel(): Listing = Listing(
    id = id,
    name = name,
    kind = kind.toAppModel(),
)

fun ListingKind.toRunner(): RunnerListingKind = when (this) {
    ListingKind.DEFAULT -> RunnerListingKind.DEFAULT
    ListingKind.LIST -> RunnerListingKind.LIST
}

fun Listing.toRunner(): RunnerListing = RunnerListing(
    id = id,
    name = name,
    kind = kind.toRunner(),
)

fun RunnerLinkValue.toAppModel(): LinkValue = when (this) {
    is RunnerLinkValue.Url -> LinkValue.Url(url = v1)
    is RunnerLinkValue.Listing -> LinkValue.Listing(listing = v1.toAppModel())
    is RunnerLinkValue.Anime -> LinkValue.Anime(anime = v1.toAppModel())
}

fun RunnerLink.toAppModel(): Link = Link(
    title = title,
    subtitle = subtitle,
    imageUrl = imageUrl,
    value = value?.toAppModel(),
)

fun RunnerFilterItem.toAppModel(): FilterItem = FilterItem(
    title = title,
    values = values?.map { it.toAppModel() },
)

fun RunnerAnimeWithEpisode.toAppModel(): AnimeWithEpisode = AnimeWithEpisode(
    anime = anime.toAppModel(),
    episode = episode.toAppModel(animeId = anime.key, sourceId = anime.sourceId),
)

fun RunnerHomeComponentValue.toAppModel(): HomeComponentValue = when (this) {
    is RunnerHomeComponentValue.ImageScroller -> HomeComponentValue.ImageScroller(
        links = links.map { it.toAppModel() },
        autoScrollInterval = autoScrollInterval,
        width = width,
        height = height,
    )
    is RunnerHomeComponentValue.BigScroller -> HomeComponentValue.BigScroller(
        entries = entries.map { it.toAppModel() },
        autoScrollInterval = autoScrollInterval,
    )
    is RunnerHomeComponentValue.Scroller -> HomeComponentValue.Scroller(
        entries = entries.map { it.toAppModel() },
        listing = listing?.toAppModel(),
    )
    is RunnerHomeComponentValue.AnimeList -> HomeComponentValue.AnimeList(
        ranking = ranking,
        pageSize = pageSize,
        entries = entries.map { it.toAppModel() },
        listing = listing?.toAppModel(),
    )
    is RunnerHomeComponentValue.AnimeEpisodeList -> HomeComponentValue.AnimeEpisodeList(
        pageSize = pageSize,
        entries = entries.map { it.toAppModel() },
        listing = listing?.toAppModel(),
    )
    is RunnerHomeComponentValue.Filters -> HomeComponentValue.Filters(
        items = v1.map { it.toAppModel() },
    )
    is RunnerHomeComponentValue.Links -> HomeComponentValue.Links(
        links = v1.map { it.toAppModel() },
    )
}

fun RunnerHomeComponent.toAppModel(): HomeComponent = HomeComponent(
    title = title,
    subtitle = subtitle,
    value = value.toAppModel(),
)

/** App-side shape for a paginated search/anime-list result. */
fun RunnerAnimePageResult.toAppPage(): KrxPage<Anime> =
    KrxPage(entries = entries.map { it.toAppModel() }, hasNextPage = hasNextPage)

private const val DEFAULT_QUALITY = "1080p FHD"

/** A paginated page of app models (source-agnostic). */
data class KrxPage<T>(
    val entries: List<T>,
    val hasNextPage: Boolean,
)

// ────────────────────────────────────────────────────────────────────────────
// Reverse direction: app model → runner wire format. The runner's fields are
// the SDK contract, so any field the app carries extra is dropped here (the
// runner instance already belongs to the specific source).
// ────────────────────────────────────────────────────────────────────────────

fun AnimeStatus.toRunner(): RunnerAnimeStatus = when (this) {
    AnimeStatus.ONGOING -> RunnerAnimeStatus.ONGOING
    AnimeStatus.COMPLETED -> RunnerAnimeStatus.COMPLETED
    AnimeStatus.UNKNOWN -> RunnerAnimeStatus.UNKNOWN
}

fun CategoryLink.toRunner(): RunnerCategoryLink = RunnerCategoryLink(
    name = name,
    filters = filters.map { it.toRunner() },
)

fun AnimeSeason.toRunner(): RunnerAnimeSeason = RunnerAnimeSeason(
    animeId = animeId,
    title = title,
    id = id,
)

fun Episode.toRunner(): RunnerEpisode = RunnerEpisode(
    key = id,
    episodeNumber = episodeNumber,
    title = title,
    dateUploaded = null,
    thumbnail = thumbnailUrl,
    quality = quality,
    durationSeconds = durationSeconds,
    url = null,
    language = null,
    locked = false,
)

fun Anime.toRunner(): RunnerAnime = RunnerAnime(
    key = id,
    sourceId = sourceId,
    title = title,
    originalTitle = originalTitle,
    cover = posterUrl,
    banner = bannerUrl.ifEmpty { null },
    description = description.ifEmpty { null },
    episodeCount = episodeCount,
    currentEpisode = currentEpisode,
    rating = rating,
    ratingCount = ratingCount,
    status = status.toRunner(),
    releaseYear = releaseYear?.toRunner(),
    genres = genres.map { it.toRunner() },
    authors = authors.map { it.toRunner() },
    studio = studio?.toRunner(),
    seasonOf = seasonOf?.toRunner(),
    countries = countries.map { it.toRunner() },
    isFeatured = isFeatured,
    views = views,
    nextEpisodeAirInfo = nextEpisodeAirInfo,
    qualityTag = qualityTag,
    seasons = seasons.map { it.toRunner() },
    episodes = episodes.map { it.toRunner() },
    url = null,
)

fun StreamInfo.toRunner(): RunnerStreamInfo = RunnerStreamInfo(
    key = id,
    name = name,
    quality = quality,
)

fun SubtitleInfo.toRunner(): RunnerSubtitleInfo = RunnerSubtitleInfo(
    url = url,
    language = language,
    label = label,
    headers = headers,
)

fun RangeLong.toRunner(): RunnerRangeLong = RunnerRangeLong(startMs, endMs)

fun StreamData.toRunner(): RunnerStreamData = RunnerStreamData(
    url = url,
    streamType = when (type) {
        git.shin.komorei.model.StreamType.HLS -> RunnerStreamType.HLS
        git.shin.komorei.model.StreamType.MP4 -> RunnerStreamType.MP4
        git.shin.komorei.model.StreamType.DASH -> RunnerStreamType.DASH
        git.shin.komorei.model.StreamType.OTHER -> RunnerStreamType.OTHER
    },
    headers = headers,
    isContent = isContent,
    subtitles = subtitles.map { it.toRunner() },
    intro = intro?.toRunner(),
    outro = outro?.toRunner(),
)

fun git.shin.komorei.model.FilterValue.toRunner(): RunnerFilterValue = when (this) {
    is git.shin.komorei.model.FilterValue.Text -> RunnerFilterValue.Text(id, value)
    is git.shin.komorei.model.FilterValue.Sort -> RunnerFilterValue.Sort(id, index, ascending)
    is git.shin.komorei.model.FilterValue.Check -> RunnerFilterValue.Check(id, value)
    is git.shin.komorei.model.FilterValue.Select -> RunnerFilterValue.Select(id, value)
    is git.shin.komorei.model.FilterValue.MultiSelect ->
        RunnerFilterValue.MultiSelect(id, included, excluded)
    is git.shin.komorei.model.FilterValue.Range -> RunnerFilterValue.Range(id, from, to)
}