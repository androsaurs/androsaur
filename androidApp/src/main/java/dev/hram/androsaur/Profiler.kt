package dev.hram.androsaur

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * Profiling of the scenarios: the stages of a command (the audio module's events and the app's own marks) go to the hub, which lays
 * them out by time and shows failures and slow stages. A run starts at the wake word; its number is its time. Only stage names,
 * milliseconds and reason codes are sent, never the words that were said. Without a hub the marks are dropped, nothing waits for it.
 */
class Profiler(private val send: (JSONArray) -> Unit) {
    @Volatile var run: String = ""
        private set
    @Volatile var onRun: (String) -> Unit = {}
    private var startedAt = 0L
    private val queue = ConcurrentLinkedQueue<JSONObject>()
    private val sender = Executors.newSingleThreadExecutor { r -> Thread(r, "profiler").apply { isDaemon = true } }

    /** An event of the audio module, as the journal gives it (`atMs`, `type`, `data`). */
    fun audio(e: JSONObject) {
        val type = e.optString("type")
        val at = e.optLong("atMs")
        if (type == "wake_word") { run = "w$at"; startedAt = at; onRun(run) }
        add("audio", type, at, e.optJSONObject("data"))
    }

    /** The app's own mark, on the same clock as the audio module (one phone). */
    fun app(stage: String, vararg data: Pair<String, Any>) {
        val now = System.currentTimeMillis()
        Log.i("Trace", "$now $stage ${data.joinToString(" ") { "${it.first}=${it.second}" }}")
        add("app", stage, now, JSONObject().also { j -> data.forEach { (k, v) -> j.put(k, v) } })
    }

    private fun add(src: String, stage: String, at: Long, data: JSONObject?) {
        // Events outside a run (the engine loading, the ring of a timer) are not scenarios; a run longer than 3 minutes is over.
        if (run.isEmpty() || at - startedAt > RUN_LIMIT_MS || at < startedAt) return
        val kept = JSONObject()
        data?.keys()?.forEach { k -> if (k in KEYS) kept.put(k, data.get(k)) }
        if (queue.size >= MAX_QUEUE) queue.poll()
        queue.add(JSONObject().put("run", run).put("ts", at).put("src", src).put("stage", stage).put("data", kept))
    }

    /** Sends what has gathered, off the caller's thread. */
    fun flush() {
        if (queue.isEmpty()) return
        sender.execute {
            val batch = JSONArray()
            while (batch.length() < BATCH) batch.put(queue.poll() ?: break)
            if (batch.length() > 0) runCatching { send(batch) }
        }
    }

    private companion object {
        const val RUN_LIMIT_MS = 180_000L
        const val MAX_QUEUE = 1000
        const val BATCH = 200
        val KEYS = setOf(
            "reason", "understood", "intent", "peakDbfs", "thresholdDbfs", "utterances", "speaker", "firstAudioMs", "audioMs",
            "synthMs", "loadMs", "durationMs", "chars", "engine", "levelDbfs", "end", "status", "code", "bytes",
        )
    }
}
