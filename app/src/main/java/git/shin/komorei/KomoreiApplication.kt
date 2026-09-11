package git.shin.komorei

import android.app.Activity
import android.app.Application
import android.os.Bundle
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

    /**
     * The most recently resumed [Activity], updated from the app lifecycle. Non-UI
     * layers (e.g. [git.shin.komorei.ui.player.PlayerViewModel] fullscreen rotation)
     * reach the live window / requestedOrientation through this instead of trying to
     * cast the application context to an Activity (which is always null).
     */
    var currentActivity: Activity? = null
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }
            override fun onActivityPaused(activity: Activity) {
                if (currentActivity === activity) currentActivity = null
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity === activity) currentActivity = null
            }
        })
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}