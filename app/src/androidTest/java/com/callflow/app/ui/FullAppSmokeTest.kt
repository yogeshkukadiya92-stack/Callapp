package com.callflow.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.callflow.app.BuildConfig
import com.callflow.app.MainActivity
import com.callflow.app.data.session.EncryptedSessionStore
import com.callflow.app.data.session.OnboardingStore
import com.callflow.app.data.session.StoredSession
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue

/** Run only in the isolated com.callflow.qa build with the fake backend enabled. */
class FullAppSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun allPrimaryTabsAndMoreDestinationsOpen() {
        assumeTrue(BuildConfig.APPLICATION_ID == "com.callflow.qa" && BuildConfig.USE_FAKE_BACKEND)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun shell(command: String) {
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
            }
        }
        shell("cmd role add-role-holder android.app.role.DIALER ${context.packageName}")
        listOf("READ_PHONE_STATE", "READ_CALL_LOG", "CALL_PHONE").forEach {
            shell("pm grant ${context.packageName} android.permission.$it")
        }
        context.getSharedPreferences("callflow_privacy", 0).edit().putBoolean("call_tracking_disclosure_v1", true).commit()
        runBlocking {
            OnboardingStore(context).complete()
            EncryptedSessionStore(context).save(StoredSession("qa-token", "qa-refresh", "QA Tester"))
            val db = androidx.room.Room.databaseBuilder(context, com.callflow.app.data.local.CallFlowDatabase::class.java, "callflow.db").build()
            val now = System.currentTimeMillis()
            val dao = db.dao()
            dao.upsertLeads(listOf(com.callflow.app.data.local.LeadEntity("design-lead", "design-lead", "Riya Shah", "Wellness plan", "Ahmedabad", "+919000000001", "+91 90000 00001", "hot", "local-user", null, now + 3600000, now, "local-user", 1)))
            val hasDesignCall = db.openHelper.readableDatabase.query("SELECT id FROM calls WHERE id = 'design-call'").use { it.moveToFirst() }
            if (!hasDesignCall) dao.insertCall(com.callflow.app.data.local.CallEntity("design-call", null, "design-lead", "local-user", null, "+919000000001", "OUTGOING", now - 360000, now - 350000, now - 78000, null, "SYNCED", 1, "SIM 1"))
            dao.upsertFollowUps(listOf(com.callflow.app.data.local.FollowUpEntity("design-followup", "design-lead", now + 3600000, "Discuss annual plan", 1, "local-user", "CALL", "PENDING", now, now, 1, "SYNCED")))
            db.close()
        }
        fun settle() {
            compose.waitForIdle()
            // Navigation transitions and Room emissions also run on Android's frame clock.
            Thread.sleep(700)
            compose.waitForIdle()
        }
        fun capture(name: String) {
            settle()
            compose.waitForIdle()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val file = java.io.File(context.getExternalFilesDir(null), "design-$name.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Analytics").fetchSemanticsNodes().isNotEmpty() }
            capture("home")
            compose.onNodeWithText("Missed").performClick()
            settle()
            compose.onNodeWithText("Call History").assertIsDisplayed()
            compose.onNodeWithText("Missed").assertIsDisplayed()
            compose.onNodeWithTag("nav-home").performClick()
            settle()
            compose.onNodeWithText("7 days").performClick()
            compose.onNodeWithText("30 days").performClick()
            compose.onNodeWithText("Today").performClick()
            compose.onNodeWithText("DIAL", substring = true).performClick()
            capture("dial")
            androidx.test.espresso.Espresso.pressBack()
            settle()
            compose.onNodeWithTag("nav-calls").performClick()
            settle()
            compose.onNodeWithText("Call History").assertIsDisplayed()
            capture("history")
            compose.onAllNodesWithText("Tap to add note")[0].performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Cancel").fetchSemanticsNodes().isNotEmpty() }
            capture("post-call-note")
            compose.onNodeWithText("Cancel").performClick()
            capture("call-details")
            androidx.test.espresso.Espresso.pressBack()
            settle()
            compose.onNodeWithText("Analysis", useUnmergedTree = true).performScrollTo().performClick()
            settle()
            compose.onNodeWithText("Call analysis").assertIsDisplayed()
            capture("analysis")
            compose.onAllNodesWithText("7 days")[0].performClick()
            compose.onNodeWithTag("nav-leads").performClick()
            capture("leads")
            compose.onNodeWithText("Riya Shah").performScrollTo().performClick()
            capture("lead-details")
            androidx.test.espresso.Espresso.pressBack()
            settle()
            compose.onNodeWithText("Search assigned leads").assertIsDisplayed().performTextInput("No matching QA lead")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Custom").performClick()
            compose.onNodeWithText("Custom date range").assertIsDisplayed()
            compose.onNodeWithText("CANCEL").performClick()
            compose.onNodeWithTag("nav-followups").performClick()
            capture("followups")
            compose.onNodeWithText("Overdue").performClick()
            compose.onNodeWithText("Upcoming").performClick()
            compose.onNodeWithTag("nav-more").performClick()
            capture("more")
            compose.onNodeWithText("View profile").performClick()
            capture("profile")
            androidx.test.espresso.Espresso.pressBack()
            settle()
            compose.onNodeWithText("Reports").performClick()
            capture("reports")
            androidx.test.espresso.Espresso.pressBack()
            settle()
            compose.onNodeWithText("Team Hub").performClick()
            capture("team")
            compose.onNodeWithText("Call Scripts").performClick()
            compose.onNodeWithText("Announcements").performClick()
            androidx.test.espresso.Espresso.pressBack()
            settle()
            compose.onNodeWithText("Settings").performClick()
            settle()
            compose.onNodeWithText("Setup health").assertIsDisplayed()
            capture("settings")
            androidx.test.espresso.Espresso.pressBack()
            settle()
            compose.onNodeWithTag("nav-home").performClick()
            settle()
            compose.onNodeWithText("Analytics").assertIsDisplayed()
        }
    }
}
