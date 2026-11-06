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

    @Test fun anUnknownVerdictIsAFailureNotASuccess() {
        assertEquals("Не получилось передать на колонку.", AnnounceAnswers.forStatus("something_new"))
    }
}
