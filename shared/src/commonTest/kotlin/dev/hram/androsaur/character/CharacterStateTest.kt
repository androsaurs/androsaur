package dev.hram.androsaur.character

import dev.hram.androsaur.face.Expression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterStateTest {
    private fun state(expression: Expression = Expression.NEUTRAL, mode: String = "", speaking: Boolean = false, mouth: Float = 0f) =
        CharacterState.from(expression, 0.5 to -0.5, speaking, mouth, mode, dimmed = false)

    @Test fun aCommandKeepsListeningAfterTheWakeWordFlashHasExpired() {
        val visual = state(Expression.NEUTRAL, "command")
        assertEquals(Expression.LISTENING, visual.expression)
        assertEquals(Interaction.COMMAND, visual.interaction)
        assertFalse(visual.sleeping)
        assertEquals(Expression.LISTENING, state(Expression.SLEEPY, "recording").expression)
    }

    @Test fun silentMouthClosesAndInvalidModuleValuesCannotReachTheRig() {
        assertEquals(0f, state(mouth = 0.9f).mouthOpen)
        assertEquals(1f, state(speaking = true, mouth = 3f).mouthOpen)
        val visual = CharacterState.from(Expression.NEUTRAL, Double.NaN to 9.0, true, Float.NaN, "unknown", false)
        assertEquals(0f, visual.lookX)
        assertEquals(1f, visual.lookY)
        assertEquals(0f, visual.mouthOpen)
        assertEquals(Interaction.IDLE, visual.interaction)
    }

    @Test fun speechAndPlaybackPreventTheSleepingRendererFromFreezing() {
        assertTrue(state(Expression.SLEEPY).sleeping)
        assertFalse(state(Expression.SLEEPY, speaking = true).sleeping)
        assertFalse(state(Expression.SLEEPY, mode = "note").sleeping)
        assertEquals(Interaction.NOTE, state(mode = "note").interaction)
    }

    @Test fun modeSurvivesRestartAndUnknownSavedValuesUseSimple() {
        for (mode in CharacterMode.entries) assertEquals(mode, CharacterMode.restore(mode.name))
        assertEquals(CharacterMode.SIMPLE, CharacterMode.restore(null))
        assertEquals(CharacterMode.SIMPLE, CharacterMode.restore("future-mode"))
    }
}
