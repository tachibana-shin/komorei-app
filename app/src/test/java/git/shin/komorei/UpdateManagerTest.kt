package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.update.UpdateCheckResult
import git.shin.komorei.data.update.UpdateManager
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateManagerTest {
    @Test
    fun latestReleaseWithApkIsAvailable() = runBlocking {
        val manager = updateManager(
            """{
                "tag_name":"v99.0.0",
                "body":"Release notes",
                "assets":[
                    {"id":123,"name":"komorei-release.apk","size":1234,"digest":"sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","browser_download_url":"https://github.com/tachibana-shin/komorei-app/releases/download/v99.0.0/komorei-release.apk"}
                ]
            }""",
        )

        val result = manager.checkForUpdate().getOrThrow()

        assertTrue(result is UpdateCheckResult.Available)
        result as UpdateCheckResult.Available
        assertEquals("99.0.0", result.info.version)
        assertEquals("Release notes", result.info.releaseNotes)
        assertTrue(result.info.downloadUrl.endsWith("komorei-release.apk"))
    }

    @Test
    fun rejectsReleaseWithoutAssetDigest() = runBlocking {
        val manager = updateManager(
            """{
                "tag_name":"v99.0.0",
                "body":"Release notes",
                "assets":[
                    {"id":123,"name":"komorei-release.apk","size":1234,"browser_download_url":"https://github.com/tachibana-shin/komorei-app/releases/download/v99.0.0/komorei-release.apk"}
                ]
            }""",
        )

        assertTrue(manager.checkForUpdate().isFailure)
    }

    @Test
    fun olderReleaseIsUpToDate() = runBlocking {
        val manager = updateManager(
            """{
                "tag_name":"v0.0.1",
                "body":"Old",
                "assets":[]
            }""",
        )

        assertEquals(UpdateCheckResult.UpToDate, manager.checkForUpdate().getOrThrow())
    }

    @Test
    fun firstReleaseWithSameVersionNameIsStillOffered() = runBlocking {
        val manager = updateManager(
            """{
                "tag_name":"v1.0.0",
                "body":"First release",
                "assets":[
                    {"id":123,"name":"komorei-release.apk","size":1234,"digest":"sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","browser_download_url":"https://github.com/tachibana-shin/komorei-app/releases/download/v1.0.0/komorei-release.apk"}
                ]
            }""",
        )

        assertTrue(manager.checkForUpdate().getOrThrow() is UpdateCheckResult.Available)
    }

    private fun updateManager(body: String): UpdateManager {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return UpdateManager(context, client)
    }
}
