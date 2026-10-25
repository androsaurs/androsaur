package dev.hram.androsaur.hub

import dev.hram.androsaur.face.Reflexes

/** A whole-number setting this app lets the hub change; the hub shows it from this description and checks the range itself too. */
data class IntSetting(
    val module: String,
    val key: String,
    val title: String,
    val description: String,
    val unit: String,
    val min: Long,
    val max: Long,
    val default: Long,
) {
    /** The value to use: the one the hub set if it is in range, otherwise the default (no hub, reset in the hub, or a bad value). */
    fun resolve(fromHub: Long?): Long = fromHub?.takeIf { it in min..max } ?: default
}

/** The settings of the main app that the hub manages. The face timings are the first ones. */
object HubSettings {
    val DIM_AFTER = IntSetting(
        module = "androsaur",
        key = "dim_after_s",
        title = "Затемнение экрана, когда никого нет",
        description = "Через сколько секунд без людей, голоса и касаний лицо засыпает и экран гаснет до минимума.",
        unit = "с",
        min = 5,
        max = 3600,
        default = Reflexes.DIM_AFTER_MS / 1000,
    )

    val GREETING = IntSetting(
        module = "androsaur",
        key = "greeting",
        title = "Приветствие при первой встрече за день",
        description = "1 — Петя здоровается с узнанным человеком, когда видит его впервые за день, и говорит погоду; 0 — молчит.",
        unit = "",
        min = 0,
        max = 1,
        default = 1,
    )

    val all = listOf(DIM_AFTER, GREETING)
}
