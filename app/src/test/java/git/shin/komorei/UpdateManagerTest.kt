package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.update.UpdateCheckResult
import git.shin.komorei.data.update.UpdateManager
import git.shin.komorei.data.update.shouldOfferUpdate
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateManagerTest {
    @Test
    fun latestReleaseWithApkIsAvailable() =
        runBlocking {
            val manager =
                updateManager(
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
    fun rejectsReleaseWithoutAssetDigest() =
        runBlocking {
            val manager =
                updateManager(
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
    fun olderReleaseIsUpToDate() =
        runBlocking {
            val manager =
                updateManager(
                    """{
                "tag_name":"v0.0.1",
                "body":"Old",
                "assets":[]
            }""",
                )

            assertEquals(UpdateCheckResult.UpToDate, manager.checkForUpdate().getOrThrow())
        }

    @Test
    fun firstReleaseWithSameVersionNameIsStillOffered() {
        // A never-released build (versionCode 1) must still be offered the
        // first release even though the tag equals its own versionName.
        assertTrue(shouldOfferUpdate(version = "1.0.0", versionName = "1.0.0", versionCode = 1))
    }

    @Test
    fun sameVersionNameOnAnInstalledReleaseIsNotOffered() {
        // Once a real release is installed (any versionCode above 1) the tag is
        // only a hint, so an identical versionName must NOT nag. This is the
        // branch that regressed when the release job bumped VERSION_CODE from
        // 1 to 100014 while the test still read it from BuildConfig.
        assertFalse(shouldOfferUpdate(version = "1.0.0", versionName = "1.0.0", versionCode = 100014))
    }

    @Test
    fun aNewerTagIsAlwaysOffered() {
        assertTrue(shouldOfferUpdate(version = "99.0.0", versionName = "1.0.0", versionCode = 100014))
    }

    @Test
    fun anOlderTagIsNeverOffered() {
        assertFalse(shouldOfferUpdate(version = "0.0.1", versionName = "1.0.0", versionCode = 1))
    }

    private fun updateManager(body: String): UpdateManager {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }.build()
        return UpdateManager(context, client)
    }
}
