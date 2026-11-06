package dev.hram.androsaur.character

enum class CharacterAccessory(val id: String) {
    NONE("none"), GLASSES("glasses"), BOW("bow"), SCARF("scarf"), CROWN("crown"), SEED("seed"),
    BOBBLE_HAT("bobble_hat"), HEADPHONES("headphones"), CAP("cap"), HOODIE("hoodie");
    companion object {
        fun restore(id: String?): CharacterAccessory = entries.firstOrNull { it.id == id } ?: NONE
    }
}

val CharacterId.hasAccessories: Boolean get() = this == CharacterId.PUF || this == CharacterId.HAMSTER || this == CharacterId.PENGUIN || this == CharacterId.PENGUIN2
val CharacterId.supportedAccessories: Set<CharacterAccessory> get() = when (this) {
    CharacterId.PUF -> setOf(CharacterAccessory.NONE, CharacterAccessory.GLASSES, CharacterAccessory.BOW, CharacterAccessory.SCARF, CharacterAccessory.CROWN)
    CharacterId.HAMSTER -> setOf(CharacterAccessory.NONE, CharacterAccessory.GLASSES, CharacterAccessory.BOW, CharacterAccessory.SEED)
    CharacterId.PENGUIN, CharacterId.PENGUIN2 -> setOf(
        CharacterAccessory.NONE, CharacterAccessory.BOBBLE_HAT, CharacterAccessory.HEADPHONES,
        CharacterAccessory.SCARF, CharacterAccessory.CAP, CharacterAccessory.HOODIE,
    )
    else -> setOf(CharacterAccessory.NONE)
}
fun CharacterId.supportsAccessory(accessory: CharacterAccessory): Boolean = accessory in supportedAccessories
fun CharacterId.outfitCode(accessory: CharacterAccessory): Int = when (this) {
    CharacterId.PUF -> when (accessory) {
        CharacterAccessory.SCARF -> 1; CharacterAccessory.CROWN -> 2
        CharacterAccessory.GLASSES -> 3; CharacterAccessory.BOW -> 4; else -> 0
    }
    CharacterId.HAMSTER -> when (accessory) {
        CharacterAccessory.SEED -> 1; CharacterAccessory.BOW -> 2
        CharacterAccessory.GLASSES -> 3; else -> 0
    }
    // The penguin rig's `outfit` input: 1 bobble hat, 2 headphones, 3 scarf, 4 cap, 5 hoodie.
    CharacterId.PENGUIN, CharacterId.PENGUIN2 -> when (accessory) {
        CharacterAccessory.BOBBLE_HAT -> 1; CharacterAccessory.HEADPHONES -> 2; CharacterAccessory.SCARF -> 3
        CharacterAccessory.CAP -> 4; CharacterAccessory.HOODIE -> 5; else -> 0
    }
    else -> 0
}

/** Removing a named accessory must not remove another one that is currently worn. */
fun accessoryTarget(current: CharacterAccessory, requested: CharacterAccessory, wear: Boolean): CharacterAccessory =
    if (wear) requested else if (requested == CharacterAccessory.NONE || requested == current) CharacterAccessory.NONE else current

data class AccessoryEdit(val person: String, val character: String, val accessory: CharacterAccessory, val revision: Long)

/** Callers serialize access. Pending local edits survive remote refreshes and stale acknowledgements. */
class PersonalAccessories(
    initial: Map<String, Map<String, String>> = emptyMap(),
    pending: List<AccessoryEdit> = emptyList(),
) {
    var values: Map<String, Map<String, String>> = initial.mapValues { it.value.toMap() }; private set
    private val edits = pending.associateBy { it.person to it.character }.toMutableMap()
    private var revision = pending.maxOfOrNull { it.revision } ?: 0L
    val pending: List<AccessoryEdit> get() = edits.values.toList()

    init { overlay() }

    fun forPerson(person: String?, character: CharacterId): CharacterAccessory =
        CharacterAccessory.restore(values[person]?.get(character.id)).takeIf { character.supportsAccessory(it) } ?: CharacterAccessory.NONE

    fun choose(person: String, character: CharacterId, accessory: CharacterAccessory): AccessoryEdit {
        require(person.isNotBlank() && character.hasAccessories && character.supportsAccessory(accessory))
        val edit = AccessoryEdit(person, character.id, accessory, ++revision)
        edits[person to character.id] = edit
        overlay()
        return edit
    }

    fun merge(remote: Map<String, Map<String, String>>?) {
        if (remote != null) values = remote.mapValues { it.value.toMap() }
        overlay()
    }

    /** An old response must not consume a newer edit, including glasses -> none -> glasses. */
    fun acknowledge(edit: AccessoryEdit): Boolean {
        val key = edit.person to edit.character
        if (edits[key] != edit) return false
        edits.remove(key)
        return true
    }

    private fun overlay() {
        val merged = values.mapValues { it.value.toMutableMap() }.toMutableMap()
        for (edit in edits.values) merged.getOrPut(edit.person) { mutableMapOf() }[edit.character] = edit.accessory.id
        values = merged.mapValues { it.value.toMap() }
    }
}
