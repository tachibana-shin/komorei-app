package git.shin.komorei.sdk

import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeSeason
import git.shin.komorei.model.CategoryLink
import git.shin.komorei.model.Episode
import git.shin.komorei.model.Filter
import git.shin.komorei.model.RangeLong
import git.shin.komorei.model.StreamData
import git.shin.komorei.model.StreamInfo
import git.shin.komorei.model.SubtitleInfo
import git.shin.komorei.sdk.runner.Anime as RunnerAnime
import git.shin.komorei.sdk.runner.AnimePageResult as RunnerAnimePageResult
import git.shin.komorei.sdk.runner.AnimeSeason as RunnerAnimeSeason
import git.shin.komorei.sdk.runner.AnimeStatus as RunnerAnimeStatus
import git.shin.komorei.sdk.runner.CategoryLink as RunnerCategoryLink
import git.shin.komorei.sdk.runner.Episode as RunnerEpisode
import git.shin.komorei.sdk.runner.Filter as RunnerFilter
import git.shin.komorei.sdk.runner.FilterKind as RunnerFilterKind
import git.shin.komorei.sdk.runner.FilterValue as RunnerFilterValue
import git.shin.komorei.sdk.runner.RangeLong as RunnerRangeLong
import git.shin.komorei.sdk.runner.SortFilterDefault as RunnerSortFilterDefault
import git.shin.komorei.sdk.runner.StreamData as RunnerStreamData
import git.shin.komorei.sdk.runner.StreamInfo as RunnerStreamInfo
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

/** App-side shape for a paginated search/anime-list result. */
fun RunnerAnimePageResult.toAppPage(): KrxPage<Anime> =
    KrxPage(entries = entries.map { it.toAppModel() }, hasNextPage = hasNextPage)

private const val DEFAULT_QUALITY = "1080p FHD"

/** A paginated page of app models (source-agnostic). */
data class KrxPage<T>(
    val entries: List<T>,
    val hasNextPage: Boolean,
)