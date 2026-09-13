package git.shin.komorei.sdk

import git.shin.komorei.sdk.runner.KomoreiRunner
import java.util.zip.ZipInputStream

/**
 * Loads `.krx` source packages for the embedded runner.
 *
 * A `.krx` is a zip archive (aidoku convention) whose wasm payload lives at
 * `Payload/main.wasm`. [load] extracts it, creates the runner bound to a real
 * [KrxHostImpl] and runs the source `start()` export — exactly like an app would
 * install a source. The returned [KomoreiRunner] (an `AutoCloseable`) is the
 * handle for all source calls (search / details / streams / filters / ...).
 */
object KrxManager {

    /** The wasm payload path inside a `.krx` archive. */
    const val MAIN_WASM_ENTRY = "Payload/main.wasm"

    /**
     * Extracts `Payload/main.wasm` from a `.krx` archive. Returns null when the
     * entry is missing or the archive is invalid.
     */
    fun extractMainWasm(krx: ByteArray): ByteArray? {
        ZipInputStream(krx.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == MAIN_WASM_ENTRY) {
                    return zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    /**
     * Instantiates a runner for the given `.krx` bytes and runs its `start()`.
     * Throws [IllegalArgumentException] when the package has no wasm payload.
     *
     * Callers should run this off the main thread (the runner is synchronous and
     * its host performs blocking IO on every call).
     */
    fun load(host: KrxHostImpl, krx: ByteArray): KomoreiRunner {
        val wasm = extractMainWasm(krx)
            ?: throw IllegalArgumentException("Invalid .krx package: missing $MAIN_WASM_ENTRY")
        val runner = KomoreiRunner(host)
        try {
            runner.load(wasm)
            runner.start()
        } catch (e: Exception) {
            runner.close()
            throw e
        }
        return runner
    }
}