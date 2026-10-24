package dev.hram.androsaur

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.hram.androsaur.face.Reflexes
import kotlinx.coroutines.delay
import org.json.JSONObject

/** What the screen shows about one module. */
class ModuleUi(val label: String, val link: String = "…", val running: Boolean = false, val lines: List<String> = emptyList(), val error: String? = null)

/**
 * The skeleton of the "brain": it keeps the links to the modules, starts them, polls their state and turns what it sees into the
 * gaze and wake flash of the face (reflexes, no LLM). Runs on a background thread; the screen reads [audio], [camera] and the face fields.
 */
class Brain(context: Context) {
    private val audioClient = ModuleClient(context, "audio", "Звук", "dev.hram.androsaur.audio", "AudioService")
    private val cameraClient = ModuleClient(context, "camera", "Камера", "dev.hram.androsaur.camera", "CameraService")
    private val prefs = context.getSharedPreferences("brain", Context.MODE_PRIVATE)

    var audio by mutableStateOf(ModuleUi("Звук")); private set
    var camera by mutableStateOf(ModuleUi("Камера")); private set
    var tracking by mutableStateOf(false); private set
    var note by mutableStateOf<String?>(null); private set
    /** Face centre in the unmirrored camera picture (fractions 0..1), or null when nobody is seen. */
    @Volatile var face: Pair<Double, Double>? = null; private set
    @Volatile var lastWakeAt: Long? = null; private set
    @Volatile var wakeText: String = ""; private set
    @Volatile var lastActivityAt = SystemClock.elapsedRealtime(); private set
    private var lastEventId = -1L

    fun touch() { lastActivityAt = SystemClock.elapsedRealtime() }

    /** Starts both services from this visible app and tells which one refused. */
    fun startModules() {
        val problems = listOf(audioClient to "Звук", cameraClient to "Камера").mapNotNull { (client, name) ->
            val link = client.connect()
            if (link != "ready") "$name: ${describe(link)}" else client.start()?.let { "$name: ${describe(it)}" }
        }
        note = if (problems.isEmpty()) "Модули запущены" else problems.joinToString("; ")
    }

    /** Switches the camera to the front lens and turns the face detector on (or off and back). */
    fun switchTracking(on: Boolean) {
        if (on) {
            val status = cameraClient.get("/status")?.second
            if (status == null) { note = "Камера не отвечает"; return }
            prefs.edit().putString("lens_before", status.optString("lens", "back")).apply()
            val lens = cameraClient.post("/camera", JSONObject().put("lens", "front"))
            if (lens == null || lens.first != 200) { note = "Не удалось включить фронтальную камеру: ${lens?.second?.optString("error") ?: "нет ответа"}"; return }
            val lab = cameraClient.post("/faces", JSONObject().put("enabled", true).put("mode", "C"))
            if (lab == null || lab.first != 200) { note = "Не удалось включить слежение: ${lab?.second?.optString("error") ?: "нет ответа"}"; return }
            tracking = true; note = "Слежение за лицом включено"
        } else {
            cameraClient.post("/faces", JSONObject().put("enabled", false))
            cameraClient.post("/camera", JSONObject().put("lens", prefs.getString("lens_before", "back")))
            tracking = false; face = null; note = "Слежение выключено"
        }
    }

    private fun describe(code: String) = when (code) {
        "not_installed" -> "не установлен"
        "permission_denied" -> "нет разрешения (другая подпись или установка до модуля)"
        "start_refused" -> "Android не дал запустить из фона"
        else -> code
    }

    /** The polling loop; call it from a background coroutine and cancel it when the screen goes away. */
    suspend fun run() {
        var round = 0
        while (true) {
            if (round % 5 == 0) refreshStatus()
            pollEvents()
            if (tracking) pollFace()
            round++
            delay(250)
        }
    }

    private fun refreshStatus() {
        audio = refresh(audioClient) { s ->
            val engine = s.optJSONObject("engine")
            listOfNotNull(
                if (s.optBoolean("listening")) "слушает" else "не слушает",
                engine?.let { "движок ${it.optString("id")} (${it.optString("state")})" },
                s.optString("phrase").takeIf { it.isNotEmpty() }?.let { "фраза «$it»" },
                s.optJSONObject("microphone")?.let { "уровень ${it.optDouble("levelDbfs")} дБ" },
            ) to s.optString("error").takeIf { it.isNotEmpty() }
        }
        camera = refresh(cameraClient) { s ->
            listOfNotNull(
                "камера ${s.optString("lens")} · ${s.optString("state")}",
                if (tracking) "слежение включено" else null,
                s.optString("error").takeIf { it.isNotEmpty() && it != "null" }?.let { "ошибка: $it" },
            ) to null
        }
    }

    private fun refresh(client: ModuleClient, summarize: (JSONObject) -> Pair<List<String>, String?>): ModuleUi {
        val link = client.connect()
        if (link != "ready") return ModuleUi(client.label, describe(link))
        val reply = client.get("/status") ?: return ModuleUi(client.label, "связь есть, сервис не отвечает (не запущен)")
        if (reply.first != 200) return ModuleUi(client.label, "ответ ${reply.first}")
        val (lines, error) = summarize(reply.second)
        return ModuleUi(client.label, "на связи", running = true, lines = lines, error = error)
    }

    private fun pollEvents() {
        if (!audio.running) return
        val events = audioClient.get("/events?after=${if (lastEventId < 0) Long.MAX_VALUE else lastEventId}")?.second
        if (lastEventId < 0) {
            // The first look only learns where the journal ends, so old detections do not flash the face.
            lastEventId = audioClient.get("/status")?.second?.optLong("latestEventId", 0) ?: return
            return
        }
        val list = events?.optJSONArray("events") ?: return
        for (i in 0 until list.length()) {
            val e = list.getJSONObject(i)
            lastEventId = maxOf(lastEventId, e.optLong("id"))
            if (e.optString("type") == "wake_word") {
                lastWakeAt = SystemClock.elapsedRealtime(); lastActivityAt = lastWakeAt!!
                wakeText = e.optJSONObject("data")?.optString("heard").orEmpty()
            }
        }
    }

    private fun pollFace() {
        val reply = cameraClient.get("/faces")?.second ?: return
        val boxes = reply.optJSONObject("mediapipe")?.optJSONArray("lastBoxes")
        val first = if (boxes != null && boxes.length() > 0) boxes.getJSONObject(0) else null
        face = first?.let { (it.optDouble("x") + it.optDouble("w") / 2) to (it.optDouble("y") + it.optDouble("h") / 2) }
        if (face != null) lastActivityAt = SystemClock.elapsedRealtime()
    }

    fun expression(now: Long) = Reflexes.expression(now, lastWakeAt, face != null, lastActivityAt)
}
