package dev.hram.androsaur

import android.os.SystemClock
import dev.hram.androsaur.stand.TrackingObservation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal fun trackingJson(session: String, sample: TrackingObservation, now: Long) = JSONObject()
    .put("v", 1).put("session", session).put("seq", sample.seq).put("ageMs", sample.age(now))
    .put("visible", sample.visible).put("x", sample.x ?: JSONObject.NULL).put("y", sample.y ?: JSONObject.NULL)

/** A bounded diagnostics queue. Stand control does not wait for the optional hub. */
class TrackingDiagnostics(private val hub: HubClient, private val stand: StandConnection) {
    private val session = UUID.randomUUID().toString()
    private val pending = ArrayDeque<TrackingObservation>()

    fun observe(sample: TrackingObservation) {
        synchronized(pending) { pending.addLast(sample); while (pending.size > 128) pending.removeFirst() }
        stand.observe(session, sample)
    }

    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            delay(2000)
            val now = SystemClock.elapsedRealtime()
            val batch = synchronized(pending) {
                while (pending.isNotEmpty() && pending.first().age(now) > 30_000) pending.removeFirst()
                pending.toList()
            }
            if (batch.isEmpty() || !hub.paired) continue
            val json = JSONArray(batch.map { trackingJson(session, it, now) })
            if (hub.postTracking(session, json)) synchronized(pending) {
                while (pending.isNotEmpty() && pending.first().seq <= batch.last().seq) pending.removeFirst()
            }
        }
    }
}
