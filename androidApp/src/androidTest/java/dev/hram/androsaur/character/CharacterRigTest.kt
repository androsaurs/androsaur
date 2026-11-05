package dev.hram.androsaur.character

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.controllers.RiveFileController
import dev.hram.androsaur.R
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterRigTest {
    @Test fun bundledRigLoadsAndAdvancesAllExpressionsWithSpeechAndGaze() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Rive.init(context, RendererType.Canvas)
        for (character in CharacterId.entries) {
            exercise(context, character)
        }
    }

    private fun exercise(context: Context, character: CharacterId) {
        val file = File(context.resources.openRawResource(character.resource).use { it.readBytes() }, RendererType.Canvas)
        val controller = RiveFileController(autoplay = false)
        try {
            val board = file.artboard(character.artboardName)
            val machine = board.stateMachine("Character")
            val expected = setOf("lookX", "lookY", "mouthOpen", "expression", "interaction", "speaking", "sleeping") +
                if (character.outfitCount > 0) setOf("outfit") else emptySet()
            assertEquals(expected, machine.inputNames.toSet())
            controller.setRiveFile(file, character.artboardName)
            controller.isActive = true
            controller.play("Character", isStateMachine = true)
            for (expression in 0..4) for (interaction in 0..3) {
                controller.setNumberState("Character", "expression", expression.toFloat())
                controller.setNumberState("Character", "interaction", interaction.toFloat())
                controller.setNumberState("Character", "speaking", 1f)
                for (frame in 0..60) {
                    controller.setNumberState("Character", "mouthOpen", frame / 60f)
                    controller.setNumberState("Character", "lookX", frame / 30f - 1f)
                    controller.setNumberState("Character", "lookY", 1f - frame / 30f)
                    controller.advance(1 / 60f)
                }
            }
            for (outfit in 0 until character.outfitCount) {
                controller.setNumberState("Character", "outfit", outfit.toFloat())
                controller.advance(0.2f)
            }
            controller.setNumberState("Character", "sleeping", 1f)
            controller.advance(0.5f)
        } finally { controller.isActive = false; controller.release(); file.release() }
    }
}
