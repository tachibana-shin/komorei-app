package git.shin.komorei.di

import android.content.Context
import android.webkit.WebSettings
import androidx.room.Room
import coil.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import git.shin.komorei.data.backup.DriveBackupApi
import git.shin.komorei.data.backup.GoogleDriveBackupApi
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.data.local.KrxDefaultsStore
import git.shin.komorei.data.local.RoomKrxDefaultsStore
import git.shin.komorei.data.local.dao.AnimeDao
import git.shin.komorei.data.local.dao.KrxDefaultsDao
import git.shin.komorei.data.remote.WebViewCookieJar
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    /**
     * Whole-call budget for every request the shared client makes, in seconds.
     *
     * Generous enough for a large listing page over a slow connection, short
     * enough that a server trickling bytes cannot pin the runner's engine mutex
     * — and with it every source in the app — indefinitely. See
     * [provideOkHttpClient] for why that coupling exists.
     */
    private const val HTTP_CALL_TIMEOUT_SECONDS = 60L

    @Provides
    @Singleton
    fun provideUserAgent(
        @ApplicationContext context: Context,
    ): String =
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(userAgent: String): OkHttpClient {
        val logging =
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
        return OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val request =
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", userAgent)
                        .build()
                chain.proceed(request)
            }.addInterceptor(logging)
            .cookieJar(WebViewCookieJar())
            .followRedirects(true)
            .followSslRedirects(true)
            // OkHttp's per-socket timeouts (10s connect/read/write) are all
            // RESETS by every byte that arrives, and callTimeout defaults to 0 —
            // no overall budget at all. So a server that trickles one byte just
            // under the read timeout holds a request open forever.
            //
            // That is not merely a slow request here: a source's `net_request` is
            // a host callback, so it runs on the runner's big-stack worker while
            // that worker is still holding the engine mutex. One trickling
            // response therefore wedges EVERY source — the next source blocks on
            // the mutex, its skeleton never resolves, and the app looks like it
            // cannot open any source at all rather than one slow site.
            //
            // A whole-call budget converts that into an ordinary IOException the
            // source can handle or abort, which is what a timeout is for.
            .callTimeout(HTTP_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @DriveHttpClient
    fun provideDriveHttpClient(): OkHttpClient {
        // Do not reuse the source client here: it logs headers and carries the
        // WebView cookie jar, which must never see a Google bearer token.
        return OkHttpClient
            .Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    @UpdateHttpClient
    fun provideUpdateHttpClient(): OkHttpClient {
        // OTA metadata/APK downloads must not inherit source cookies or header
        // logging from the WebView-backed client.
        return OkHttpClient
            .Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideDriveBackupApi(
        @DriveHttpClient client: OkHttpClient,
    ): DriveBackupApi = GoogleDriveBackupApi(client)

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): KomoreiDatabase =
        Room
            .databaseBuilder(
                context,
                KomoreiDatabase::class.java,
                "komorei_db",
            )
            // CategoryLink JSON shape changed (filters: SelectedFilter → FilterValue) — dev data
            // written with the old shape would break the new adapters.
            .fallbackToDestructiveMigration()
            .addMigrations(
                KomoreiDatabase.MIGRATION_2_3,
                KomoreiDatabase.MIGRATION_3_4,
                KomoreiDatabase.MIGRATION_4_5,
            ).build()

    @Provides
    @Singleton
    fun provideAnimeDao(database: KomoreiDatabase): AnimeDao = database.animeDao()

    @Provides
    @Singleton
    fun provideKrxDefaultsDao(database: KomoreiDatabase): KrxDefaultsDao = database.krxDefaultsDao()

    @Provides
    @Singleton
    fun provideKrxDefaultsStore(dao: KrxDefaultsDao): KrxDefaultsStore = RoomKrxDefaultsStore(dao)

    @Provides
    @Singleton
    fun provideKrxHost(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        krxDefaultsStore: KrxDefaultsStore,
    ): KrxHostImpl = KrxHostImpl(context, okHttpClient, krxDefaultsStore)

    @Provides
    @Singleton
    fun provideKrxSourceRegistry(
        @ApplicationContext context: Context,
        krxHost: KrxHostImpl,
    ): KrxSourceRegistry = KrxSourceRegistry(context, krxHost)

    // AnimeRepository is provided via its @Inject constructor; just the cache.
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
annotation class DriveHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
annotation class UpdateHttpClient
