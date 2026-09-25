package git.shin.komorei.data.deeplink

import android.net.Uri
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Non-content deep links handled by the app itself. Content links (for example
 * `komorei://komorei.example/<path>`) continue through [DeepLinkManager] and
 * [git.shin.komorei.data.deeplink.DeepLinkResolver].
 */
sealed interface ExternalDeepLinkRequest {
    data class AddRepository(
        val url: String,
    ) : ExternalDeepLinkRequest

    data class InstallSource(
        val url: String,
    ) : ExternalDeepLinkRequest

    data object Invalid : ExternalDeepLinkRequest
}

/**
 * Parses Komorei-owned install/repository links before source wasm gets a
 * chance to inspect them.
 *
 * Supported examples:
 * - `komorei://addSourceList?url=https://example.test/index.min.json`
 * - `komorei://addSource?url=https://example.test/source.krx`
 *
 * Only the outer `komorei://` scheme is accepted. The nested repository or
 * package URL may be HTTP(S), since that is the resource the app downloads.
 * Local paths, other custom schemes and malformed nested URLs are rejected.
 */
object ExternalDeepLinkParser {
    private val repositoryHosts =
        setOf(
            "addsourcelist",
            "add-source-list",
            "addrepository",
            "add-repository",
            "addrepo",
            "add-repo",
            "repo",
            "repository",
        )
    private val sourceHosts =
        setOf(
            "addsource",
            "add-source",
            "installsource",
            "install-source",
            "install",
        )

    fun parse(uri: Uri): ExternalDeepLinkRequest? {
        if (!uri.scheme.equals("komorei", ignoreCase = true)) return null

        val host = uri.host?.lowercase()
        val rawUrl = nestedUrl(uri)
        return when {
            host in repositoryHosts -> rawUrl.toRequest(ExternalDeepLinkRequest::AddRepository)
            host in sourceHosts -> rawUrl.toRequest(ExternalDeepLinkRequest::InstallSource)
            // A source id/path is a content deep link, not an install command.
            else -> null
        }
    }

    private fun nestedUrl(uri: Uri): String? =
        uri.getQueryParameter("url")
            ?: uri.getQueryParameter("uri")
            ?: uri.getQueryParameter("source")

    private fun String?.toRequest(
        factory: (String) -> ExternalDeepLinkRequest,
    ): ExternalDeepLinkRequest {
        val normalized =
            this?.trim()?.toHttpUrlOrNull()?.toString()
                ?: return ExternalDeepLinkRequest.Invalid
        return factory(normalized)
    }
}
