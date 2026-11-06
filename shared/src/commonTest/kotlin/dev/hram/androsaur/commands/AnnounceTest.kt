package dev.hram.androsaur.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AnnounceTest {
    @Test fun everyVerdictHasItsOwnWords() {
        assertEquals("Передано.", AnnounceAnswers.forStatus("spoken"))
        assertEquals("Дом недоступен.", AnnounceAnswers.forStatus(null))
        assertEquals(AnnounceAnswers.forStatus(null), AnnounceAnswers.forStatus("no_home"))
        val others = listOf("unclear", "no_groq", "unknown_room", "error").map { AnnounceAnswers.forStatus(it) }
        assertEquals(others.size, others.toSet().size)
        assertNotEquals("Передано.", others.first())
    }

    @Test fun theSenderHearsWhatWasPassedOn() {
        assertEquals("Передал на колонку Детская: «идите ужинать»", AnnounceAnswers.forStatus("spoken", "Детская", "идите ужинать"))
        assertEquals("Передал на все колонки: «обед»", AnnounceAnswers.forStatus("spoken", "все колонки", " обед "))
        assertEquals("Передано.", AnnounceAnswers.forStatus("spoken", "Детская", ""))
        assertEquals("Не разобрал сообщение. Услышал: «мм». Скажи ещё раз.", AnnounceAnswers.forStatus("unclear", "Детская", "мм"))
    }

    @Test fun anUnknownVerdictIsAFailureNotASuccess() {
        assertEquals("Не получилось передать на колонку.", AnnounceAnswers.forStatus("something_new"))
    }
}
