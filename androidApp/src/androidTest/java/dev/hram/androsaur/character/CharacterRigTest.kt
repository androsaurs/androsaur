package dev.hram.androsaur.character

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
        val file = File(context.resources.openRawResource(R.raw.androsaur_character).use { it.readBytes() }, RendererType.Canvas)
        val controller = RiveFileController(autoplay = false)
        try {
            val board = file.artboard("Androsaur")
            val machine = board.stateMachine("Character")
            assertEquals(setOf("lookX", "lookY", "mouthOpen", "expression", "interaction", "speaking", "sleeping"), machine.inputNames.toSet())
            controller.setRiveFile(file, "Androsaur")
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
            controller.setNumberState("Character", "sleeping", 1f)
            controller.advance(0.5f)
        } finally { controller.isActive = false; controller.release(); file.release() }
    }
}
