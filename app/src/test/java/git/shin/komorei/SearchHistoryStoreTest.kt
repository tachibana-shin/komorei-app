package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.SearchHistoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [SearchHistoryStore] history semantics — in particular that
 * typing-pause partials ("t", "te", "tes") are superseded by the settled
 * query ("test") instead of accumulating as junk entries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchHistoryStoreTest {

    private fun newStore(): SearchHistoryStore {
        // Fresh prefs name so tests are isolated from each other.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SearchHistoryStore(context)
        store.clearHistory()
        return store
    }

    @Test
    fun addQuery_supersedesPrefixPartials() {
        val store = newStore()

        // Simulate a slow-typing search: partials recorded on pause, then the
        // settled query. The partials must be dropped once the full query lands.
        store.addQuery("t")
        store.addQuery("te")
        store.addQuery("tes")
        store.addQuery("test")

        assertEquals(listOf("test"), store.getHistory())
    }

    @Test
    fun addQuery_movesExistingToFront() {
        val store = newStore()

        store.addQuery("naruto")
        store.addQuery("frieren")
        store.addQuery("naruto")

        assertEquals(listOf("naruto", "frieren"), store.getHistory())
    }

    @Test
    fun addQuery_keepsShorterNonSubstringQuery() {
        val store = newStore()

        // A distinct short query not contained in the new one survives.
        store.addQuery("frieren")
        store.addQuery("naruto shippuden")

        assertEquals(listOf("naruto shippuden", "frieren"), store.getHistory())
    }

    @Test
    fun addQuery_supersedesShorterSubstringWhenExtended() {
        val store = newStore()

        // "naruto" is a strict substring-prefix of "naruto shippuden" — the
        // longer settled query supersedes it.
        store.addQuery("naruto")
        store.addQuery("naruto shippuden")

        assertEquals(listOf("naruto shippuden"), store.getHistory())
    }

    @Test
    fun blankQueriesIgnored() {
        val store = newStore()
        store.addQuery("   ")
        assertTrue(store.getHistory().isEmpty())
    }
}