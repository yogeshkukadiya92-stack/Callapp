package com.callflow.app.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class FollowUpReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val notifications: FollowUpNotificationManager,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching { notifications.notifyDue() }
        .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}
