package com.callflow.app.telecom

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PostCallTarget(val leadId: String, val callId: String)

@Singleton
class PostCallCoordinator @Inject constructor() {
    private val mutableTarget = MutableStateFlow<PostCallTarget?>(null)
    val target: StateFlow<PostCallTarget?> = mutableTarget.asStateFlow()
    fun show(leadId: String, callId: String) { mutableTarget.value = PostCallTarget(leadId, callId) }
    fun consume(value: PostCallTarget) { if (mutableTarget.value == value) mutableTarget.value = null }
}
