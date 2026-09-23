package com.callflow.app.telecom

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.callflow.app.core.contacts.DeviceContactResolver
import com.callflow.app.core.phone.CountryAwarePhoneNumberNormalizer
import com.callflow.app.data.local.CallFlowDatabase
import com.callflow.app.data.local.LeadEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CallerIdentityResolverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: CallFlowDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CallFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `priority 1 - resolves lead name when phone exists in leads`() = runTest {
        val lead = LeadEntity(
            id = "lead-1",
            serverId = null,
            name = "Ajmal Saiyad",
            company = "Tech Corp",
            city = "Ahmedabad",
            normalizedPhone = "+919876543210",
            displayPhone = "+91 98765 43210",
            stageId = "new",
            assignedUserId = "user-1",
            campaignId = null,
            nextFollowUpAt = null,
            updatedAt = 1000L,
            updatedBy = "user-1",
            version = 1L
        )
        db.dao().insertLead(lead)

        val fakeContacts = object : DeviceContactResolver(context) {
            override fun resolveContactName(phoneNumber: String): String? = "Phonebook Contact"
        }

        val resolver = CallerIdentityResolver(db.dao(), CountryAwarePhoneNumberNormalizer(), fakeContacts)
        val identity = resolver.resolve("+919876543210")

        assertEquals("Ajmal Saiyad", identity.name)
        assertTrue(identity.isLead)
        assertFalse(identity.isContact)
        assertEquals("Ajmal Saiyad", identity.displayHeader)
    }

    @Test
    fun `priority 2 - resolves device contact name when lead not found`() = runTest {
        val fakeContacts = object : DeviceContactResolver(context) {
            override fun resolveContactName(phoneNumber: String): String? {
                return if (phoneNumber.contains("9898449786")) "Ramesh Patel" else null
            }
        }

        val resolver = CallerIdentityResolver(db.dao(), CountryAwarePhoneNumberNormalizer(), fakeContacts)
        val identity = resolver.resolve("+919898449786")

        assertEquals("Ramesh Patel", identity.name)
        assertFalse(identity.isLead)
        assertTrue(identity.isContact)
        assertEquals("Ramesh Patel", identity.displayHeader)
    }

    @Test
    fun `priority 3 - falls back to phone number when neither lead nor contact exists`() = runTest {
        val fakeContacts = object : DeviceContactResolver(context) {
            override fun resolveContactName(phoneNumber: String): String? = null
        }

        val resolver = CallerIdentityResolver(db.dao(), CountryAwarePhoneNumberNormalizer(), fakeContacts)
        val identity = resolver.resolve("+919999999999")

        assertNull(identity.name)
        assertFalse(identity.isLead)
        assertFalse(identity.isContact)
        assertEquals("+919999999999", identity.displayHeader)
    }

    @Test
    fun `isUsableDisplayName filters out generic telecom and junk strings`() {
        assertFalse(CallUiController.isUsableDisplayName("Business call"))
        assertFalse(CallUiController.isUsableDisplayName("business call"))
        assertFalse(CallUiController.isUsableDisplayName("Unknown"))
        assertFalse(CallUiController.isUsableDisplayName("Unknown caller"))
        assertFalse(CallUiController.isUsableDisplayName("Private number"))
        assertFalse(CallUiController.isUsableDisplayName("null"))
        assertFalse(CallUiController.isUsableDisplayName(""))
        assertFalse(CallUiController.isUsableDisplayName(null))

        assertTrue(CallUiController.isUsableDisplayName("Ramesh Patel"))
        assertTrue(CallUiController.isUsableDisplayName("Ajmal Saiyad"))
        assertTrue(CallUiController.isUsableDisplayName("Prakash"))
    }
}
