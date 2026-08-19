package com.callflow.app.core.phone

import javax.inject.Inject

interface PhoneNumberNormalizer { fun normalize(raw: String, defaultCountryCode: String = "91"): String? }

class CountryAwarePhoneNumberNormalizer @Inject constructor() : PhoneNumberNormalizer {
    override fun normalize(raw: String, defaultCountryCode: String): String? {
        val hasPlus = raw.trim().startsWith('+')
        val digits = raw.filter(Char::isDigit)
        if (digits.length < 7) return null
        val canonical = when {
            hasPlus -> digits
            digits.startsWith(defaultCountryCode) && digits.length > 10 -> digits
            digits.startsWith("0") && digits.length == 11 -> defaultCountryCode + digits.drop(1)
            digits.length == 10 -> defaultCountryCode + digits
            else -> digits
        }
        return "+$canonical"
    }
}
