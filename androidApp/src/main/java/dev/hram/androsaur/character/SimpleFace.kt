package dev.hram.androsaur.character

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.hram.androsaur.face.Expression

/** The original lightweight face, with no module access or Rive dependency. */
@Composable
fun SimpleFace(state: CharacterState, modifier: Modifier = Modifier) {
    val expression = state.expression
    val eyeScale by animateFloatAsState(when (expression) { Expression.LISTENING -> 1.2f; Expression.ATTENTIVE -> 1.1f; Expression.SLEEPY -> 0.9f; Expression.SAD -> 0.95f; Expression.TIRED -> 0.95f; Expression.EXHAUSTED -> 0.9f; else -> 1f }, tween(300), label = "eye")
    val lid by animateFloatAsState(when (expression) { Expression.SLEEPY -> 0.6f; Expression.SAD -> 0.25f; Expression.TIRED -> 0.5f; Expression.EXHAUSTED -> 0.85f; else -> 0f }, tween(600), label = "lid")
    val mouthOpen by animateFloatAsState(state.mouthOpen, tween(70), label = "mouth")
    val gx by animateFloatAsState(state.lookX, tween(180), label = "gx")
    val gy by animateFloatAsState(state.lookY, tween(180), label = "gy")
    Canvas(modifier.testTag("character-simple")) {
        val c = Offset(size.width / 2, size.height * 0.42f)
        val r = minOf(size.width * 0.17f, size.height * 0.28f) * eyeScale
        val gap = size.width * 0.22f
        for (side in listOf(-1f, 1f)) {
            val eye = Offset(c.x + side * gap, c.y - size.height * 0.08f)
            drawCircle(Color.White, r, eye)
            drawCircle(Color.Black, r * 0.45f, Offset(eye.x + gx * r * 0.45f, eye.y + gy * r * 0.45f))
            if (lid > 0f) drawRect(Color.Black, Offset(eye.x - r - 1, eye.y - r - 1), Size(2 * r + 2, 2 * r * lid + 1))
        }
        val mouth = Offset(c.x, c.y + size.height * 0.26f)
        val w = size.width * 0.16f
        if (state.speaking) {
            val h = w * (0.12f + 0.8f * mouthOpen)
            drawRoundRect(Color.White, Offset(mouth.x - w * 0.5f, mouth.y - h / 2), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2))
        } else when (expression) {
            Expression.LISTENING -> drawOval(Color.White, Offset(mouth.x - w * 0.45f, mouth.y - w * 0.2f), Size(w * 0.9f, w * 0.8f))
            Expression.ATTENTIVE -> drawArc(Color.White, 20f, 140f, false, Offset(mouth.x - w, mouth.y - w * 0.7f), Size(2 * w, 1.4f * w), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            Expression.SAD, Expression.TIRED, Expression.EXHAUSTED -> drawArc(Color.White, 200f, 140f, false, Offset(mouth.x - w, mouth.y - w * 0.2f), Size(2 * w, 1.4f * w), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            else -> drawLine(Color.White, Offset(mouth.x - w * 0.6f, mouth.y), Offset(mouth.x + w * 0.6f, mouth.y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
