package dev.hram.androsaur.commands

/** Shared contract, independent of the provider and Android. Expiry is the hub's next midnight. */
data class DailyHoroscope(
    val person: String,
    val sign: String,
    val signName: String,
    val text: String,
    val sourceName: String,
    val fetchedAtMs: Long,
    val validUntilMs: Long,
    val sourceSpeechName: String = sourceName,
) {
    fun usableFor(person: String, nowMs: Long): Boolean =
        this.person == person && sign.isNotBlank() && signName.isNotBlank() && text.isNotBlank() &&
            text.length <= 3000 && sourceName.isNotBlank() &&
            nowMs >= fetchedAtMs && nowMs < validUntilMs && validUntilMs - fetchedAtMs <= 26 * 3_600_000L

    fun spoken(): String = "Гороскоп на сегодня для знака $signName, по данным ${sourceSpeechName.ifBlank { sourceName }}. $text"
}

object HoroscopeAnswers {
    fun problem(status: String): String = when (status) {
        "unknown_person" -> "Посмотри в камеру, чтобы я узнал тебя."
        "sign_missing" -> "Укажи свой знак зодиака в хабе, в разделе Люди."
        "disabled" -> "Гороскоп выключен. Выбери источник в интеграциях хаба."
        else -> "Сейчас не удалось получить гороскоп на сегодня. Попробуй чуть позже."
    }
}
