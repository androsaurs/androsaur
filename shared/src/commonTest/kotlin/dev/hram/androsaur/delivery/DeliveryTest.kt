package dev.hram.androsaur.delivery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeliveryTest {
    private val aliases = mapOf("жена" to "Маша", "Мама" to "Ольга")
    private fun n(id: String, to: String, at: Long) = WaitingNote(id, to, at)

    @Test fun aNoteForTheNameAndForARoleGoToThatPerson() {
        val waiting = listOf(n("b", "маша", 20), n("a", "жена", 10), n("c", "мама", 5), n("d", "женя", 1))
        assertEquals(listOf("a", "b"), Delivery.forPerson(waiting, null, "Маша", aliases).map { it.id })
    }

    @Test fun nobodyGetsOthersNotes() {
        assertEquals(emptyList(), Delivery.forPerson(listOf(n("a", "жена", 1)), null, "Женя", aliases))
    }

    @Test fun matchingIgnoresCaseAndYo() {
        assertEquals(listOf("a"), Delivery.forPerson(listOf(n("a", "Ёлка", 1)), null, "елка", emptyMap()).map { it.id })
    }

    @Test fun gateWaitsBetweenLooksAndAfterDelivery() {
        val gate = DeliveryGate(idleCheckMs = 3000, afterDeliveryMs = 20000)
        assertTrue(gate.shouldCheck("Маша", 0))
        gate.checkedEmpty("Маша", 0)
        assertFalse(gate.shouldCheck("маша", 2999))
        assertTrue(gate.shouldCheck("Маша", 3000))
        gate.delivered("Маша", 3000)
        assertFalse(gate.shouldCheck("Маша", 22999))
        assertTrue(gate.shouldCheck("Женя", 5000))
        gate.left("Маша")
        assertTrue(gate.shouldCheck("Маша", 4000))
    }

    @Test fun aNoteForAnIdIsThePersonsWhateverTheyAreCalledNow() {
        val waiting = listOf(WaitingNote("a", "женя", 1, toId = "p1"), WaitingNote("b", "папа", 2, toId = "p2"), WaitingNote("c", "жена", 3))
        assertEquals(listOf("a"), Delivery.forPerson(waiting, "p1", "Папа", emptyMap()).map { it.id })
        assertEquals(listOf("a", "c"), Delivery.forPerson(waiting, "p1", "Папа", mapOf("жена" to "Папа")).map { it.id })
    }

    @Test fun aNoteBeforeIdsGetsTheIdOfThePersonByName() {
        val people = mapOf("p1" to "Катя", "p2" to "Лёша")
        assertEquals("p1", Delivery.idFor("катя", people))
        assertEquals(null, Delivery.idFor("жена", people))
    }
}
