package git.shin.komorei

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SourceMigrationRepository
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.data.local.KrxDefaultsStore
import git.shin.komorei.data.local.RoomKrxDefaultsStore
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.source.SourceSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for [SourceSettingsViewModel] — the per-source settings
 * screen (migration, defaults, and dynamic settings).
 *
 * Uses a real [AnimeRepository] backed by the committed fake source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [36])
@RunWith(RobolectricTestRunner::class)
class SourceSettingsViewModelTest {
    private lateinit var repository: AnimeRepository
    private lateinit var viewModel: SourceSettingsViewModel
    private val mainDispatcher = UnconfinedTestDispatcher()

    companion object {
        private val fakeKrx: String =
            System.getProperty("komorei.test.fakeKrx")
                ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() =
        runBlocking {
            // Before anything that can reach `Dispatchers.Main`: the test
            // dispatcher has to be installed first, or whichever test class
            // happens to run first in a fresh JVM fails on it.
            Dispatchers.setMain(mainDispatcher)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val host = KrxHostImpl(context)
            val registry = KrxSourceRegistry(context, host)
            registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
            val db =
                Room
                    .inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            repository = AnimeRepository(registry)
            val defaultsStore: KrxDefaultsStore = RoomKrxDefaultsStore(db.krxDefaultsDao())
            val migrationRepo = SourceMigrationRepository(db.animeDao(), repository)
            viewModel =
                SourceSettingsViewModel(
                    context,
                    repository,
                    defaultsStore,
                    migrationRepo,
                    SavedStateHandle(mapOf("sourceId" to "vi.fake-source")),
                )
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sourceSettingsViewModelLoadsSettings() =
        runBlocking {
            // Verify the VM initializes without error
            viewModel.loadSettings()
            assertNotNull(viewModel)
        }

    @Test
    fun sourceSettingsViewModelHasMessages() =
        runBlocking {
            // Verify messages flow is accessible (assert so the lambda's last
            // expression is Unit — JUnit rejects a test method that returns a value).
            assertNotNull(viewModel.messages)
        }

    @Test
    fun sourceSettingsViewModelResetSettings() =
        runBlocking {
            // Verify resetSettings works
            viewModel.resetSettings()
        }

    @Test
    fun sourceSettingsViewModelMigrateData() =
        runBlocking {
            // Verify migrateData works
            viewModel.migrateData()
        }

    @Test
    fun sourceSettingsViewModelDifferentSource() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db2 =
                Room
                    .inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            val defaultsStore: KrxDefaultsStore = RoomKrxDefaultsStore(db2.krxDefaultsDao())
            val migrationRepo = SourceMigrationRepository(db2.animeDao(), repository)
            val vm2 =
                SourceSettingsViewModel(
                    context,
                    repository,
                    defaultsStore,
                    migrationRepo,
                    SavedStateHandle(mapOf("sourceId" to "vi.fake-source")),
                )
            assertNotNull(vm2)
        }
}
