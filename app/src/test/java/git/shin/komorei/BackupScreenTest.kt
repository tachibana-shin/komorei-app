package git.shin.komorei

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.data.SearchHistoryStore
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.data.backup.BackupCodec
import git.shin.komorei.data.backup.BackupRepository
import git.shin.komorei.data.backup.BackupSyncScheduler
import git.shin.komorei.data.backup.BackupSyncSettingsStore
import git.shin.komorei.data.backup.DriveBackupApi
import git.shin.komorei.data.backup.RemoteDriveFile
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.backup.BackupScreen
import git.shin.komorei.ui.screens.backup.BackupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class BackupScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: KomoreiDatabase
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun rendersAndOpensBackupOptions() {
        val viewModel = newViewModel()
        composeTestRule.setContent { BackupScreen(onBack = {}, viewModel = viewModel) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("backup_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("backup_background_sync_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("backup_create").performClick()
    }

    @Test
    fun backButtonInvokesCallback() {
        var calls = 0
        val viewModel = newViewModel()
        composeTestRule.setContent { BackupScreen(onBack = { calls++ }, viewModel = viewModel) }
        composeTestRule.onNodeWithTag("backup_back").performClick()
        assertEquals(1, calls)
    }

    private fun newViewModel(): BackupViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val host = KrxHostImpl(context)
        val repository = BackupRepository(
            context = context,
            database = database,
            animeDao = database.animeDao(),
            defaultsDao = database.krxDefaultsDao(),
            sourceStateStore = SourceStateStore(context),
            searchHistoryStore = SearchHistoryStore(context),
            sourceRegistry = KrxSourceRegistry(context, host),
            codec = BackupCodec(),
        )
        val syncSettings = BackupSyncSettingsStore(context)
        val scheduler = BackupSyncScheduler(context, syncSettings)
        return BackupViewModel(repository, EmptyDriveApi, syncSettings, scheduler)
    }

    private object EmptyDriveApi : DriveBackupApi {
        override suspend fun findBackup(accessToken: String): RemoteDriveFile? = null
        override suspend fun uploadBackup(
            accessToken: String,
            bytes: ByteArray,
            existingId: String?,
        ): RemoteDriveFile = error("not used")
        override suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray? = null
    }
}
