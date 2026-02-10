package com.feelbachelor.doompedia

import android.app.Application
import com.feelbachelor.doompedia.data.db.WikiDatabase
import com.feelbachelor.doompedia.data.importer.AssetBootstrapper
import com.feelbachelor.doompedia.data.importer.DeltaApplier
import com.feelbachelor.doompedia.data.importer.PackInstaller
import com.feelbachelor.doompedia.data.importer.ShardDownloader
import com.feelbachelor.doompedia.data.net.WikipediaApiClient
import com.feelbachelor.doompedia.data.repo.UserPreferencesStore
import com.feelbachelor.doompedia.data.repo.WikiRepository
import com.feelbachelor.doompedia.data.update.PackUpdateService
import com.feelbachelor.doompedia.ranking.FeedRanker
import com.feelbachelor.doompedia.ranking.RankingConfigLoader
import com.feelbachelor.doompedia.worker.PackUpdateWorker

class DoompediaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Updates are manual-only for now; cancel any previously scheduled periodic worker.
        PackUpdateWorker.cancel(this)
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
    val bootstrapper = AssetBootstrapper(application, db)
}
