package dev.hram.androsaur.commands

/**
 * What the assistant says after «передай в детскую…»: the hub's verdict on the message for a speaker, in words.
 * [text] is what the hub made of the voice and [room] is where it went: the sender hears them, so a misheard word is noticed.
 */
object AnnounceAnswers {
    fun forStatus(status: String?, room: String? = null, text: String? = null): String = when (status) {
        "spoken" -> if (text.isNullOrBlank()) "Передано." else "Передал ${where(room)}: «${text.trim()}»"
        "unclear" -> if (text.isNullOrBlank()) "Не разобрал сообщение. Скажи ещё раз." else "Не разобрал сообщение. Услышал: «${text.trim()}». Скажи ещё раз."
        "no_groq" -> "Не могу расшифровать сообщение: в хабе не включён Groq."
        "no_home" -> "Дом недоступен."
        "unknown_room" -> "Такой комнаты больше нет."
        // null: the hub did not answer at all.
        null -> "Дом недоступен."
        else -> "Не получилось передать на колонку."
    }

    private fun where(room: String?): String = when {
        room.isNullOrBlank() -> "на колонку"
        room.startsWith("все") -> "на $room"
        else -> "на колонку $room"
    }
}
