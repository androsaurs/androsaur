package dev.hram.androsaur.commands

/** What the assistant says after «передай в детскую…»: the hub's verdict on the message for a speaker, in words. */
object AnnounceAnswers {
    fun forStatus(status: String?): String = when (status) {
        "spoken" -> "Передано."
        "unclear" -> "Не разобрал сообщение. Скажи ещё раз."
        "no_groq" -> "Не могу расшифровать сообщение: в хабе не включён Groq."
        "no_home" -> "Дом недоступен."
        "unknown_room" -> "Такой комнаты больше нет."
        // null: the hub did not answer at all.
        null -> "Дом недоступен."
        else -> "Не получилось передать на колонку."
    }
}
