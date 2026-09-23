package com.callflow.app.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallNotificationManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(state: InCallUiState) {
        createChannel()
        val hasName = !state.displayName.isNullOrBlank() && state.displayName != state.phoneNumber
        val title = if (hasName) state.displayName!! else state.phoneNumber.ifBlank { "Phone call" }
        val person = Person.Builder().setName(title).setImportant(true).build()
        val content = PendingIntent.getActivity(context, 20, Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val answer = action(TelecomActionReceiver.ACTION_ANSWER, 21)
        val decline = action(TelecomActionReceiver.ACTION_DECLINE, 22)
        val hangUp = action(TelecomActionReceiver.ACTION_HANG_UP, 23)
        val style = if (state.incoming && state.state == PlatformCallState.RINGING) NotificationCompat.CallStyle.forIncomingCall(person, decline, answer) else NotificationCompat.CallStyle.forOngoingCall(person, hangUp)
        val subtext = when {
            hasName && state.phoneNumber.isNotBlank() -> "${if (state.incoming) "Incoming" else "Ongoing"} · ${state.phoneNumber}"
            state.incoming -> "Incoming call"
            else -> "Call in progress"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.callflow.app.R.drawable.ic_callflow)
            .setContentTitle(title)
            .setContentText(subtext)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(state.state != PlatformCallState.DISCONNECTED)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(content)
            .setFullScreenIntent(content, state.incoming && state.state == PlatformCallState.RINGING)
            .setStyle(style)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)
    private fun action(action: String, requestCode: Int) = PendingIntent.getBroadcast(context, requestCode, Intent(context, TelecomActionReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun createChannel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Phone calls", NotificationManager.IMPORTANCE_HIGH).apply { description = "Incoming and ongoing business calls"; lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC })
    }
    companion object { private const val CHANNEL = "callflow_calls"; private const val NOTIFICATION_ID = 4801 }
}
