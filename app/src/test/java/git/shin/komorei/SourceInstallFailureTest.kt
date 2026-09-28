package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.ExternalSourceInfo
import git.shin.komorei.data.SourceReposRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.sources.SourcesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Installing a source that cannot actually be used.
 *
 * The package built here passes every check up to the point of being trusted and
 * fails only afterwards: `KrxManager.readInfo` reads a well-formed
 * `source.json`, so the install is allowed to proceed, and the wasm payload is
 * then simply absent. That is the shape of a genuinely bad download, and it is
 * the path that used to leave the app somewhere the reader could not get out of
 * — the row said "đang cài" indefinitely, and the source had already been
 * published to the list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SourceInstallFailureTest {
    private lateinit var context: Context
    private lateinit var repository: AnimeRepository
    private lateinit var registry: KrxSourceRegistry
    private lateinit var stateStore: SourceStateStore
    private val mainDispatcher = UnconfinedTestDispatcher()

    companion object {
        private val fakeKrx: String =
            System.getProperty("komorei.test.fakeKrx")
                ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")

        private const val BROKEN_ID = "vi.broken-source"

        /** A well-formed manifest and no wasm — valid enough to install, impossible to run. */
        private fun brokenPackage(): ByteArray {
            val manifest =
                """{"info":{"id":"$BROKEN_ID","name":"Broken","version":1,"url":"https://example.invalid"}}"""
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("source.json"))
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            return out.toByteArray()
        }

        /** Nothing listens on port 1, so the download fails the way a dropped connection does. */
        private fun unreachable(
            id: String = BROKEN_ID,
            name: String = "Broken",
        ) = ExternalSourceInfo(
            id = id,
            name = name,
            version = "1",
            downloadURL = "http://127.0.0.1:1/$id.krx",
            languages = listOf("Vietnamese"),
        )
    }

    @Before
    fun setUp() =
        runBlocking {
            Dispatchers.setMain(mainDispatcher)
            context = ApplicationProvider.getApplicationContext()
            stateStore = SourceStateStore(context)
            registry = KrxSourceRegistry(context, KrxHostImpl(context))
            registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes()).let {
                assertNotNull("fake source should load", it)
            }
            repository = AnimeRepository(registry)
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVM(): SourcesViewModel =
        SourcesViewModel(
            appContext = context,
            repository = repository,
            registry = registry,
            stateStore = stateStore,
            reposRepository = SourceReposRepository(OkHttpClient.Builder().build()),
        )

    @Test
    fun installSpinnerClearsWhenTheDownloadFails() =
        runBlocking {
            val vm = createVM()
            vm.installExternal(unreachable())

            // The row must settle. Before the fix a throw anywhere in the install
            // skipped the removal of the id, and the row read "đang cài" for the
            // rest of the process — the only way out was restarting the app.
            assertTrue("the installing set must drain", vm.installingIds.first { it.isEmpty() }.isEmpty())
        }

    @Test
    fun failedPackageIsNotLeftListedAsInstalled() =
        runBlocking {
            val vm = createVM()
            val before = vm.sources.first { it.isNotEmpty() }.map { it.source.id }

            // Install the broken package straight through the registry: this is
            // the throw that used to escape past the spinner cleanup.
            val result = runCatching { registry.installKrx(brokenPackage()) }
            assertTrue("a package with no wasm must not install", result.isFailure || result.getOrNull() == null)

            val after = vm.sources.first { list -> list.isNotEmpty() }.map { it.source.id }
            assertFalse(
                "a package that could not be loaded must not appear as installed",
                BROKEN_ID in after,
            )
            assertEquals("a failed install must not add a source", before, after)
        }

    @Test
    fun failedInstallLeavesNothingOnDisk() =
        runBlocking {
            createVM()
            runCatching { registry.installKrx(brokenPackage()) }

            val file = File(File(context.filesDir, "sources"), "$BROKEN_ID.krx")
            assertFalse("a failed install must not leave the package on disk", file.exists())
        }

    @Test
    fun failedInstallDoesNotBlockTheNextAttempt() =
        runBlocking {
            val vm = createVM()
            vm.installExternal(unreachable())
            vm.installingIds.first { it.isEmpty() }

            // The guard is keyed on the installing set, so a stuck entry turned
            // every later tap on that row into a silent no-op.
            vm.installExternal(unreachable())
            vm.installingIds.first { it.isEmpty() }
            assertTrue("the id must not be left behind", vm.installingIds.value.isEmpty())
        }
}
