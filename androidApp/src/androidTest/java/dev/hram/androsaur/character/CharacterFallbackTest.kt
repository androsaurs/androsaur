package dev.hram.androsaur.character

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class CharacterFallbackTest {
    @get:Rule val ui = createComposeRule()

    @Test fun missingOrUnsupportedRigShowsTheSimpleFaceAndReportsTheFailure() {
        var error: String? = null
        ui.setContent {
            RiveCharacter(CharacterState(), { error = it }, Modifier.fillMaxSize(), load = { throw IllegalArgumentException("Invalid rig") })
        }
        ui.onNodeWithTag("character-simple").assertIsDisplayed()
        ui.runOnIdle { assertNotNull(error) }
    }
}
