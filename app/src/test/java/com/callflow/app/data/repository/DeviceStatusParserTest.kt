package com.callflow.app.data.repository

import com.callflow.app.core.model.DeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceStatusParserTest {
    @Test fun acceptsBackendStatusCaseInsensitively() = assertEquals(DeviceStatus.ACTIVE, parseDeviceStatus("active"))
    @Test fun unknownBackendStatusFailsClosed() = assertEquals(DeviceStatus.PENDING_APPROVAL, parseDeviceStatus("UNRECOGNIZED"))
}
