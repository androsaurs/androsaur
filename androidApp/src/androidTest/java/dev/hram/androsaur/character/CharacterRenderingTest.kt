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
        ui.waitUntil(5_000) { !view!!.controller.isActive && view!!.artboardRenderer?.isPlaying == false }
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
