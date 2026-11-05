package dev.hram.androsaur.character

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class PersonalCharacterRenderingTest {
    @get:Rule val ui = createComposeRule()

    @Test fun replacingPersonalRigsReleasesOldNativeViewsAndKeepsRendering() {
        val identity = mutableStateOf(CharacterId.DINOSAUR)
        ui.setContent {
            CharacterView(CharacterState(speaking = true, mouthOpen = 0.6f), CharacterMode.COMPLEX, 0L, {}, identity.value)
        }
        repeat(2) {
            for (character in CharacterId.entries) {
                ui.runOnIdle { identity.value = character }
                ui.mainClock.advanceTimeBy(700)
                ui.waitForIdle()
                ui.onNodeWithTag("character-rive", useUnmergedTree = true).assertIsDisplayed()
            }
        }
    }
}
