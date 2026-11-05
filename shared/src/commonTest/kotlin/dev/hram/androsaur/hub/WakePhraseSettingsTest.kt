package dev.hram.androsaur.hub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WakePhraseSettingsTest {
    @Test fun firstConnectionDoesNotReplaceTheLocalPhrase() {
        assertNull(WakePhraseSettings.target(null, false))
    }

    @Test fun hubOverrideAndResetHaveDifferentSemanticsFromFirstConnection() {
        assertEquals("зюзя", WakePhraseSettings.target("зюзя", false))
        assertEquals("железяка~1", WakePhraseSettings.target("железяка~1", true))
        assertEquals("зюзя", WakePhraseSettings.target(null, true))
    }

    @Test fun receiptMustNotBeAcknowledgedUntilTheActualEngineIsReady() {
        assertFalse(WakePhraseSettings.applied("зюзя", "железяка", "vosk", "ready", true))
        assertFalse(WakePhraseSettings.applied("зюзя", "зюзя", "vosk", "loading", true))
        assertFalse(WakePhraseSettings.applied("зюзя", "зюзя", "vosk", "ready", false))
        assertFalse(WakePhraseSettings.applied("зюзя", "зюзя", "mww:okay_nabu", "ready", true))
        assertTrue(WakePhraseSettings.applied("зюзя", "зюзя", "vosk", "ready", true))
        assertTrue(WakePhraseSettings.applied("зюзя", "зюзя", "vosk-grammar", "ready", true))
        assertTrue(WakePhraseSettings.applied("зюзя", "зюзя", "kws:zyuzya", "ready", true))
        assertFalse(WakePhraseSettings.applied("зюзя", "зюзя", "kws:zyuzya", "loading", true))
    }
}
