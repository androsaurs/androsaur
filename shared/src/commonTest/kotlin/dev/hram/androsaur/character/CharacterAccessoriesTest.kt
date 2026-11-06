package dev.hram.androsaur.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class CharacterAccessoriesTest {
    @Test fun everyAvailableAccessoryMapsToItsOwnRigsCode() {
        assertEquals(4, CharacterId.PUF.outfitCode(CharacterAccessory.BOW))
        assertEquals(2, CharacterId.HAMSTER.outfitCode(CharacterAccessory.BOW))
        assertEquals(1, CharacterId.PUF.outfitCode(CharacterAccessory.SCARF))
        assertEquals(2, CharacterId.PUF.outfitCode(CharacterAccessory.CROWN))
        assertEquals(1, CharacterId.HAMSTER.outfitCode(CharacterAccessory.SEED))
        val choices = PersonalAccessories()
        assertFailsWith<IllegalArgumentException> { choices.choose("katya", CharacterId.PUF, CharacterAccessory.SEED) }
        assertFailsWith<IllegalArgumentException> { choices.choose("katya", CharacterId.HAMSTER, CharacterAccessory.SCARF) }
        assertTrue(choices.pending.isEmpty())
    }

    @Test fun removingOneAccessoryDoesNotRemoveAnotherAndClearAllDoes() {
        assertEquals(CharacterAccessory.GLASSES, accessoryTarget(CharacterAccessory.GLASSES, CharacterAccessory.BOW, false))
        assertEquals(CharacterAccessory.NONE, accessoryTarget(CharacterAccessory.BOW, CharacterAccessory.BOW, false))
        assertEquals(CharacterAccessory.NONE, accessoryTarget(CharacterAccessory.SEED, CharacterAccessory.NONE, false))
        assertEquals(CharacterAccessory.CROWN, accessoryTarget(CharacterAccessory.GLASSES, CharacterAccessory.CROWN, true))
    }

    @Test fun newAccessoriesSurviveRestartAndOnlyLatestEditOfOneOutfitIsQueued() {
        val choices = PersonalAccessories()
        val bow = choices.choose("katya", CharacterId.PUF, CharacterAccessory.BOW)
        choices.choose("katya", CharacterId.PUF, CharacterAccessory.CROWN)
        val restored = PersonalAccessories(choices.values, choices.pending)
        restored.merge(mapOf("katya" to mapOf("puf" to "glasses")))
        assertEquals(CharacterAccessory.CROWN, restored.forPerson("katya", CharacterId.PUF))
        assertFalse(restored.acknowledge(bow))
        assertEquals(1, restored.pending.size)
    }
    @Test fun nativeGlassesCodesAreSupportedOnlyByTheTwoRigs() {
        for (id in CharacterId.entries) {
            assertEquals(if (id in listOf(CharacterId.PUF, CharacterId.HAMSTER)) 3 else 0, id.outfitCode(CharacterAccessory.GLASSES))
            assertEquals(0, id.outfitCode(CharacterAccessory.NONE))
        }
    }

    @Test fun offlineEditSurvivesRestartAndStaleRefreshUntilAcknowledged() {
        val choices = PersonalAccessories()
        choices.choose("katya", CharacterId.PUF, CharacterAccessory.GLASSES)
        val restored = PersonalAccessories(choices.values, choices.pending)
        restored.merge(mapOf("katya" to mapOf("puf" to "none")))
        assertEquals(CharacterAccessory.GLASSES, restored.forPerson("katya", CharacterId.PUF))
        assertEquals(1, restored.pending.size)
        assertTrue(restored.acknowledge(restored.pending.single()))
        restored.merge(mapOf("katya" to mapOf("puf" to "none")))
        assertEquals(CharacterAccessory.NONE, restored.forPerson("katya", CharacterId.PUF))
    }

    @Test fun staleAcknowledgementDoesNotEatTheLatestCommandOrAnABAEdit() {
        val choices = PersonalAccessories()
        val old = choices.choose("katya", CharacterId.PUF, CharacterAccessory.GLASSES)
        choices.choose("katya", CharacterId.PUF, CharacterAccessory.NONE)
        assertFalse(choices.acknowledge(old))
        assertEquals(CharacterAccessory.NONE, choices.forPerson("katya", CharacterId.PUF))
        val latest = choices.choose("katya", CharacterId.PUF, CharacterAccessory.GLASSES)
        assertFalse(choices.acknowledge(old))
        assertEquals(listOf(latest), choices.pending)
        assertTrue(choices.acknowledge(latest))
    }

    @Test fun peopleAndCharactersHaveIndependentPreferences() {
        val choices = PersonalAccessories()
        choices.choose("katya", CharacterId.PUF, CharacterAccessory.GLASSES)
        choices.choose("papa", CharacterId.HAMSTER, CharacterAccessory.GLASSES)
        choices.merge(emptyMap())
        assertEquals(CharacterAccessory.NONE, choices.forPerson("katya", CharacterId.HAMSTER))
        assertEquals(CharacterAccessory.NONE, choices.forPerson(null, CharacterId.PUF))
        assertEquals(CharacterAccessory.GLASSES, choices.forPerson("papa", CharacterId.HAMSTER))
        assertEquals(CharacterAccessory.GLASSES, choices.forPerson("katya", CharacterId.PUF))
        assertEquals(2, choices.pending.size)
    }

    @Test fun penguinHasEveryOutfitOfItsRig() {
        val codes = listOf(
            CharacterAccessory.NONE to 0, CharacterAccessory.BOBBLE_HAT to 1, CharacterAccessory.HEADPHONES to 2,
            CharacterAccessory.SCARF to 3, CharacterAccessory.CAP to 4, CharacterAccessory.HOODIE to 5,
        )
        for ((accessory, code) in codes) {
            assertEquals(code, CharacterId.PENGUIN.outfitCode(accessory), accessory.id)
            assertEquals(true, CharacterId.PENGUIN.supportsAccessory(accessory), accessory.id)
        }
        assertEquals(CharacterId.PENGUIN, CharacterId.restore("penguin"))
        // The second penguin has the same rig contract and the same outfits.
        assertEquals(CharacterId.PENGUIN2, CharacterId.restore("penguin2"))
        for ((accessory, code) in codes) assertEquals(code, CharacterId.PENGUIN2.outfitCode(accessory), accessory.id)
        assertEquals(true, CharacterId.PENGUIN2.hasAccessories)
        assertEquals(CharacterAccessory.HOODIE, CharacterAccessory.restore("hoodie"))
        for (other in listOf(CharacterAccessory.GLASSES, CharacterAccessory.BOW, CharacterAccessory.CROWN, CharacterAccessory.SEED))
            assertEquals(false, CharacterId.PENGUIN.supportsAccessory(other), other.id)
        // Their outfits are the penguin's only.
        for (id in CharacterId.entries.filter { it != CharacterId.PENGUIN && it != CharacterId.PENGUIN2 })
            for (hat in listOf(CharacterAccessory.BOBBLE_HAT, CharacterAccessory.HEADPHONES, CharacterAccessory.CAP, CharacterAccessory.HOODIE))
                assertEquals(false, id.supportsAccessory(hat), "${id.id} ${hat.id}")
    }
}
