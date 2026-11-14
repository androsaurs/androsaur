package dev.hram.androsaur

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import kotlinx.coroutines.*

/** Shell DUMP permission required. The owner key stays in the phone's Keystore-backed binding. */
class StandOtaService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            android.app.NotificationChannel("stand-ota-debug", "Обновление подставки", android.app.NotificationManager.IMPORTANCE_LOW))
        val builder = if (android.os.Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(this, "stand-ota-debug")
            else android.app.Notification.Builder(this)
        startForeground(606, builder.setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Проверка подставки").setOngoing(true).build())
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) {
            if (intent?.getStringExtra("op") == "servo-stop") scope.launch {
                try {
                    val store = StandStore(applicationContext)
                    val ip = checkNotNull(store.read()).getString("lastIp")
                    StandClient(store).call(ip, "/v1/servos/stop", JSONObject(), timeoutMs = 15_000)
                    Log.i("StandOta", "servo-stop acknowledged")
                } catch (e: Exception) { Log.e("StandOta", "servo-stop failed: ${e.javaClass.simpleName}") }
            }
            return START_NOT_STICKY
        }
        running = true
        val op = intent?.getStringExtra("op") ?: "upload"
        val viaHost = intent?.getBooleanExtra("via-host", false) == true
        scope.launch {
            try {
                require(op in setOf("upload", "confirm-running", "stage-only", "reject-sha", "reject-image", "reject-confirm", "center", "servo-stop"))
                require(!viaHost || op in setOf("upload", "confirm-running"))
                val store = StandStore(applicationContext)
                val binding = checkNotNull(store.read())
                // Builder-only ADB reverse TLS passthrough; trust/identity checks remain unchanged.
                val ip = if (viaHost) "127.0.0.1" else binding.getString("lastIp")
                val client = StandClient(store)
                fun status() = client.call(ip, "/v1/status", timeoutMs = 15_000)
                val before = status()
                val baseline = before.getJSONObject("ota")
                if (op == "confirm-running") {
                    val file = File(filesDir, "stand-ota.bin")
                    require(file.length() in 288..4L * 1024 * 1024)
                    val expected = file.readBytes().copyOfRange(176, 208).hex()
                    check(baseline.getString("elfSha256") == expected)
                    check(before.getLong("uptimeS") >= 15)
                    check(client.confirmFirmware(ip, expected).getBoolean("ok"))
                    check(status().getJSONObject("ota").getString("state") == "valid")
                    Log.i("StandOta", "PASS: running image identity verified and owner-confirmed")
                    return@launch
                }
                check(baseline.getString("state") == "valid")
                if (op == "center" || op == "servo-stop") {
                    val path = if (op == "center") "/v1/servos/center" else "/v1/servos/stop"
                    val reply = client.call(ip, path, JSONObject(), timeoutMs = 15_000)
                    Log.i("StandOta", "servo-command: op=$op tracking=${reply.getJSONObject("tracking")}")
                    if (op == "center") {
                        delay(5500)
                        val after = status().getJSONObject("tracking")
                        check(!after.getBoolean("outputEnabled"))
                        Log.i("StandOta", "PASS: center command acknowledged, PWM stopped after 5 seconds; tracking=$after")
                    }
                    return@launch
                }
                val image = File(filesDir, "stand-ota.bin")
                require(image.length() in 288..4L * 1024 * 1024)
                val bytes = image.readBytes()
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
                val elf = bytes.copyOfRange(176, 208).hex()
                Log.i("StandOta", "begin: op=$op bytes=${bytes.size} partition=${baseline.getString("partition")}")
                if (op.startsWith("reject-")) {
                    try {
                        when (op) {
                            "reject-sha" -> client.uploadFirmware(ip, image, "0".repeat(64))
                            "reject-confirm" -> client.confirmFirmware(ip, "0".repeat(64))
                            else -> {
                                val bad = File(cacheDir, "stand-ota-invalid.bin")
                                try {
                                    val invalidHeader = bytes.copyOf(288).also { it[0] = 0 }
                                    bad.writeBytes(invalidHeader)
                                    client.uploadFirmware(ip, bad, MessageDigest.getInstance("SHA-256").digest(invalidHeader).hex())
                                } finally { bad.delete() }
                            }
                        }
                        error("invalid request accepted")
                    } catch (e: StandHttpException) { check(e.status == 400) }
                    val after = status().getJSONObject("ota")
                    check(after.getString("partition") == baseline.getString("partition"))
                    check(after.getString("elfSha256") == baseline.getString("elfSha256"))
                    check(after.getString("state") == "valid")
                    Log.i("StandOta", "PASS: $op rejected; running partition unchanged")
                } else {
                    check(client.uploadFirmware(ip, image, digest).getBoolean("ok"))
                    Log.i("StandOta", "accepted; waiting for network reboot")
                    if (op == "upload") {
                        // Wait beyond the scheduled reboot: do not mistake the old status for the candidate.
                        delay(4000)
                        val deadline = SystemClock.elapsedRealtime() + 90_000
                        var confirmed = false
                        while (SystemClock.elapsedRealtime() < deadline) {
                            val status = try { status() }
                                catch (_: java.io.IOException) { delay(2000); continue }
                            val ota = status.getJSONObject("ota")
                            if (ota.getString("partition") == baseline.getString("partition")) { delay(2000); continue }
                            check(ota.getString("elfSha256") == elf) { "unexpected running image" }
                            if (status.getLong("uptimeS") < 15) { delay(2000); continue }
                            val final = try {
                                // Confirmation is idempotent for this exact ELF; uploads/motor commands are not retried.
                                check(client.confirmFirmware(ip, elf).getBoolean("ok"))
                                status().getJSONObject("ota")
                            } catch (_: java.io.IOException) { delay(2000); continue }
                            check(final.getString("state") == "valid" && final.getString("elfSha256") == elf)
                            Log.i("StandOta", "PASS: OTA reboot, image identity, owner confirmation; partition=${final.getString("partition")} version=${final.getString("version")}")
                            confirmed = true
                            break
                        }
                        check(confirmed) { "candidate did not become ready; left unconfirmed for automatic rollback" }
                    }
                }
            } catch (e: Exception) {
                // Never log binding/keys, URLs, headers or arbitrary response bodies.
                Log.e("StandOta", "FAIL: ${e.javaClass.simpleName}; http=${(e as? StandHttpException)?.status ?: 0}")
            } finally { stopSelf() }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
