package git.shin.komorei

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import git.shin.komorei.data.deeplink.DeepLinkManager
import git.shin.komorei.ui.screens.MainScreen
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.MyApplicationTheme
import git.shin.komorei.ui.tv.LocalTvMode
import git.shin.komorei.ui.tv.rememberIsTvMode
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var deepLinkManager: DeepLinkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars with light icons regardless of API level / theme
        // detection: the app is a dark cinema-style UI, so a light scrim would show
        // as white strips on both bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
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
                        color = BackgroundDark
                    ) {
                        MainScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Hands the incoming deep-link URI to [DeepLinkManager] (if any). Called
     * from both `onCreate` (cold start / singleTask relaunch) and `onNewIntent`
     * (warm start) — the manager coalesces, and the UI resolves it once up.
     */
    private fun handleIntent(intent: Intent?) {
        intent?.data?.toString()?.let(deepLinkManager::submit)
    }
}

