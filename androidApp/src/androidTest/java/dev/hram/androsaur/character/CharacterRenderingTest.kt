package dev.hram.androsaur.character

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.SMINumber
import androidx.test.platform.app.InstrumentationRegistry
import dev.hram.androsaur.face.Expression
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CharacterRenderingTest {
    @get:Rule val ui = createComposeRule()

    private fun pixels(bitmap: Bitmap?, color: Int): Int {
        if (bitmap == null) return 0
        val data = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(data, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return data.count { it == color }.also { bitmap.recycle() }
    }

    private fun coloredPixels(bitmap: Bitmap?): Int {
        if (bitmap == null) return 0
        val data = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(data, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return data.count { (it ushr 24) > 0 && (it and 0xffffff) != 0 }.also { bitmap.recycle() }
    }

    @Test fun sleepingCharacterSurvivesAnExtraRenderAndResize() {
        var view: RiveAnimationView? = null
        val state = mutableStateOf(CharacterState())
        val height = mutableStateOf(600.dp)
        val loader: (Context) -> RiveAnimationView = { loadCharacterView(it).also { view = it } }
        ui.setContent { RiveCharacter(state.value, {}, Modifier.fillMaxWidth().height(height.value), load = loader) }
        ui.waitUntil(10_000) {
            var colored = 0
            ui.runOnIdle { colored = coloredPixels(view?.bitmap) }
            colored > 2000
        }
        ui.runOnIdle { state.value = CharacterState(expression = Expression.SLEEPY, dimmed = true) }
        ui.mainClock.advanceTimeBy(700)
        ui.waitUntil(5_000) { view?.artboardRenderer?.isPlaying == false }
        // Reproduce a queued redraw after the sleep timer paused the machine.
        // With controller.isActive=false, native draw clears the surface and skips the artboard.
        ui.runOnIdle { view!!.artboardRenderer!!.start() }
        Thread.sleep(400)
        ui.runOnIdle {
            assertTrue("Sleeping character was cleared by an extra render", coloredPixels(view?.bitmap) > 2000)
        }
        for (size in listOf(450, 700, 600)) {
            ui.runOnIdle { height.value = size.dp }
            ui.waitForIdle()
            ui.waitUntil(5_000) {
                var colored = 0
                ui.runOnIdle { colored = coloredPixels(view?.bitmap) }
                colored > 2000
            }
        }
    }

    @Test fun pufRendersAndHisMouthOpensWhenSpeaking() {
        checkSpeakingRig(CharacterId.PUF, 0xff3a2a5c.toInt())
    }

    @Test fun hamsterRendersAndHisMouthOpensWhenSpeaking() {
        checkSpeakingRig(CharacterId.HAMSTER, 0xff5a2f2a.toInt())
    }

    private fun checkSpeakingRig(character: CharacterId, mouthColor: Int) {
        var view: RiveAnimationView? = null
        val state = mutableStateOf(CharacterState())
        val loader: (Context) -> RiveAnimationView = { loadCharacterView(it, character).also { loaded -> view = loaded } }
        ui.setContent { RiveCharacter(state.value, {}, Modifier.fillMaxWidth().height(600.dp), load = loader) }
        ui.waitUntil(10_000) {
            var white = 0
            ui.runOnIdle { white = pixels(view?.bitmap, android.graphics.Color.WHITE) }
            white > 200
        }
        var closed = 0
        ui.runOnIdle { closed = pixels(view?.bitmap, mouthColor) }
        ui.runOnIdle { state.value = CharacterState(speaking = true, mouthOpen = 1f) }
        ui.waitUntil(10_000) {
            var opened = 0
            ui.runOnIdle { opened = pixels(view?.bitmap, mouthColor) }
            opened > closed + 100
        }
        ui.runOnIdle {
            val output = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "${character.id}-render.png")
            view?.bitmap?.let { bitmap ->
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun adapterSendsTheCategoryWithoutAnimatingThroughOtherCodes() {
        var view: RiveAnimationView? = null
        val state = mutableStateOf(CharacterState())
        val loader: (Context) -> RiveAnimationView = { loadCharacterView(it).also { loaded -> view = loaded } }
        ui.setContent { RiveCharacter(state.value, {}, Modifier.fillMaxWidth().height(600.dp), load = loader) }
        ui.waitUntil(10_000) { view?.controller?.playingStateMachines?.isNotEmpty() == true }
        // Freeze Compose's animation clock: category changes must still reach Rive immediately.
        // With animateFloatAsState(expression, tween(300)) this stays at 0 and fails.
        ui.mainClock.autoAdvance = false
        for ((expression, code) in listOf(Expression.SAD to 4f, Expression.NEUTRAL to 0f, Expression.ATTENTIVE to 1f)) {
            ui.runOnIdle { state.value = CharacterState(expression = expression) }
            ui.mainClock.advanceTimeByFrame()
            ui.waitUntil(5_000) {
                (view?.controller?.playingStateMachines?.firstOrNull()?.input("expression") as? SMINumber)?.value == code
            }
        }
    }

    @Test fun eyesAreVisibleAndTheRenderedMouthRespondsToSpeech() {
        var view: RiveAnimationView? = null
        val state = mutableStateOf(CharacterState())
        val stageHeight = mutableStateOf(800.dp)
        val loader: (Context) -> RiveAnimationView = { loadCharacterView(it).also { view = it } }
        ui.setContent {
            RiveCharacter(state.value, {}, Modifier.fillMaxWidth().height(stageHeight.value), load = loader)
        }
        // Inspect the native texture, not just the presence of the Compose container.
        ui.waitUntil(10_000) {
            var white = 0
            ui.runOnIdle { white = pixels(view?.bitmap, android.graphics.Color.WHITE) }
            white > 200
        }
        var closed = 0
        ui.runOnIdle { closed = pixels(view?.bitmap, 0xff112a24.toInt()) }
        ui.runOnIdle { state.value = CharacterState(speaking = true, mouthOpen = 1f) }
        ui.waitUntil(10_000) {
            var opened = 0
            ui.runOnIdle { opened = pixels(view?.bitmap, 0xff112a24.toInt()) }
            opened > closed + 500
        }
        ui.runOnIdle { assertTrue(view?.controller?.isActive == true) }
        ui.runOnIdle { state.value = CharacterState(expression = Expression.SLEEPY, dimmed = true) }
        ui.mainClock.advanceTimeBy(700)
        ui.waitUntil(5_000) { view!!.controller.isActive && view!!.artboardRenderer?.isPlaying == false }
        ui.runOnIdle { state.value = CharacterState() }
        ui.waitUntil(5_000) { view!!.controller.isActive && view!!.controller.playingStateMachines.isNotEmpty() }
        // Resizing must retain the live Surface while its rendering thread finishes a frame.
        // The default Rive TextureView callback released it and crashed on Android 15.
        for (height in listOf(500, 1000, 600, 900, 800)) {
            ui.runOnIdle { stageHeight.value = height.dp }
            ui.waitForIdle()
        }
        ui.waitUntil(5_000) {
            var white = 0
            ui.runOnIdle { white = pixels(view?.bitmap, android.graphics.Color.WHITE) }
            white > 200
        }
    }
}
