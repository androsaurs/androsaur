package dev.hram.androsaur.commands

/** The hub's verdict on «запомни» / «вспомни»: [status], what it made of the voice or found ([text]) and, when a record was replaced, the earlier one. */
data class ThingReply(val status: String, val text: String? = null, val previous: String? = null)

/** What the assistant says after «запомни» and «вспомни»: the answer repeats what was kept or found, so a misheard word is noticed. */
object ThingAnswers {
    fun forReply(reply: ThingReply?): String {
        val text = reply?.text?.trim().orEmpty()
        return when (reply?.status) {
            "saved" -> if (text.isEmpty()) "Запомнил." else "Запомнил: $text."
            "updated" -> when {
                text.isEmpty() -> "Обновил."
                reply.previous.isNullOrBlank() -> "Обновил: $text."
                else -> "Обновил: $text. Раньше было: ${reply.previous.trim()}."
            }
            "found" -> if (text.isEmpty()) "Нашёл." else "Нашёл: $text."
            "not_found" -> if (text.isEmpty()) "Не нашёл. Скажи «запомни», и я запишу." else "Не нашёл: $text. Скажи «запомни», и я запишу."
            "unclear" -> "Не разобрал. Скажи ещё раз."
            "no_groq" -> "Не могу расшифровать: в хабе не включён Groq."
            // null: the hub did not answer at all.
            "no_memory", null -> "Память недоступна."
            else -> "Не получилось."
        }
    }
}
