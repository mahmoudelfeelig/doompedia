package com.feelbachelor.doompedia.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.feelbachelor.doompedia.DoompediaApplication
import com.feelbachelor.doompedia.data.update.PackUpdateStatus
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class PackUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DoompediaApplication
        val settings = app.container.preferences.settings.first()
        val updateService = app.container.updateService

        if (settings.manifestUrl.isBlank()) {
            app.container.preferences.setLastUpdate(
                timestampIso = nowIso(),
                status = "Skipped: manifest URL not set",
            )
            return Result.success()
        }

        val outcome = updateService.checkAndApply(
            manifestUrl = settings.manifestUrl,
            wifiOnly = settings.wifiOnlyDownloads,
            installedVersion = settings.installedPackVersion,
        )

        app.container.preferences.setInstalledPackVersion(outcome.installedVersion)
        app.container.preferences.setLastUpdate(
            timestampIso = nowIso(),
            status = outcome.message,
        )

        return when (outcome.status) {
            PackUpdateStatus.FAILED -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "doompedia-pack-updates"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PackUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

private fun nowIso(): String = java.time.Instant.now().toString()
