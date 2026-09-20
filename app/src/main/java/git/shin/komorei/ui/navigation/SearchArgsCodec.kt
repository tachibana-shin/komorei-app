package git.shin.komorei.ui.navigation

import android.net.Uri
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import git.shin.komorei.model.FilterValue
import git.shin.komorei.model.FilterValueJsonAdapter

/**
 * URL + SavedStateHandle codec for the per-source search page's query and
 * enabled filter values.
 *
 * Filters serialize to JSON through the app's polymorphic [FilterValueJsonAdapter]
 * ([FilterValue] is the exact element type of the list, so Moshi's built-in
 * collection adapter delegates each element to it). Query/filters arguments are
 * percent-encoded inside the route (`Uri.encode` — Navigation URL-decodes query
 * arguments when matching), while the SavedStateHandle mirror stores the raw
 * strings so the ViewModel round-trips them without a second decode.
 */
object SearchArgsCodec {

    private val moshi = Moshi.Builder()
        .add(FilterValue::class.java, FilterValueJsonAdapter())
        .build()
    private val filtersAdapter: JsonAdapter<List<FilterValue>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, FilterValue::class.java))

    /** Encodes the free-text [query] for a route `?query=` argument. */
    fun encodeQuery(query: String): String = Uri.encode(query)

    /** Encodes [filters] as a URL-safe JSON query argument. */
    fun encodeFilters(filters: List<FilterValue>): String = Uri.encode(toJson(filters))

    /** Raw JSON (no percent-escaping) for SavedStateHandle round-trips. */
    fun toJson(filters: List<FilterValue>): String = filtersAdapter.toJson(filters)

    /**
     * Decodes the stored JSON (arg or handle mirror) back to [FilterValue]s.
     * The `Uri.decode` inside is harmless for the JSON form (ids/options never
     * contain `%`) and normalizes the nav-arg path whichever way Navigation
     * decoded the query argument.
     */
    fun fromJson(raw: String?): List<FilterValue> =
        raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { filtersAdapter.fromJson(Uri.decode(it)) }.getOrNull() }
            ?: emptyList()
}