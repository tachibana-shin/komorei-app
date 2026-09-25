package git.shin.komorei.ui.navigation

import android.net.Uri
import com.squareup.moshi.Moshi
import git.shin.komorei.model.Listing

/**
 * URL-safe JSON codec for the [Listing] navigation argument.
 *
 * A route can only carry strings, so the whole listing (id + display name +
 * kind) is serialized — the Listing screen then opens that exact catalog
 * without needing the source's `listings()` to resolve ids again. Handy for
 * deep links too: any source listing becomes a complete route.
 */
object ListingArgCodec {
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(Listing::class.java)

    /** [Listing] → a URL-encodable JSON string (safe inside a route segment). */
    fun encode(listing: Listing): String = Uri.encode(adapter.toJson(listing))

    /** Decodes the [Listing] back, or null when the argument is malformed. */
    fun decode(encoded: String?): Listing? = encoded?.let { runCatching { adapter.fromJson(Uri.decode(it)) }.getOrNull() }
}
