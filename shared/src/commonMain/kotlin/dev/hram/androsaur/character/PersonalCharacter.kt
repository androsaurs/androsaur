package dev.hram.androsaur.character

enum class CharacterId(val id: String, val title: String) {
    DINOSAUR("dinosaur", "Динозаврик"), CAT("cat", "Кошечка"), DOG("dog", "Собачка"), PUF("puf", "Пуф"),
    HAMSTER("hamster", "Хомяк");

    companion object {
        fun restore(id: String?): CharacterId = entries.firstOrNull { it.id == id } ?: DINOSAUR
    }
}

/** Callers serialize updates. A slow older sync must not undo a newer direct lookup. */
class CharacterChoices(initial: Map<String, String> = emptyMap()) {
    var values: Map<String, String> = initial.toMap(); private set
    private var acceptedRequest = 0L

    fun accept(request: Long, choices: Map<String, String>?): Boolean {
        if (choices == null || request <= acceptedRequest) return false
        values = choices.toMap()
        acceptedRequest = request
        return true
    }
}

/** A brief lost face must not switch rigs. Recognition of another person replaces the owner. */
class PersonalCharacter(private val graceMs: Long = 8_000) {
    var person: String? = null; private set
    private var lastSeen = 0L

    fun observe(personId: String?, present: Boolean, now: Long) {
        if (present && !personId.isNullOrBlank()) {
            person = personId
            lastSeen = now
        } else if (now - lastSeen >= graceMs) person = null
    }

    fun resolve(assignments: Map<String, String>): CharacterId = CharacterId.restore(assignments[person])
}
