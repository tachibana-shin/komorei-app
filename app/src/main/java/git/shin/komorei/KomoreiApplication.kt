package git.shin.komorei

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import git.shin.komorei.data.remote.KomoreiDataSourceFactory
import javax.inject.Inject

@HiltAndroidApp
class KomoreiApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var dataSourceFactory: KomoreiDataSourceFactory

    override fun newImageLoader(): ImageLoader = imageLoader
}
