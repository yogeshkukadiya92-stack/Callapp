package com.callflow.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callflow.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CriticalCallingFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginHomeAndAssignedLeadsNavigationRemainUsable() {
        completeOnboardingAndLogin()
        compose.onNodeWithText("TODAY’S CALLS").assertIsDisplayed()
        compose.onNodeWithTag("nav-leads").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Search assigned leads").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun completeOnboardingAndLogin() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Welcome to CallFlow").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("NEXT").performClick()
        compose.onNodeWithText("NEXT").performClick()
        compose.onNodeWithText("CONTINUE TO SIGN IN").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Mobile number or email").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mobile number or email").performTextInput("caller@example.com")
        compose.onNodeWithText("Password").performTextInput("demo1234")
        compose.onNodeWithText("SIGN IN").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("TODAY’S CALLS").fetchSemanticsNodes().isNotEmpty() }
    }
}
