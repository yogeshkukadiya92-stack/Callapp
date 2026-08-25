package com.callflow.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.callflow.app.domain.repository.SyncRepository
import com.callflow.app.telecom.CallLogImporter
import com.callflow.app.notifications.FollowUpNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: SyncRepository,
    private val callLogImporter: CallLogImporter,
    private val followUpNotifications: FollowUpNotificationManager,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val syncResult = runCatching {
            callLogImporter.importNewCalls()
            repository.syncPending().getOrThrow()
            followUpNotifications.notifyDue()
        }
        return syncResult.fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount >= 8) Result.failure() else Result.retry() },
        )
    }

    companion object {
        private const val UNIQUE_NAME = "callflow-outbox-sync"
        fun syncNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SyncWorker>().build())
        }

        fun syncAfterLogin(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("callflow-login-sync", ExistingWorkPolicy.REPLACE, request)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
