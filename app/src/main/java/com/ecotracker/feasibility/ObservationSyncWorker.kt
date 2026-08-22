package com.wildlife.feasibility

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Performs one conservative, read-only observation refresh. WorkManager persists the schedule
 * across process death and applies its own exponential retry delay when the public APIs fail.
 */
class ObservationSyncWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : Worker(appContext, parameters) {
    override fun doWork(): Result {
        val account = AccountStore(applicationContext).verified() ?: return Result.success()
        return runCatching {
            OnDeviceWildlifeRepository(applicationContext).use { repository ->
                repository.syncObservations(account)
                MarkerStore(applicationContext).load()
                    .asSequence()
                    .filter { it.state == MarkerState.CONFIRMED }
                    .mapNotNull { it.matchedObservationUuid }
                    .distinct()
                    .forEach { uuid -> repository.confirmObservation(account, uuid) }
            }
        }.fold(
            onSuccess = { Result.success() },
            // Confirmation is idempotent, so a repeated snapshot is safe after partial failure.
            onFailure = { Result.retry() },
        )
    }
}

/** Owns one low-frequency schedule for all public observations belonging to the linked account. */
object ObservationSyncScheduler {
    private const val UNIQUE_WORK_NAME = "wildlife-public-observation-sync"
    private const val INTERVAL_HOURS = 12L
    private const val BACKOFF_MINUTES = 30L

    fun schedule(context: Context) {
        if (AccountStore(context).verified() == null) {
            cancel(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<ObservationSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .addTag(UNIQUE_WORK_NAME)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
