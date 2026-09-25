package git.shin.komorei.data.update

/** Metadata for a signed APK published by the Komorei GitHub release workflow. */
data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val assetId: Long,
    val expectedSize: Long,
    val sha256: String,
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val info: UpdateInfo) : UpdateCheckResult
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateUiState
}
