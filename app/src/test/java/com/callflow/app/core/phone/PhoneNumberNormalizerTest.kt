package com.callflow.app.core.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberNormalizerTest {
    private val subject = CountryAwarePhoneNumberNormalizer()
    @Test fun normalizesCommonIndianFormats() {
        assertEquals("+919876543210", subject.normalize("+91 98765-43210"))
        assertEquals("+919876543210", subject.normalize("09876543210"))
        assertEquals("+919876543210", subject.normalize("9876543210"))
    }
    @Test fun rejectsTooShortNumbers() = assertNull(subject.normalize("12345"))
}
