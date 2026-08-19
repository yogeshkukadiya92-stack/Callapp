package com.callflow.app.core.time

import java.time.Instant
import javax.inject.Inject

interface DateTimeProvider { fun now(): Instant }

class SystemDateTimeProvider @Inject constructor() : DateTimeProvider {
    override fun now(): Instant = Instant.now()
}
