package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.LogStreamClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LogStreamClientTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearSetting() {
        LogStreamClient.setUrl(context, "").getOrThrow()
    }

    @Test
    fun acceptsAndNormalizesLogcatUrl() {
        assertTrue(LogStreamClient.setUrl(context, "http://10.0.2.2:9000").isSuccess)
        assertEquals("http://10.0.2.2:9000/", LogStreamClient.currentUrl(context))
    }

    @Test
    fun rejectsNonRootUrl() {
        assertTrue(LogStreamClient.setUrl(context, "http://10.0.2.2:9000/logs").isFailure)
        assertEquals("", LogStreamClient.currentUrl(context))
    }

    @Test
    fun clearingUrlDisablesRemoteLogging() {
        LogStreamClient.setUrl(context, "https://logs.example.test:9000").getOrThrow()
        assertFalse(LogStreamClient.setUrl(context, "").getOrThrow())
        assertEquals("", LogStreamClient.currentUrl(context))
    }
}
