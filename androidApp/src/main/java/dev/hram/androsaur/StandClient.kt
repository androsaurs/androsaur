package dev.hram.androsaur

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.hram.androsaur.stand.StandIdentity
import dev.hram.androsaur.stand.StandProtocol
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.net.URL
import java.io.File

/** Secrets stay encrypted with a non-exportable Android Keystore AES key; no toString of credentials. */
class StandStore(context: Context) {
    private val prefs = context.getSharedPreferences("stand", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("androsaur.stand", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("androsaur.stand", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): JSONObject? {
        val stored = prefs.getString("binding", null) ?: return null
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }
    @Synchronized fun write(binding: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(binding.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("binding", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }
    @Synchronized fun forget() { check(prefs.edit().remove("binding").commit()) }
    companion object {
        fun randomKey() = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        fun identity(binding: JSONObject) = StandIdentity(binding.getString("id"), binding.getString("fingerprint"))
    }
}

class StandClient(private val store: StandStore) {
    /** Verifies pin during the TLS handshake, before sending an Authorization header. No redirects. */
    fun call(ip: String, path: String, body: JSONObject? = null, timeoutMs: Int = 3000): JSONObject {
        require(path in setOf("/v1/status", "/v1/hub", "/v1/policy", "/v1/servos/center", "/v1/servos/stop"))
        return request(ip, path, body = body, timeoutMs = timeoutMs)
    }

    fun uploadFirmware(ip: String, image: File, sha256: String): JSONObject {
        require(image.length() in 288..4L * 1024 * 1024)
        require(Regex("[0-9a-f]{64}").matches(sha256))
        return request(ip, "/v1/ota", image = image, hash = sha256, timeoutMs = 15_000)
    }

    fun confirmFirmware(ip: String, elfSha256: String): JSONObject {
        require(Regex("[0-9a-f]{64}").matches(elfSha256))
        return request(ip, "/v1/ota/confirm", hash = elfSha256, timeoutMs = 15_000)
    }

    private fun request(ip: String, path: String, body: JSONObject? = null, image: File? = null, hash: String? = null, timeoutMs: Int = 3000): JSONObject {
        require(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}").matches(ip))
        val binding = checkNotNull(store.read())
        val identity = StandStore.identity(binding)
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw java.security.cert.CertificateException("client certificate not supported")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty() || !MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(chain[0].encoded), identity.fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()))
                    throw java.security.cert.CertificateException("stand certificate changed")
            }
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
        val conn = URL("https://$ip:${StandProtocol.PORT}$path").openConnection() as HttpsURLConnection
        try {
            conn.sslSocketFactory = ssl.socketFactory
            // The explicit BLE-pinned certificate identifies the stand, not the DHCP address.
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session ->
                val cert = session.peerCertificates.firstOrNull() as? X509Certificate
                cert != null && MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(cert.encoded), identity.fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            }
            conn.instanceFollowRedirects = false; conn.connectTimeout = timeoutMs; conn.readTimeout = timeoutMs
            if (image != null) conn.readTimeout = 180_000
            conn.setRequestProperty("Authorization", "Bearer ${binding.getString("key")}")
            if (image != null) {
                conn.requestMethod = "POST"; conn.doOutput = true
                conn.setFixedLengthStreamingMode(image.length())
                conn.setRequestProperty("Content-Type", "application/octet-stream")
                conn.setRequestProperty("X-Stand-Id", identity.id)
                conn.setRequestProperty("X-Image-SHA256", checkNotNull(hash))
                conn.outputStream.use { output -> image.inputStream().use { it.copyTo(output, 4096) } }
            } else if (path == "/v1/ota/confirm") {
                conn.requestMethod = "POST"; conn.doOutput = true
                conn.setFixedLengthStreamingMode(0)
                conn.setRequestProperty("X-ELF-SHA256", checkNotNull(hash))
                conn.outputStream.close()
            } else if (body != null) {
                conn.requestMethod = "POST"; conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            if (conn.responseCode != 200) throw StandHttpException(conn.responseCode)
            val buffer = ByteArray(8193)
            val count = conn.inputStream.use { input ->
                var count = 0
                while (count < buffer.size) { val n = input.read(buffer, count, buffer.size - count); if (n < 0) break; count += n }
                count
            }
            check(count <= 8192)
            val reply = JSONObject(String(buffer, 0, count, Charsets.UTF_8))
            if (path in setOf("/v1/status", "/v1/policy", "/v1/servos/center", "/v1/servos/stop")) {
                check(reply.getString("id") == identity.id)
                check(reply.getString("phoneId") == binding.getString("phoneId"))
            }
            return reply
        } finally { conn.disconnect() }
    }
}

class StandHttpException(val status: Int) : java.io.IOException("stand response $status")
