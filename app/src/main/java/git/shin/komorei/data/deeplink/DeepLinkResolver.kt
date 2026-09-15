package git.shin.komorei.data.deeplink

import android.net.Uri
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.ResolvedDeepLink
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the source that recognizes a deep-link URL (Aidoku semantics: the link
 * is offered to every installed source; the first one whose `handle_deep_link`
 * returns a result claims it).
 *
 * Sources whose `baseUrl` host matches the link's host are tried FIRST — that
 * source is almost certainly the owner, so the rare cold-start link doesn't
 * spin up every runner. The rest still get a chance (a link may use any domain).
 */
@Singleton
class DeepLinkResolver @Inject constructor(
    private val repository: AnimeRepository,
) {

    /**
     * Resolves [url] against all installed sources. Returns the claiming source
     * + its app-model target, or null when no source handles the URL.
     */
    suspend fun resolve(url: String): ResolvedDeepLink? {
        val linkHost = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()
        val candidates = repository.sources
            .filter { !it.isAggregator }
            .sortedBy { source ->
                val sourceHost =
                    runCatching { Uri.parse(source.baseUrl).host?.lowercase() }.getOrNull()
                if (linkHost != null && sourceHost != null && sourceHost == linkHost) 0 else 1
            }
        for (source in candidates) {
            val target = repository.handleDeepLink(source.id, url) ?: continue
            return ResolvedDeepLink(source.id, target)
        }
        return null
    }
}