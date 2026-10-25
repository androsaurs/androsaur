package dev.hram.androsaur.hub

import kotlin.test.Test
import kotlin.test.assertEquals

class RenameTest {
    @Test fun rolesFollowTheRenamedPerson() = assertEquals(
        mapOf("муж" to "Папа", "жена" to "Катя"),
        Rename.aliases(mapOf("муж" to "Женя", "жена" to "Катя"), "женя", "Папа"),
    )
}
