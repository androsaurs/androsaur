package dev.hram.androsaur.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ThingsTest {
    @Test fun theAnswerRepeatsWhatWasKeptOrFound() {
        assertEquals("Запомнил: батарейки в ящике.", ThingAnswers.forReply(ThingReply("saved", "батарейки в ящике")))
        assertEquals("Нашёл: батарейки в ящике.", ThingAnswers.forReply(ThingReply("found", "батарейки в ящике")))
        assertEquals("Обновил: батарейки в нижнем ящике. Раньше было: батарейки в верхнем ящике.",
            ThingAnswers.forReply(ThingReply("updated", "батарейки в нижнем ящике", "батарейки в верхнем ящике")))
        assertEquals("Не нашёл: пылесос. Скажи «запомни», и я запишу.", ThingAnswers.forReply(ThingReply("not_found", "пылесос")))
    }

    @Test fun forgettingNamesWhatWasPutAsideOrAsksToBeMoreExact() {
        assertEquals("Забыл: купить макароны.", ThingAnswers.forReply(ThingReply("forgotten", "купить макароны")))
        assertEquals("Таких записей несколько: купить макароны; макароны в ящике. Скажи точнее.",
            ThingAnswers.forReply(ThingReply("ambiguous", "купить макароны; макароны в ящике")))
        assertEquals("Нечего забывать: доктора.", ThingAnswers.forReply(ThingReply("nothing_to_forget", "доктора")))
    }

    @Test fun noMemoryAndNoHubAreTheSameWordsAndAnUnknownVerdictIsAFailure() {
        assertEquals("Память недоступна.", ThingAnswers.forReply(null))
        assertEquals(ThingAnswers.forReply(null), ThingAnswers.forReply(ThingReply("no_memory")))
        assertEquals("Не получилось.", ThingAnswers.forReply(ThingReply("something_new")))
        val others = listOf("unclear", "no_groq", "error").map { ThingAnswers.forReply(ThingReply(it)) }
        assertEquals(others.size, others.toSet().size)
        assertNotEquals("Запомнил.", others.first())
    }
}
