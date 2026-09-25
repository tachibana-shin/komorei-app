package git.shin.komorei.data.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import git.shin.komorei.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Foreground Drive authorization using Google's current AuthorizationClient.
 * Access tokens are intentionally short-lived and are passed directly to the
 * Drive REST client; Play services owns their local cache.
 */
object GoogleDriveAuthorization {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    fun isConfigured(): Boolean =
        BuildConfig.DRIVE_ANDROID_CLIENT_ID.isNotBlank() &&
            BuildConfig.DRIVE_ANDROID_CLIENT_ID != "UNCONFIGURED" &&
            BuildConfig.DRIVE_WEB_CLIENT_ID.isNotBlank() &&
            BuildConfig.DRIVE_WEB_CLIENT_ID != "UNCONFIGURED"

    fun authorize(
        activity: Activity,
        resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>,
        onResult: (Result<String>) -> Unit,
    ) {
        if (!isConfigured()) {
            onResult(Result.failure(IllegalStateException("Google Drive OAuth is not configured")))
            return
        }

        val builder = request()

        runCatching {
            Identity
                .getAuthorizationClient(activity)
                .authorize(builder)
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        val pendingIntent = result.pendingIntent
                        if (pendingIntent == null) {
                            onResult(Result.failure(IllegalStateException("Google authorization returned no resolution")))
                        } else {
                            resolutionLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                            )
                        }
                    } else {
                        onResult(accessToken(result))
                    }
                }.addOnFailureListener { error -> onResult(Result.failure(error)) }
        }.onFailure { error -> onResult(Result.failure(error)) }
    }

    /**
     * Background workers cannot present a consent Activity. They can still use
     * the grant/access token cached by Play services; if user interaction is
     * required, the worker reports AUTH_REQUIRED and waits for the next run.
     */
    suspend fun authorizeForBackground(context: Context): Result<String> {
        if (!isConfigured()) {
            return Result.failure(BackgroundAuthorizationRequiredException())
        }
        return try {
            val result =
                Identity
                    .getAuthorizationClient(context)
                    .authorize(request())
                    .await()
            if (result.hasResolution()) {
                throw BackgroundAuthorizationRequiredException()
            }
            accessToken(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun handleResolutionResult(
        activity: Activity,
        data: Intent?,
        onResult: (Result<String>) -> Unit,
    ) {
        if (data == null) {
            onResult(Result.failure(IllegalStateException("Google authorization returned no result")))
            return
        }
        runCatching {
            val result =
                Identity
                    .getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(data)
            onResult(accessToken(result))
        }.onFailure { error -> onResult(Result.failure(error)) }
    }

    private fun request() =
        AuthorizationRequest
            .builder()
            .setRequestedScopes(listOf(Scope(SCOPE)))
            .requestOfflineAccess(BuildConfig.DRIVE_WEB_CLIENT_ID)
            .build()

    private suspend fun <T> Task<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            addOnCanceledListener {
                if (continuation.isActive) continuation.cancel()
            }
        }

    private fun accessToken(result: AuthorizationResult): Result<String> {
        val token = result.accessToken
        return if (token.isNullOrBlank()) {
            Result.failure(IllegalStateException("No Drive access token returned"))
        } else {
            Result.success(token)
        }
    }
}

class BackgroundAuthorizationRequiredException :
    IllegalStateException(
        "Google Drive authorization requires the foreground",
    )
