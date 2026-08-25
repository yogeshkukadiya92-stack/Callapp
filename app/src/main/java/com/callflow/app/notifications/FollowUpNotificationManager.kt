package com.callflow.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.callflow.app.MainActivity
import com.callflow.app.R
import com.callflow.app.data.local.CallFlowDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowUpNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CallFlowDao,
) : FollowUpReminderScheduler {
    fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, "Sales follow-ups", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Due and overdue lead follow-up reminders"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    suspend fun notifyDue() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val now = Instant.now().toEpochMilli()
        val due = dao.dueFollowUps(now + REMIND_AHEAD_MS)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        due.forEach { followUp ->
            val overdue = followUp.scheduledAt < now
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_callflow)
                .setContentTitle(if (overdue) "Overdue sales follow-up" else "Follow-up coming up")
                .setContentText(followUp.note?.takeIf(String::isNotBlank) ?: "Open CallFlow to contact this assigned lead")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
            NotificationManagerCompat.from(context).notify(followUp.id.hashCode(), notification)
        }
    }

    override fun schedule(followUpId: String, at: Instant) {
        val delay = Duration.between(Instant.now(), at.minusSeconds(REMIND_AHEAD_MS / 1000)).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<FollowUpReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf("followUpId" to followUpId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("follow-up-$followUpId", ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(followUpId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("follow-up-$followUpId")
        NotificationManagerCompat.from(context).cancel(followUpId.hashCode())
    }

    private companion object {
        const val CHANNEL_ID = "sales_follow_ups"
        const val REMIND_AHEAD_MS = 15 * 60 * 1000L
    }
}
