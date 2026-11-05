package dev.hram.androsaur.character

import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.AnimationState
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive
import dev.hram.androsaur.R
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterExpressionTest {
    @Test fun everyRigTransitionsDirectlyBetweenCategoriesAndCanInterruptAMix() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Rive.init(context, RendererType.Canvas)
        for (character in CharacterId.entries) {
            val resource = character.resource
            val label = context.resources.getResourceEntryName(resource)
            val file = File(context.resources.openRawResource(resource).use { it.readBytes() }, RendererType.Canvas)
            val controller = RiveFileController(autoplay = false)
            try {
                controller.setRiveFile(file, character.artboardName)
                controller.isActive = true
                controller.play("Character", isStateMachine = true)
                fun frames(count: Int): List<String> {
                    val entered = mutableListOf<String>()
                    repeat(count) {
                        controller.advance(1 / 60f)
                        for (machine in controller.playingStateMachines) {
                            entered += machine.statesChanged.filterIsInstance<AnimationState>()
                                .map { it.name }.filter { it.startsWith("expression ") }
                        }
                    }
                    return entered
                }
                frames(30)
                fun pose(code: Int): String = "expression " + if (character.outfitCount > 0)
                    listOf("NEUTRAL", "ATTENTIVE", "LISTENING", "SLEEPY", "SAD")[code] else code.toString()
                // Enumerate every non-identical pair, including NEUTRAL <-> SAD.
                for (from in 0..4) for (to in 0..4) if (from != to) {
                    controller.setNumberState("Character", "expression", from.toFloat())
                    frames(30)
                    controller.setNumberState("Character", "expression", to.toFloat())
                    assertEquals("$label: $from -> $to", listOf(pose(to)), frames(30))
                }
                controller.setNumberState("Character", "expression", 0f)
                frames(30)
                controller.setNumberState("Character", "expression", 4f)
                assertEquals("$label: start mix", listOf(pose(4)), frames(2))
                // ATTENTIVE must replace SAD before its 300 ms mix has completed.
                controller.setNumberState("Character", "expression", 1f)
                assertEquals("$label: interrupt mix", listOf(pose(1)), frames(2))
                assertEquals("$label: no delayed states", emptyList<String>(), frames(30))
            } finally {
                controller.isActive = false
                controller.release()
                file.release()
            }
        }
    }
}
