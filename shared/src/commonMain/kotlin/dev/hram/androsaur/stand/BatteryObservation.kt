package dev.hram.androsaur.stand

/** Android reports actual charging, not merely a cable being plugged in. */
data class BatteryObservation(val seq: Long, val sampledAtMs: Long, val percent: Int, val charging: Boolean) {
    fun age(now: Long) = (now - sampledAtMs).coerceAtLeast(0)
    companion object {
        fun from(seq: Long, now: Long, level: Int, scale: Int, charging: Boolean?): BatteryObservation? {
            if (level < 0 || scale <= 0 || level > scale || charging == null) return null
            return BatteryObservation(seq, now, (level.toLong() * 100 / scale).toInt(), charging)
        }
    }
}
