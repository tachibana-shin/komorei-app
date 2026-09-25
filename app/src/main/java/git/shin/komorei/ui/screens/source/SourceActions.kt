package git.shin.komorei.ui.screens.source

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a source's homepage (`source.json` `url`) in the user's browser.
 * Fails silently when no handler exists (virtually every device ships one).
 */
fun openSourceWebsite(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
