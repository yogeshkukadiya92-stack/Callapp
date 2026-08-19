package com.callflow.app.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TelecomActionReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: CallUiController
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> controller.answer()
            ACTION_DECLINE -> controller.reject()
            ACTION_HANG_UP -> controller.disconnect()
        }
    }
    companion object {
        const val ACTION_ANSWER = "com.callflow.app.telecom.ANSWER"
        const val ACTION_DECLINE = "com.callflow.app.telecom.DECLINE"
        const val ACTION_HANG_UP = "com.callflow.app.telecom.HANG_UP"
    }
}
