package dev.hram.androsaur.character

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.rule.GrantPermissionRule
import dev.hram.androsaur.MainActivity
import org.junit.Rule
import org.junit.Test

class CharacterModeTest {
    @get:Rule val permission = GrantPermissionRule.grant(Manifest.permission.ACCESS_COARSE_LOCATION)
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun switchingAndRecreatingWorksWithoutTheHubOrModules() {
        ui.onNodeWithTag("assistant-screen").performTouchInput { click(center) }
        ui.onNodeWithText("Простой").performClick()
        ui.onNodeWithTag("character-simple", useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithText("Сложный").performClick()
        ui.onNodeWithTag("character-rive", useUnmergedTree = true).assertIsDisplayed()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("character-rive", useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithTag("assistant-screen").performTouchInput { click(center) }
        ui.onNodeWithText("Сложный").assertIsSelected()
        // Repeated disposal/recreation must release native resources without crashing.
        repeat(3) {
            ui.onNodeWithText("Простой").performClick()
            ui.onNodeWithTag("character-simple", useUnmergedTree = true).assertIsDisplayed()
            ui.onNodeWithText("Сложный").performClick()
            ui.onNodeWithTag("character-rive", useUnmergedTree = true).assertIsDisplayed()
        }
        ui.onNodeWithText("Простой").performClick()
    }
}
