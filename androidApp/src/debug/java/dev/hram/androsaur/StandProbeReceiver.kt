package dev.hram.androsaur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*

/** Debug-only, shell DUMP permission required. No ownership/Wi-Fi writes and no credential logs. */
class StandProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "dev.hram.androsaur.DEBUG_STAND_PROBE") return
        val viaBle = intent.getStringExtra("op") == "ble-status"
        val networkProbe = intent.getStringExtra("op") == "status" || viaBle
        val raw = intent.getStringExtra("qr")
        if (!networkProbe && raw == null) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val connection = StandConnection(context.applicationContext, HubClient(context, context.getSharedPreferences("brain", Context.MODE_PRIVATE)))
                if (networkProbe) {
                    check(withTimeout(55_000) { connection.probeConnection(intent.getStringExtra("ip"), viaBle) })
                    Log.i("StandProbe", "PASS: address lookup, pinned TLS, owner authentication, identity")
                } else {
                    check(connection.acceptLabel(checkNotNull(raw)))
                    check(withTimeout(50_000) { connection.probeLabel() })
                    Log.i("StandProbe", "PASS: label, Security2, identity, wrong-secret rejection, reconnect")
                }
            } catch (e: Exception) { Log.e("StandProbe", "FAIL: ${e.javaClass.simpleName}; cause=${e.cause?.javaClass?.simpleName.orEmpty()}") }
            finally { pending.finish() }
        }
    }
}
