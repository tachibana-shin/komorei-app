package git.shin.komorei

import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.runner.HomePartialResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import git.shin.komorei.sdk.runner.HomeComponent as RunnerHomeComponent
import git.shin.komorei.sdk.runner.HomeComponentValue as RunnerHomeComponentValue
import git.shin.komorei.sdk.runner.HomeLayout as RunnerHomeLayout
import git.shin.komorei.sdk.runner.Link as RunnerLink

/**
 * The host half of streaming homes: a source calls `send_partial_result` from
 * inside `get_home`, the runner decodes it and re-enters [KrxHostImpl.partialHome]
 * on the runner thread, and the Home view model collects what lands there.
 *
 * Lives outside `git.shin.komorei.sdk` on purpose — that whole package is routed
 * to `testSdkRunnerUnitTest`, because JNA's native state does not survive a
 * Robolectric sandbox, and none of this needs the runner. The wasm side is
 * covered end to end by the Rust test `home_streams_partial_results_before_it_returns`.
 *
 * No `withTimeout` anywhere: inside `runTest` it measures *virtual* time, so it
 * fires the instant the scheduler goes idle rather than waiting for a send. A
 * genuine hang is what `runTest`'s own real-time watchdog is for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomePartialResultHostTest {
    private fun host() = KrxHostImpl(ApplicationProvider.getApplicationContext<android.content.Context>())

    /** A row of plain links, as a source sends for its catalogue strip. */
    private fun row(title: String) =
        RunnerHomeComponent(
            title = title,
            subtitle = null,
            value =
                RunnerHomeComponentValue.Links(
                    listOf(
                        RunnerLink(
                            title = title,
                            subtitle = null,
                            imageUrl = null,
                            value = null,
                        ),
                    ),
                ),
        )

    /**
     * Suspends until a collector is live.
     *
     * Mirrors `AnimeRepository.awaitPartialHomeSubscribers` in production: the
     * send path never blocks, so a row emitted before anyone subscribes is gone
     * and the test would otherwise race its own collector.
     */
    private suspend fun awaitSubscriber(host: KrxHostImpl) {
        host.partialHomeSubscribers.first { it > 0 }
    }

    @Test
    fun `a streamed row arrives as an app model`() =
        runTest {
            val host = host()
            val first = async { host.partialHomeResults.first() }
            awaitSubscriber(host)

            host.partialHome(HomePartialResult.Component(row("Nổi bật")))

            assertEquals("Nổi bật", first.await().title)
        }

    @Test
    fun `a placeholder layout is not painted, only the rows after it`() =
        runTest {
            val host = host()
            val first = async { host.partialHomeResults.first() }
            awaitSubscriber(host)

            // A source announces the shape of its page with an empty layout so the
            // app can show the right skeletons. Those rows carry no content, so
            // forwarding them would put a blank strip above the real ones — the
            // first thing a collector must see is the first real row.
            host.partialHome(
                HomePartialResult.Layout(
                    RunnerHomeLayout(components = listOf(row("Nổi bật"), row("Mới cập nhật"))),
                ),
            )
            host.partialHome(HomePartialResult.Component(row("Mới cập nhật")))

            assertEquals("Mới cập nhật", first.await().title)
        }

    @Test
    fun `rows arrive in the order the source sent them`() =
        runTest {
            val host = host()
            val titles = async { host.partialHomeResults.take(3).toList() }
            awaitSubscriber(host)

            host.partialHome(HomePartialResult.Component(row("one")))
            host.partialHome(HomePartialResult.Component(row("two")))
            host.partialHome(HomePartialResult.Component(row("three")))

            assertEquals(listOf("one", "two", "three"), titles.await().map { it.title })
        }

    @Test
    fun `sending with nobody listening never blocks`() {
        val host = host()
        // The runner calls this re-entrantly on a thread that is inside a
        // blocking wasm call. Overrunning the buffer is the only way a send can
        // fail, and it must fail by dropping the row — never by suspending a
        // thread the wasm call is waiting on.
        repeat(KrxHostImpl.PARTIAL_HOME_BUFFER + 8) {
            host.partialHome(HomePartialResult.Component(row("row $it")))
        }
    }
}
