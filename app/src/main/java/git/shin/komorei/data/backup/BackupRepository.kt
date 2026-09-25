package git.shin.komorei.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.BuildConfig
import git.shin.komorei.data.SearchHistoryStore
import git.shin.komorei.data.SourceStateSnapshot
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.data.local.dao.AnimeDao
import git.shin.komorei.data.local.dao.KrxDefaultsDao
import git.shin.komorei.data.local.entity.AnimeEntity
import git.shin.komorei.data.local.entity.KrxDefaultsEntity
import git.shin.komorei.data.local.entity.WatchHistoryEntity
import git.shin.komorei.sdk.KrxSourceRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Versioned logical snapshots and the local backup file store.
 *
 * This is deliberately separate from Aidoku's binary `.aib` format: the data
 * model is Komorei-specific and the JSON envelope is easy to migrate. A `null`
 * section means it was not selected; restoring it leaves that local section
 * untouched.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: KomoreiDatabase,
    private val animeDao: AnimeDao,
    private val defaultsDao: KrxDefaultsDao,
    private val sourceStateStore: SourceStateStore,
    private val searchHistoryStore: SearchHistoryStore,
    private val sourceRegistry: KrxSourceRegistry,
    private val codec: BackupCodec,
) {
    private val directory: File
        get() = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    suspend fun createPayload(options: BackupOptions = BackupOptions()): BackupPayload {
        val now = System.currentTimeMillis()
        return BackupPayload(
            createdAt = now,
            appVersion = BuildConfig.VERSION_NAME,
            appBuild = BuildConfig.VERSION_CODE,
            anime =
                if (options.includeLibrary) {
                    animeDao
                        .getAllAnimeEntities()
                        .sortedWith(compareBy<AnimeEntity> { it.anime.sourceId }.thenBy { it.anime.id })
                        .map { BackupAnime(it.anime, it.isBookmarked, it.bookmarkAddedAt) }
                } else {
                    null
                },
            watchHistory =
                if (options.includeHistory) {
                    animeDao
                        .getAllWatchHistory()
                        .sortedWith(
                            compareBy<WatchHistoryEntity> { it.sourceId }
                                .thenBy { it.animeId }
                                .thenBy { it.episodeId },
                        ).map { it.toBackup() }
                } else {
                    null
                },
            sourceDefaults =
                if (options.includeSourceDefaults) {
                    defaultsDao.getAll().map { BackupSourceDefault(it.key, it.type, it.value) }
                } else {
                    null
                },
            sourceState =
                if (options.includeSourceState) {
                    sourceStateStore.snapshot().toBackup()
                } else {
                    null
                },
            searchHistory =
                if (options.includeSearchHistory) {
                    searchHistoryStore.snapshot()
                } else {
                    null
                },
            userSources =
                if (options.includeUserSources) {
                    sourceRegistry.exportUserSources().map {
                        BackupUserSource(
                            id = it.id,
                            data = Base64.encodeToString(it.bytes, Base64.NO_WRAP),
                        )
                    }
                } else {
                    null
                },
        )
    }

    suspend fun createLocalBackup(
        name: String? = null,
        options: BackupOptions = BackupOptions(),
    ): BackupFileInfo {
        val payload = createPayload(options).copy(name = name?.trim()?.takeIf { it.isNotEmpty() })
        return writePayload(payload)
    }

    suspend fun createPayloadBytes(
        name: String? = null,
        options: BackupOptions = BackupOptions(),
    ): ByteArray =
        codec
            .encode(
                createPayload(options).copy(name = name?.trim()?.takeIf { it.isNotEmpty() }),
            ).toByteArray(Charsets.UTF_8)

    suspend fun importUri(uri: Uri): BackupFileInfo {
        val bytes =
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw BackupFormatException("Could not open the selected backup")
        val payload = codec.decode(bytes)
        return writePayload(payload)
    }

    suspend fun importBytes(
        bytes: ByteArray,
        fallbackName: String? = null,
    ): BackupFileInfo {
        val payload = codec.decode(bytes)
        return writePayload(payload.copy(name = payload.name ?: fallbackName))
    }

    suspend fun exportToUri(
        fileName: String,
        uri: Uri,
    ) {
        val file = safeFile(fileName)
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(file.readBytes())
        } ?: throw BackupFormatException("Could not open the export destination")
    }

    suspend fun readBytes(fileName: String): ByteArray = safeFile(fileName).readBytes()

    suspend fun listLocalBackups(): List<BackupFileInfo> =
        directory
            .listFiles { file -> file.isFile && file.name.endsWith(FILE_EXTENSION) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map(::inspect)

    fun inspect(file: File): BackupFileInfo {
        val fallback =
            BackupFileInfo(
                fileName = file.name,
                createdAt = file.lastModified(),
                name = null,
                sizeBytes = file.length(),
                counts = BackupCounts(),
                valid = false,
            )
        return runCatching {
            val payload = codec.decode(file.readBytes())
            BackupFileInfo(
                fileName = file.name,
                createdAt = payload.createdAt,
                name = payload.name,
                sizeBytes = file.length(),
                counts = payload.counts(),
                valid = true,
            )
        }.getOrDefault(fallback)
    }

    suspend fun delete(fileName: String): Boolean = safeFile(fileName).delete()

    /**
     * Restores the selected sections after making a local safety snapshot.
     * Source packages are installed only after the Room transaction succeeds.
     */
    suspend fun restore(fileName: String): BackupRestoreResult {
        val payload = codec.decode(readBytes(fileName))
        // Decode/validate optional binary sections before the first DB mutation;
        // a truncated Base64 package must not leave a half-restored library.
        val sourcePackages =
            payload.userSources?.map { source ->
                source.toPackage() ?: throw BackupFormatException("Invalid source package data")
            }
        val safetyName = "pre-restore-${timestamp()}"
        createLocalBackup(
            safetyName,
            BackupOptions(
                includeLibrary = true,
                includeHistory = true,
                includeSourceState = true,
                includeSourceDefaults = true,
                includeSearchHistory = true,
                includeUserSources = true,
            ),
        )

        database.withTransaction {
            payload.anime?.let { rows ->
                animeDao.deleteAllAnimeEntities()
                rows
                    .sortedWith(
                        compareBy<BackupAnime> { it.anime.sourceId }
                            .thenBy { it.anime.id },
                    ).chunked(ANIME_CHUNK_SIZE)
                    .forEach { chunk ->
                        animeDao.upsertAnimes(
                            chunk.map {
                                AnimeEntity(it.anime, it.isBookmarked, it.bookmarkAddedAt)
                            },
                        )
                    }
            }
            payload.watchHistory?.let { rows ->
                animeDao.deleteAllWatchHistory()
                rows
                    .sortedWith(
                        compareBy<BackupWatchHistory> { it.sourceId }
                            .thenBy { it.animeId }
                            .thenBy { it.episodeId },
                    ).chunked(HISTORY_CHUNK_SIZE)
                    .forEach { chunk -> animeDao.upsertWatchHistoryBatch(chunk.map { it.toEntity() }) }
            }
            payload.sourceDefaults?.let { rows ->
                defaultsDao.deleteAll()
                rows
                    .sortedBy { it.key }
                    .chunked(DEFAULTS_CHUNK_SIZE)
                    .forEach { chunk ->
                        defaultsDao.upsertAll(
                            chunk.map {
                                KrxDefaultsEntity(it.key, it.type, it.value)
                            },
                        )
                    }
            }
        }

        payload.sourceState?.let { sourceStateStore.restore(it.toSnapshot()) }
        payload.searchHistory?.let(searchHistoryStore::restore)

        val skippedSources =
            sourcePackages
                ?.let { packages ->
                    sourceRegistry.importUserSources(packages)
                }.orEmpty()

        return BackupRestoreResult(
            restored = payload.counts(),
            skippedSources = skippedSources,
        )
    }

    private suspend fun writePayload(payload: BackupPayload): BackupFileInfo {
        val encoded = codec.encode(payload).toByteArray(Charsets.UTF_8)
        val baseName =
            payload.name
                ?.replace(Regex("[^A-Za-z0-9._-]+"), "_")
                ?.trim('_', '.', '-')
                ?.take(40)
                ?.takeIf { it.isNotEmpty() }
        val prefix = if (baseName == null) "komorei" else "komorei_$baseName"
        var file = File(directory, "${prefix}_${timestamp()}.$FILE_EXTENSION")
        var suffix = 1
        while (file.exists()) {
            file = File(directory, "${prefix}_${timestamp()}_$suffix.$FILE_EXTENSION")
            suffix++
        }
        val temporary = File(directory, ".${file.name}.tmp")
        temporary.writeBytes(encoded)
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            if (!temporary.delete()) {
                Log.w(TAG, "Could not remove the temp backup at $temporary")
            }
        }
        return inspect(file)
    }

    private fun safeFile(fileName: String): File {
        val candidate = File(directory, fileName)
        require(candidate.canonicalFile.parentFile == directory.canonicalFile) {
            "Invalid backup file name"
        }
        require(candidate.isFile) { "Backup file not found" }
        return candidate
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun WatchHistoryEntity.toBackup() =
        BackupWatchHistory(
            animeId = animeId,
            sourceId = sourceId,
            episodeId = episodeId,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            lastWatchedAt = lastWatchedAt,
            progressMs = progressMs,
            durationMs = durationMs,
        )

    private fun BackupWatchHistory.toEntity() =
        WatchHistoryEntity(
            animeId = animeId,
            sourceId = sourceId,
            episodeId = episodeId,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            lastWatchedAt = lastWatchedAt,
            progressMs = progressMs,
            durationMs = durationMs,
        )

    private fun SourceStateSnapshot.toBackup() =
        BackupSourceState(
            disabledSources = disabledSources,
            pinnedSources = pinnedSources,
            repositoryUrls = repositoryUrls,
        )

    private fun BackupSourceState.toSnapshot() =
        SourceStateSnapshot(
            disabledSources = disabledSources,
            pinnedSources = pinnedSources,
            repositoryUrls = repositoryUrls,
        )

    private fun BackupUserSource.toPackage(): InstalledSourcePackage? =
        runCatching {
            InstalledSourcePackage(id, Base64.decode(data, Base64.DEFAULT))
        }.getOrNull()

    private fun BackupPayload.counts() =
        BackupCounts(
            library = anime?.size ?: 0,
            history = watchHistory?.size ?: 0,
            sourceDefaults = sourceDefaults?.size ?: 0,
            sources = userSources?.size ?: 0,
        )

    private companion object {
        const val TAG = "BackupRepository"
        const val DIRECTORY_NAME = "backups"
        const val FILE_EXTENSION = "kbackup"

        // Anime has many embedded columns; keep each INSERT well below
        // SQLite's 999 bind-parameter limit.
        const val ANIME_CHUNK_SIZE = 20
        const val HISTORY_CHUNK_SIZE = 100
        const val DEFAULTS_CHUNK_SIZE = 100
    }
}
