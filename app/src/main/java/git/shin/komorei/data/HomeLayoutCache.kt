package git.shin.komorei.data

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeWithEpisode
import git.shin.komorei.model.FilterItem
import git.shin.komorei.model.FilterValue
import git.shin.komorei.model.FilterValueJsonAdapter
import git.shin.komorei.model.HomeComponent
import git.shin.komorei.model.HomeComponentValue
import git.shin.komorei.model.Link
import git.shin.komorei.model.LinkValue
import git.shin.komorei.model.Listing
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Disk cache of each source's FULL home layout (the app-model [HomeComponent]
 * list), keyed by source id, plus the merged layout of the "all" aggregator.
 *
 * The aggregator unions every source, so a cold read of its tab always costs
 * all sources at once. WITHIN a session the result already lives in
 * [AnimeRepository.homeCache] for its whole process lifetime, but process
 * death (and the Android "swipe away" killing the app) throws all of it away.
 * Persisting it here and trusting it for a few hours turns the common case
 * (process restarts, tab re-selection, process-death resume) into a file read
 * instead of N wasm executions.
 */
@Singleton
class HomeLayoutCache
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        // Local JSON is a cache, not a backup: any parse failure (schema drift
        // after an update, corrupt write) just falls through to a refetch.
        private val moshiRef = arrayOfNulls<Moshi>(1)
        private val moshi: Moshi by lazy {
            Moshi
                .Builder()
                .add(FilterValue::class.java, FilterValueJsonAdapter())
                .add(LinkValue::class.java, LinkValueJsonAdapter { moshiRef[0]!! })
                .add(HomeComponentValue::class.java, HomeComponentValueJsonAdapter { moshiRef[0]!! })
                .build()
                .also { moshiRef[0] = it }
        }
        private val cacheAdapter by lazy {
            moshi.adapter(CacheFile::class.java)
        }

        private val cacheDir: File by lazy {
            File(context.cacheDir, "home_layout").apply { mkdirs() }
        }

        fun get(sourceId: String): List<HomeComponent>? {
            val file = fileFor(sourceId)
            if (!file.exists()) return null
            return runCatching {
                val parsed = cacheAdapter.fromJson(file.readText()) ?: return null
                if (System.currentTimeMillis() - parsed.cachedAt <= TTL_MS) parsed.components else null
            }.getOrNull()
        }

        fun set(
            sourceId: String,
            components: List<HomeComponent>,
        ) {
            if (components.isEmpty()) return
            runCatching {
                fileFor(sourceId).writeText(
                    cacheAdapter.toJson(CacheFile(System.currentTimeMillis(), components)),
                )
            }
        }

        /**
         * Drops one source's cached layout — the next [get] misses and the
         * caller re-fetches.
         */
        fun clear(sourceId: String) {
            runCatching { fileFor(sourceId).delete() }
        }

        /** Drops every cached layout, the merged "all" entry included. */
        fun clearAll() {
            runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
        }

        private fun fileFor(sourceId: String): File = File(cacheDir, "${sourceId.replace(SAFE_ID_CHARS, "_")}.json")

        @JsonClass(generateAdapter = true)
        internal data class CacheFile(
            val cachedAt: Long,
            val components: List<HomeComponent>,
        )

        private companion object {
            /** A few hours — long enough to reuse across sessions. */
            val TTL_MS = TimeUnit.HOURS.toMillis(6)
            val SAFE_ID_CHARS = Regex("[^A-Za-z0-9._-]")
        }
    }

/**
 * Sealed [LinkValue] adapter — `{"type": "url" | "listing" | "anime", ...}`.
 * Keeps the same discriminator style as [FilterValueJsonAdapter].
 */
private class LinkValueJsonAdapter(
    private val moshiProvider: () -> Moshi,
) : JsonAdapter<LinkValue>() {
    private val moshi: Moshi by lazy(moshiProvider)
    private val listingAdapter by lazy { moshi.adapter(Listing::class.java) }
    private val animeAdapter by lazy { moshi.adapter(Anime::class.java) }

    override fun fromJson(reader: JsonReader): LinkValue? {
        reader.beginObject()
        val fields = LinkedHashMap<String, Any?>()
        while (reader.hasNext()) fields[reader.nextName()] = readJsonValue(reader)
        reader.endObject()
        return when (fields["type"] as? String) {
            "url" -> LinkValue.Url(url = fields["url"] as? String ?: "")
            "listing" -> LinkValue.Listing(listing = listingAdapter.fromJsonValue(fields["listing"])!!)
            "anime" -> LinkValue.Anime(anime = animeAdapter.fromJsonValue(fields["anime"])!!)
            else -> throw JsonDataException("Unknown link value type: ${fields["type"]}")
        }
    }

    override fun toJson(
        writer: JsonWriter,
        value: LinkValue?,
    ) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        when (value) {
            is LinkValue.Url -> {
                writer.name("type").value("url")
                writer.name("url").value(value.url)
            }
            is LinkValue.Listing -> {
                writer.name("type").value("listing")
                writer.name("listing")
                writer.writeValueTree(listingAdapter.toJsonValue(value.listing))
            }
            is LinkValue.Anime -> {
                writer.name("type").value("anime")
                writer.name("anime")
                writer.writeValueTree(animeAdapter.toJsonValue(value.anime))
            }
        }
        writer.endObject()
    }
}

/**
 * Sealed [HomeComponentValue] adapter — `{"type": "image_scroller" | ..., ...}`.
 */
private class HomeComponentValueJsonAdapter(
    private val moshiProvider: () -> Moshi,
) : JsonAdapter<HomeComponentValue>() {
    private val moshi: Moshi by lazy(moshiProvider)
    private val listingAdapter by lazy { moshi.adapter(Listing::class.java) }
    private val linksAdapter by lazy {
        moshi.adapter<List<Link>>(Types.newParameterizedType(List::class.java, Link::class.java))
    }
    private val animeAdapterList by lazy {
        moshi.adapter<List<Anime>>(Types.newParameterizedType(List::class.java, Anime::class.java))
    }
    private val animeWithEpisodeAdapterList by lazy {
        moshi.adapter<List<AnimeWithEpisode>>(
            Types.newParameterizedType(List::class.java, AnimeWithEpisode::class.java),
        )
    }
    private val filterItemAdapterList by lazy {
        moshi.adapter<List<FilterItem>>(Types.newParameterizedType(List::class.java, FilterItem::class.java))
    }

    override fun fromJson(reader: JsonReader): HomeComponentValue? {
        reader.beginObject()
        val fields = LinkedHashMap<String, Any?>()
        while (reader.hasNext()) fields[reader.nextName()] = readJsonValue(reader)
        reader.endObject()
        return when (fields["type"] as? String) {
            "image_scroller" ->
                HomeComponentValue.ImageScroller(
                    links = linksAdapter.fromJsonValue(fields["links"]) ?: emptyList(),
                    autoScrollInterval = (fields["autoScrollInterval"] as? Double)?.toFloat(),
                    width = (fields["width"] as? Double)?.toInt(),
                    height = (fields["height"] as? Double)?.toInt(),
                )
            "big_scroller" ->
                HomeComponentValue.BigScroller(
                    entries = animeAdapterList.fromJsonValue(fields["entries"]) ?: emptyList(),
                    autoScrollInterval = (fields["autoScrollInterval"] as? Double)?.toFloat(),
                )
            "scroller" ->
                HomeComponentValue.Scroller(
                    entries = linksAdapter.fromJsonValue(fields["entries"]) ?: emptyList(),
                    listing = fields["listing"]?.let { listingAdapter.fromJsonValue(it) },
                )
            "anime_list" ->
                HomeComponentValue.AnimeList(
                    ranking = fields["ranking"] as? Boolean ?: false,
                    pageSize = (fields["pageSize"] as? Double)?.toInt(),
                    entries = linksAdapter.fromJsonValue(fields["entries"]) ?: emptyList(),
                    listing = fields["listing"]?.let { listingAdapter.fromJsonValue(it) },
                )
            "anime_episode_list" ->
                HomeComponentValue.AnimeEpisodeList(
                    pageSize = (fields["pageSize"] as? Double)?.toInt(),
                    entries = animeWithEpisodeAdapterList.fromJsonValue(fields["entries"]) ?: emptyList(),
                    listing = fields["listing"]?.let { listingAdapter.fromJsonValue(it) },
                )
            "filters" ->
                HomeComponentValue.Filters(
                    items = filterItemAdapterList.fromJsonValue(fields["items"]) ?: emptyList(),
                )
            "links" ->
                HomeComponentValue.Links(
                    links = linksAdapter.fromJsonValue(fields["links"]) ?: emptyList(),
                )
            else -> throw JsonDataException("Unknown home component type: ${fields["type"]}")
        }
    }

    override fun toJson(
        writer: JsonWriter,
        value: HomeComponentValue?,
    ) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        when (value) {
            is HomeComponentValue.ImageScroller -> {
                writer.name("type").value("image_scroller")
                writer.name("links")
                writer.writeValueTree(linksAdapter.toJsonValue(value.links))
                if (value.autoScrollInterval != null) writer.name("autoScrollInterval").value(value.autoScrollInterval.toDouble())
                if (value.width != null) writer.name("width").value(value.width.toDouble())
                if (value.height != null) writer.name("height").value(value.height.toDouble())
            }
            is HomeComponentValue.BigScroller -> {
                writer.name("type").value("big_scroller")
                writer.name("entries")
                writer.writeValueTree(animeAdapterList.toJsonValue(value.entries))
                if (value.autoScrollInterval != null) writer.name("autoScrollInterval").value(value.autoScrollInterval.toDouble())
            }
            is HomeComponentValue.Scroller -> {
                writer.name("type").value("scroller")
                writer.name("entries")
                writer.writeValueTree(linksAdapter.toJsonValue(value.entries))
                if (value.listing != null) {
                    writer.name("listing")
                    writer.writeValueTree(listingAdapter.toJsonValue(value.listing))
                }
            }
            is HomeComponentValue.AnimeList -> {
                writer.name("type").value("anime_list")
                writer.name("ranking").value(value.ranking)
                if (value.pageSize != null) writer.name("pageSize").value(value.pageSize.toDouble())
                writer.name("entries")
                writer.writeValueTree(linksAdapter.toJsonValue(value.entries))
                if (value.listing != null) {
                    writer.name("listing")
                    writer.writeValueTree(listingAdapter.toJsonValue(value.listing))
                }
            }
            is HomeComponentValue.AnimeEpisodeList -> {
                writer.name("type").value("anime_episode_list")
                if (value.pageSize != null) writer.name("pageSize").value(value.pageSize.toDouble())
                writer.name("entries")
                writer.writeValueTree(animeWithEpisodeAdapterList.toJsonValue(value.entries))
                if (value.listing != null) {
                    writer.name("listing")
                    writer.writeValueTree(listingAdapter.toJsonValue(value.listing))
                }
            }
            is HomeComponentValue.Filters -> {
                writer.name("type").value("filters")
                writer.name("items")
                writer.writeValueTree(filterItemAdapterList.toJsonValue(value.items))
            }
            is HomeComponentValue.Links -> {
                writer.name("type").value("links")
                writer.name("links")
                writer.writeValueTree(linksAdapter.toJsonValue(value.links))
            }
        }
        writer.endObject()
    }
}

/** Reads any JSON value into Kotlin primitives/collections (numbers as [Double]). */
private fun readJsonValue(reader: JsonReader): Any? =
    when (reader.peek()) {
        JsonReader.Token.NULL -> reader.nextNull()
        JsonReader.Token.BOOLEAN -> reader.nextBoolean()
        JsonReader.Token.STRING -> reader.nextString()
        JsonReader.Token.NUMBER -> reader.nextDouble()
        JsonReader.Token.BEGIN_ARRAY -> {
            reader.beginArray()
            val list = ArrayList<Any?>()
            while (reader.hasNext()) list.add(readJsonValue(reader))
            reader.endArray()
            list
        }
        JsonReader.Token.BEGIN_OBJECT -> {
            reader.beginObject()
            val map = LinkedHashMap<String, Any?>()
            while (reader.hasNext()) map[reader.nextName()] = readJsonValue(reader)
            reader.endObject()
            map
        }
        else -> {
            reader.skipValue()
            null
        }
    }

/** Writes a generic JSON value-tree produced by `Adapter.toJsonValue`. */
private fun JsonWriter.writeValueTree(value: Any?) {
    when (value) {
        null -> nullValue()
        is Boolean -> value(value)
        is Number -> value(value.toDouble())
        is String -> value(value)
        is List<*> -> {
            beginArray()
            value.forEach { writeValueTree(it) }
            endArray()
        }
        is Map<*, *> -> {
            beginObject()
            value.forEach { (k, v) ->
                name(k as String)
                writeValueTree(v)
            }
            endObject()
        }
        else -> throw JsonDataException("Unexpected JSON value: $value")
    }
}
