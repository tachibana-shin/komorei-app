package git.shin.komorei

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.data.local.KrxDefaultsStore
import git.shin.komorei.model.SourceSettingValue
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.sdk.runner.HostDefaultValue
import git.shin.komorei.ui.screens.home.HomeViewModel
import git.shin.komorei.ui.screens.source.SourceSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The per-source home screen (`Screen.SourceHome`) scoping: a [HomeViewModel]
 * constructed from the route's `sourceId` nav arg loads ONLY that source's
 * home data, and the settings pipeline (`get_settings` via the real runner →
 * [SourceSettingsViewModel]) exposes the source's dynamic settings read-only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SourceHomeScopedTest {

    private lateinit var repository: AnimeRepository
    private lateinit var host: KrxHostImpl
    private lateinit var registry: KrxSourceRegistry
    private lateinit var defaultsStore: KrxDefaultsStore
    private val mainDispatcher = UnconfinedTestDispatcher()

    companion object {
        private val fakeKrx: String = System.getProperty("komorei.test.fakeKrx")
            ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() = runBlocking {
        assertNotNull(
            "missing uniffi.component.komorei_runner.libraryOverride (set by app/build.gradle.kts)",
            System.getProperty("uniffi.component.komorei_runner.libraryOverride"),
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        host = KrxHostImpl(context)
        defaultsStore = host.defaultsStore
        registry = KrxSourceRegistry(context, host)
        val runner = registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
        assertNotNull("fake source should load", runner)
        repository = AnimeRepository(registry)
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun scopedViewModelLoadsOnlyItsSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = HomeViewModel(
            context,
            repository,
            SourceStateStore(context),
            SavedStateHandle(mapOf("sourceId" to "vi.fake-source")),
        )

        // The scoped source resolves (from ALL sources, disabled or not).
        val scoped = withTimeout(15_000) { vm.source.first { it != null }!! }
        assertEquals("vi.fake-source", scoped.id)
        assertEquals("Komorei Fake (VI)", scoped.name)

        // Only the scoped source's home is ever loaded — never the catalog.
        awaitUntil { vm.sourceDataMap.value["vi.fake-source"]?.isLoading == false }
        assertEquals(setOf("vi.fake-source"), vm.sourceDataMap.value.keys)
        assertTrue(vm.sourceDataMap.value.getValue("vi.fake-source").home.isNotEmpty())
    }

    @Test
    fun scopedViewModelStillExposesListings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = HomeViewModel(
            context,
            repository,
            SourceStateStore(context),
            SavedStateHandle(mapOf("sourceId" to "vi.fake-source")),
        )

        vm.loadListings("vi.fake-source")
        awaitUntil { vm.listingStateMap.value["vi.fake-source"]?.listings?.size == 4 }
        assertEquals(
            listOf("latest", "popular", "ongoing", "completed"),
            vm.listingStateMap.value.getValue("vi.fake-source").listings.map { it.id },
        )
    }

    @Test
    fun settingsAreExposedReadOnlyFromRunner() = runBlocking {
        val settings = repository.getSettings("vi.fake-source")

        // Fake source exposes its two ToggleSettings plus one ButtonSetting
        // ("Xoá bộ nhớ đệm nguồn") from get_dynamic_settings — mirrored 1:1.
        assertEquals(3, settings.size)
        assertEquals(listOf("prefer_fhd", "show_intro", "clear_cache"), settings.map { it.key })
        assertTrue(settings[0].value is SourceSettingValue.Toggle)
        assertTrue(settings[1].value is SourceSettingValue.Toggle)
        assertTrue(settings[2].value is SourceSettingValue.Button)
        assertTrue(settings[0].notification != null)
        assertEquals(listOf("settings"), settings[0].refreshes)
        // The button's `notification` is the key forwarded on tap.
        assertEquals("clear_cache", settings[2].notification)
    }

    @Test
    fun settingChangeWithNotificationIsForwardedToTheSource() = runBlocking {
        val vm = SourceSettingsViewModel(
            repository,
            defaultsStore,
            SavedStateHandle(mapOf("sourceId" to "vi.fake-source")),
        )
        awaitUntil { vm.uiState.value.settings != null }

        // prefer_fhd declares notification "Đã thay đổi ưu tiên chất lượng" —
        // the app must forward it to handle_notification after the write, and
        // the source mirrors it back into defaults (round-trip, observable).
        val prefer = vm.uiState.value.settings!!.first { it.key == "prefer_fhd" }
        val current = (prefer.value as SourceSettingValue.Toggle).default
        vm.toggleSetting("prefer_fhd", current)

        awaitUntil {
            val v = registry.defaultsGet("vi.fake-source", "last_notification") as? HostDefaultValue.String
            v?.v1 == "Đã thay đổi ưu tiên chất lượng"
        }
    }

    @Test
    fun buttonSettingClickSendsItsNotificationToTheSource() = runBlocking {
        val vm = SourceSettingsViewModel(
            repository,
            defaultsStore,
            SavedStateHandle(mapOf("sourceId" to "vi.fake-source")),
        )
        awaitUntil { vm.uiState.value.settings != null }

        val button = vm.uiState.value.settings!!.first { it.key == "clear_cache" }
        assertTrue(button.value is SourceSettingValue.Button)
        vm.runSetting(button)

        awaitUntil {
            val v = registry.defaultsGet("vi.fake-source", "last_notification") as? HostDefaultValue.String
            v?.v1 == "clear_cache"
        }
    }

    @Test
    fun writingDefaultsIsPickedUpByNextGetSettings() = runBlocking {
        // Start clean: force the first toggle off (written through the same
        // scoped host the source reads via defaults_get).
        registry.defaultsSet("vi.fake-source", "prefer_fhd", HostDefaultValue.Bool(false))
        var settings = repository.getSettings("vi.fake-source")
        assertEquals(false, (settings[0].value as SourceSettingValue.Toggle).default)

        // Flip it on — the source's get_dynamic_settings reads the Krx
        // defaults store via defaults_get, so the next read reflects it.
        registry.defaultsSet("vi.fake-source", "prefer_fhd", HostDefaultValue.Bool(true))
        settings = repository.getSettings("vi.fake-source")
        assertEquals(true, (settings[0].value as SourceSettingValue.Toggle).default)
    }

    @Test
    fun settingsViewModelTogglePolicyWritesAndReloads() = runBlocking {
        val vm = SourceSettingsViewModel(
            repository,
            defaultsStore,
            SavedStateHandle(mapOf("sourceId" to "vi.fake-source")),
        )
        awaitUntil { vm.uiState.value.settings != null }

        // Flip the first toggle: optimistic value true, then silent reload.
        vm.toggleSetting("prefer_fhd", false)
        awaitUntil { (vm.uiState.value.settings!![0].value as SourceSettingValue.Toggle).default == true }

        // Wait for the Room write to complete (it runs on a background
        // coroutine after the optimistic UI update), then verify the
        // persisted store agrees — under the source-namespaced key.
        awaitUntil {
            val v = registry.defaultsGet("vi.fake-source", "prefer_fhd") as? HostDefaultValue.Bool
            v?.v1 == true
        }
    }

    @Test
    fun languagePickerWriteIsReadableByTheSourceScopedHost() = runBlocking {
        val vm = SourceSettingsViewModel(
            repository,
            defaultsStore,
            SavedStateHandle(mapOf("sourceId" to "vi.fake-source")),
        )
        awaitUntil { vm.uiState.value.settings != null }

        // The app-injected language picker writes `{sourceId}.languages` — the
        // exact key the source reads via defaults_get::<Vec<String>>("languages").
        vm.setLanguages(listOf("en", "vi"))
        awaitUntil { vm.uiState.value.selectedLanguages == listOf("en", "vi") }
        awaitUntil {
            val v = registry.defaultsGet("vi.fake-source", "languages") as? HostDefaultValue.StringArray
            v?.v1 == listOf("en", "vi")
        }
    }

    @Test
    fun resetSettingsWipesOnlyTheSourceOwnRows() = runBlocking {
        val vm = SourceSettingsViewModel(
            repository,
            defaultsStore,
            SavedStateHandle(mapOf("sourceId" to "vi.fake-source")),
        )
        awaitUntil { vm.uiState.value.settings != null }

        // Seed values for THIS source (a toggle + the language key)...
        registry.defaultsSet("vi.fake-source", "prefer_fhd", HostDefaultValue.Bool(true))
        registry.defaultsSet("vi.fake-source", "languages", HostDefaultValue.StringArray(listOf("en", "vi")))
        // ...and the SAME setting key for another source (must survive the reset).
        registry.defaultsSet("en.example-source", "prefer_fhd", HostDefaultValue.Bool(true))
        awaitUntil {
            registry.defaultsGet("en.example-source", "prefer_fhd") as? HostDefaultValue.Bool == HostDefaultValue.Bool(true)
        }

        vm.resetSettings()

        // This source's rows are gone — the toggle falls back to its description
        // default (false) via get_settings, and the language key is cleared.
        awaitUntil { registry.defaultsGet("vi.fake-source", "prefer_fhd") == null }
        awaitUntil { registry.defaultsGet("vi.fake-source", "languages") == null }
        awaitUntil { vm.uiState.value.selectedLanguages.isEmpty() }
        val settings = vm.uiState.value.settings.orEmpty()
        assertEquals(false, (settings.firstOrNull { it.key == "prefer_fhd" }?.value as SourceSettingValue.Toggle).default)

        // Cross-source isolation: the other source's row is untouched.
        assertEquals(
            HostDefaultValue.Bool(true),
            registry.defaultsGet("en.example-source", "prefer_fhd"),
        )
    }

    @Test
    fun settingsViewModelLoadsFromNavArgs() = runBlocking {
        val vm = SourceSettingsViewModel(
            repository,
            defaultsStore,
            SavedStateHandle(mapOf("sourceId" to "vi.fake-source")),
        )

        awaitUntil { vm.uiState.value.settings != null }
        val state = vm.uiState.value
        assertEquals("vi.fake-source", state.source?.id)
        assertEquals(3, state.settings?.size)
        assertEquals(listOf("prefer_fhd", "show_intro", "clear_cache"), state.settings!!.map { it.key })
        assertTrue(!state.isLoading && !state.error)
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(15_000) {
            while (!condition()) delay(50)
        }
    }
}