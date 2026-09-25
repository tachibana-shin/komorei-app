package git.shin.komorei

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import git.shin.komorei.R
import git.shin.komorei.data.deeplink.DeepLinkManager
import git.shin.komorei.data.deeplink.ExternalDeepLinkHandler
import git.shin.komorei.data.deeplink.ExternalDeepLinkParser
import git.shin.komorei.data.deeplink.ExternalDeepLinkRequest
import git.shin.komorei.data.deeplink.ExternalDeepLinkResult
import git.shin.komorei.ui.screens.MainScreen
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.MyApplicationTheme
import git.shin.komorei.ui.tv.LocalTvMode
import git.shin.komorei.ui.tv.rememberIsTvMode
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var deepLinkManager: DeepLinkManager

    @Inject
    lateinit var externalDeepLinkHandler: ExternalDeepLinkHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars with light icons regardless of API level / theme
        // detection: the app is a dark cinema-style UI, so a light scrim would show
        // as white strips on both bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent {
            // Detect Android TV once; every TV focus highlight downstream reads
            // LocalTvMode and stays a no-op on phones/tablets.
            val isTv = rememberIsTvMode()
            CompositionLocalProvider(LocalTvMode provides isTv) {
                MyApplicationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = BackgroundDark,
                    ) {
                        MainScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Classifies an incoming URI before handing content links to the existing
     * source resolver. Repository/package links are app-owned and may perform
     * network IO, so they are handled separately from the one-slot content
     * deep-link channel.
     */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!uri.scheme.equals("komorei", ignoreCase = true)) return
        when (val request = ExternalDeepLinkParser.parse(uri)) {
            is ExternalDeepLinkRequest.AddRepository -> handleExternalDeepLink(request)
            is ExternalDeepLinkRequest.InstallSource -> handleExternalDeepLink(request)

            ExternalDeepLinkRequest.Invalid -> {
                Toast.makeText(this, R.string.deeplink_invalid, Toast.LENGTH_LONG).show()
            }

            null -> deepLinkManager.submit(uri.toString())
        }
    }

    private fun handleExternalDeepLink(request: ExternalDeepLinkRequest) {
        lifecycleScope.launch {
            val message =
                when (val result = externalDeepLinkHandler.handle(request)) {
                    is ExternalDeepLinkResult.RepositoryAdded -> getString(R.string.deeplink_repo_added)
                    ExternalDeepLinkResult.RepositoryAlreadyAdded -> getString(R.string.deeplink_repo_exists)
                    ExternalDeepLinkResult.RepositoryUnavailable -> getString(R.string.deeplink_repo_failed)
                    is ExternalDeepLinkResult.SourceInstalled ->
                        getString(
                            R.string.deeplink_source_installed,
                            result.name,
                        )
                    is ExternalDeepLinkResult.SourceAlreadyInstalled ->
                        getString(
                            R.string.deeplink_source_exists,
                            result.name,
                        )
                    ExternalDeepLinkResult.SourceInstallFailed -> getString(R.string.deeplink_source_failed)
                    ExternalDeepLinkResult.Invalid -> getString(R.string.deeplink_invalid)
                }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }
}
