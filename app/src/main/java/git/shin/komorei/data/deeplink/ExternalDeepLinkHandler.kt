package git.shin.komorei.data.deeplink

import git.shin.komorei.data.RepoLoadResult
import git.shin.komorei.data.SourceReposRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.sdk.KrxManager
import git.shin.komorei.sdk.KrxSourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Result of handling an app-owned (non-content) deep link. */
sealed interface ExternalDeepLinkResult {
    data class RepositoryAdded(
        val url: String,
    ) : ExternalDeepLinkResult

    data object RepositoryAlreadyAdded : ExternalDeepLinkResult

    data object RepositoryUnavailable : ExternalDeepLinkResult

    data class SourceInstalled(
        val id: String,
        val name: String,
    ) : ExternalDeepLinkResult

    data class SourceAlreadyInstalled(
        val id: String,
        val name: String,
    ) : ExternalDeepLinkResult

    data object SourceInstallFailed : ExternalDeepLinkResult

    data object Invalid : ExternalDeepLinkResult
}

/**
 * Executes repository/source installation links using the same download and
 * registry paths as the Sources UI. Keeping this separate from
 * [DeepLinkResolver] prevents an install command from being offered to every
 * installed source and keeps long downloads out of the content-link channel.
 */
@Singleton
class ExternalDeepLinkHandler @Inject constructor(
    private val reposRepository: SourceReposRepository,
    private val stateStore: SourceStateStore,
    private val sourceRegistry: KrxSourceRegistry,
) {
    private val mutex = Mutex()

    suspend fun handle(request: ExternalDeepLinkRequest): ExternalDeepLinkResult =
        withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    when (request) {
                        is ExternalDeepLinkRequest.AddRepository -> addRepository(request.url)
                        is ExternalDeepLinkRequest.InstallSource -> installSource(request.url)
                        ExternalDeepLinkRequest.Invalid -> ExternalDeepLinkResult.Invalid
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                when (request) {
                    is ExternalDeepLinkRequest.AddRepository -> ExternalDeepLinkResult.RepositoryUnavailable
                    is ExternalDeepLinkRequest.InstallSource -> ExternalDeepLinkResult.SourceInstallFailed
                    ExternalDeepLinkRequest.Invalid -> ExternalDeepLinkResult.Invalid
                }
            }
        }

    private suspend fun addRepository(rawUrl: String): ExternalDeepLinkResult {
        val url = rawUrl.trim().trimEnd('/')
        if (stateStore.repos.value.any { it.trimEnd('/') == url }) {
            return ExternalDeepLinkResult.RepositoryAlreadyAdded
        }

        // Match Aidoku: validate/fetch the manifest before persisting the URL.
        return when (reposRepository.fetchSourceList(url)) {
            is RepoLoadResult.Success ->
                if (stateStore.addRepo(url)) {
                    ExternalDeepLinkResult.RepositoryAdded(url)
                } else {
                    ExternalDeepLinkResult.RepositoryUnavailable
                }
            RepoLoadResult.Unavailable -> ExternalDeepLinkResult.RepositoryUnavailable
        }
    }

    private suspend fun installSource(url: String): ExternalDeepLinkResult {
        val bytes =
            reposRepository.downloadPackage(url)
                ?: return ExternalDeepLinkResult.SourceInstallFailed

        val packageInfo = KrxManager.readInfo(bytes)
        if (packageInfo != null) {
            sourceRegistry.sourceMeta(packageInfo.id)?.let { existing ->
                stateStore.setDisabled(existing.id, false)
                return ExternalDeepLinkResult.SourceAlreadyInstalled(existing.id, existing.name)
            }
        }

        val meta =
            sourceRegistry.installKrx(bytes)
                ?: return ExternalDeepLinkResult.SourceInstallFailed
        stateStore.setDisabled(meta.id, false)
        return ExternalDeepLinkResult.SourceInstalled(meta.id, meta.name)
    }
}
