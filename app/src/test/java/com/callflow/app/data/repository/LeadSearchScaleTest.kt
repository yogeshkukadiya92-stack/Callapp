package com.callflow.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.callflow.app.data.local.CallFlowDatabase
import com.callflow.app.data.local.LeadEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LeadSearchScaleTest {
    private lateinit var database: CallFlowDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CallFlowDatabase::class.java).build()
    }

    @After fun tearDown() = database.close()

    @Test fun tenThousandLeadQueueAndSearchStayBounded() = runTest {
        val now = 1_777_000_000_000L
        database.dao().upsertLeads((0 until 10_000).map { index ->
            LeadEntity(
                id = "lead-$index",
                serverId = "server-$index",
                name = if (index == 9_999) "Unique Prospect Zenith" else "Prospect $index",
                company = "Company ${index % 250}",
                city = "City ${index % 50}",
                normalizedPhone = "+919${index.toString().padStart(9, '0')}",
                displayPhone = "+91 9${index.toString().padStart(9, '0')}",
                stageId = "new",
                assignedUserId = "employee",
                campaignId = "campaign",
                nextFollowUpAt = now + index,
                updatedAt = now + index,
                updatedBy = "seed",
                version = 1,
            )
        })

        assertEquals(50, database.dao().observeCallingQueue().first().size)
        var results = emptyList<LeadEntity>()
        val elapsed = measureTimeMillis { results = database.dao().searchLeads("Unique Prospect Zenith", "").first() }

        assertEquals(listOf("lead-9999"), results.map { it.id })
        assertTrue("10k lead search took ${elapsed}ms", elapsed < 2_000)
    }
}
