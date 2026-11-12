package dev.hram.androsaur.stand

/** Network address is discovered separately; neither a name nor an IP proves hardware identity. */
data class StandIdentity(val id: String, val fingerprint: String) {
    init {
        require(Regex("stand-[0-9a-f]{12}").matches(id))
        require(Regex("[0-9a-f]{64}").matches(fingerprint))
    }
}

object StandProtocol {
    const val SERVICE = "_androsaur-stand._tcp."
    const val PORT = 8443
    const val BLE_SERVICE = "0000ffff-0000-1000-8000-00805f9b34fb"
    fun mayClaim(existing: StandIdentity?, candidate: StandIdentity) = existing == null || existing == candidate
    fun secureProvisioning(version: Int, capabilities: List<String>, code: String, patchVersion: Int = 0) =
        version == 2 && patchVersion == 1 && "no_pop" !in capabilities && Regex("[0-9a-f]{32}").matches(code)
}

/** A physical setup credential. Deliberately not a data class: toString must not reveal PoP. */
class StandSetupLabel(val identity: StandIdentity, val name: String, val secret: String) {
    init {
        require(name == "ANDRO_" + identity.id.takeLast(6))
        require(Regex("[0-9a-f]{32}").matches(secret))
    }
    override fun toString() = "StandSetupLabel(${identity.id})"
}
