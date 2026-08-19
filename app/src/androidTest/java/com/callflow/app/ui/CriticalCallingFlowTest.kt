package com.callflow.app.ui

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callflow.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CriticalCallingFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginHomeQueueAndLeadDetailRemainUsable() {
        completeOnboardingAndLogin()
        compose.onNodeWithText("Good Morning").assertIsDisplayed()
        openAnitaLeadDetail()
        compose.onNodeWithText("CALL NOW", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Timeline").assertIsDisplayed()
    }

    @Test
    fun manualFallbackCallDispositionAndSaveNextRemainUsableWithoutDialerRole() {
        completeOnboardingAndLogin()
        openAnitaLeadDetail()
        compose.onNodeWithText("CALL NOW", substring = true).performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Ready to call").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Automatic call tracking is off").assertIsDisplayed()
        compose.onNodeWithText("You can continue manually if you decline.", substring = true).assertIsDisplayed()

        Intents.init()
        intending(hasAction(Intent.ACTION_DIAL)).respondWith(ActivityResult(Activity.RESULT_CANCELED, null))
        compose.onNodeWithText("CALL NOW", substring = true).performClick()
        intended(hasAction(Intent.ACTION_DIAL))
        Intents.release()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("CALL RESULT").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Follow-up") and hasClickAction()).performClick()
        compose.onNodeWithText("Tomorrow").performClick()
        compose.onNodeWithText("Notes").performTextInput("Send pricing details")
        compose.onNodeWithText("SAVE & NEXT").performClick()

        compose.waitUntil(8_000) { compose.onAllNodesWithText("Anita Sharma").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Anita Sharma").assertIsDisplayed()
        compose.onNodeWithText("FOLLOW UP").assertIsDisplayed()
    }

    private fun completeOnboardingAndLogin() {
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Welcome to CallFlow").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("NEXT").performClick()
        compose.onNodeWithText("NEXT").performClick()
        compose.onNodeWithText("CONTINUE TO SIGN IN").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Mobile number or email").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mobile number or email").performTextInput("caller@example.com")
        compose.onNodeWithText("Password").performTextInput("demo1234")
        compose.onNodeWithText("SIGN IN").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Good Morning").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openAnitaLeadDetail() {
        compose.onNodeWithText("START CALLING", substring = true).performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Anita Sharma").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Anita Sharma", substring = true) and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("Anita Sharma").performClick()
    }
}
