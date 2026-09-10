package git.shin.komorei.data.remote

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom DataSource.Factory for Media3 to handle custom headers, cookies and dynamic segments.
 * Inspired by git.shin.animevsub patterns.
 */
@UnstableApi
@Singleton
class KomoreiDataSourceFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val userAgent: String
) : DataSource.Factory {

    private var customHeaders: Map<String, String> = emptyMap()

    fun setHeaders(headers: Map<String, String>) {
        this.customHeaders = headers
    }

    override fun createDataSource(): DataSource {
        val dataSource = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgent)
        
        val httpDataSource = dataSource.createDataSource()
        
        // Inject custom headers (Referer, Cookie, etc.)
        customHeaders.forEach { (key, value) ->
            httpDataSource.setRequestProperty(key, value)
        }
        
        return httpDataSource
    }
}
