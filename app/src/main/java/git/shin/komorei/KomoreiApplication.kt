package git.shin.komorei

import android.app.Application
import git.shin.komorei.data.AnimeRepository

class KomoreiApplication : Application() {
    lateinit var repository: AnimeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AnimeRepository()
    }
}
