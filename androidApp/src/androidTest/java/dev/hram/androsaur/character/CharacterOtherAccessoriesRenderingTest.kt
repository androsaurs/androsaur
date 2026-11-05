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

class CharacterOtherAccessoriesRenderingTest {
    @get:Rule val ui = createComposeRule()

    private fun pixels(view: RiveAnimationView?, color: Int): Int {
        val bitmap = view?.bitmap ?: return 0
        val data = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(data, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        return data.count { it == color }
    }

    @Test fun bowsScarfCrownAndSeedAppearOnTheCorrectCharacterAndCanBeRemoved() {
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
        val outfits = listOf(
            Triple(CharacterId.PUF, CharacterAccessory.SCARF, 0xffff6f82.toInt()),
            Triple(CharacterId.PUF, CharacterAccessory.CROWN, 0xfff4a93b.toInt()),
            Triple(CharacterId.PUF, CharacterAccessory.BOW, 0xffff5c7a.toInt()),
            Triple(CharacterId.HAMSTER, CharacterAccessory.SEED, 0xff3a3330.toInt()),
            Triple(CharacterId.HAMSTER, CharacterAccessory.BOW, 0xffff5c7a.toInt()),
        )
        for ((character, item, tint) in outfits) {
            if (selected.value != character) {
                ui.runOnIdle { view = null; selected.value = character; accessory.value = CharacterAccessory.NONE }
                ui.waitUntil(10_000) {
                    var white = 0
                    ui.runOnIdle { white = pixels(view, android.graphics.Color.WHITE) }
                    white > 200
                }
            }
            ui.runOnIdle { accessory.value = item }
            ui.waitUntil(10_000) {
                var visible = 0
                ui.runOnIdle { visible = pixels(view, tint) }
                visible > 50
            }
            ui.runOnIdle {
                val output = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "${character.id}-${item.id}.png")
                view?.bitmap?.let { bitmap ->
                    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                accessory.value = CharacterAccessory.NONE
            }
            ui.waitUntil(10_000) {
                var visible = 0
                ui.runOnIdle { visible = pixels(view, tint) }
                visible == 0
            }
        }
    }
}
