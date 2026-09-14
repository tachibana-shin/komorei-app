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
import kotlinx.coroutines.withContext
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
    }

    // ── metadata (known at startup, from assets) ───────────────────────────

    @Volatile private var bundledMetas: List<KrxSourceMeta>? = null
    private val metaMap = ConcurrentHashMap<String, Source>()
    private val krxFileNames = ConcurrentHashMap<String, String>() // sourceId → asset filename

    // ── loaded runners ──────────────────────────────────────────────────────

    private val runners = ConcurrentHashMap<String, KomoreiRunner>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<KomoreiRunner?>>()
    private val dispatchers = ConcurrentHashMap<String, CoroutineDispatcher>()

    // ── metadata discovery ──────────────────────────────────────────────────

    /**
     * Read `source.json` from every bundled `.krx` in `assets/sources/` and
     * populate [metaMap] / [krxFileNames]. Fast (a few ms for a handful of
     * small zips). Idempotent — subsequent calls return the cached list.
     *
     * Thread-safe but may briefly block on first call (synchronized).
     */
    fun bundledMetas(): List<KrxSourceMeta> {
        bundledMetas?.let { return it }
        synchronized(this) {
            bundledMetas?.let { return it }
            val names = context.assets.list(SOURCES_DIR) ?: emptyArray()
            val metas = names.filter { it.endsWith(".krx") }.mapNotNull { name ->
                try {
                    val bytes = context.assets.open("$SOURCES_DIR/$name").readBytes()
                    KrxManager.readInfo(bytes)?.also { meta ->
                        metaMap[meta.id] = Source(
                            id = meta.id,
                            name = meta.name,
                            version = meta.version.toString(),
                            baseUrl = meta.url,
                        )
                        krxFileNames[meta.id] = name
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read $SOURCES_DIR/$name", e)
                    null
                }
            }
            bundledMetas = metas
            Log.i(TAG, "Discovered ${metas.size} bundled source(s)")
            return metas
        }
    }

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
                ?: error("No bundled .krx for source $sourceId")
            val runner = withContextIO(sourceId) {
                val bytes = context.assets.open("$SOURCES_DIR/$krxName").readBytes()
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
                metaMap[meta.id] = Source(
                    id = meta.id,
                    name = meta.name,
                    version = meta.version.toString(),
                    baseUrl = meta.url,
                )
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
