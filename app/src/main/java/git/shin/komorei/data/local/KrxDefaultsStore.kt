package git.shin.komorei.data.local

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import git.shin.komorei.data.local.dao.KrxDefaultsDao
import git.shin.komorei.data.local.entity.KrxDefaultsEntity
import git.shin.komorei.sdk.runner.HostDefaultValue
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * The single owner of Krx defaults persistence — the values behind a source's
 * `defaults_get`/`defaults_set` imports (and, on the app side, the per-source
 * settings screen writes).
 *
 * Both sides must hit the SAME store:
 *  - the wasm source reads/writes through [git.shin.komorei.sdk.KrxHostImpl],
 *  - the app writes through `SourceSettingsViewModel` (Hilt injects this same
 *    singleton).
 *
 * Keys are stored namespaced by source id — `{sourceId}.{key}` — mirroring
 * Aidoku's `UserDefaults["{sourceId}.{key}"]` convention (the runner prefixes
 * every defaults key with the calling source's id, so `defaults_get("x")` in
 * source A and source B read two different rows). Cross-source key collisions
 * are therefore impossible, and per-source choices like a `"language"`
 * default work: the app writes `{sourceId}.language` while a source reads it
 * back via `defaults_get("language")`. The same row is read when the write
 * came from the wasm side (`defaults_set`) or the app side (settings screen).
 * A [HostDefaultValue.Null] write removes the key.
 */
interface KrxDefaultsStore {
    suspend fun get(key: String): HostDefaultValue?

    suspend fun set(
        key: String,
        value: HostDefaultValue,
    )

    /**
     * Deletes every setting stored for source [sourceId] — every row whose key
     * starts with `{sourceId}.` (source-id namespacing keeps this scoped to the
     * one source: "Reset Settings", Aidoku's `removeSettings(from:)`). Other
     * sources are untouched.
     */
    suspend fun deleteAll(sourceId: String)
}

/**
 * The store key for a setting [key] of source [sourceId] — `{sourceId}.{key}`,
 * matching Aidoku's namespaced UserDefaults keys. Used by the host (wasm
 * `defaults_get`/`defaults_set`) and by app-side writers ([SourceSettingsViewModel])
 * so both sides always hit the same row.
 */
fun krxDefaultsKey(
    sourceId: String,
    key: String,
): String = "$sourceId.$key"

/** [KrxDefaultsStore] backed by Room — the production singleton. */
class RoomKrxDefaultsStore(
    private val dao: KrxDefaultsDao,
) : KrxDefaultsStore {
    override suspend fun get(key: String): HostDefaultValue? {
        val entity = dao.get(key) ?: return null
        return decode(entity)
    }

    override suspend fun set(
        key: String,
        value: HostDefaultValue,
    ) {
        if (value is HostDefaultValue.Null) {
            dao.delete(key)
            return
        }
        dao.upsert(encode(key, value))
    }

    override suspend fun deleteAll(sourceId: String) = dao.deleteByPrefix("$sourceId.")

    private fun encode(
        key: String,
        value: HostDefaultValue,
    ): KrxDefaultsEntity =
        when (value) {
            is HostDefaultValue.Bool -> KrxDefaultsEntity(key, "bool", value.v1.toString())
            is HostDefaultValue.Int -> KrxDefaultsEntity(key, "int", value.v1.toString())
            is HostDefaultValue.Float -> KrxDefaultsEntity(key, "float", value.v1.toString())
            is HostDefaultValue.String -> KrxDefaultsEntity(key, "string", value.v1)
            is HostDefaultValue.StringArray ->
                KrxDefaultsEntity(key, "string_array", JSONArray(value.v1).toString())
            is HostDefaultValue.Data ->
                KrxDefaultsEntity(key, "data", Base64.encodeToString(value.v1, Base64.NO_WRAP))
            HostDefaultValue.Null -> error("Null must be handled by the caller (delete)")
        }

    private fun decode(entity: KrxDefaultsEntity): HostDefaultValue? =
        when (entity.type) {
            "bool" -> HostDefaultValue.Bool(entity.value.toBoolean())
            "int" -> HostDefaultValue.Int(entity.value.toInt())
            "float" -> HostDefaultValue.Float(entity.value.toFloat())
            "string" -> HostDefaultValue.String(entity.value)
            "string_array" ->
                HostDefaultValue.StringArray(
                    JSONArray(entity.value).let { arr ->
                        List(arr.length()) { i -> arr.getString(i) }
                    },
                )
            "data" -> HostDefaultValue.Data(Base64.decode(entity.value, Base64.NO_WRAP))
            else -> null
        }
}

/**
 * Default fallback [KrxDefaultsStore] for hosts constructed WITHOUT dependency
 * injection (every unit/instrumented test calls `KrxHostImpl(context)`).
 * Memoized per application context so two `KrxHostImpl`s built from the same
 * context in one test share one store — required for the write→read roundtrip
 * tests. Production always passes the Hilt singleton (in `RepositoryModule`),
 * so this path is test-only.
 */
internal fun platformKrxDefaultsStore(context: Context): KrxDefaultsStore {
    val app = context.applicationContext
    return fallbackDefaultsStores.getOrPut(app) {
        RoomKrxDefaultsStore(
            Room
                .databaseBuilder(app, KrxDefaultsDatabase::class.java, "komorei_krx_defaults")
                .build()
                .krxDefaultsDao(),
        )
    }
}

private val fallbackDefaultsStores = ConcurrentHashMap<Context, KrxDefaultsStore>()

/**
 * Self-contained Room DB for the defaults store only. Production keeps the
 * `krx_defaults` table inside the main [KomoreiDatabase]; this standalone
 * database exists purely so non-injected hosts (tests) get a real store
 * without dragging in the anime/watch-history schema.
 */
@Database(entities = [KrxDefaultsEntity::class], version = 1, exportSchema = false)
abstract class KrxDefaultsDatabase : RoomDatabase() {
    abstract fun krxDefaultsDao(): KrxDefaultsDao
}
