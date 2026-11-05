package dev.hram.androsaur.character

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.hram.androsaur.face.Reflexes

/** Common stage: burn-in protection applies to both renderers. Captions belong to the screen. */
@Composable
fun CharacterView(state: CharacterState, mode: CharacterMode, now: Long, onError: (String?) -> Unit) {
    val (dx, dy) = Reflexes.drift(now)
    val driftX by animateFloatAsState(dx.toFloat(), tween(4000), label = "character dx")
    val driftY by animateFloatAsState(dy.toFloat(), tween(4000), label = "character dy")
    Box(Modifier.fillMaxSize().offset { IntOffset((driftX * 14.dp.toPx()).toInt(), (driftY * 14.dp.toPx()).toInt()) }) {
        when (mode) {
            CharacterMode.SIMPLE -> SimpleFace(state, Modifier.fillMaxSize())
            CharacterMode.COMPLEX -> RiveCharacter(state, onError, Modifier.fillMaxSize())
        }
    }
}
