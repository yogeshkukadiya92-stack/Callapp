package com.callflow.app.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.callflow.app.sync.SyncWorker

/**
 * Listens for phone state changes from the native Android telephony manager.
 * When an incoming or outgoing call ends (EXTRA_STATE_IDLE), enqueues an immediate background sync
 * to ingest the completed call record from the Android system CallLog and sync it.
 * This ensures call details are tracked automatically while the user uses their phone's native dialer.
 */
class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state == TelephonyManager.EXTRA_STATE_IDLE) {
            SyncWorker.syncNow(context)
        }
    }
}
