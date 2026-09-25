package git.shin.komorei.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One persisted Krx "user default" — the value behind a source's
 * `defaults_get`/`defaults_set` imports.
 *
 * Values are stored with a [type] discriminator and a single [value] string so
 * the storage shape is independent of the HostDefaultValue variant:
 *
 *  - `"bool"`          → `"true"` / `"false"`
 *  - `"int"`           → decimal string
 *  - `"float"`         → decimal string
 *  - `"string"`        → the raw string
 *  - `"string_array"`  → JSON array of strings
 *  - `"data"`          → Base64
 *
 * A `Null` write deletes the row (an absent row == no stored value), matching
 * the old SharedPreferences semantics where the key simply wasn't present.
 *
 * Keys are the raw setting keys the source itself names — mirrors Aidoku's
 * `UserDefaults`, which also stores un-namespaced keys.
 */
@Entity(tableName = "krx_defaults")
data class KrxDefaultsEntity(
    @PrimaryKey
    val key: String,
    val type: String,
    val value: String,
)
