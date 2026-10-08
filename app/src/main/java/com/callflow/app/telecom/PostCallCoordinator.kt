package com.callflow.app.telecom

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PostCallTarget(
    val leadId: String?,
    val callId: String,
    val callerName: String? = null,
    val phone: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@Singleton
class PostCallCoordinator @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context) {
    private val preferences = context.getSharedPreferences("pending-call-results", android.content.Context.MODE_PRIVATE)
    private val pending = runCatching {
        val array = org.json.JSONArray(preferences.getString("queue", "[]"))
        (0 until array.length()).map { index ->
            array.getJSONObject(index).let {
                PostCallTarget(
                    if (it.isNull("lead")) null else it.getString("lead"),
                    it.getString("call"),
                    if (it.has("name") && !it.isNull("name")) it.getString("name") else null,
                    if (it.has("phone") && !it.isNull("phone")) it.getString("phone") else null,
                )
            }
        }.toMutableList()
    }.getOrDefault(mutableListOf())
    private val consumedNavigation = runCatching {
        val array = org.json.JSONArray(preferences.getString("queue", "[]"))
        (0 until array.length()).mapNotNull { index ->
            array.getJSONObject(index).takeIf { it.optBoolean("navigationConsumed", true) }?.getString("call")
        }.toMutableSet()
    }.getOrDefault(mutableSetOf<String>())
    private val mutableTarget = MutableStateFlow<PostCallTarget?>(pending.firstOrNull { it.callId !in consumedNavigation })
    val target: StateFlow<PostCallTarget?> = mutableTarget.asStateFlow()

    @Synchronized fun show(leadId: String?, callId: String, callerName: String? = null, phone: String? = null) {
        val index = pending.indexOfFirst { it.callId == callId }
        val existing = pending.getOrNull(index)
        val target = PostCallTarget(leadId ?: existing?.leadId, callId, callerName ?: existing?.callerName, phone ?: existing?.phone, existing?.timestamp ?: System.currentTimeMillis())
        if (index >= 0) {
            pending[index] = target
        } else {
            pending.add(0, target)
        }
        publish()
    }

    @Synchronized fun setDirectTarget(callId: String, leadId: String?) {
        val existing = pending.firstOrNull { it.callId == callId }
        val target = existing ?: PostCallTarget(leadId, callId)
        pending.removeAll { it.callId == callId }
        pending.add(0, target)
        consumedNavigation.remove(callId)
        mutableTarget.value = null
        mutableTarget.value = target
        publish()
    }

    @Synchronized fun complete(callId: String) { pending.removeAll { it.callId == callId }; publish() }
    @Synchronized fun consume(value: PostCallTarget) {
        // Keep the result and reminder pending, but navigate automatically only
        // once, including across process recreation. A notification tap can reopen it.
        consumedNavigation.add(value.callId)
        publish()
    }

    private fun publish() {
        val array = org.json.JSONArray()
        pending.forEach {
            array.put(
                org.json.JSONObject()
                    .put("lead", it.leadId)
                    .put("call", it.callId)
                    .put("name", it.callerName)
                    .put("phone", it.phone)
                    .put("navigationConsumed", it.callId in consumedNavigation)
            )
        }
        preferences.edit().putString("queue", array.toString()).apply()
        mutableTarget.value = pending.firstOrNull { it.callId !in consumedNavigation }

        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        if (pending.isEmpty()) manager.cancel(4802) else runCatching {
            val channel = android.app.NotificationChannel(
                "callflow_results",
                "Call notes",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prompt to record notes after phone calls"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)

            val latest = pending.first()
            val contactTitle = latest.callerName?.takeIf(String::isNotBlank)
                ?: latest.phone?.takeIf(String::isNotBlank)
            val title = if (!contactTitle.isNullOrBlank()) "Add Call Note · $contactTitle" else "Add your call notes"
            val text = if (pending.size == 1) "Call ended. Tap to record notes, status & follow-up"
            else "${pending.size} calls waiting for notes or disposition"

            val intent = android.content.Intent(context, com.callflow.app.MainActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("target_call_id", latest.callId)
                putExtra("open_post_call", true)
                latest.leadId?.let { putExtra("target_lead_id", it) }
            }
            val action = android.app.PendingIntent.getActivity(
                context,
                24,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            manager.notify(
                4802,
                androidx.core.app.NotificationCompat.Builder(context, "callflow_results")
                    .setSmallIcon(com.callflow.app.R.drawable.ic_callflow)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                    .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(action)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .addAction(com.callflow.app.R.drawable.ic_callflow, "ADD NOTE", action)
                    .build()
            )
        }
    }
}
