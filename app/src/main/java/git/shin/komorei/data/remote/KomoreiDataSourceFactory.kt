package git.shin.komorei.data.remote

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import git.shin.komorei.model.StreamData
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom [HttpDataSource.Factory] for Media3 that:
 *  - injects per-stream headers (Referer, Cookie, User-Agent…) on every request
 *    (playlist + segments), sourced from the resolved [StreamData]
 *  - wraps every data source in [TransformableHttpDataSource] when the active source
 *    provides [SegmentUrlInterceptor] / [SegmentDataInterceptor]
 *  - runs through the shared OkHttpClient so [WebViewCookieJar] cookies and the
 *    user-agent interceptor are applied automatically
 *
 * Inspired by git.shin.animevsub.data.remote patterns.
 */
@UnstableApi
@Singleton
class KomoreiDataSourceFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val userAgent: String
) : HttpDataSource.Factory {

    private val defaultRequestProperties = mutableMapOf<String, String>()

    @Volatile
    private var streamData: StreamData? = null

    @Volatile
    private var urlInterceptor: SegmentUrlInterceptor? = null

    @Volatile
    private var dataInterceptor: SegmentDataInterceptor? = null

    /**
     * (Re)configure the factory for the currently resolved stream. Must be called before
     * building a media source so subsequent requests carry the right headers/transformers.
     */
    fun configure(
        streamData: StreamData?,
        urlInterceptor: SegmentUrlInterceptor? = null,
        dataInterceptor: SegmentDataInterceptor? = null
    ) {
        this.streamData = streamData
        this.urlInterceptor = urlInterceptor
        this.dataInterceptor = dataInterceptor
        defaultRequestProperties.clear()
        defaultRequestProperties.putAll(streamData?.headers ?: emptyMap())
    }

    override fun setDefaultRequestProperties(
        defaultRequestProperties: Map<String, String>
    ): HttpDataSource.Factory {
        this.defaultRequestProperties.clear()
        this.defaultRequestProperties.putAll(defaultRequestProperties)
        return this
    }

    override fun createDataSource(): HttpDataSource {
        val base = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(defaultRequestProperties.toMap())
            .createDataSource()

        val urlInter = urlInterceptor
        val dataInter = dataInterceptor
        return if (urlInter != null || dataInter != null) {
            TransformableHttpDataSource(base, streamData, urlInter, dataInter)
        } else {
            base
        }
    }
}