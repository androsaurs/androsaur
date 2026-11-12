package dev.hram.androsaur

import android.Manifest
import android.bluetooth.BluetoothManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.espressif.provisioning.Session
import com.espressif.provisioning.listeners.ResponseListener
import com.espressif.provisioning.security.Security2
import com.espressif.provisioning.transport.BLETransport
import dev.hram.androsaur.stand.StandProtocol
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Opt-in hardware probe: no claim, no Wi-Fi configuration, no owner/hub key is sent. */
@RunWith(AndroidJUnit4::class)
class StandBleTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(*(
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)))

    @Suppress("MissingPermission")
    @Test fun encryptedEndpointRequiresProofOfPossession() {
        val args = InstrumentationRegistry.getArguments()
        val code = args.getString("pop")
        assumeTrue("physical setup code is required for this opt-in hardware test", code != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hub = HubClient(context, context.getSharedPreferences("stand-test-hub", 0))
        val connection = StandConnection(context, hub)
        try {
            connection.scan()
            val until = System.currentTimeMillis() + 15000
            while (connection.ui.value.devices.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(100)
            val address = connection.ui.value.devices.firstOrNull()?.first
            assertNotNull("stand BLE advertisement not discovered", address)
            connection.stopScan()
            val adapter = context.getSystemService(BluetoothManager::class.java).adapter
            fun probe(pop: String): Boolean {
                val transport = BLETransport(context)
                try {
                    transport.connect(adapter.getRemoteDevice(address), UUID.fromString(StandProtocol.BLE_SERVICE))
                    val deadline = System.currentTimeMillis() + 15000
                    while (transport.versionInfo == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
                    assertNotNull("provisioning version not received", transport.versionInfo)
                    val version = JSONObject(transport.versionInfo).getJSONObject("prov")
                    assertEquals(2, version.getInt("sec_ver"))
                    assertEquals(1, version.getInt("sec_patch_ver"))
                    val session = Session(transport, Security2("androsaur", pop, 1))
                    val established = CompletableFuture<Boolean>()
                    session.init(null, object : Session.SessionListener {
                        override fun OnSessionEstablished() { established.complete(true) }
                        override fun OnSessionEstablishFailed(e: Exception) { established.complete(false) }
                    })
                    if (!established.get(15, TimeUnit.SECONDS)) return false
                    val reply = CompletableFuture<ByteArray>()
                    session.sendDataToDevice("stand-owner", "{\"probe\":true}".toByteArray(), object : ResponseListener {
                        override fun onSuccess(data: ByteArray) { reply.complete(data) }
                        override fun onFailure(e: Exception) { reply.completeExceptionally(IllegalStateException("protected endpoint failed")) }
                    })
                    val json = JSONObject(String(reply.get(15, TimeUnit.SECONDS)))
                    assertFalse(json.getBoolean("ok"))
                    assertEquals("invalid_request", json.getString("error"))
                    return true
                } finally { transport.disconnect(); Thread.sleep(500) }
            }
            assertTrue("correct PoP did not establish a protected session", probe(code!!))
            val acceptedWrong = runCatching { probe("0".repeat(32)) }.getOrDefault(false)
            assertFalse("wrong PoP was accepted", acceptedWrong)
            assertTrue("stand did not recover after the rejected session", probe(code))
        } finally { connection.stopScan() }
    }
}
