package git.shin.komorei.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteDriveFile(
    val id: String,
    val name: String,
    val modifiedTime: String?,
    val size: Long?,
)

class DriveApiException(val statusCode: Int, message: String) : Exception(message)

/** Small Drive REST surface; the hidden appDataFolder keeps the backup private. */
interface DriveBackupApi {
    suspend fun findBackup(accessToken: String): RemoteDriveFile?
    suspend fun uploadBackup(accessToken: String, bytes: ByteArray, existingId: String?): RemoteDriveFile
    suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray?
}

@Singleton
class GoogleDriveBackupApi @Inject constructor(
    private val client: OkHttpClient,
) : DriveBackupApi {
    override suspend fun findBackup(accessToken: String): RemoteDriveFile? =
        withContext(Dispatchers.IO) {
            val url = FILES_URL.toHttpUrl().newBuilder()
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("q", "name = '${BackupFormat.FILE_NAME}' and trashed = false")
                .addQueryParameter("pageSize", "10")
                .addQueryParameter("fields", "files(id,name,modifiedTime,size)")
                .build()
            val response = execute(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build(),
            )
            val files = response.optJSONArray("files") ?: return@withContext null
            if (files.length() == 0) return@withContext null
            files.optJSONObject(0)?.toRemoteFile()
        }

    override suspend fun uploadBackup(
        accessToken: String,
        bytes: ByteArray,
        existingId: String?,
    ): RemoteDriveFile = withContext(Dispatchers.IO) {
        val response = if (existingId == null) {
            val metadata = JSONObject()
                .put("name", BackupFormat.FILE_NAME)
                .put("parents", org.json.JSONArray().put("appDataFolder"))
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata",
                    null,
                    metadata.toString().toRequestBody(JSON_MEDIA_TYPE),
                )
                .addFormDataPart(
                    "file",
                    BackupFormat.FILE_NAME,
                    bytes.toRequestBody(JSON_MEDIA_TYPE),
                )
                .build()
            val url = UPLOAD_URL.toHttpUrl().newBuilder()
                .addQueryParameter("uploadType", "multipart")
                .addQueryParameter("fields", "id,name,modifiedTime,size")
                .build()
            execute(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .post(body)
                    .build(),
            )
        } else {
            val url = "$UPLOAD_URL/$existingId".toHttpUrl().newBuilder()
                .addQueryParameter("uploadType", "media")
                .addQueryParameter("fields", "id,name,modifiedTime,size")
                .build()
            execute(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .patch(bytes.toRequestBody(JSON_MEDIA_TYPE))
                    .build(),
            )
        }
        response.toRemoteFile()
    }

    override suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val url = FILES_URL.toHttpUrl().newBuilder()
                .addPathSegment(fileId)
                .addQueryParameter("alt", "media")
                .build()
            val raw = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build(),
            ).execute()
            raw.use {
                if (it.code == 404) return@withContext null
                if (!it.isSuccessful) {
                    throw DriveApiException(it.code, "Google Drive download failed (${it.code})")
                }
                it.body.bytes()
            }
        }

    private fun execute(request: Request): JSONObject {
        val response = client.newCall(request).execute()
        response.use {
            val statusCode = it.code
            val body = it.body.string()
            if (!it.isSuccessful) {
                throw DriveApiException(statusCode, "Google Drive request failed ($statusCode)")
            }
            return runCatching { JSONObject(body) }
                .getOrElse { throw DriveApiException(statusCode, "Google Drive returned invalid JSON") }
        }
    }

    private fun JSONObject.toRemoteFile() = RemoteDriveFile(
        id = getString("id"),
        name = optString("name", BackupFormat.FILE_NAME),
        modifiedTime = optString("modifiedTime").takeIf { it.isNotBlank() },
        size = optLong("size", -1L).takeIf { it >= 0L },
    )

    private companion object {
        const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
