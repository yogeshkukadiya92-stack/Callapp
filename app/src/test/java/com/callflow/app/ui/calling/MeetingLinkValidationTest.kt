package com.callflow.app.ui.calling

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingLinkValidationTest {
    @Test fun acceptsGoogleMeetAndZoomHttpsLinks() {
        assertTrue("https://meet.google.com/abc-defg-hij".isSupportedMeetingLink())
        assertTrue("https://us02web.zoom.us/j/123456".isSupportedMeetingLink())
    }

    @Test fun rejectsInsecureAndUnrelatedLinks() {
        assertFalse("http://meet.google.com/abc".isSupportedMeetingLink())
        assertFalse("https://example.com/meeting".isSupportedMeetingLink())
        assertFalse("not a link".isSupportedMeetingLink())
    }
}
