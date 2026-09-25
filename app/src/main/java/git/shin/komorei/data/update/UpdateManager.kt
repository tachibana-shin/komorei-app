package git.shin.komorei.data.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.BuildConfig
import git.shin.komorei.di.UpdateHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks the signed Komorei APK attached to the latest GitHub Release and
 * installs it through Android's package installer.
 *
 * The release is still validated by Android's installer, but we also verify
 * the package name, versionCode, and signing certificate before opening the
 * installer. A debug build therefore cannot silently install a release APK
 * signed by a different key.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @UpdateHttpClient private val client: OkHttpClient,
) {
    suspend fun checkForUpdate(): Result<UpdateCheckResult> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url(LATEST_RELEASE_URL)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("User-Agent", "Komorei/${BuildConfig.VERSION_NAME}")
                        .build()

                val response = client.newCall(request).execute()
                val result =
                    response.use {
                        if (!it.isSuccessful) {
                            throw IOException("GitHub release request failed (${it.code})")
                        }
                        val release = JSONObject(it.body.string())
                        val tag = release.optString("tag_name").trim()
                        val version =
                            normalizeVersion(tag)
                                ?: throw IOException("GitHub release has an invalid tag")
                        // The first direct APK release can legitimately reuse the
                        // current versionName while carrying a higher Android
                        // versionCode. Let code=1 builds inspect the APK; installed
                        // release builds use the tag only as a cheap hint and the APK
                        // verifier below remains authoritative.
                        if (!shouldOfferUpdate(version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)) {
                            return@withContext Result.success(UpdateCheckResult.UpToDate)
                        }

                        val asset =
                            findApkAsset(release.optJSONArray("assets"))
                                ?: throw IOException("GitHub release has no unambiguous APK asset")
                        val assetId = asset.optLong("id", -1L)
                        val expectedSize = asset.optLong("size", -1L)
                        val digest = asset.optString("digest")
                        if (assetId <= 0L || expectedSize <= 0L || !digest.isValidSha256Digest()) {
                            throw IOException("GitHub release APK metadata is incomplete")
                        }
                        val downloadUrl =
                            asset
                                .optString("browser_download_url")
                                .toHttpUrlOrNull()
                                ?.takeIf { it.scheme == "https" && it.host == "github.com" }
                                ?.toString()
                                ?: throw IOException("GitHub release APK URL is not trusted")

                        UpdateCheckResult.Available(
                            UpdateInfo(
                                version = version,
                                releaseNotes = release.optString("body").take(MAX_RELEASE_NOTES_CHARS),
                                downloadUrl = downloadUrl,
                                assetId = assetId,
                                expectedSize = expectedSize,
                                sha256 = digest.substring(7),
                            ),
                        )
                    }
                Result.success(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    /**
     * Downloads an APK into the app cache, verifies it, then opens the system
     * installer. The caller remains responsible for the user-facing progress
     * and error state.
     */
    suspend fun downloadAndInstall(
        info: UpdateInfo,
        onProgress: (Int) -> Unit = {},
    ) {
        val url =
            info.downloadUrl
                .toHttpUrlOrNull()
                ?.takeIf { it.scheme == "https" && it.host == "github.com" }
                ?: throw IOException("Update URL is not trusted")

        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, UPDATE_DIRECTORY).apply { mkdirs() }
            val file = File(directory, "komorei-${info.assetId}.apk")
            file.delete()

            try {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Accept", "application/vnd.android.package-archive")
                        .header("User-Agent", "Komorei/${BuildConfig.VERSION_NAME}")
                        .build()
                val response = client.newCall(request).execute()
                response.use {
                    if (!it.isSuccessful) {
                        throw IOException("APK download failed (${it.code})")
                    }
                    val body = it.body
                    val contentLength = body.contentLength()
                    if (contentLength > 0L && contentLength != info.expectedSize) {
                        throw IOException("APK content length does not match release metadata")
                    }
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(file).use { output ->
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > MAX_APK_BYTES || total > info.expectedSize) {
                                    throw IOException("APK is larger than the release metadata")
                                }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                if (info.expectedSize > 0L) {
                                    onProgress(((total * 100L) / info.expectedSize).toInt().coerceIn(0, 99))
                                }
                            }
                        }
                    }
                    if (total != info.expectedSize) {
                        throw IOException("Downloaded APK size does not match release metadata")
                    }
                    val actualDigest = digest.digest().toHexString()
                    if (!actualDigest.equals(info.sha256, ignoreCase = true)) {
                        throw IOException("Downloaded APK SHA-256 does not match release metadata")
                    }
                }
                onProgress(100)
                if (!verifyApk(file)) {
                    throw IOException("Downloaded APK failed package/signature validation")
                }
                withContext(Dispatchers.Main.immediate) {
                    installApk(file)
                }
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }
    }

    private fun findApkAsset(assets: org.json.JSONArray?): JSONObject? {
        if (assets == null) return null
        val apkAssets =
            buildList {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        add(asset)
                    }
                }
            }
        return apkAssets.singleOrNull()
    }

    private fun String.isValidSha256Digest(): Boolean =
        startsWith("sha256:", ignoreCase = true) &&
            length == 71 &&
            substring(7).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    private fun verifyApk(file: File): Boolean {
        val packageManager = context.packageManager
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
        val downloaded =
            packageManager.getPackageArchiveInfo(file.absolutePath, flags)
                ?: return false
        if (downloaded.packageName != context.packageName) return false

        val current = packageManager.getPackageInfo(context.packageName, flags)
        val downloadedVersion =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                downloaded.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                downloaded.versionCode.toLong()
            }
        val currentVersion =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                current.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                current.versionCode.toLong()
            }
        if (downloadedVersion <= currentVersion) return false

        val currentCertificates = certificateFingerprints(current)
        val downloadedCertificates = certificateFingerprints(downloaded)
        return currentCertificates.isNotEmpty() &&
            downloadedCertificates.isNotEmpty() &&
            downloadedCertificates.all { it in currentCertificates }
    }

    private fun certificateFingerprints(info: PackageInfo): Set<String> {
        val signatures =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = info.signingInfo ?: return emptySet()
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
        return signatures
            .orEmpty()
            .map { signature ->
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .toHexString()
            }.toSet()
    }

    private fun installApk(file: File) {
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                clipData = ClipData.newRawUri("Komorei update", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/tachibana-shin/komorei-app/releases/latest"
        const val UPDATE_DIRECTORY = "updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val MAX_APK_BYTES = 200L * 1024L * 1024L
        const val MAX_RELEASE_NOTES_CHARS = 10_000
    }
}

private fun ByteArray.toHexString(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

/** The only versionCode that is still treated as "never installed a release". */
private const val FIRST_BUILD_VERSION_CODE = 1

/**
 * Whether a release tagged [version] should be offered as an update to a build
 * reporting [versionName] / [versionCode].
 *
 * The parameters are explicit on purpose. The release automation rewrites
 * `gradle.properties` on every single release, so a rule tested through
 * `BuildConfig` silently changes meaning each time one ships — which is
 * exactly what happened to the "first release" case when VERSION_CODE moved
 * from 1 to 100014.
 */
internal fun shouldOfferUpdate(
    version: String,
    versionName: String,
    versionCode: Int,
): Boolean {
    val tagLooksNewer = isNewerVersion(version, versionName)
    val sameVersion = version == versionName
    return tagLooksNewer || (sameVersion && versionCode <= FIRST_BUILD_VERSION_CODE)
}

private fun normalizeVersion(raw: String): String? {
    val value = raw.trim().removePrefix("v")
    val core = value.substringBefore('-').substringBefore('+')
    if (core.isBlank()) return null
    val parts = core.split('.')
    if (parts.any { it.toIntOrNull() == null }) return null
    return parts.joinToString(".")
}

private fun isNewerVersion(
    latest: String,
    current: String,
): Boolean {
    val latestParts = versionParts(latest) ?: return false
    val currentParts = versionParts(current) ?: return false
    for (index in 0 until maxOf(latestParts.size, currentParts.size)) {
        val left = latestParts.getOrElse(index) { 0 }
        val right = currentParts.getOrElse(index) { 0 }
        if (left != right) return left > right
    }
    return false
}

private fun versionParts(version: String): List<Int>? = normalizeVersion(version)?.split('.')?.mapNotNull { it.toIntOrNull() }
