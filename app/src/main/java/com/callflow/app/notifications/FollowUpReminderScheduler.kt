package com.callflow.app.notifications

import java.time.Instant

interface FollowUpReminderScheduler {
    fun schedule(followUpId: String, at: Instant)
    fun cancel(followUpId: String)

    data object NoOp : FollowUpReminderScheduler {
        override fun schedule(followUpId: String, at: Instant) = Unit
        override fun cancel(followUpId: String) = Unit
    }
}
