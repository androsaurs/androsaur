package dev.hram.androsaur

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import dev.hram.androsaur.hub.HubSettings
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
        val applied = stored().optInt("rev", 0)
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
        if (config.toString() == stored().toString()) return Result.success(Reply(null, commands, peopleRev, upload, place, weather))
        prefs.edit().putString(KEY_CONFIG, config.toString()).apply()
        return Result.success(Reply(config, commands, peopleRev, upload, place, weather))
    }

    /** Hands the hub the sets of the people it asked for (the camera's export, filtered). The sets are never logged. */
    fun upload(people: JSONArray, faceModel: String): Boolean {
        val base = url.ifEmpty { return false }
        val body = JSONObject().put("device", deviceId).put("faceModel", faceModel).put("people", people)
        return call("POST", "$base/api/device/people/upload", body, readTimeoutMs = 20_000)?.first == 204
    }

    /** The people of the house with their sets ({rev, faceModel, people}), or null when the hub did not give them. */
    fun download(): JSONObject? {
        val base = url.ifEmpty { return null }
        val reply = call("POST", "$base/api/device/people/download", JSONObject().put("device", deviceId), readTimeoutMs = 20_000)
        return reply?.takeIf { it.first == 200 }?.second
    }

    private fun register(base: String): Boolean {
        registered = call("POST", "$base/api/device/register", registration())?.first == 200
        return registered
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

    private fun call(method: String, address: String, body: JSONObject?, readTimeoutMs: Int = 5000): Pair<Int, JSONObject>? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(address).openConnection() as HttpURLConnection).apply {
                requestMethod = method; connectTimeout = 3000; readTimeout = readTimeoutMs
                if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.use { it.write(body.toString().toByteArray()) } }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            code to (runCatching { JSONObject(text) }.getOrDefault(JSONObject()))
        } catch (_: Exception) { null }
        finally { connection?.disconnect() }
    }

    private companion object { const val KEY_URL = "hub_url"; const val KEY_DEVICE = "hub_device_id"; const val KEY_CONFIG = "hub_config"; const val KEY_PEOPLE_REV = "hub_people_rev" }
}
