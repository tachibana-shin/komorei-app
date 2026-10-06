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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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

        /** A user-installed id for the update tests, distinct from the bundled one. */
        private const val UPDATE_ID = "vi.update-fixture"

        /** A well-formed manifest and no wasm — valid enough to install, impossible to run. */
        private fun brokenPackage(): ByteArray = brokenPackageFor(BROKEN_ID)

        /** The same, but claiming [id], so it can be aimed at an installed source. */
        private fun brokenPackageFor(id: String): ByteArray {
            val manifest = """{"info":{"id":"$id","name":"Broken","version":1,"url":"https://example.invalid"}}"""
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("source.json"))
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            return out.toByteArray()
        }

        /**
         * The real fixture repacked with a different version and name but the
         * same id and the same wasm — a genuine "newer release of this source",
         * not a different source wearing its name.
         */
        private fun repackedWithVersion(
            id: String,
            version: Int,
            name: String,
        ): ByteArray {
            val out = ByteArrayOutputStream()
            ZipInputStream(File(fakeKrx).inputStream().buffered()).use { source ->
                ZipOutputStream(out).use { zip ->
                    var entry = source.nextEntry
                    while (entry != null) {
                        val payload = source.readBytes()
                        val isManifest = entry.name == "source.json" || entry.name == "Payload/source.json"
                        zip.putNextEntry(ZipEntry(entry.name))
                        if (isManifest) {
                            val manifest =
                                """{"info":{"id":"$id","name":"$name","version":$version,""" +
                                    """"url":"https://example.invalid","languages":["Vietnamese"]}}"""
                            zip.write(manifest.toByteArray(Charsets.UTF_8))
                        } else {
                            zip.write(payload)
                        }
                        zip.closeEntry()
                        entry = source.nextEntry
                    }
                }
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
            reposRepository = SourceReposRepository(OkHttpClient.Builder().build(), ApplicationProvider.getApplicationContext()),
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

    /**
     * Upgrading a source that is already installed.
     *
     * The duplicate-id check rejected every package for a source that was
     * already there, and every package an update offers is for a source that is
     * already there — so the "Update" pill could not update anything, and the
     * only way to get a new version was uninstall (losing settings and history)
     * and reinstall.
     */
    @Test
    fun installingOverAnInstalledSourceReplacesIt() =
        runBlocking {
            val vm = createVM()
            // Under its own id: setUp already registered the shipped
            // vi.fake-source, and a bundled id is deliberately not replaceable.
            val first = registry.installKrx(repackedWithVersion(UPDATE_ID, version = 1, name = "Original"))
            assertNotNull("the first install should succeed", first)
            val id = first!!.id
            assertTrue("the source should now be user-installed", registry.isUserInstalled(id))

            // Same id, higher version, and a name the caller can observe. The
            // fixture is the same wasm — the point is that the package is
            // accepted at all, and that the registry ends up describing the
            // second package rather than the first.
            val second = registry.installKrx(repackedWithVersion(id, version = 99, name = "Upgraded"))
            assertNotNull("an update must be accepted, not rejected as a duplicate", second)
            assertEquals("the id must be preserved across an update", id, second!!.id)
            assertEquals("the new version must be what the registry reports", 99, second.version)

            val listed = vm.sources.first { list -> list.any { it.source.id == id } }
            val source = listed.first { it.source.id == id }.source
            assertEquals("the list must show the updated name", "Upgraded", source.name)
        }

    @Test
    fun aFailedUpdateKeepsTheWorkingVersion() =
        runBlocking {
            val vm = createVM()
            val good = registry.installKrx(repackedWithVersion(UPDATE_ID, version = 1, name = "Original"))
            assertNotNull("the first install should succeed", good)
            val id = good!!.id
            val versionBefore = good.version

            // A replacement that cannot load: same id, valid manifest, no wasm.
            val broken = registry.installKrx(brokenPackageFor(id))
            assertNull("the broken replacement must not report success", broken)

            // The point of the difference from a first install: a failed update
            // leaves the reader with the version they had, not with nothing.
            assertTrue("the source must survive a failed update", registry.isUserInstalled(id))
            val after =
                vm.sources
                    .first { list -> list.any { it.source.id == id } }
                    .first { it.source.id == id }
                    .source
            // The app's Source carries the version as text; the manifest's is a
            // number. Compared as the string the list actually shows.
            assertEquals(
                "the previous version must still be registered",
                versionBefore.toString(),
                after.version,
            )
            val file = File(File(context.filesDir, "sources"), "$id.krx")
            assertTrue("the previous package must be back on disk", file.exists())
        }

    @Test
    fun aBundledSourceCannotBeReplaced() =
        runBlocking {
            val vm = createVM()
            // vi.fake-source ships inside the APK. Writing it to filesDir would
            // shadow the shipped package for as long as the entry existed, so an
            // "update" would outlive the app version that shipped it.
            val repackaged = registry.installKrx(repackedWithVersion("vi.fake-source", 99, "Hijacked"))
            assertNull("a bundled source must not be replaceable", repackaged)

            val listed = vm.sources.first { list -> list.any { it.source.id == "vi.fake-source" } }
            val source = listed.first { it.source.id == "vi.fake-source" }.source
            assertEquals(
                "the bundled source must keep its shipped metadata",
                "Komorei Fake (VI)",
                source.name,
            )
        }
}
