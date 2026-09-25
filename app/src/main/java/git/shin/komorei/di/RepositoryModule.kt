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
import git.shin.komorei.data.AnimeRepository
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
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
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
            .addMigrations(KomoreiDatabase.MIGRATION_2_3, KomoreiDatabase.MIGRATION_3_4)
            .build()

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

    @Provides
    @Singleton
    fun provideAnimeRepository(registry: KrxSourceRegistry): AnimeRepository = AnimeRepository(registry)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
annotation class DriveHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
annotation class UpdateHttpClient
