package dev.hram.androsaur.stand

import kotlin.math.hypot

data class TrackingBox(val x: Double, val y: Double, val w: Double, val h: Double) {
    val valid get() = listOf(x, y, w, h).all { it.isFinite() } && w > 0 && h > 0
    val cx get() = (x + w / 2).coerceIn(0.0, 1.0)
    val cy get() = (y + h / 2).coerceIn(0.0, 1.0)
}

/** Signed, normalised error in the upright unmirrored frame: X right, Y up. Null means no target. */
data class TrackingObservation(val seq: Long, val sampledAtMs: Long, val x: Double?, val y: Double?) {
    val visible get() = x != null && y != null
    fun age(now: Long) = (now - sampledAtMs).coerceAtLeast(0)
}

/** One update per fresh analysis result. Keep a geometric target stable when detector ordering changes. */
class FaceTracking {
    private var frame = ""
    private var seq = 0L
    private var previous: TrackingBox? = null
    private var targetAt = 0L
    private var last: TrackingObservation? = null

    fun ingest(frameId: String, sampledAtMs: Long, boxes: List<TrackingBox>, now: Long): TrackingObservation? {
        if (frameId.isEmpty() || frameId == frame) return null
        frame = frameId
        if (sampledAtMs <= 0 || now - sampledAtMs !in 0L..500L) return lost(now)
        val candidates = boxes.filter { it.valid }
        val old = previous
        val target = if (old != null && now - targetAt < 1000) {
            candidates.minByOrNull { hypot(it.cx - old.cx, it.cy - old.cy) }
                ?.takeIf { hypot(it.cx - old.cx, it.cy - old.cy) <= .25 }
        } else candidates.maxByOrNull { it.w * it.h }
        if (target != null) { previous = target; targetAt = now }
        return TrackingObservation(++seq, sampledAtMs, target?.let { 2 * it.cx - 1 }, target?.let { 1 - 2 * it.cy })
            .also { last = it }
    }

    fun lost(now: Long, force: Boolean = false): TrackingObservation? {
        val old = last ?: return null
        if (!old.visible || (!force && old.age(now) <= 500)) return null
        if (force) previous = null
        return TrackingObservation(++seq, now, null, null).also { last = it }
    }
}
