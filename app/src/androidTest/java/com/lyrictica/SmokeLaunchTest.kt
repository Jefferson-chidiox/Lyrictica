package com.lyrictica

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeLaunchTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun verifyAppLaunchesAndShowsHeader() {
        // Assert that the default track label is displayed (VisualizerScreen uppercases it)
        composeTestRule.onNodeWithText("NO TRACK SELECTED").assertIsDisplayed()

        // Assert that the import action is available
        composeTestRule.onNodeWithText("OPEN").assertIsDisplayed()
    }
}
