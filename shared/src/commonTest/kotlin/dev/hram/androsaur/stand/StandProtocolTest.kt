package dev.hram.androsaur.stand

import kotlin.test.*

class StandProtocolTest {
    private val a = StandIdentity("stand-112233445566", "a".repeat(64))
    @Test fun rejectsReplacementUntilExplicitUnpairing() {
        assertTrue(StandProtocol.mayClaim(null, a))
        assertTrue(StandProtocol.mayClaim(a, a))
        assertFalse(StandProtocol.mayClaim(a, a.copy(id = "stand-112233445567")))
        assertFalse(StandProtocol.mayClaim(a, a.copy(fingerprint = "b".repeat(64))))
    }
    @Test fun rejectsDowngradeAndMissingProofOfPossession() {
        assertTrue(StandProtocol.secureProvisioning(2, emptyList(), "a".repeat(32), 1))
        assertFalse(StandProtocol.secureProvisioning(2, emptyList(), "a".repeat(32), 0))
        assertFalse(StandProtocol.secureProvisioning(2, listOf("no_pop"), "a".repeat(32), 1))
        assertFalse(StandProtocol.secureProvisioning(0, emptyList(), "a".repeat(32)))
        assertFalse(StandProtocol.secureProvisioning(1, listOf("no_pop"), "a".repeat(32)))
        assertFalse(StandProtocol.secureProvisioning(1, emptyList(), ""))
    }
    @Test fun rejectsMalformedIdentity() {
        assertFailsWith<IllegalArgumentException> { StandIdentity("192.168.1.10", a.fingerprint) }
        assertFailsWith<IllegalArgumentException> { StandIdentity(a.id, "short") }
    }
    @Test fun labelBindsTheSetupSecretToTheExactHardware() {
        val label = StandSetupLabel(a, "ANDRO_445566", "c".repeat(32))
        assertFalse(label.toString().contains(label.secret))
        assertFailsWith<IllegalArgumentException> { StandSetupLabel(a, "ANDRO_000000", "c".repeat(32)) }
        assertFailsWith<IllegalArgumentException> { StandSetupLabel(a, "ANDRO_445566", "short") }
    }
}
