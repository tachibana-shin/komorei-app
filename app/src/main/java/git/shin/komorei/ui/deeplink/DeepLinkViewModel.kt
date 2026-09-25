package git.shin.komorei.ui.deeplink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.deeplink.DeepLinkManager
import git.shin.komorei.data.deeplink.DeepLinkResolver
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.model.DeepLinkTarget
import git.shin.komorei.model.Episode
import git.shin.komorei.model.Listing
import git.shin.komorei.model.ResolvedDeepLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A deep link, turned into something the UI can execute. The target keys come
 * from the source's `handle_deep_link`; [OpenAnime] carries Lite stubs built
 * from those keys — the player upgrades them via `getAnimeUpdate` (the same
 * path a process-death restore rides), so only id + sourceId need to be real.
 */
sealed interface DeepLinkAction {
    data class OpenAnime(
        val anime: Anime,
        val episode: Episode?,
    ) : DeepLinkAction

    data class OpenListing(
        val sourceId: String,
        val listing: Listing,
    ) : DeepLinkAction
}

/**
 * Bridges [DeepLinkManager.pending] → resolution → one-shot [action].
 * [MainScreen] performs the action (open player / navigate to listing) and
 * then calls [markConsumed]; the manager is consumed as soon as the action is
 * produced so a second submit doesn't queue up behind the first.
 */
@HiltViewModel
class DeepLinkViewModel @Inject constructor(
    private val manager: DeepLinkManager,
    private val resolver: DeepLinkResolver,
) : ViewModel() {
    private val _action = MutableStateFlow<DeepLinkAction?>(null)
    val action: StateFlow<DeepLinkAction?> = _action.asStateFlow()

    init {
        viewModelScope.launch {
            manager.pending
                .filterNotNull()
                .collect { url ->
                    var action = resolver.resolve(url)?.toAction()
                    // Consume the manager ONLY if the pending link is still the one
                    // we just resolved — a link submitted mid-resolution takes
                    // priority: the stale action is dropped and the loop re-reads
                    // the newer link (StateFlow conflates to the latest value).
                    if (manager.pending.value == url) {
                        manager.markConsumed()
                    } else {
                        action = null
                    }
                    _action.value = action
                }
        }
    }

    /** Call after the action has been performed so it won't re-fire. */
    fun markConsumed() {
        _action.value = null
    }
}

private fun ResolvedDeepLink.toAction(): DeepLinkAction =
    when (val target = this.target) {
        is DeepLinkTarget.Anime ->
            DeepLinkAction.OpenAnime(
                anime = stubAnime(sourceId, target.animeKey),
                episode = null,
            )

        is DeepLinkTarget.Episode ->
            DeepLinkAction.OpenAnime(
                anime = stubAnime(sourceId, target.animeKey),
                episode = stubEpisode(sourceId, target.animeKey, target.episodeKey),
            )

        is DeepLinkTarget.Listing -> DeepLinkAction.OpenListing(sourceId, target.listing)
    }

/**
 * A valid Lite card for `getAnimeUpdate` — the "id + sourceId only" contract
 * the runner is happy to upgrade (the upgrade replaces this shell entirely).
 */
private fun stubAnime(
    sourceId: String,
    key: String,
): Anime =
    Anime(
        id = key,
        sourceId = sourceId,
        title = key,
        originalTitle = "",
        posterUrl = "",
        bannerUrl = "",
        description = "",
        episodeCount = 0,
        currentEpisode = null,
        rating = null,
        ratingCount = null,
        status = AnimeStatus.UNKNOWN,
        releaseYear = null,
        genres = emptyList(),
        authors = emptyList(),
        studio = null,
        seasonOf = null,
        // Optional fields keep their defaults (countries/episodes/seasons/…).
    )

/**
 * Episode stub for `/watch/<anime>/<ep>`: `id` IS the runner episode key
 * (`Episode.toRunner().key = id`), so `getStreamList`/`getStream` serialize
 * the right key. The player swaps in the full record (real title/number) right
 * after the `getAnimeUpdate` upgrade.
 */
private fun stubEpisode(
    sourceId: String,
    animeKey: String,
    episodeKey: String,
): Episode =
    Episode(
        id = episodeKey,
        animeId = animeKey,
        sourceId = sourceId,
        episodeNumber = episodeKey,
        title = episodeKey,
    )
