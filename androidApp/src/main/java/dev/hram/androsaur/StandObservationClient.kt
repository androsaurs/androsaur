package dev.hram.androsaur

import android.os.SystemClock
import dev.hram.androsaur.stand.TrackingObservation
import dev.hram.androsaur.stand.BatteryObservation
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** One HTTP/1.1 TLS pool for fresh observations; never a TLS handshake or an unbounded queue per frame. */
internal class StandObservationClient(private val binding: JSONObject) : AutoCloseable {
    private data class Pending(val session: String, val sample: TrackingObservation)
    private data class PendingBattery(val session: String, val sample: BatteryObservation)
    private fun batteryJson(session: String, sample: BatteryObservation, now: Long) = JSONObject()
        .put("v", 1).put("session", session).put("seq", sample.seq).put("ageMs", sample.age(now))
        .put("percent", sample.percent).put("charging", sample.charging)
    private var warmAddress: String? = null
    private val identity = StandStore.identity(binding)
    private val pin = identity.fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun matches(cert: X509Certificate) = MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(cert.encoded), pin)
    private val trust = object : X509TrustManager {
        override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw java.security.cert.CertificateException("client certificates not supported")
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            if (chain.isEmpty() || !matches(chain[0])) throw java.security.cert.CertificateException("stand certificate changed")
        }
    }
    private val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
    private val http = OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, trust)
        .hostnameVerifier { _, session -> (session.peerCertificates.firstOrNull() as? X509Certificate)?.let(::matches) == true }
        .protocols(listOf(Protocol.HTTP_1_1)).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(700, TimeUnit.MILLISECONDS)
        .readTimeout(2, TimeUnit.SECONDS).callTimeout(2500, TimeUnit.MILLISECONDS)
        .addNetworkInterceptor { chain ->
            val pending = chain.request().tag(Pending::class.java)
            val battery = chain.request().tag(PendingBattery::class.java)
            val request = if (battery != null) {
                val now = SystemClock.elapsedRealtime()
                if (battery.sample.age(now) > 60_000) throw StaleObservationException()
                val body = batteryJson(battery.session, battery.sample, now).toString().toRequestBody("application/json".toMediaType())
                chain.request().newBuilder().post(body).header("Content-Length", body.contentLength().toString())
                    .removeHeader("Transfer-Encoding").build()
            } else if (pending == null) chain.request() else {
                val now = SystemClock.elapsedRealtime()
                // This runs AFTER TLS connection establishment, not before a potentially slow handshake.
                if (pending.sample.age(now) > 500) throw StaleObservationException()
                val body = trackingJson(pending.session, pending.sample, now).toString()
                    .toRequestBody("application/json".toMediaType())
                chain.request().newBuilder().post(body).header("Content-Length", body.contentLength().toString())
                    .removeHeader("Transfer-Encoding").build()
            }
            chain.proceed(request)
        }.build()

    fun belongsTo(other: JSONObject) = identity == StandStore.identity(other) && binding.optString("key") == other.optString("key")

    fun isWarm(ip: String) = warmAddress == ip

    fun prepare(ip: String) {
        require(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}").matches(ip))
        val request = Request.Builder().url("https://$ip:8443/v1/status")
            .header("Authorization", "Bearer ${binding.getString("key")}").build()
        http.newBuilder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                check(response.code == 200)
                val reply = JSONObject(response.body?.string() ?: error("missing status"))
                check(reply.optString("id") == identity.id && reply.optString("phoneId") == binding.getString("phoneId"))
            }
        warmAddress = ip
    }

    fun send(ip: String, session: String, sample: TrackingObservation) {
        require(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}").matches(ip))
        val request = Request.Builder().url("https://$ip:8443/v1/tracking")
            .header("Authorization", "Bearer ${binding.getString("key")}")
            .tag(Pending::class.java, Pending(session, sample))
            .post(trackingJson(session, sample, SystemClock.elapsedRealtime()).toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            check(response.code == 200)
            val body = response.body ?: error("missing response")
            require(body.contentLength() in -1..8192)
            val reply = JSONObject(body.string())
            check(reply.optBoolean("ok") && reply.optString("id") == identity.id && reply.optString("phoneId") == binding.getString("phoneId"))
        }
    }

    fun sendBattery(ip: String, session: String, sample: BatteryObservation) {
        require(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}").matches(ip))
        val request = Request.Builder().url("https://$ip:8443/v1/battery")
            .header("Authorization", "Bearer ${binding.getString("key")}")
            .tag(PendingBattery::class.java, PendingBattery(session, sample))
            .post(batteryJson(session, sample, SystemClock.elapsedRealtime()).toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            check(response.code == 200)
            val body = response.body ?: error("missing response")
            require(body.contentLength() in -1..8192)
            val reply = JSONObject(body.string())
            check(reply.optBoolean("ok") && reply.optString("id") == identity.id && reply.optString("phoneId") == binding.getString("phoneId"))
        }
    }

    override fun close() { http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
}

internal class StaleObservationException : IOException("observation expired during connection setup")
