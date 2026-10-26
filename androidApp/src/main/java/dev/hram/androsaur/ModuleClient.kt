package dev.hram.androsaur

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One Androsaur module (audio, camera) installed on this same phone. The module hands its port and access code to apps signed with
 * the same key through a provider guarded by a signature permission; after that it is plain HTTP on localhost, the same protocol the
 * module speaks over the network. The access code lives in memory only and is never logged.
 */
class ModuleClient(private val context: Context, val id: String, val label: String, private val pkg: String, private val service: String) {
    private class Link(val port: Int, val token: String)
    @Volatile private var link: Link? = null

    val linked get() = link != null

    /** Asks the module for its port and code. Returns `ready`, `not_installed` or `permission_denied`. */
    fun connect(): String {
        link?.let { return "ready" }
        return try {
            val reply = context.contentResolver.call(Uri.parse("content://$pkg.link"), "link", null, null)
            val token = reply?.getString("token")
            if (reply == null || token.isNullOrEmpty()) "not_installed"
            else { link = Link(reply.getInt("port"), token); "ready" }
        } catch (_: SecurityException) { "permission_denied" }
        catch (_: IllegalArgumentException) { "not_installed" }
        catch (e: Exception) { Log.w(TAG, "link to $id failed: ${e.javaClass.simpleName}"); "not_installed" }
    }

    /** Starts the module's foreground service from this visible app. Returns null on success or an error code. */
    fun start(): String? = try {
        context.startForegroundService(Intent().setClassName(pkg, "$pkg.$service"))
        null
    } catch (_: SecurityException) { "permission_denied" }
    catch (e: IllegalStateException) { Log.w(TAG, "start of $id refused", e); "start_refused" }
    catch (e: Exception) { Log.w(TAG, "start of $id failed", e); "start_failed" }

    /** The HTTP code and JSON of a call, or null when the module does not answer. */
    fun get(path: String): Pair<Int, JSONObject>? = call("GET", path, null)
    fun post(path: String, body: JSONObject): Pair<Int, JSONObject>? = call("POST", path, body)

    /** The raw bytes of a GET (a note's WAV), or null when the module does not answer or says no. */
    fun getBytes(path: String): ByteArray? {
        val l = link ?: return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("http://127.0.0.1:${l.port}$path").openConnection() as HttpURLConnection).apply {
                connectTimeout = 800; readTimeout = 5000
                setRequestProperty("Authorization", "Bearer ${l.token}")
            }
            if (connection.responseCode == 200) connection.inputStream.use { it.readBytes() } else null
        } catch (_: Exception) { null }
        finally { connection?.disconnect() }
    }

    private fun call(method: String, path: String, body: JSONObject?): Pair<Int, JSONObject>? {
        val l = link ?: return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("http://127.0.0.1:${l.port}$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method; connectTimeout = 800; readTimeout = 2500
                setRequestProperty("Authorization", "Bearer ${l.token}")
                if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.use { it.write(body.toString().toByteArray()) } }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            code to (runCatching { JSONObject(text) }.getOrDefault(JSONObject()))
        } catch (_: Exception) { null }
        finally { connection?.disconnect() }
    }

    private companion object { const val TAG = "Androsaur" }
}
