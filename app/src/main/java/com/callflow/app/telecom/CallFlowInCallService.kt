package com.callflow.app.telecom

import android.telecom.Call
import android.telecom.InCallService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull

@AndroidEntryPoint
class CallFlowInCallService : InCallService() {
    @Inject lateinit var tracker: CallLifecycleTracker
    @Inject lateinit var controller: CallUiController
    @Inject lateinit var notifications: CallNotificationManager
    @Inject lateinit var dao: com.callflow.app.data.local.CallFlowDao
    @Inject lateinit var normalizer: com.callflow.app.core.phone.PhoneNumberNormalizer
    @Inject lateinit var postCall: PostCallCoordinator
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onCreate() {
        super.onCreate()
        controller.attachService(this)
        scope.launch { controller.state.collectLatest { state -> if (state.hasCall) notifications.show(state) else notifications.cancel() } }
        scope.launch { postCall.target.filterNotNull().collectLatest {
            runCatching { startActivity(android.content.Intent(this@CallFlowInCallService, com.callflow.app.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        } }
    }
    override fun onDestroy() { scope.cancel(); controller.detachService(this); super.onDestroy() }
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        controller.attachCall(call)
        tracker.onCallAdded(call)
        val rawNumber = call.details.handle?.schemeSpecificPart.orEmpty()
        scope.launch {
            val normalized = normalizer.normalize(rawNumber) ?: rawNumber.filter(Char::isDigit)
            controller.setMatchedLeadName(rawNumber, dao.findByPhone(normalized).firstOrNull()?.name)
        }
        notifications.show(controller.state.value)
        runCatching { startActivity(android.content.Intent(this, InCallActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
    }
    override fun onCallRemoved(call: Call) {
        tracker.onCallRemoved(call)
        controller.detachCall(call)
        notifications.cancel()
        super.onCallRemoved(call)
    }
}
