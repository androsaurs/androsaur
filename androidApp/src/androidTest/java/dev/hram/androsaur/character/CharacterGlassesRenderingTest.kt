package dev.hram.androsaur.character

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.RiveAnimationView
import org.junit.Rule
import org.junit.Test

class CharacterGlassesRenderingTest {
    @get:Rule val ui = createComposeRule()

    private fun pixels(view: RiveAnimationView?, color: Int): Int {
        val bitmap = view?.bitmap ?: return 0
        val data = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(data, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        return data.count { it == color }
    }

    @Test fun glassesBecomeVisibleAndCanBeRemovedFromBothRigs() {
        val selected = mutableStateOf<CharacterId?>(null)
        val accessory = mutableStateOf(CharacterAccessory.NONE)
        var view: RiveAnimationView? = null
        ui.setContent {
            selected.value?.let { character -> key(character) {
                val load = remember(character) { { context: Context -> loadCharacterView(context, character).also { view = it } } }
                RiveCharacter(CharacterState(), {}, Modifier.fillMaxWidth().height(600.dp), load,
                    outfit = character.outfitCode(accessory.value))
            } }
        }
        for ((character, tint) in listOf(CharacterId.PUF to 0xff3a2a5c.toInt(), CharacterId.HAMSTER to 0xff4a2e1e.toInt())) {
            ui.runOnIdle { view = null; accessory.value = CharacterAccessory.NONE; selected.value = character }
            ui.waitUntil(10_000) {
                var white = 0
                ui.runOnIdle { white = pixels(view, android.graphics.Color.WHITE) }
                white > 200
            }
            var bare = 0
            ui.runOnIdle { bare = pixels(view, tint) }
            ui.runOnIdle { accessory.value = CharacterAccessory.GLASSES }
            ui.waitUntil(10_000) {
                var glasses = 0
                var darkLenses = 0
                ui.runOnIdle {
                    glasses = pixels(view, tint)
                    darkLenses = pixels(view, 0xff11151e.toInt())
                }
                glasses > bare + 400 && darkLenses > 1500
            }
            ui.runOnIdle {
                val output = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "${character.id}-glasses.png")
                view?.bitmap?.let { bitmap ->
                    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                accessory.value = CharacterAccessory.NONE
            }
            ui.waitUntil(10_000) {
                var removed = 0
                var darkLenses = 0
                ui.runOnIdle {
                    removed = pixels(view, tint)
                    darkLenses = pixels(view, 0xff11151e.toInt())
                }
                removed < bare + 200 && darkLenses == 0
            }
        }
    }
}
