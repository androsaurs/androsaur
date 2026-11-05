package dev.hram.androsaur.character

import dev.hram.androsaur.face.Expression

/** A visual choice, stored locally. Persist names rather than enum ordinals. */
enum class CharacterMode {
    SIMPLE, COMPLEX;

    companion object {
        fun restore(value: String?): CharacterMode = entries.firstOrNull { it.name == value } ?: SIMPLE
    }
}

enum class Interaction { IDLE, COMMAND, RECORDING, NOTE }

/** Renderer-independent snapshot. Coordinates are screen-relative, in -1..1; mouth is in 0..1. */
data class CharacterState(
    val expression: Expression = Expression.NEUTRAL,
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    val speaking: Boolean = false,
    val mouthOpen: Float = 0f,
    val interaction: Interaction = Interaction.IDLE,
    val dimmed: Boolean = false,
) {
    val sleeping: Boolean get() = expression == Expression.SLEEPY && !speaking && interaction == Interaction.IDLE

    companion object {
        fun from(expression: Expression, gaze: Pair<Double, Double>, speaking: Boolean, mouth: Float, mode: String, dimmed: Boolean): CharacterState {
            val interaction = when (mode) {
                "command" -> Interaction.COMMAND
                "recording" -> Interaction.RECORDING
                "note" -> Interaction.NOTE
                else -> Interaction.IDLE
            }
            return CharacterState(
                expression = if (interaction == Interaction.COMMAND || interaction == Interaction.RECORDING) Expression.LISTENING else expression,
                lookX = gaze.first.toFloat().finiteClamp(-1f, 1f),
                lookY = gaze.second.toFloat().finiteClamp(-1f, 1f),
                speaking = speaking,
                mouthOpen = if (speaking) mouth.finiteClamp(0f, 1f) else 0f,
                interaction = interaction,
                dimmed = dimmed,
            )
        }

        private fun Float.finiteClamp(min: Float, max: Float) = if (isFinite()) coerceIn(min, max) else 0f
    }
}
