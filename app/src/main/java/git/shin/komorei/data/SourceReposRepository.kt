package git.shin.komorei.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A source edition advertised by an external repo (Aidoku `ExternalSourceInfo`).
 * [downloadURL] is the `.aix`/`.krx` package that can be installed directly.
 */
data class ExternalSourceInfo(
    val id: String,
    val name: String,
    val version: String,
    val iconUrl: String? = null,
    val downloadURL: String? = null,
    val languages: List<String> = emptyList(),
    val contentRating: Int = 0,
)

data class RepoSourceList(
    val name: String,
    val feedbackURL: String? = null,
    val sources: List<ExternalSourceInfo>,
)

/** Outcome of fetching a repo's source list. */
sealed interface RepoLoadResult {
    data class Success(val repo: RepoSourceList) : RepoLoadResult
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
) {

    suspend fun fetchSourceList(url: String): RepoLoadResult {
        return try {
            val text = withContext(Dispatchers.IO) {
                okHttp.newCall(Request.Builder().url(url).build())
                    .execute().use { response ->
                        if (!response.isSuccessful) return@withContext null
                        response.body.string()
                    }
            } ?: return RepoLoadResult.Unavailable
            val repo = parseManifest(text) ?: return RepoLoadResult.Unavailable
            RepoLoadResult.Success(repo)
        } catch (e: Exception) {
            RepoLoadResult.Unavailable
        }
    }

    suspend fun downloadPackage(url: String): ByteArray? {
        return try {
            val bytes = withContext(Dispatchers.IO) {
                okHttp.newCall(Request.Builder().url(url).build())
                    .execute().use { response ->
                        if (!response.isSuccessful) return@withContext null
                        response.body.bytes()
                    }
            }
            if (bytes == null || bytes.isEmpty()) null else bytes
        } catch (e: Exception) {
            null
        }
    }

    private fun parseManifest(text: String): RepoSourceList? {
        return try {
            val root = JSONObject(text)
            val name = root.optString("name").ifBlank {
                root.optString("repoName").ifBlank { "Kho nguồn" }
            }
            val feedback = root.optString("fb").ifBlank {
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
    }

    private fun findRepoName(arr: JSONArray): String {
        // Best-effort: reuse the first source's name family when no manifest name.
        if (arr.length() == 0) return "Kho nguồn"
        val first = arr.getJSONObject(0)
        return "Kho nguồn (${first.optString("name").ifBlank { "?" }})"
    }

    private fun parseSources(arr: JSONArray): List<ExternalSourceInfo> {
        val out = mutableListOf<ExternalSourceInfo>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isBlank() || name.isBlank()) continue
            out += ExternalSourceInfo(
                id = id,
                name = name,
                version = obj.optString("version").ifBlank { "0.0.0" },
                iconUrl = obj.optString("icon").ifBlank { obj.optString("iconURL").ifBlank { null } },
                downloadURL = obj.optString("file").ifBlank {
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
 * Compares "1.0.0" vs "1.2" component-wise. Returns >0 when [a] is newer,
 * <0 when older, 0 when equal. Missing trailing parts compare as 0, so
 * "1.0" == "1.0.0" and "1.0.1" > "1.0".
 */
fun compareVersions(a: String, b: String): Int {
    val aParts = a.trim().split('.')
    val bParts = b.trim().split('.')
    for (i in 0 until maxOf(aParts.size, bParts.size)) {
        val aN = aParts.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
        val bN = bParts.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
        if (aN != bN) return aN - bN
    }
    return 0
}