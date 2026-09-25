package git.shin.komorei.data.backup

import com.squareup.moshi.JsonClass
import git.shin.komorei.model.Anime

/** The versioned on-disk envelope used by Komorei backups. */
object BackupFormat {
    const val NAME = "komorei.backup"
    const val SCHEMA_VERSION = 1
    const val FILE_NAME = "komorei-backup.kbackup"
}

@JsonClass(generateAdapter = true)
data class BackupPayload(
    val format: String = BackupFormat.NAME,
    val schemaVersion: Int = BackupFormat.SCHEMA_VERSION,
    val createdAt: Long,
    val appVersion: String,
    val appBuild: Int,
    val name: String? = null,
    /** null means this section was not selected; an empty list means clear it. */
    val anime: List<BackupAnime>? = null,
    val watchHistory: List<BackupWatchHistory>? = null,
    val sourceDefaults: List<BackupSourceDefault>? = null,
    val sourceState: BackupSourceState? = null,
    val searchHistory: List<String>? = null,
    val userSources: List<BackupUserSource>? = null,
)

@JsonClass(generateAdapter = true)
data class BackupAnime(
    val anime: Anime,
    val isBookmarked: Boolean,
    val bookmarkAddedAt: Long?,
)

@JsonClass(generateAdapter = true)
data class BackupWatchHistory(
    val animeId: String,
    val sourceId: String,
    val episodeId: String,
    val episodeNumber: String,
    val episodeTitle: String,
    val lastWatchedAt: Long,
    val progressMs: Long,
    val durationMs: Long,
)

@JsonClass(generateAdapter = true)
data class BackupSourceDefault(
    val key: String,
    val type: String,
    val value: String,
)

@JsonClass(generateAdapter = true)
data class BackupSourceState(
    val disabledSources: List<String> = emptyList(),
    val pinnedSources: List<String> = emptyList(),
    val repositoryUrls: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BackupUserSource(
    val id: String,
    /** Base64 without line breaks; the package is the executable source of truth. */
    val data: String,
)

/** Which optional logical sections a new backup should contain. */
data class BackupOptions(
    val includeLibrary: Boolean = true,
    val includeHistory: Boolean = true,
    val includeSourceState: Boolean = true,
    val includeSourceDefaults: Boolean = false,
    val includeSearchHistory: Boolean = false,
    val includeUserSources: Boolean = false,
)

data class BackupCounts(
    val library: Int = 0,
    val history: Int = 0,
    val sourceDefaults: Int = 0,
    val sources: Int = 0,
)

/** Lightweight metadata for the backup list; full JSON is decoded on restore. */
data class BackupFileInfo(
    val fileName: String,
    val createdAt: Long,
    val name: String?,
    val sizeBytes: Long,
    val counts: BackupCounts,
    val valid: Boolean,
)

data class BackupRestoreResult(
    val restored: BackupCounts,
    val skippedSources: List<String> = emptyList(),
)

data class InstalledSourcePackage(
    val id: String,
    val bytes: ByteArray,
)
