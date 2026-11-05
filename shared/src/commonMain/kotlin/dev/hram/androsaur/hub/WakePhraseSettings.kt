package dev.hram.androsaur.hub

/** Policy for the audio setting; no dependency on the audio APK or on Android. */
object WakePhraseSettings {
    const val DEFAULT = "зюзя"

    /** First connection leaves a local choice alone. Reset after a hub override restores the default. */
    fun target(fromHub: String?, wasManaged: Boolean): String? = fromHub ?: DEFAULT.takeIf { wasManaged }

    fun supportsPhrase(engine: String): Boolean = engine in setOf("vosk", "vosk-grammar", "kws:zyuzya")

    /** A stored phrase alone does not prove the recognizer has finished loading it. */
    fun applied(target: String, phrase: String, engine: String, state: String, listening: Boolean): Boolean =
        target == phrase && supportsPhrase(engine) && state == "ready" && listening
}
