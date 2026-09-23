package com.callflow.app.telecom

import com.callflow.app.core.contacts.DeviceContactResolver
import com.callflow.app.core.phone.PhoneNumberNormalizer
import com.callflow.app.data.local.CallFlowDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CallerIdentity(
    val name: String?,
    val phoneNumber: String,
    val isLead: Boolean = false,
    val isContact: Boolean = false,
) {
    val displayHeader: String get() = name?.takeIf(String::isNotBlank) ?: phoneNumber.ifBlank { "Calling…" }
}

@Singleton
class CallerIdentityResolver @Inject constructor(
    private val dao: CallFlowDao,
    private val normalizer: PhoneNumberNormalizer,
    private val contacts: DeviceContactResolver,
) {
    suspend fun resolve(rawPhone: String): CallerIdentity {
        val trimmed = rawPhone.trim()
        if (trimmed.isBlank()) {
            return CallerIdentity(name = null, phoneNumber = "")
        }
        val normalized = normalizer.normalize(trimmed) ?: trimmed.filter(Char::isDigit)

        // 1. Priority 1: Check CRM Leads
        val leadName = dao.findByPhone(normalized).firstOrNull()?.name
            ?: dao.findByPhone(trimmed).firstOrNull()?.name
        if (!leadName.isNullOrBlank()) {
            return CallerIdentity(name = leadName, phoneNumber = trimmed, isLead = true)
        }

        // 2. Priority 2: Check device Contacts
        val contactName = withContext(Dispatchers.IO) {
            contacts.resolveContactName(trimmed)
                ?: (if (normalized.isNotBlank() && normalized != trimmed) contacts.resolveContactName(normalized) else null)
        }
        if (!contactName.isNullOrBlank()) {
            return CallerIdentity(name = contactName, phoneNumber = trimmed, isContact = true)
        }

        // 3. Priority 3: Fallback to normal phone number
        return CallerIdentity(name = null, phoneNumber = trimmed)
    }
}
