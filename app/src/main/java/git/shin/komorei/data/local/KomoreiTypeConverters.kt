package git.shin.komorei.data.local

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import git.shin.komorei.model.AnimeSeason
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.model.CategoryLink
import git.shin.komorei.model.Episode

class KomoreiTypeConverters {
    private val moshi = Moshi.Builder().build()
    
    private val categoryLinkAdapter = moshi.adapter(CategoryLink::class.java)
    private val listCategoryLinkAdapter = moshi.adapter<List<CategoryLink>>(
        Types.newParameterizedType(List::class.java, CategoryLink::class.java)
    )
    private val listEpisodeAdapter = moshi.adapter<List<Episode>>(
        Types.newParameterizedType(List::class.java, Episode::class.java)
    )
    private val listSeasonAdapter = moshi.adapter<List<AnimeSeason>>(
        Types.newParameterizedType(List::class.java, AnimeSeason::class.java)
    )

    @TypeConverter
    fun fromAnimeStatus(status: AnimeStatus): String = status.value

    @TypeConverter
    fun toAnimeStatus(value: String): AnimeStatus = AnimeStatus.fromString(value)

    @TypeConverter
    fun fromCategoryLink(link: CategoryLink?): String? = link?.let { categoryLinkAdapter.toJson(it) }

    @TypeConverter
    fun toCategoryLink(json: String?): CategoryLink? = json?.let { categoryLinkAdapter.fromJson(it) }

    @TypeConverter
    fun fromListCategoryLink(links: List<CategoryLink>): String = listCategoryLinkAdapter.toJson(links)

    @TypeConverter
    fun toListCategoryLink(json: String): List<CategoryLink> = json.let { listCategoryLinkAdapter.fromJson(it) ?: emptyList() }

    @TypeConverter
    fun fromListEpisode(episodes: List<Episode>): String = listEpisodeAdapter.toJson(episodes)

    @TypeConverter
    fun toListEpisode(json: String): List<Episode> = json.let { listEpisodeAdapter.fromJson(it) ?: emptyList() }

    @TypeConverter
    fun fromListSeason(seasons: List<AnimeSeason>): String = listSeasonAdapter.toJson(seasons)

    @TypeConverter
    fun toListSeason(json: String): List<AnimeSeason> = json.let { listSeasonAdapter.fromJson(it) ?: emptyList() }
}
