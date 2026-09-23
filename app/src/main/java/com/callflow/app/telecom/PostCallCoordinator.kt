package com.callflow.app.telecom

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PostCallTarget(val leadId: String?, val callId: String)

@Singleton
class PostCallCoordinator @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context) {
    private val preferences = context.getSharedPreferences("pending-call-results", android.content.Context.MODE_PRIVATE)
    private val pending = runCatching {
        val array = org.json.JSONArray(preferences.getString("queue", "[]"))
        (0 until array.length()).map { index -> array.getJSONObject(index).let { PostCallTarget(if (it.isNull("lead")) null else it.getString("lead"), it.getString("call")) } }.toMutableList()
    }.getOrDefault(mutableListOf())
    private val mutableTarget = MutableStateFlow<PostCallTarget?>(pending.firstOrNull())
    val target: StateFlow<PostCallTarget?> = mutableTarget.asStateFlow()
    @Synchronized fun show(leadId: String?, callId: String) {
        // A deferred older note must not suppress the popup for a newly ended call.
        if (pending.none { it.callId == callId }) pending.add(0, PostCallTarget(leadId, callId))
        publish()
    }
    @Synchronized fun complete(callId: String) { pending.removeAll { it.callId == callId }; publish() }
    fun consume(value: PostCallTarget) { /* Navigation does not acknowledge an unsaved result. */ }
    private fun publish() {
        val array = org.json.JSONArray()
        pending.forEach { array.put(org.json.JSONObject().put("lead", it.leadId).put("call", it.callId)) }
        preferences.edit().putString("queue", array.toString()).apply()
        mutableTarget.value = pending.firstOrNull()
        // Android can block background activity launches; retain a user-visible way back to the result.
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        if (pending.isEmpty()) manager.cancel(4802) else runCatching {
            manager.createNotificationChannel(android.app.NotificationChannel("callflow_results", "Call notes", android.app.NotificationManager.IMPORTANCE_DEFAULT))
            val intent = android.content.Intent(context, com.callflow.app.MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val action = android.app.PendingIntent.getActivity(context, 24, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            manager.notify(4802, androidx.core.app.NotificationCompat.Builder(context, "callflow_results")
                .setSmallIcon(com.callflow.app.R.drawable.ic_callflow)
                .setContentTitle("Add your call notes")
                .setContentText("${pending.size} completed call(s) waiting for notes or a result")
                .setContentIntent(action).setAutoCancel(true).build())
        }
    }
}
