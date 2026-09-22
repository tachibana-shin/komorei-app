package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The SDK's `SegmentUrlInterceptor` / `SegmentDataInterceptor` are OPTIONAL
 * traits: a source only exports `intercept_segment_url` / `intercept_segment_data`
 * when it implements them (the fake source does). Every other source (like
 * vi.kkphim) calls the runner export and gets `RunnerException.ExportMissing`,
 * which used to kill the media request ("Playback failed due to an unspecified
 * network or file error.") before a real stream could load. The repo must fall
 * back to the untouched URL / bytes in that case.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class SegmentInterceptorOptionalTest {

    private lateinit var repository: AnimeRepository

    companion object {
        private const val KKPHIM = "vi.kkphim"
        private const val FAKE = "vi.fake-source"
        private val kkphimKrx: String = System.getProperty("komorei.test.kkphimKrx")
            ?: error("missing -Dkomorei.test.kkphimKrx (set by app/build.gradle.kts)")
        private val fakeKrx: String = System.getProperty("komorei.test.fakeKrx")
            ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val host = KrxHostImpl(context)
        val registry = KrxSourceRegistry(context, host)
        registry.loadKrx(KKPHIM, File(kkphimKrx).readBytes())
        registry.loadKrx(FAKE, File(fakeKrx).readBytes())
        repository = AnimeRepository(registry)
    }

    @Test
    fun sourceWithoutTheOptionalTraitFallsBackToIdentity() {
        val recordUrl = "https://example.com/media/master.m3u8"
        val recordData = byteArrayOf(0, 1, 2, 3, 4)

        // vi.kkphim does not export intercept_segment_* — the interceptor must
        // leave the URL and the body untouched instead of throwing ExportMissing.
        val urlIntercept = repository.segmentUrlInterceptorFor(KKPHIM)
            ?: error("interceptor should exist for a loaded source")
        assertEquals(recordUrl, urlIntercept.intercept(null, recordUrl))

        val dataIntercept = repository.segmentDataInterceptorFor(KKPHIM)
            ?: error("data interceptor should exist for a loaded source")
        assertEquals(
            recordData.toList(),
            dataIntercept.intercept(null, recordUrl, recordData).toList(),
        )
    }

    @Test
    fun sourceImplementingTheTraitStillUsesTheRealExport() {
        val url = "https://example.com/media/master.m3u8"
        val data = byteArrayOf(9, 8, 7)

        // vi.fake-source DOES export both interceptors (identity impl) — the
        // runner round-trip must return its value, not the fallback.
        val urlIntercept = repository.segmentUrlInterceptorFor(FAKE)
            ?: error("interceptor should exist for a loaded source")
        assertEquals(url, urlIntercept.intercept(null, url))

        val dataIntercept = repository.segmentDataInterceptorFor(FAKE)
            ?: error("data interceptor should exist for a loaded source")
        assertEquals(data.toList(), dataIntercept.intercept(null, url, data).toList())
    }
}