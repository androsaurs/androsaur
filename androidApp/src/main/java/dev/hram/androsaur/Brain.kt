package dev.hram.androsaur

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.hram.androsaur.delivery.Delivery
import dev.hram.androsaur.delivery.DeliveryGate
import dev.hram.androsaur.delivery.WaitingNote
import dev.hram.androsaur.face.Reflexes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    @Volatile var lastFaceAt: Long? = null; private set
    /** Who said the wake word: a name, or "Не узнаю" for a face the camera does not know; shown while [Reflexes.badgeVisible] holds. */
    @Volatile var badge: String = ""; private set
    @Volatile var recognizedAt: Long? = null; private set
    /** Whether the audio module is speaking and how wide the mouth is (0..1), from its own loudness of the sound that plays. */
    @Volatile var speaking = false; private set
    @Volatile var mouth = 0f; private set
    /** What the audio module waits for besides speech: `command`, `recording`, `note` or empty; the face says it in words so the person knows when to talk. */
    @Volatile var mode = ""; private set
    private var identifySince = 0L
    private var identifyUntil = 0L
    private var sawStranger = false
    private var wasPresent = false
    private var nextIdentifyAt = 0L
    private var lastEventId = -1L
    /** The run of the audio service the event numbers belong to; a restarted service numbers its events from 1 again. */
    private var audioBoot = ""

    fun touch() { lastActivityAt = SystemClock.elapsedRealtime() }

    /** Starts both services from this visible app and tells which one refused. */
    fun startModules() {
        val problems = listOf(audioClient to "Звук", cameraClient to "Камера").mapNotNull { (client, name) ->
            val link = client.connect()
            if (link != "ready") "$name: ${describe(link)}" else client.start()?.let { "$name: ${describe(it)}" }
        }
        note = if (problems.isEmpty()) "Модули запущены" else problems.joinToString("; ")
    }

    /** Turns the face detector on (or off). The camera of this project looks at people by default; if somebody chose the back one, it is switched to front. */
    fun switchTracking(on: Boolean) {
        if (on) {
            val status = cameraClient.get("/status")?.second
            if (status == null) { note = "Камера не отвечает"; return }
            if (status.optString("lens") != "front") {
                val lens = cameraClient.post("/camera", JSONObject().put("lens", "front"))
                if (lens == null || lens.first != 200) { note = "Не удалось включить фронтальную камеру: ${lens?.second?.optString("error") ?: "нет ответа"}"; return }
            }
            val lab = cameraClient.post("/faces", JSONObject().put("enabled", true).put("mode", "C"))
            if (lab == null || lab.first != 200) { note = "Не удалось включить слежение: ${lab?.second?.optString("error") ?: "нет ответа"}"; return }
            tracking = true; prefs.edit().putBoolean("want_tracking", true).apply(); note = "Слежение за лицом включено"
        } else {
            cameraClient.post("/faces", JSONObject().put("enabled", false))
            tracking = false; face = null; prefs.edit().putBoolean("want_tracking", false).apply(); note = "Слежение выключено"
        }
    }

    private fun describe(code: String) = when (code) {
        "not_installed" -> "не установлен"
        "permission_denied" -> "нет разрешения (другая подпись или установка до модуля)"
        "start_refused" -> "Android не дал запустить из фона"
        else -> code
    }

    /** The polling loop; call it from a background coroutine and cancel it when the screen goes away. */
    suspend fun run() = coroutineScope {
        launch { followMouth() }
        launch { deliverNotes() }
        launch { shareContext() }
        loop()
    }

    /** Polls the speech state fast while the assistant talks (the mouth needs about 15 updates a second) and slowly while it is quiet. */
    private suspend fun followMouth() {
        while (true) {
            val reply = if (audio.running) audioClient.get("/speech")?.second else null
            speaking = reply?.optBoolean("speaking") == true
            mode = reply?.optString("mode").orEmpty()
            if (mode.isNotEmpty()) lastActivityAt = SystemClock.elapsedRealtime()
            mouth = if (speaking) reply!!.optDouble("mouth", 0.0).toFloat() else 0f
            if (speaking) lastActivityAt = SystemClock.elapsedRealtime()
            delay(if (speaking) 60 else 400)
        }
    }

    private val gate = DeliveryGate()

    /**
     * Tells the audio module who is in front of the camera (so a note left by voice says who left it) and which people the camera
     * knows (so they can be named as recipients). The name is sent when it changes and every few seconds besides, as the module
     * forgets a name that is not renewed.
     */
    private suspend fun shareContext() {
        var sent: String? = null
        var sentAt = 0L
        var peopleAt = 0L
        while (true) {
            delay(1000)
            if (!audio.running) { sent = null; continue }
            val now = SystemClock.elapsedRealtime()
            val name = badgeText(now).takeIf { it.isNotEmpty() && it != UNKNOWN } ?: ""
            if (name != sent || now - sentAt > 5000) {
                if (audioClient.post("/context", JSONObject().put("person", name))?.first == 200) { sent = name; sentAt = now }
            }
            if (camera.running && now - peopleAt > 30_000) {
                val people = cameraClient.get("/people")?.second?.optJSONArray("people") ?: continue
                val names = org.json.JSONArray(List(people.length()) { people.getJSONObject(it).optString("name") }.filter { it.isNotEmpty() })
                if (audioClient.post("/recipients", JSONObject().put("addNames", names))?.first == 200) peopleAt = now
            }
        }
    }

    /** Roles for people («жена» to the name the camera knows), kept in the preferences as JSON; notes for a role go to that person. */
    private fun aliases(): Map<String, String> = runCatching {
        val json = JSONObject(prefs.getString("aliases", "{}") ?: "{}")
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())

    /**
     * Voice notes by face: when a person is recognised and notes wait for them (by name or by role), the audio module plays them,
     * one after another, each with «Тебе сообщение от …». If the person leaves or somebody else is recognised meanwhile, the note
     * is cut short and stays waiting (the audio module marks a note heard only when it played to the end).
     */
    private suspend fun deliverNotes() {
        var lastName = ""
        while (true) {
            delay(500)
            val now = SystemClock.elapsedRealtime()
            val name = badge.takeIf { it.isNotEmpty() && it != UNKNOWN && Reflexes.badgeVisible(now, recognizedAt, lastFaceAt) }
            if (name == null) { if (lastName.isNotEmpty() && !Reflexes.present(now, lastFaceAt)) { gate.left(lastName); lastName = "" }; continue }
            lastName = name
            if (!audio.running || speaking || !gate.shouldCheck(name, now)) continue
            val list = audioClient.get("/notes?pending=1")?.second?.optJSONArray("notes") ?: continue
            val waiting = List(list.length()) { list.getJSONObject(it).let { n -> WaitingNote(n.getString("id"), n.getString("to"), n.getLong("createdAtMs")) } }
            val mine = Delivery.forPerson(waiting, name, aliases())
            if (mine.isEmpty()) { gate.checkedEmpty(name, now); continue }
            var finished = true
            for (item in mine) if (!play(item, name)) { finished = false; break }
            // A note cut short (the camera lost the face for a moment) is tried again soon, not after the long pause that follows a delivery.
            if (finished) gate.delivered(name, SystemClock.elapsedRealtime()) else gate.checkedEmpty(name, SystemClock.elapsedRealtime())
        }
    }

    /** Plays one note and waits for it; false when it was cut short because the person is no longer the one in front of the camera. */
    private suspend fun play(item: WaitingNote, name: String): Boolean {
        val started = audioClient.post("/notes/play", JSONObject().put("id", item.id).put("announce", true))
        if (started == null || started.first != 200) { note = "Записка не проигралась: ${started?.second?.optString("error") ?: "нет ответа"}"; return false }
        note = "Записка для $name"
        var idleRounds = 0
        while (idleRounds < 3) {
            delay(250)
            lastActivityAt = SystemClock.elapsedRealtime()
            val now = SystemClock.elapsedRealtime()
            val still = badgeText(now) == name
            if (!still) { audioClient.post("/notes/play/stop", JSONObject()); note = "Записка прервана: $name ушёл"; return false }
            val state = audioClient.get("/status")?.second?.optJSONObject("notes")?.optString("state")
            idleRounds = if (state == "idle") idleRounds + 1 else 0
        }
        return true
    }

    private suspend fun loop() {
        // The app opens on the face and brings everything up by itself: both services are started from this visible app,
        // then the loop below keeps them running and switches the face tracking on.
        startModules()
        var round = 0
        while (true) {
            if (round % 5 == 0) refreshStatus()
            if (round % 20 == 0) autoTrack()
            pollEvents()
            if (tracking) { pollFace(); watchArrival() }
            if (identifyUntil != 0L) pollIdentity()
            round++
            delay(250)
        }
    }

    /** Watching the person is the normal state: unless the owner switched tracking off, it is switched on as soon as the camera answers. */
    private fun autoTrack() {
        if (tracking || !camera.running || !prefs.getBoolean("want_tracking", true)) return
        switchTracking(true)
    }

    private fun refreshStatus() {
        audio = refresh(audioClient) { s ->
            val boot = s.optString("bootId")
            if (boot.isNotEmpty() && boot != audioBoot) {
                // A service that restarted while this app ran: its events are all new, read them from the start.
                if (audioBoot.isNotEmpty()) lastEventId = 0
                audioBoot = boot
            }
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

    private val silentRounds = mutableMapOf<String, Int>()
    private val lastRestartAt = mutableMapOf<String, Long>()

    private fun refresh(client: ModuleClient, summarize: (JSONObject) -> Pair<List<String>, String?>): ModuleUi {
        val link = client.connect()
        if (link != "ready") return ModuleUi(client.label, describe(link))
        val reply = client.get("/status")
        if (reply == null) {
            // The module's service is gone (killed or updated; Android does not bring it back). This app is on screen, so it may start it again.
            val misses = (silentRounds[client.id] ?: 0) + 1
            silentRounds[client.id] = misses
            val now = SystemClock.elapsedRealtime()
            if (misses >= 3 && now - (lastRestartAt[client.id] ?: 0L) > 15_000) {
                lastRestartAt[client.id] = now
                val problem = client.start()
                note = if (problem == null) "${client.label}: сервис не отвечал, запущен заново" else "${client.label}: не удалось запустить заново (${describe(problem)})"
            }
            return ModuleUi(client.label, "связь есть, сервис не отвечает (запускаю заново)")
        }
        silentRounds[client.id] = 0
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
                startIdentify()
            }
        }
    }

    /**
     * Somebody comes into view: forget who was there before and ask the camera who this is at once. While a face stays in view
     * it is asked again: soon after a failure (the first look may be a turned head or a blur), rarely after a success, so that
     * another person who steps in without a gap in between does not keep the name of the one before.
     */
    private fun watchArrival() {
        val now = SystemClock.elapsedRealtime()
        val here = Reflexes.present(now, lastFaceAt)
        if (here && !wasPresent) { badge = ""; recognizedAt = null; nextIdentifyAt = 0L }
        wasPresent = here
        if (here && identifyUntil == 0L && now >= nextIdentifyAt) startIdentify(if (recognizedAt != null && badge != UNKNOWN) RECHECK_MS else IDENTIFY_MS)
    }

    /** Asks the camera who is in front of it (at a person's arrival and at the wake word). Only when it is already watching (tracking is on). */
    private fun startIdentify(ms: Long = IDENTIFY_MS) {
        if (!tracking) return
        nextIdentifyAt = SystemClock.elapsedRealtime() + if (badge == UNKNOWN || recognizedAt == null) RETRY_IDENTIFY_MS else RECHECK_EVERY_MS
        val reply = cameraClient.post("/people/identify", JSONObject().put("ms", ms))
        if (reply == null || reply.first != 200) { note = "Опознание не началось: ${reply?.second?.optString("error") ?: "нет ответа"}"; return }
        identifySince = SystemClock.elapsedRealtime(); identifyUntil = identifySince + ms + 300; sawStranger = false
    }

    /** Reads what the camera has made of the face: the first name wins, a face it does not know shows as such when the window ends. */
    private fun pollIdentity() {
        val now = SystemClock.elapsedRealtime()
        val who = cameraClient.get("/people")?.second?.optJSONObject("who")
        // Only a result made after the wake word counts, not one left from earlier.
        if (who != null && who.optLong("ageMs", Long.MAX_VALUE) <= now - identifySince + 200) {
            val name = if (who.isNull("name")) null else who.optString("name")
            if (name != null) { badge = name; recognizedAt = now; identifyUntil = 0L; return }
            sawStranger = true
        }
        if (now > identifyUntil) {
            identifyUntil = 0L
            if (sawStranger) { badge = UNKNOWN; recognizedAt = now }
        }
    }

    private fun pollFace() {
        val reply = cameraClient.get("/faces")?.second ?: return
        if (!reply.optBoolean("enabled", false)) {
            // The camera restarted and forgot the tracking this app asked for: ask again.
            val again = cameraClient.post("/faces", JSONObject().put("enabled", true).put("mode", "C"))
            if (again == null || again.first != 200) { note = "Слежение не вернулось: ${again?.second?.optString("error") ?: "нет ответа"}" }
            return
        }
        val boxes = reply.optJSONObject("mediapipe")?.optJSONArray("lastBoxes")
        val first = if (boxes != null && boxes.length() > 0) boxes.getJSONObject(0) else null
        face = first?.let { (it.optDouble("x") + it.optDouble("w") / 2) to (it.optDouble("y") + it.optDouble("h") / 2) }
        if (face != null) { lastActivityAt = SystemClock.elapsedRealtime(); lastFaceAt = lastActivityAt }
    }

    /** The name to draw under the face now, or an empty text when none should be shown. */
    fun badgeText(now: Long): String = if (Reflexes.badgeVisible(now, recognizedAt, lastFaceAt)) badge else ""

    private companion object { const val IDENTIFY_MS = 3500L; const val RETRY_IDENTIFY_MS = 6000L
        const val RECHECK_MS = 2000L; const val RECHECK_EVERY_MS = 5000L; const val UNKNOWN = "Не узнаю" }

    fun expression(now: Long) = Reflexes.expression(now, lastWakeAt, Reflexes.present(now, lastFaceAt), lastActivityAt, watching = tracking)
}
