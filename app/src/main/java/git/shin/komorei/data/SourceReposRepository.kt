package git.shin.komorei.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A source edition advertised by an external repo (Aidoku `ExternalSourceInfo`).
 * [downloadURL] is the `.aix`/`.krx` package that can be installed directly.
 *
 * [repoName] is the DISPLAY NAME of the repo whose manifest advertised this
 * edition (threaded through by the catalog builder; see
 * `ExternalCatalog.buildExternalCatalog`). With multiple repos configured the
 * add-source sheet uses it to answer "nguồn này thuộc kho nào" — the repo that
 * advertised the version that won the cross-repo dedup (the same repo whose
 * [downloadURL] the install would actually pull from).
 */
data class ExternalSourceInfo(
    val id: String,
    val name: String,
    val version: String,
    val iconUrl: String? = null,
    val downloadURL: String? = null,
    val languages: List<String> = emptyList(),
    val contentRating: Int = 0,
    val repoName: String? = null,
)

data class RepoSourceList(
    val name: String,
    val feedbackURL: String? = null,
    val sources: List<ExternalSourceInfo>,
)

/** Outcome of fetching a repo's source list. */
sealed interface RepoLoadResult {
    data class Success(
        val repo: RepoSourceList,
    ) : RepoLoadResult

    data object Unavailable : RepoLoadResult
}

/**
 * Fetches external source lists from Aidoku-style repos (JSON manifest) and
 * downloads `.aix` / `.krx` packages for installation.
 *
 * Repo manifest format (Aidoku): a JSON object with `sources: [...]`, or a bare
 * array when the repo is a single-source shorthand. Each source entry carries
 * `id` / `name` / `version` / `icon` / `file` (package URL) / `languages` /
 * `nsfw`. Unknown fields are tolerated.
 */
@Singleton
class SourceReposRepository @Inject constructor(
    private val okHttp: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    suspend fun fetchSourceList(url: String): RepoLoadResult {
        return try {
            val text =
                withContext(Dispatchers.IO) {
                    okHttp
                        .newCall(Request.Builder().url(url).build())
                        .execute()
                        .use { response ->
                            if (!response.isSuccessful) return@withContext null
                            response.body.string()
                        }
                } ?: return RepoLoadResult.Unavailable
            val repo = parseManifest(text) ?: return RepoLoadResult.Unavailable
            // Aidoku-style manifests advertise relative package/icon paths
            // (`sources/<id>-v<N>.krx`); resolve them against the repo URL.
            RepoLoadResult.Success(repo.withResolvedUrls(url))
        } catch (e: Exception) {
            RepoLoadResult.Unavailable
        }
    }

    suspend fun downloadPackage(url: String): ByteArray? {
        return try {
            val bytes =
                withContext(Dispatchers.IO) {
                    okHttp
                        .newCall(Request.Builder().url(url).build())
                        .execute()
                        .use { response ->
                            if (!response.isSuccessful) return@withContext null
                            response.body.bytes()
                        }
                }
            if (bytes == null || bytes.isEmpty()) null else bytes
        } catch (e: Exception) {
            null
        }
    }

    private fun parseManifest(text: String): RepoSourceList? =
        try {
            val root = JSONObject(text)
            val name =
                root.optString("name").ifBlank {
                    root.optString("repoName").ifBlank { context.getString(R.string.repos_default_name) }
                }
            val feedback =
                root
                    .optString("fb")
                    .ifBlank {
                        root.optString("feedbackURL").ifBlank { root.optString("feedback").ifBlank { "" } }
                    }.takeIf { it.isNotBlank() }
            val sourcesJson =
                if (root.has("sources")) root.getJSONArray("sources") else JSONArray(text)
            RepoSourceList(
                name = name,
                feedbackURL = feedback,
                sources = parseSources(sourcesJson),
            )
        } catch (e: Exception) {
            // Legacy bare-array form:  [ { ... }, ... ]
            try {
                val arr = JSONArray(text)
                val name = findRepoName(arr)
                RepoSourceList(name = name, sources = parseSources(arr))
            } catch (e2: Exception) {
                null
            }
        }

    private fun findRepoName(arr: JSONArray): String {
        // Best-effort: reuse the first source's name family when no manifest name.
        if (arr.length() == 0) return context.getString(R.string.repos_default_name)
        val first = arr.getJSONObject(0)
        return context.getString(R.string.repos_default_name_format, first.optString("name").ifBlank { "?" })
    }

    /**
     * Resolves relative `downloadURL`/`iconUrl` values (the Aidoku manifest
     * convention) against the repo base URL so the downloads work regardless of
     * where the manifest is hosted.
     */
    private fun RepoSourceList.withResolvedUrls(baseUrl: String): RepoSourceList =
        copy(
            sources =
                sources.map { source ->
                    source.copy(
                        downloadURL = source.downloadURL?.let { absolutizeUrl(baseUrl, it) },
                        iconUrl = source.iconUrl?.let { absolutizeUrl(baseUrl, it) },
                    )
                },
        )

    private fun parseSources(arr: JSONArray): List<ExternalSourceInfo> {
        val out = mutableListOf<ExternalSourceInfo>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isBlank() || name.isBlank()) continue
            out +=
                ExternalSourceInfo(
                    id = id,
                    name = name,
                    version = obj.optString("version").ifBlank { "0.0.0" },
                    iconUrl = obj.optString("icon").ifBlank { obj.optString("iconURL").ifBlank { null } },
                    downloadURL =
                        obj.optString("file").ifBlank {
                            obj.optString("downloadURL").ifBlank { obj.optString("url").ifBlank { null } }
                        },
                    languages = parseLanguages(obj),
                    contentRating = if (obj.has("nsfw")) obj.optInt("nsfw") else obj.optInt("contentRating"),
                )
        }
        return out
    }

    private fun parseLanguages(obj: JSONObject): List<String> {
        if (!obj.has("languages")) return emptyList()
        return try {
            val arr = obj.getJSONArray("languages")
            buildList { for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Resolves a URL that may be relative against a base URL. Absolute `http(s)`
 * URLs pass through untouched; everything else (relative paths and
 * root-relative `/...` paths alike) is resolved against [baseUrl] using the
 * standard `URL(base, relative)` semantics. A file-like final path segment
 * (e.g. `index.min.json`) is dropped so `https://host/repo` and
 * `https://host/repo/index.min.json` both resolve `sources/x.krx` to
 * `https://host/repo/sources/x.krx`. Returns [url] unchanged when [baseUrl] is
 * not a valid URL.
 */
fun absolutizeUrl(
    baseUrl: String,
    url: String,
): String {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    val base = baseUrl.toHttpUrlOrNull() ?: return url
    val path = base.encodedPath
    val directoryPath =
        when {
            path.isEmpty() -> "/"
            path.endsWith("/") -> path
            else -> {
                val last = path.substringAfterLast('/')
                if (last.contains('.')) {
                    path.substringBeforeLast('/') + "/"
                } else {
                    path + "/"
                }
            }
        }
    // Drop query/fragment: the directory is derived from the path alone, which
    // is what java.net.URL(protocol, host, port, path) used to do.
    val directory =
        base
            .newBuilder()
            .encodedPath(directoryPath)
            .query(null)
            .fragment(null)
            .build()
    return directory.resolve(url)?.toString() ?: url
}

/**
 * Compares "1.0.0" vs "1.2" component-wise. Returns >0 when [a] is newer,
 * <0 when older, 0 when equal. Missing trailing parts compare as 0, so
 * "1.0" == "1.0.0" and "1.0.1" > "1.0".
 */
fun compareVersions(
    a: String,
    b: String,
): Int {
    val aParts = a.trim().split('.')
    val bParts = b.trim().split('.')
    for (i in 0 until maxOf(aParts.size, bParts.size)) {
        val aN = aParts.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
        val bN = bParts.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
        if (aN != bN) return aN - bN
    }
    return 0
}
