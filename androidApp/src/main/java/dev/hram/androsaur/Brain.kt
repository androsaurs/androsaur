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
import dev.hram.androsaur.hub.HubSettings
import dev.hram.androsaur.hub.Rename
import dev.hram.androsaur.commands.Answers
import dev.hram.androsaur.weather.Greeting
import dev.hram.androsaur.weather.WeatherChoice
import dev.hram.androsaur.weather.WeatherReading
import org.json.JSONArray
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
    val hub = HubClient(context, prefs)
    val weatherSource = WeatherSource(context, prefs)
    /** The weather to show under the clock, or null when there is none (no place known, nothing fetched, or too old). */
    var weather by mutableStateOf<WeatherReading?>(null); private set

    var audio by mutableStateOf(ModuleUi("Звук")); private set
    var camera by mutableStateOf(ModuleUi("Камера")); private set
    var tracking by mutableStateOf(false); private set
    var note by mutableStateOf<String?>(null); private set
    /** How the link to the hub is doing, for the panel. */
    var hubState by mutableStateOf(""); private set
    /** After how long without anybody the screen dims; the hub can change it, the last value received is used without the hub. */
    @Volatile var dimAfterMs = applyConfig(hub.stored()); private set
    /** Whether to greet a person at the first meeting of the day (a hub setting, on by default). */
    @Volatile private var greetingOn = greetingFrom(hub.stored())
    /** Face centre in the unmirrored camera picture (fractions 0..1), or null when nobody is seen. */
    @Volatile var face: Pair<Double, Double>? = null; private set
    @Volatile var lastWakeAt: Long? = null; private set
    @Volatile var wakeText: String = ""; private set
    @Volatile var lastActivityAt = SystemClock.elapsedRealtime(); private set
    @Volatile var lastFaceAt: Long? = null; private set
    /** Who said the wake word: a name, or "Не узнаю" for a face the camera does not know; shown while [Reflexes.badgeVisible] holds. */
    @Volatile var badge: String = ""; private set
    /** The camera's permanent id of the person in [badge] (a name changes, the id does not), or null. */
    @Volatile private var badgeId: String? = null
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

    /** A touch on the face: the person is here; a ringing timer is silenced (done on the timer's thread, not the screen's). */
    fun touch() { lastActivityAt = SystemClock.elapsedRealtime(); if (ringing) stopRingRequested = true }

    /** When the timer rings, wall clock ms; null when none is set. Kept in the preferences, so a restart does not lose it. */
    var timerEndsAt by mutableStateOf(prefs.getLong("timer_ends_at", 0L).takeIf { it > 0 }); private set
    /** The timer's bell is ringing (the audio module plays it). */
    var ringing by mutableStateOf(false); private set
    @Volatile private var stopRingRequested = false
    /** Short commands heard by the audio module, waiting to be answered on the timer's thread. */
    private val commands = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, JSONObject>>()

    private fun setTimer(endsAt: Long?) {
        timerEndsAt = endsAt
        prefs.edit().putLong("timer_ends_at", endsAt ?: 0L).apply()
    }

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
        launch { syncHub() }
        launch { followWeather() }
        launch { followCommands() }
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

    /**
     * Talks to the hub every half a minute: tells it whom the camera knows and how the last commands ended, takes the settings
     * and the new commands, hands it the sets of faces it asks for and loads the people of the house when they changed (so a
     * clean phone recognises the family without being taught). After a change it talks again at once, so the admin panel sees
     * it applied without waiting a round. Without the hub (none set, or it does not answer) nothing changes and the results
     * wait for the next contact.
     */
    private suspend fun syncHub() {
        var done = emptyList<HubClient.Done>()
        while (true) {
            if (hub.url.isEmpty()) { hubState = "Хаб не задан"; delay(HUB_EVERY_MS); continue }
            val result = hub.sync(cameraReport(), done)
            val reply = result.getOrNull()
            if (reply == null) {
                hubState = "Хаб: ${result.exceptionOrNull()?.message} (${hub.url}), работаю на последних настройках"
                delay(HUB_EVERY_MS); continue
            }
            hubState = "Хаб: на связи (${hub.url})"
            reply.config?.let { dimAfterMs = applyConfig(it); greetingOn = greetingFrom(it) }
            weatherSource.fromHub(reply.weatherPlace, reply.weather)
            done = reply.commands.map { HubClient.Done(it.id, execute(it)) }
            if (reply.upload.isNotEmpty()) uploadPeople(reply.upload)
            if (reply.peopleRev != hub.peopleRev) loadHousePeople()
            if (reply.config == null && done.isEmpty()) delay(HUB_EVERY_MS)
        }
    }

    /** Whom the camera knows for the hub: id, name, number of sets and their fingerprint (not the sets), and the face model. */
    private fun cameraReport(): HubClient.People? {
        if (!camera.running) return null
        val reply = cameraClient.get("/people")?.second ?: return null
        val model = reply.optJSONObject("model")?.optString("id").orEmpty().ifEmpty { return null }
        val people = reply.optJSONArray("people") ?: return null
        val list = JSONArray()
        for (p in List(people.length()) { people.getJSONObject(it) }) {
            if (p.optString("id").isEmpty()) continue
            list.put(JSONObject().put("id", p.getString("id")).put("name", p.optString("name")).put("samples", p.optInt("samples")).put("digest", p.optString("digest").ifEmpty { JSONObject.NULL }))
        }
        return HubClient.People(list, model)
    }

    /** Hands the hub the sets of the people it asked for. They pass through memory only. */
    private fun uploadPeople(ids: List<String>) {
        val export = cameraClient.get("/people/export")?.second ?: return
        val all = export.optJSONArray("people") ?: return
        val wanted = JSONArray()
        for (p in List(all.length()) { all.getJSONObject(it) }) if (p.optString("id") in ids) wanted.put(p)
        val digests = cameraReport()?.people?.let { r -> List(r.length()) { r.getJSONObject(it) }.associate { it.getString("id") to it.opt("digest") } }.orEmpty()
        for (i in 0 until wanted.length()) wanted.getJSONObject(i).put("digest", digests[wanted.getJSONObject(i).getString("id")] ?: JSONObject.NULL)
        if (wanted.length() > 0 && !hub.upload(wanted, export.optString("model"))) note = "Хаб не принял наборы лиц"
    }

    /** Loads the people of the house into the camera: new people are added, known ones take the name and the sets they lack. */
    private fun loadHousePeople() {
        val house = hub.download() ?: return
        val people = house.optJSONArray("people") ?: JSONArray()
        val reply = cameraClient.post("/people/import", JSONObject().put("model", house.optString("faceModel")).put("people", people))
        if (reply == null || reply.first != 200) { note = "Люди дома не загрузились: ${reply?.second?.optString("error") ?: "камера не ответила"}"; return }
        val errors = reply.second.optJSONObject("errors")
        if (errors != null && errors.length() > 0) note = "Люди дома: ${errors.length()} не загрузились (${errors.keys().asSequence().map { errors.getString(it) }.distinct().joinToString()})"
        hub.peopleRev = house.optInt("rev", 0)
        cameraPeople()?.let { sendPeople(it) }
    }

    /** The people the camera knows as (permanent id, name), not their faces; null when it does not answer. */
    private fun cameraPeople(): List<Pair<String, String>>? {
        if (!camera.running) return null
        val people = cameraClient.get("/people")?.second?.optJSONArray("people") ?: return null
        return List(people.length()) { people.getJSONObject(it) }.map { it.optString("id") to it.optString("name") }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
    }

    /** Does a command from the hub; returns null when it worked, or an error code for the admin panel. */
    private fun execute(command: HubClient.Command): String? = when (command.kind) {
        "rename_person" -> renamePerson(command.person, command.to)
        "merge_person" -> command.into?.let { mergePerson(command.person, it, command.to) } ?: "unknown_command"
        else -> "unknown_command"
    }

    /**
     * Renames the person with the permanent id [personId] to [to]. Only the name changes: the camera keeps their faces and id, and
     * notes are tied to the id, so whatever was left for them stays theirs. The audio module's recipients and the roles follow.
     * Done twice (the report to the hub got lost) it still ends well.
     */
    private fun renamePerson(personId: String, to: String): String? {
        val before = cameraPeople() ?: return "camera_unavailable"
        val name = before.firstOrNull { it.first == personId }?.second
        if (name == to) return null
        if (name == null) return "unknown_person"
        val reply = cameraClient.post("/people/rename", JSONObject().put("id", personId).put("to", to)) ?: return "camera_unavailable"
        if (reply.first != 200) return reply.second.optString("error").ifEmpty { "http_${reply.first}" }
        cameraPeople()?.let { sendPeople(it) }
        prefs.edit().putString("aliases", JSONObject(Rename.aliases(aliases(), name, to)).toString()).apply()
        if (badgeId == personId) badge = to
        note = "Хаб: «$name» теперь «$to»"
        return null
    }

    /**
     * The person [from] taught on this phone (in another system) is the person [into] of the house, called [name]: the camera
     * joins their sets or gives them the house's id, and their notes and recipient entry move to that id. Done twice it ends well.
     */
    private fun mergePerson(from: String, into: String, name: String): String? {
        val reply = cameraClient.post("/people/merge", JSONObject().put("from", from).put("into", into).put("name", name)) ?: return "camera_unavailable"
        if (reply.first != 200) return reply.second.optString("error").ifEmpty { "http_${reply.first}" }
        val audioReply = audioClient.post("/people/merge", JSONObject().put("from", from).put("into", into))
        if (audioReply == null || audioReply.first != 200) return "audio_unavailable"
        if (badgeId == from) { badgeId = into; badge = name }
        cameraPeople()?.let { sendPeople(it) }
        note = "Хаб: «$name» объединён с человеком дома"
        return null
    }

    /** Hands the camera's people (id, name) to the audio module, which keeps its recipients by id: a renamed person is updated in place. */
    private fun sendPeople(people: List<Pair<String, String>>): Boolean =
        audioClient.post("/recipients", JSONObject().put("addPeople", JSONArray(people.map { JSONObject().put("id", it.first).put("name", it.second) })))?.first == 200

    /**
     * Notes made before ids are tied once to the person whose current name they were left for (and to who left them), so that a
     * later rename does not change whom they are for.
     */
    private fun addressOldNotes(people: List<Pair<String, String>>) {
        val list = audioClient.get("/notes")?.second?.optJSONArray("notes") ?: return
        val byId = people.toMap()
        for (n in List(list.length()) { list.getJSONObject(it) }) {
            val toId = if (n.isNull("toId")) Delivery.idFor(n.optString("to"), byId) else null
            val fromId = if (n.isNull("fromId")) n.optString("from").takeIf { it.isNotEmpty() }?.let { Delivery.idFor(it, byId) } else null
            if (toId != null || fromId != null) audioClient.post("/notes/address", JSONObject().put("id", n.getString("id")).put("toId", toId ?: "").put("fromId", fromId ?: ""))
        }
    }

    /**
     * The weather, once a minute: the hub's while it is fresh; otherwise this phone asks MET Norway itself (no more than every half
     * an hour) for the place set in the hub, or where the phone is; without the network the last weather stays, with its age.
     */
    /**
     * Answers the short voice commands and keeps the timer: the time, the weather today and tomorrow, a timer that rings over the
     * face («Время вышло.» and a bell until «стоп», the wake word or a touch), how much is left, «стоп». All of it is local to
     * this phone; the weather is whatever [weather] holds (the hub's or the phone's own).
     */
    private suspend fun followCommands() {
        while (true) {
            delay(250)
            if (stopRingRequested) { stopRingRequested = false; audioClient.post("/ring/stop", JSONObject()) }
            val ends = timerEndsAt
            if (ends != null && System.currentTimeMillis() >= ends) {
                setTimer(null)
                // A timer that ran out long ago (the phone was off) is no use ringing now.
                if (System.currentTimeMillis() - ends > 10 * 60_000L) continue
                lastActivityAt = SystemClock.elapsedRealtime()
                say(Answers.TIMER_UP)
                delay(1800)
                audioClient.post("/ring", JSONObject().put("seconds", 60))
            }
            val (intent, data) = commands.poll() ?: continue
            lastActivityAt = SystemClock.elapsedRealtime()
            val now = java.time.ZonedDateTime.now()
            when (intent) {
                "time" -> say(Answers.time(now.hour, now.minute))
                "weather" -> say(Answers.weatherNow(weather, weather?.laterTimeMs?.let { at ->
                    val time = java.time.Instant.ofEpochMilli(at).atZone(now.zone)
                    Greeting.whenLater(time.hour, (at - System.currentTimeMillis()) / 3_600_000.0, time.toLocalDate() != now.toLocalDate())
                }))
                "weather_tomorrow" -> say(Answers.weatherTomorrow(weather))
                "timer" -> data.optString("seconds").toIntOrNull()?.let { seconds ->
                    setTimer(System.currentTimeMillis() + seconds * 1000L)
                    say(Answers.timerSet(seconds))
                }
                "timer_left" -> say(Answers.timerLeft(timerEndsAt?.let { ((it - System.currentTimeMillis()) / 1000).toInt() }))
                // «Стоп» that silenced the bell needs no words; «стоп» while the timer only counts down switches it off.
                "stop" -> if (data.optString("wasRinging") != "true" && timerEndsAt != null) { setTimer(null); say(Answers.TIMER_OFF) }
            }
        }
    }

    private fun say(text: String) {
        val reply = audioClient.post("/speak", JSONObject().put("text", text))
        if (reply == null || reply.first != 200) note = "Не сказал: ${reply?.second?.optString("error") ?: "звук не отвечает"}"
    }

    private suspend fun followWeather() {
        while (true) {
            val now = System.currentTimeMillis()
            weatherSource.refreshLocation(now)
            val place = WeatherChoice.place(weatherSource.hubPlace, weatherSource.devicePlace)
            if (WeatherChoice.fetchOwn(weatherSource.hubWeather, weatherSource.ownWeather, weatherSource.ownPlace, place, now)) {
                weatherSource.fetch(place!!, now)
            }
            weather = WeatherChoice.show(weatherSource.hubWeather, weatherSource.ownWeather, System.currentTimeMillis())
            delay(60_000)
        }
    }

    /** The dimming timeout from the settings the hub set (or the default when it set none). */
    private fun greetingFrom(config: JSONObject): Boolean =
        HubSettings.GREETING.resolve(hub.value(config, HubSettings.GREETING.module, HubSettings.GREETING.key)) == 1L

    /**
     * The greeting at the first meeting of the day with a recognised person: «Доброе утро, Папа. Плюс одиннадцать, облачно.
     * К вечеру небольшой дождь, возьми зонт.» Once a day per permanent id (kept in the preferences, so a restart does not greet
     * again); waits until it has been said, so the notes come after it. Returns whether it greeted.
     */
    private suspend fun greet(personId: String, name: String): Boolean {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        val today = now.toLocalDate().toString()
        val greeted = runCatching { JSONObject(prefs.getString("greeted", "{}") ?: "{}") }.getOrDefault(JSONObject())
        if (!greetingOn || !Greeting.due(greeted.optString(personId).ifEmpty { null }, today)) return false
        val shown = weather
        val later = shown?.laterTimeMs?.let { at ->
            val time = java.time.Instant.ofEpochMilli(at).atZone(zone)
            Greeting.whenLater(time.hour, (at - System.currentTimeMillis()) / 3_600_000.0, time.toLocalDate() != now.toLocalDate())
        }
        val text = Greeting.text(name, now.hour, shown, System.currentTimeMillis(), later)
        val reply = audioClient.post("/speak", JSONObject().put("text", text))
        if (reply == null || reply.first != 200) return false
        // Marked as soon as it started: a greeting cut short is not repeated all day.
        prefs.edit().putString("greeted", greeted.put(personId, today).toString()).apply()
        note = "Приветствие: $name"
        delay(500)
        val until = SystemClock.elapsedRealtime() + 20_000
        while (speaking && SystemClock.elapsedRealtime() < until) delay(250)
        return true
    }

    private fun applyConfig(config: JSONObject): Long =
        HubSettings.DIM_AFTER.resolve(hub.value(config, HubSettings.DIM_AFTER.module, HubSettings.DIM_AFTER.key)) * 1000

    private val gate = DeliveryGate()

    /**
     * Tells the audio module who is in front of the camera (so a note left by voice says who left it) and which people the camera
     * knows (so they can be named as recipients). The name is sent when it changes and every few seconds besides, as the module
     * forgets a name that is not renewed.
     */
    private suspend fun shareContext() {
        var sent: String? = null
        var sentId: String? = null
        var sentAt = 0L
        var peopleAt = 0L
        while (true) {
            delay(1000)
            if (!audio.running) { sent = null; continue }
            val now = SystemClock.elapsedRealtime()
            val name = badgeText(now).takeIf { it.isNotEmpty() && it != UNKNOWN } ?: ""
            val id = if (name.isEmpty()) "" else badgeId.orEmpty()
            if (name != sent || id != sentId || now - sentAt > 5000) {
                if (audioClient.post("/context", JSONObject().put("person", name).put("personId", id))?.first == 200) { sent = name; sentId = id; sentAt = now }
            }
            if (camera.running && now - peopleAt > 30_000) {
                val people = cameraPeople() ?: continue
                if (sendPeople(people)) { addressOldNotes(people); peopleAt = now }
            }
        }
    }

    /** Roles for people («жена» to the name the camera knows), kept in the preferences as JSON; notes for a role go to that person. */
    private fun aliases(): Map<String, String> = runCatching {
        val json = JSONObject(prefs.getString("aliases", "{}") ?: "{}")
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())

    /**
     * Voice notes by face: when a person is recognised and notes wait for them (by their id, or by name or role for a note
     * without one), the audio module plays them,
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
            if (!audio.running || speaking || mode.isNotEmpty()) continue
            badgeId?.let { id -> if (greet(id, name)) lastActivityAt = SystemClock.elapsedRealtime() }
            if (!gate.shouldCheck(name, now)) continue
            val list = audioClient.get("/notes?pending=1")?.second?.optJSONArray("notes") ?: continue
            val waiting = List(list.length()) { list.getJSONObject(it).let { n ->
                WaitingNote(n.getString("id"), n.getString("to"), n.getLong("createdAtMs"), if (n.isNull("toId")) null else n.optString("toId").ifEmpty { null })
            } }
            val mine = Delivery.forPerson(waiting, badgeId, name, aliases())
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
            when (e.optString("type")) {
                "command" -> e.optJSONObject("data")?.let { d -> commands.add(d.optString("intent") to d) }
                "ring_started" -> { ringing = true; lastActivityAt = SystemClock.elapsedRealtime() }
                "ring_stopped" -> ringing = false
            }
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
        if (here && !wasPresent) { badge = ""; badgeId = null; recognizedAt = null; nextIdentifyAt = 0L }
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
            if (name != null) { badge = name; badgeId = if (who.isNull("id")) null else who.optString("id").ifEmpty { null }; recognizedAt = now; identifyUntil = 0L; return }
            sawStranger = true
        }
        if (now > identifyUntil) {
            identifyUntil = 0L
            if (sawStranger) { badge = UNKNOWN; badgeId = null; recognizedAt = now }
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
        const val RECHECK_MS = 2000L; const val RECHECK_EVERY_MS = 5000L; const val UNKNOWN = "Не узнаю"; const val HUB_EVERY_MS = 30_000L }

    fun expression(now: Long) = Reflexes.expression(now, lastWakeAt, Reflexes.present(now, lastFaceAt), lastActivityAt, watching = tracking, dimAfterMs = dimAfterMs)
}
