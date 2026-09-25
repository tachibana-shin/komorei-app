package git.shin.komorei

import git.shin.komorei.data.backup.GoogleDriveBackupApi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriveBackupApiTest {
    @Test
    fun findsUploadsAndDownloadsTheAppDataBackup() =
        runBlocking {
            val requests = mutableListOf<String>()
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        requests += "${chain.request().method} ${chain.request().url}"
                        val body =
                            when {
                                chain.request().method == "GET" &&
                                    chain
                                        .request()
                                        .url.query
                                        ?.contains("spaces=appDataFolder") == true ->
                                    """{"files":[{"id":"remote-1","name":"komorei-backup.kbackup","size":"12"}]}"""
                                chain.request().method == "GET" -> "backup-bytes"
                                else -> """{"id":"remote-1","name":"komorei-backup.kbackup","size":"12"}"""
                            }
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build()
            val api = GoogleDriveBackupApi(client)

            val remote = api.findBackup("token")
            assertEquals("remote-1", remote?.id)
            api.uploadBackup("token", "new".toByteArray(), remote?.id)
            assertEquals("backup-bytes", api.downloadBackup("token", "remote-1")?.decodeToString())
            assertEquals(3, requests.size)
        }

    @Test
    fun missingRemoteFileReturnsNull() =
        runBlocking {
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("{\"files\":[]}".toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build()

            assertNull(GoogleDriveBackupApi(client).findBackup("token"))
        }
}
