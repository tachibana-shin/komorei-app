package git.shin.komorei.data.backup

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterKindJsonAdapter
import git.shin.komorei.model.FilterValue
import git.shin.komorei.model.FilterValueJsonAdapter
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class BackupFormatException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** Versioned JSON codec for Komorei backup payloads. */
@Singleton
class BackupCodec @Inject constructor() {
    private val moshi: Moshi =
        Moshi
            .Builder()
            // Anime contains CategoryLink -> FilterValue, which is polymorphic and
            // therefore cannot use Moshi's reflective adapter without these two.
            .add(FilterKind::class.java, FilterKindJsonAdapter())
            .add(FilterValue::class.java, FilterValueJsonAdapter())
            .build()

    private val adapter: JsonAdapter<BackupPayload> =
        moshi.adapter(BackupPayload::class.java)

    fun encode(payload: BackupPayload): String = adapter.indent("  ").toJson(payload)

    fun decode(json: String): BackupPayload {
        val payload =
            try {
                adapter.fromJson(json)
            } catch (error: JsonDataException) {
                throw BackupFormatException("Malformed Komorei backup", error)
            } catch (error: IOException) {
                throw BackupFormatException("Could not read Komorei backup", error)
            } ?: throw BackupFormatException("Empty Komorei backup")

        validate(payload)
        return payload
    }

    fun decode(bytes: ByteArray): BackupPayload = decode(bytes.toString(Charsets.UTF_8))

    private fun validate(payload: BackupPayload) {
        if (payload.format != BackupFormat.NAME) {
            throw BackupFormatException("Unsupported backup format: ${payload.format}")
        }
        if (payload.schemaVersion > BackupFormat.SCHEMA_VERSION) {
            throw BackupFormatException(
                "Backup schema ${payload.schemaVersion} is newer than supported " +
                    "schema ${BackupFormat.SCHEMA_VERSION}",
            )
        }
        if (payload.schemaVersion < 1) {
            throw BackupFormatException("Invalid backup schema: ${payload.schemaVersion}")
        }
        if (payload.createdAt <= 0L) {
            throw BackupFormatException("Backup has no valid creation time")
        }
    }
}
