package dev.hram.androsaur.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalCharacterTest {
    private val assignments = mapOf("katya" to "cat", "lesha" to "dog")

    @Test fun pufIsAPersonalChoiceAndSurvivesRestoringTheSavedId() {
        val selector = PersonalCharacter()
        selector.observe("katya", true, 100)
        assertEquals(CharacterId.PUF, selector.resolve(mapOf("katya" to "puf")))
        assertEquals(CharacterId.PUF, CharacterId.restore("puf"))
    }

    @Test fun hamsterIsAPersonalChoiceAndSurvivesRestoringTheSavedId() {
        val selector = PersonalCharacter()
        selector.observe("katya", true, 100)
        assertEquals(CharacterId.HAMSTER, selector.resolve(mapOf("katya" to "hamster")))
        assertEquals(CharacterId.HAMSTER, CharacterId.restore("hamster"))
    }

    @Test fun newerLookupCannotBeOverwrittenByAnOlderSlowSync() {
        val choices = CharacterChoices(mapOf("katya" to "dog"))
        assertTrue(choices.accept(2, mapOf("katya" to "cat")))
        assertFalse(choices.accept(1, mapOf("katya" to "dog")))
        assertEquals("cat", choices.values["katya"])
        assertTrue(choices.accept(3, emptyMap()))
        assertEquals(emptyMap(), choices.values)
    }

    @Test fun failedLookupPreservesOfflineChoiceAndDoesNotDiscardSuccessfulOlderWork() {
        val choices = CharacterChoices(assignments)
        assertFalse(choices.accept(2, null))
        assertEquals(assignments, choices.values)
        assertTrue(choices.accept(1, mapOf("katya" to "dog")))
        assertEquals("dog", choices.values["katya"])
    }

    @Test fun defaultAndUnsupportedCharactersStayUsable() {
        val selector = PersonalCharacter()
        assertEquals(CharacterId.DINOSAUR, selector.resolve(assignments))
        selector.observe("katya", true, 100)
        assertEquals(CharacterId.DINOSAUR, selector.resolve(mapOf("katya" to "future-character")))
    }

    @Test fun followsIdsAcrossDevicesAndDoesNotDependOnNames() {
        for (selector in listOf(PersonalCharacter(), PersonalCharacter())) {
            selector.observe("katya", true, 100)
            assertEquals(CharacterId.CAT, selector.resolve(assignments))
            selector.observe("lesha", true, 200)
            assertEquals(CharacterId.DOG, selector.resolve(assignments))
        }
    }

    @Test fun retainsOwnerBrieflyThenReturnsHomeForAbsenceOrStranger() {
        val selector = PersonalCharacter(graceMs = 8_000)
        selector.observe("katya", true, 100)
        selector.observe(null, true, 300)
        assertEquals(CharacterId.CAT, selector.resolve(assignments))
        selector.observe(null, false, 8_100)
        assertEquals(CharacterId.DINOSAUR, selector.resolve(assignments))
    }

    @Test fun appliesChangedOrClearedChoicesWhileTheSamePersonStays() {
        val selector = PersonalCharacter()
        selector.observe("katya", true, 100)
        assertEquals(CharacterId.CAT, selector.resolve(assignments))
        assertEquals(CharacterId.DOG, selector.resolve(mapOf("katya" to "dog")))
        assertEquals(CharacterId.DINOSAUR, selector.resolve(emptyMap()))
    }
}
