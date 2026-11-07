package dev.hram.androsaur

import dev.hram.androsaur.commands.ThingReply
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import dev.hram.androsaur.hub.HubSettings
import dev.hram.androsaur.character.AccessoryEdit
import dev.hram.androsaur.commands.DailyHoroscope
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * The link to androsaur-hub, the home server with the admin panel. This app is the only one on the phone that talks to it: it
 * sends what settings it has and takes the values set in the hub. The last values received are kept, so without the hub the phone
 * works on them (or on the defaults). It talks to the hub over the home Wi-Fi; the address is set from outside for now
 * (`adb shell am start … --es hub http://<hub>:8770`);
 * pairing by QR code comes later.
 */
class HubClient(context: Context, private val prefs: SharedPreferences) {
    private val name = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
    private var registered = false

    /** This phone's id for the hub: made once, kept in the preferences. */
    private val deviceId: String = prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE, it).apply() }

    val url: String get() = prefs.getString(KEY_URL, "").orEmpty()

    /** Sets the hub address (empty to forget it). Returns false for something that is not an http(s) address. */
    fun setUrl(value: String): Boolean {
        val clean = value.trim().trimEnd('/')
        if (clean.isNotEmpty() && !Regex("^https?://[^/\\s]+$").matches(clean)) return false
        prefs.edit().putString(KEY_URL, clean).apply()
        registered = false
        return true
    }

    /** The values the hub set last time, as module → key → value; empty before the first contact. */
    fun stored(): JSONObject = runCatching { JSONObject(prefs.getString(KEY_CONFIG, "{}") ?: "{}") }.getOrDefault(JSONObject())

    /** The value of a whole-number setting as received from the hub, or null when the hub did not set it. */
    fun value(config: JSONObject, module: String, key: String): Long? =
        config.optJSONObject("values")?.optJSONObject(module)?.takeIf { it.has(key) }?.optLong(key)

    fun stringValue(config: JSONObject, module: String, key: String): String? =
        config.optJSONObject("values")?.optJSONObject(module)?.opt(key) as? String

    /** Receipt is not application: acknowledge only after local settings and the audio module agree. */
    fun acknowledgeConfig() { prefs.edit().putInt(KEY_APPLIED, stored().optInt("rev", 0)).apply() }

    /**
     * A command from the hub: `rename_person` (the person with the permanent id [person] is renamed [to]) or `merge_person`
     * (the person [person] of this phone is the person [into] of the house, called [to]).
     */
    class Command(val id: Long, val kind: String, val person: String, val to: String, val into: String?)

    /** What this phone did with a command: [error] is the module's error code, null when it worked. */
    class Done(val id: Long, val error: String?)

    /**
     * What one contact brought: the settings when they changed since last time (else null), the commands still to do, the
     * version of the people of the house ([peopleRev]; newer than [peopleRev] here means load them) and the ids of the people
     * whose sets the hub asks for ([upload]).
     */
    class Reply(
        val config: JSONObject?, val commands: List<Command>, val peopleRev: Int = 0, val upload: List<String> = emptyList(),
        /** The place of the weather set in the hub (null — none, the phone uses its own) and the hub's weather for it. */
        val weatherPlace: JSONObject? = null, val weather: JSONObject? = null,
        /** Reliable transcriptions of this phone's notes (id to text), when the hub has Groq: the notes are then read out. */
        val transcripts: Map<String, String> = emptyMap(),
        /** Roles of the people of the house (person id to words such as «папа»), when the hub sent them; null — keep the last ones. */
        val roles: Map<String, List<String>>? = null,
        val characters: Map<String, String>? = null,
        val accessories: Map<String, Map<String, String>>? = null,
        /** Rooms with speakers (id, name, words) for «передай в детскую»; null — the hub did not send them, keep the last ones. */
        val rooms: JSONArray? = null,
    )

    /** Whom the camera knows, for the hub: [people] are objects with id, name, samples and digest (no sets); [faceModel] is the camera's face model. */
    class People(val people: JSONArray, val faceModel: String)

    /** The version of the people of the house this phone has loaded into its camera. */
    var peopleRev: Int
        get() = prefs.getInt(KEY_PEOPLE_REV, 0)
        set(value) { prefs.edit().putInt(KEY_PEOPLE_REV, value).apply() }

    /**
     * One contact with the hub: registers (first time in this run, or when the hub does not know this phone), tells it which
     * settings are applied, whom the camera knows ([people]; null when the camera did not answer) and how the commands ended
     * ([done]), and takes the settings, the commands and what to do about the people. [Result.failure] when the hub did not
     * answer; then nothing of [done] reached it.
     */
    fun sync(people: People?, done: List<Done>): Result<Reply> {
        val base = url.ifEmpty { return Result.success(Reply(null, emptyList())) }
        val applied = prefs.getInt(KEY_APPLIED, 0)
        val report = JSONObject().put("device", deviceId).put("applied", applied)
            .put("done", JSONArray().apply { done.forEach { put(JSONObject().put("id", it.id).put("ok", it.error == null).put("error", it.error ?: JSONObject.NULL)) } })
        if (people != null) report.put("people", people.people).put("faceModel", people.faceModel)
        if (!registered && !register(base)) return failure(null)
        var reply = call("POST", "$base/api/device/sync", report)
        if (reply?.first == 404) {
            // The hub forgot this phone (its database was made anew): introduce it again and repeat.
            registered = false
            if (!register(base)) return failure(null)
            reply = call("POST", "$base/api/device/sync", report)
        }
        if (reply == null || reply.first != 200) return failure(reply)
        val body = reply.second
        val list = body.optJSONArray("commands") ?: JSONArray()
        val commands = List(list.length()) { list.getJSONObject(it) }.map {
            Command(it.getLong("id"), it.getString("kind"), it.optString("person"), it.optString("to"), if (it.isNull("into")) null else it.optString("into"))
        }
        val asked = body.optJSONArray("upload") ?: JSONArray()
        val upload = List(asked.length()) { asked.getString(it) }
        val peopleRev = body.optInt("peopleRev", 0)
        // Only the settings are kept, the commands are not: they come again until this phone reports them done.
        val config = JSONObject().put("rev", body.optInt("rev", 0)).put("values", body.optJSONObject("values") ?: JSONObject())
        val place = body.optJSONObject("weatherPlace")
        val weather = body.optJSONObject("weather")
        val texts = body.optJSONArray("transcripts")?.let { a -> List(a.length()) { a.getJSONObject(it) }.associate { it.optString("id") to it.optString("text") } }.orEmpty()
        val roles = body.optJSONArray("roles")?.let { a ->
            List(a.length()) { a.getJSONObject(it) }.associate { r -> r.optString("person") to r.optJSONArray("words").let { w -> List(w?.length() ?: 0) { w!!.getString(it) } } }
        }
        val characters = body.optJSONObject("characters")?.let { obj ->
            obj.keys().asSequence().associateWith { obj.optString(it) }
        }
        val accessories = parseAccessories(body)
        val rooms = body.optJSONArray("rooms")
        if (config.toString() == stored().toString()) return Result.success(Reply(null, commands, peopleRev, upload, place, weather, texts, roles, characters, accessories, rooms))
        prefs.edit().putString(KEY_CONFIG, config.toString()).apply()
        return Result.success(Reply(config, commands, peopleRev, upload, place, weather, texts, roles, characters, accessories, rooms))
    }

    /** Hands the hub the sets of the people it asked for (the camera's export, filtered). The sets are never logged. */
    fun upload(people: JSONArray, faceModel: String): Boolean {
        val base = url.ifEmpty { return false }
        val body = JSONObject().put("device", deviceId).put("faceModel", faceModel).put("people", people)
        return call("POST", "$base/api/device/people/upload", body, readTimeoutMs = 20_000)?.first == 204
    }

    /** Hands the hub the sound of a voice note (WAV) for transcription. True when the hub has it (now or before). */
    fun uploadNote(id: String, to: String, from: String, createdMs: Long, durationMs: Long, wav: ByteArray): Boolean {
        val base = url.ifEmpty { return false }
        val q = "device=$deviceId&to=${java.net.URLEncoder.encode(to, "UTF-8")}&from=${java.net.URLEncoder.encode(from, "UTF-8")}&createdMs=$createdMs&durationMs=$durationMs"
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$base/api/device/notes/$id?$q").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 3000; readTimeout = 20_000; doOutput = true
                setRequestProperty("Content-Type", "audio/wav")
                outputStream.use { it.write(wav) }
            }
            connection.responseCode in 200..201
        } catch (_: Exception) { false }
        finally { connection?.disconnect() }
    }

    /**
     * Hands the hub a message for the speaker of a room (WAV): it is written out and spoken there. Returns the hub's verdict
     * (`spoken`, `unclear`, `no_groq`, `no_home`, `unknown_room`, `error`) with what it made of the voice, or null when the hub did not answer.
     */
    fun announce(roomId: String, from: String, fromId: String, wav: ByteArray): Pair<String, String?>? =
        postVoice("/api/device/announce/${java.net.URLEncoder.encode(roomId, "UTF-8")}", from, fromId, wav)?.let { it.optString("status").ifEmpty { "error" } to it.text("text") }

    /**
     * Hands the hub what was said after «запомни» (`remember`), «вспомни» / «где лежит» (`find`) or «забудь» (`forget`) (WAV). Returns the verdict (`saved`, `updated`,
     * `found`, `not_found`, `unclear`, `no_groq`, `no_memory`, `error`) with the text and, for `updated`, the earlier
     * record; null when the hub did not answer.
     */
    fun things(kind: String, from: String, fromId: String, wav: ByteArray): ThingReply? =
        postVoice("/api/device/things/$kind", from, fromId, wav)?.let { ThingReply(it.optString("status").ifEmpty { "error" }, it.text("text"), it.text("previous")) }

    /** The run the next request belongs to (set by the profiler): sent as a header so the hub files its own timings under it. */
    @Volatile var traceRun: String = ""

    /** Stage marks for the hub's profiling page; no answer is needed, a lost batch is lost. */
    fun sendTrace(events: JSONArray) {
        val base = url.ifEmpty { return }
        call("POST", "$base/api/device/trace", JSONObject().put("device", deviceId).put("events", events))
    }

    private fun JSONObject.text(name: String): String? = optString(name).takeIf { it.isNotEmpty() && !isNull(name) }

    /** A recorded voice for the hub to make sense of: the hub's JSON answer, `{"status": "error"}` for an HTTP error, null when it did not answer. */
    private fun postVoice(path: String, from: String, fromId: String, wav: ByteArray): JSONObject? {
        val base = url.ifEmpty { return null }
        val q = "from=${java.net.URLEncoder.encode(from, "UTF-8")}&fromId=${java.net.URLEncoder.encode(fromId, "UTF-8")}"
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$base$path?$q").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 3000; readTimeout = 30_000; doOutput = true
                setRequestProperty("Content-Type", "audio/wav")
                traceRun.takeIf { it.isNotEmpty() }?.let { setRequestProperty("X-Trace-Run", it) }
                outputStream.use { it.write(wav) }
            }
            if (connection.responseCode != 200) return JSONObject().put("status", "error")
            JSONObject(connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        } catch (_: Exception) { null }
        finally { connection?.disconnect() }
    }

    /** The people of the house with their sets ({rev, faceModel, people}), or null when the hub did not give them. */
    fun download(): JSONObject? {
        val base = url.ifEmpty { return null }
        val reply = call("POST", "$base/api/device/people/download", JSONObject().put("device", deviceId), readTimeoutMs = 20_000)
        return reply?.takeIf { it.first == 200 }?.second
    }

    /** Fresh choices without registering, weather, face vectors or the 30-second sync cycle. */
    class Appearance(val characters: Map<String, String>, val accessories: Map<String, Map<String, String>>?)

    private fun parseAccessories(body: JSONObject): Map<String, Map<String, String>>? = body.optJSONObject("accessories")?.let { all ->
        all.keys().asSequence().associateWith { person ->
            all.optJSONObject(person)?.let { row -> row.keys().asSequence().associateWith { row.optString(it) } }.orEmpty()
        }
    }

    private fun appearance(body: JSONObject): Appearance? {
        val characters = body.optJSONObject("characters") ?: return null
        return Appearance(characters.keys().asSequence().associateWith { characters.optString(it) }, parseAccessories(body))
    }

    fun characters(): Appearance? {
        val base = url.ifEmpty { return null }
        val response = call("GET", "$base/api/characters", null, readTimeoutMs = 1200, connectTimeoutMs = 1000)
            ?.takeIf { it.first == 200 }?.second ?: return null
        return appearance(response)
    }

    /** Null/5xx stays queued; 409 means the personal character changed while the edit was offline. */
    fun setAccessory(edit: AccessoryEdit): Pair<Int, Appearance?>? {
        val base = url.ifEmpty { return null }
        val body = JSONObject().put("person", edit.person).put("character", edit.character).put("accessory", edit.accessory.id)
        val reply = call("POST", "$base/api/characters/accessory", body, readTimeoutMs = 1200, connectTimeoutMs = 1000) ?: return null
        return reply.first to appearance(reply.second)
    }

    private fun register(base: String): Boolean {
        registered = call("POST", "$base/api/device/register", registration())?.first == 200
        return registered
    }

    /** Ask the current provider for this person's current sign. Only today's success is cached. */
    fun horoscope(person: String, forGreeting: Boolean = false): Pair<String, DailyHoroscope?> {
        val base = url
        val cacheKey = "horoscope:$base:$person"
        val greetingKey = "horoscope_greeting:$base:$person"
        fun reading(body: JSONObject?): DailyHoroscope? = body?.takeIf { it.optString("status") == "ok" }?.let {
            DailyHoroscope(it.optString("person"), it.optString("sign"), it.optString("sign_name"),
                it.optString("text"), it.optString("source_name"), it.optLong("fetched_at_ms"), it.optLong("valid_until_ms"),
                it.optString("source_speech_name").takeIf { name -> name.isNotBlank() && name != "null" } ?: it.optString("source_name"),
                it.optBoolean("greeting_enabled", false))
                .takeIf { value -> value.usableFor(person, System.currentTimeMillis()) }
        }
        val reply = if (base.isEmpty()) null else call("POST", "$base/api/horoscope",
            JSONObject().put("person", person).put("for_greeting", forGreeting),
            readTimeoutMs = if (forGreeting) 7000 else 8000, connectTimeoutMs = if (forGreeting) 1000 else 3000)
        if (reply?.first == 200) {
            val body = reply.second
            val allowed = body.optBoolean("greeting_enabled", false)
            prefs.edit().putBoolean(greetingKey, allowed).apply()
            // Opting out affects automatic delivery only; retain the command's cached forecast.
            if (forGreeting && !allowed) return "greeting_disabled" to null
            val value = reading(body)
            if (value != null) {
                prefs.edit().putString(cacheKey, body.toString()).apply()
                return "ok" to value
            }
            // A changed/cleared sign or disabled integration supersedes the offline copy immediately.
            prefs.edit().remove(cacheKey).apply()
            return body.optString("status").takeIf { it != "ok" }.orEmpty() to null
        }
        val cached = runCatching { JSONObject(prefs.getString(cacheKey, "{}") ?: "{}") }.getOrNull()
        if (forGreeting && !prefs.getBoolean(greetingKey, false)) return "greeting_disabled" to null
        reading(cached)?.let { return "ok" to it }
        prefs.edit().remove(cacheKey).apply()
        return (if (base.isEmpty()) "hub_missing" else "unavailable") to null
    }

    private fun failure(reply: Pair<Int, JSONObject>?): Result<Reply> = Result.failure(IllegalStateException(reply?.let { "ответ ${it.first}" } ?: "нет связи"))

    private fun registration(): JSONObject {
        val settings = JSONArray()
        for (s in HubSettings.all) settings.put(
            JSONObject().put("module", s.module).put("key", s.key).put("type", "int").put("title", s.title).put("description", s.description)
                .put("unit", s.unit).put("min", s.min).put("max", s.max).put("default", s.default),
        )
        return JSONObject().put("id", deviceId).put("name", name).put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("appVersion", BuildConfig.VERSION_NAME).put("settings", settings)
    }

    private fun call(method: String, address: String, body: JSONObject?, readTimeoutMs: Int = 5000, connectTimeoutMs: Int = 3000): Pair<Int, JSONObject>? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(address).openConnection() as HttpURLConnection).apply {
                requestMethod = method; connectTimeout = connectTimeoutMs; readTimeout = readTimeoutMs
                if (method == "GET") { useCaches = false; setRequestProperty("Cache-Control", "no-cache") }
                if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.use { it.write(body.toString().toByteArray()) } }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            code to (runCatching { JSONObject(text) }.getOrDefault(JSONObject()))
        } catch (_: Exception) { null }
        finally { connection?.disconnect() }
    }

    private companion object { const val KEY_URL = "hub_url"; const val KEY_DEVICE = "hub_device_id"; const val KEY_CONFIG = "hub_config"; const val KEY_PEOPLE_REV = "hub_people_rev"; const val KEY_APPLIED = "hub_applied_rev" }
}
