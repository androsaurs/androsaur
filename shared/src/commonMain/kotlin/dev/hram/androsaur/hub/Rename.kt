package dev.hram.androsaur.hub

import dev.hram.androsaur.delivery.Delivery

/**
 * What changes on this phone besides the camera when a person is renamed. Notes are tied to the person's permanent id and the
 * audio module's recipients follow the camera's people by id, so only the roles («жена» → name), kept by name, need it.
 */
object Rename {
    fun aliases(aliases: Map<String, String>, name: String, to: String): Map<String, String> =
        aliases.mapValues { (_, v) -> if (Delivery.normalize(v) == Delivery.normalize(name)) to else v }
}
