package git.shin.komorei.sdk

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.model.Source
import git.shin.komorei.sdk.runner.KomoreiRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of bundled `.krx` sources. Each source gets its own [KomoreiRunner]
 * (wasm instance + native engine mutex) loaded lazily on first access, and its
 * own single-thread [CoroutineDispatcher] so that:
 *
 *  - **WASM calls never run on the main thread** — every public call dispatches
 *    onto the source's dedicated thread.
 *  - **Cross-source calls run fully in parallel** — separate threads, no shared
 *    lock.
 *  - **Same-source calls are serialized** — single-thread dispatcher + the
 *    runner's native engine mutex.
 *
 * A single shared [KrxHostImpl] is used by all runners (OkHttp/cookies/prefs
 * are thread-safe; Jsoup handle registries are ConcurrentHashMap; WebView
 * operations hop to the main looper via a per-call latch).
 */
@Singleton
class KrxSourceRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val host: KrxHostImpl,
) {
    companion object {
        private const val TAG = "KrxSourceRegistry"
        private const val SOURCES_DIR = "sources"

        /** Source keys follow the Aidoku convention: letters, digits, dot, dash. */
        private val KEY_PATTERN = Regex("^[A-Za-z0-9.\\-]+$")

        /** Ids that must not be installed/overridden by user packages. */
        private val RESERVED_IDS = setOf("all", "local", "komga", "kavita", "suwayomi")
    }

    // ── metadata (known at startup, from assets + user filesDir) ───────────

    @Volatile private var bundledMetas: List<KrxSourceMeta>? = null
    private val metaMap = ConcurrentHashMap<String, Source>()
    private val krxFileNames = ConcurrentHashMap<String, String>() // sourceId → file name (asset or filesDir)
    private val userInstalled = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** App-model sources (non-aggregator) — emits whenever the set changes. */
    private val _sourceAppFlow = MutableStateFlow<List<Source>>(emptyList())
    val sourceAppFlow: StateFlow<List<Source>> = _sourceAppFlow

    // ── loaded runners ──────────────────────────────────────────────────────

    private val runners = ConcurrentHashMap<String, KomoreiRunner>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<KomoreiRunner?>>()
    private val dispatchers = ConcurrentHashMap<String, CoroutineDispatcher>()

    // ── metadata discovery ──────────────────────────────────────────────────

    /**
     * Read `source.json` from every bundled `.krx` in `assets/sources/` AND every
     * user-installed `.krx` in `filesDir/sources/`, populating [metaMap] /
     * [krxFileNames]. Fast (a few ms for a handful of small zips). Idempotent —
     * subsequent calls return the cached list.
     *
     * Thread-safe but may briefly block on first call (synchronized).
     */
    fun bundledMetas(): List<KrxSourceMeta> {
        bundledMetas?.let { return it }
        synchronized(this) {
            bundledMetas?.let { return it }
            val metas = buildList {
                // Bundled sources (read-only assets).
                val names = context.assets.list(SOURCES_DIR) ?: emptyArray()
                for (name in names.filter { it.endsWith(".krx") }) {
                    try {
                        val bytes = context.assets.open("$SOURCES_DIR/$name").readBytes()
                        KrxManager.readInfo(bytes)?.let { meta ->
                            add(meta)
                            metaMap[meta.id] = meta.toAppSource()
                            krxFileNames[meta.id] = name
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to read $SOURCES_DIR/$name", e)
                    }
                }
                // User-installed sources (persisted in filesDir; survive restarts).
                val dir = installedDir()
                dir.listFiles { f -> f.isFile && f.name.endsWith(".krx") }.orEmpty()
                    .sortedBy { it.name }
                    .forEach { file ->
                        try {
                            KrxManager.readInfo(file.readBytes())?.let { meta ->
                                add(meta)
                                metaMap[meta.id] = meta.toAppSource()
                                krxFileNames[meta.id] = file.name
                                userInstalled += meta.id
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to read installed ${file.name}", e)
                        }
                    }
            }
            bundledMetas = metas
            emitSources()
            Log.i(TAG, "Discovered ${metas.size} source(s) (${userInstalled.size} user-installed)")
            return metas
        }
    }

    private fun KrxSourceMeta.toAppSource() = Source(
        id = id,
        name = name,
        version = version.toString(),
        baseUrl = url,
        isEnabled = true,
        isAggregator = false,
        languages = languages,
        contentRating = contentRating,
    )

    private fun emitSources() {
        _sourceAppFlow.value = sourceAppList()
    }

    private fun installedDir(): File =
        File(context.filesDir, SOURCES_DIR).apply { mkdirs() }

    /**
     * App-model [Source] list for all known bundled sources (non-aggregator).
     * Triggers [bundledMetas] on first call.
     */
    fun sourceAppList(): List<Source> {
        bundledMetas() // ensure populated
        return metaMap.values.toList()
    }

    fun sourceMeta(sourceId: String): KrxSourceMeta? =
        bundledMetas().find { it.id == sourceId }

    /** Source ids of currently loaded runners. */
    fun loadedRunnerIds(): Set<String> = runners.keys.toSet()

    /** Direct runner access (for blocking interceptors on media threads). */
    fun runnerOrNull(sourceId: String): KomoreiRunner? = runners[sourceId]

    // ── loading ─────────────────────────────────────────────────────────────

    /**
     * Load a source from bundled assets. Runs on the source's own dedicated
     * thread; never blocks the caller. Concurrent loads of the same source are
     * deduplicated via [CompletableDeferred].
     */
    suspend fun load(sourceId: String): KomoreiRunner? {
        runners[sourceId]?.let { return it }
        val deferred = CompletableDeferred<KomoreiRunner?>()
        val existing = inFlight.putIfAbsent(sourceId, deferred)
        if (existing != null) return existing.await()

        return try {
            bundledMetas() // ensure krxFileNames populated (cheap, cached)
            val krxName = krxFileNames[sourceId]
                ?: error("No .krx registered for source $sourceId")
            val runner = withContextIO(sourceId) {
                val bytes = if (sourceId in userInstalled) {
                    File(installedDir(), krxName).readBytes()
                } else {
                    context.assets.open("$SOURCES_DIR/$krxName").readBytes()
                }
                KrxManager.load(host, bytes)
            }
            runners[sourceId] = runner
            deferred.complete(runner)
            inFlight.remove(sourceId)
            Log.i(TAG, "Loaded source $sourceId")
            runner
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load source $sourceId", e)
            deferred.complete(null)
            inFlight.remove(sourceId)
            null
        }
    }

    /**
     * Load a source from raw bytes (for testing or manual installation).
     * Registers metadata automatically from the `source.json` inside the krx.
     */
    suspend fun loadKrx(sourceId: String, krxBytes: ByteArray): KomoreiRunner? {
        // Register metadata if unknown
        if (!metaMap.containsKey(sourceId)) {
            KrxManager.readInfo(krxBytes)?.let { meta ->
                metaMap[meta.id] = meta.toAppSource()
                // Keep a stable file name so re-installs can overwrite the same file.
                krxFileNames[meta.id] = "${meta.id}.krx"
            }
        }

        runners[sourceId]?.let { return it }
        val deferred = CompletableDeferred<KomoreiRunner?>()
        val existing = inFlight.putIfAbsent(sourceId, deferred)
        if (existing != null) return existing.await()

        return try {
            val runner = withContextIO(sourceId) {
                KrxManager.load(host, krxBytes)
            }
            runners[sourceId] = runner
            deferred.complete(runner)
            inFlight.remove(sourceId)
            runner
        } catch (e: Exception) {
            deferred.complete(null)
            inFlight.remove(sourceId)
            null
        }
    }

    // ── install / uninstall ─────────────────────────────────────────────────

    /** True when [sourceId] was installed by the user (lives in filesDir, not assets). */
    fun isUserInstalled(sourceId: String): Boolean = sourceId in userInstalled

    /** True when [sourceId] is a bundled asset source (read-only; cannot be uninstalled). */
    fun isBundled(sourceId: String): Boolean = metaMap.containsKey(sourceId) && sourceId !in userInstalled

    /**
     * Installs a `.krx` package: validates the manifest, persists the bytes to
     * `filesDir/sources/<id>.krx`, registers metadata and returns the loaded
     * source. Returns null when the package is invalid, the id is reserved,
     * or the source already exists (bundled or installed).
     */
    suspend fun installKrx(krxBytes: ByteArray): KrxSourceMeta? {
        val meta = KrxManager.readInfo(krxBytes) ?: return null
        if (!KEY_PATTERN.matches(meta.id)) return null
        if (meta.id in RESERVED_IDS) return null
        if (metaMap.containsKey(meta.id)) return null

        bundledMetas() // ensure startup scan is done before mutating maps
        val file = File(installedDir(), "${meta.id}.krx")
        file.writeBytes(krxBytes)

        metaMap[meta.id] = meta.toAppSource()
        krxFileNames[meta.id] = file.name
        userInstalled += meta.id
        emitSources()

        val runner = load(meta.id)
        if (runner == null) {
            // Roll back — the wasm could not be loaded.
            metaMap.remove(meta.id)
            krxFileNames.remove(meta.id)
            userInstalled.remove(meta.id)
            file.delete()
            emitSources()
            return null
        }
        Log.i(TAG, "Installed source ${meta.id}")
        return meta
    }

    /**
     * Uninstalls a user-installed source: removes the `.krx` file, drops the
     * metadata/runner/dispatcher and emits the updated source list. Bundled
     * sources cannot be uninstalled. Returns false when nothing was removed.
     */
    suspend fun uninstall(sourceId: String): Boolean {
        if (sourceId !in userInstalled) return false
        val fileName = krxFileNames.remove(sourceId) ?: return false
        File(installedDir(), fileName).delete()

        metaMap.remove(sourceId)
        userInstalled.remove(sourceId)
        runners.remove(sourceId)?.close()
        inFlight.remove(sourceId)
        disposeDispatcher(sourceId)
        emitSources()
        Log.i(TAG, "Uninstalled source $sourceId")
        return true
    }

    private fun disposeDispatcher(sourceId: String) {
        dispatchers.remove(sourceId)?.let { dispatcher ->
            // asCoroutineDispatcher wraps an ExecutorService; close() shuts it down.
            runCatching { (dispatcher as? java.io.Closeable)?.close() }
        }
    }

    // ── call API ────────────────────────────────────────────────────────────

    /**
     * Execute a block on a source's dedicated thread. Cross-source calls are
     * fully parallel; same-source calls are serialized. **Never blocks the
     * calling thread** (main/UI).
     *
     * Returns null when the source cannot be loaded.
     */
    suspend fun <T> call(sourceId: String, block: suspend (KomoreiRunner) -> T): T? {
        val runner = load(sourceId) ?: return null
        return withContextIO(sourceId) { block(runner) }
    }

    /**
     * Execute a per-source block across all (or specified) sources in parallel.
     * Returns `sourceId → result`, filtering out sources that failed to load.
     */
    suspend fun <T> callAll(
        sourceIds: List<String>? = null,
        block: suspend (String, KomoreiRunner) -> T,
    ): Map<String, T> {
        val ids = sourceIds ?: bundledMetas().map { it.id }
        return coroutineScope {
            ids.map { id ->
                async {
                    val result = call(id) { runner -> block(id, runner) }
                    if (result != null) id to result else null
                }
            }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }
    }

    // ── internals ───────────────────────────────────────────────────────────

    private fun sourceDispatcher(sourceId: String): CoroutineDispatcher {
        return dispatchers.getOrPut(sourceId) {
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "komorei-wasm-$sourceId").apply { isDaemon = true }
            }.asCoroutineDispatcher()
        }
    }

    /**
     * Dispatch [block] onto [sourceId]'s dedicated IO thread.
     * This is a suspend wrapper; it does NOT block the caller's thread.
     */
    private suspend fun <T> withContextIO(sourceId: String, block: suspend () -> T): T {
        return withContext(sourceDispatcher(sourceId)) { block() }
    }
}
