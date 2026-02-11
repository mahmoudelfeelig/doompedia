package com.feelbachelor.doompedia

import android.app.Application
import com.feelbachelor.doompedia.data.db.WikiDatabase
import com.feelbachelor.doompedia.data.importer.AssetBootstrapper
import com.feelbachelor.doompedia.data.importer.DeltaApplier
import com.feelbachelor.doompedia.data.importer.PackInstaller
import com.feelbachelor.doompedia.data.importer.ShardDownloader
import com.feelbachelor.doompedia.data.net.WikipediaApiClient
import com.feelbachelor.doompedia.data.net.WikipediaImageClient
import com.feelbachelor.doompedia.data.repo.UserPreferencesStore
import com.feelbachelor.doompedia.data.repo.WikiRepository
import com.feelbachelor.doompedia.data.update.PackUpdateService
import com.feelbachelor.doompedia.ranking.FeedRanker
import com.feelbachelor.doompedia.ranking.RankingConfigLoader
import com.feelbachelor.doompedia.worker.PackUpdateWorker
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import java.io.File

class DoompediaApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Updates are manual-only for now; cancel any previously scheduled periodic worker.
        PackUpdateWorker.cancel(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "doompedia_article_images"))
                    .maxSizeBytes(3L * 1024L * 1024L * 1024L)
                    .build()
            }
            .build()
    }
}

class AppContainer(application: Application) {
    val appContext: Application = application
    private val db = WikiDatabase.getInstance(application)
    private val rankingConfig = RankingConfigLoader(application).load()
    private val ranker = FeedRanker(rankingConfig)

    val preferences = UserPreferencesStore(application)
    private val shardDownloader = ShardDownloader(application)
    private val packInstaller = PackInstaller(db)
    private val deltaApplier = DeltaApplier(db)
    val updateService = PackUpdateService(
        context = application,
        downloader = shardDownloader,
        packInstaller = packInstaller,
        deltaApplier = deltaApplier,
    )
    val repository = WikiRepository(
        dao = db.wikiDao(),
        rankingConfig = rankingConfig,
        ranker = ranker,
    )
    val wikipediaApiClient = WikipediaApiClient()
    val wikipediaImageClient = WikipediaImageClient()
    val bootstrapper = AssetBootstrapper(application, db)
}
