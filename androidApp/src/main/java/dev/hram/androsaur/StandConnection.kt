package dev.hram.androsaur

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.ParcelUuid
import com.espressif.provisioning.Session
import com.espressif.provisioning.listeners.ResponseListener
import com.espressif.provisioning.security.Security2
import com.espressif.provisioning.transport.BLETransport
import dev.hram.androsaur.stand.StandProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class StandUi(val message: String = "Подставка не подключена", val busy: Boolean = false,
    val devices: List<Pair<String, String>> = emptyList(), val paired: Boolean = false, val online: Boolean = false,
    val labelName: String = "", val recoveryAvailable: Boolean = false, val networks: List<String> = emptyList(),
    val checked: Boolean = false, val seenAt: Long? = null)

/** This object belongs to one Brain lifecycle; cancelling run stops discovery and closes BLE. */
@SuppressLint("MissingPermission")
class StandConnection(private val context: Context, private val hub: HubClient) {
    private val store = StandStore(context)
    private val client = StandClient(store)
    private val state = MutableStateFlow(StandUi(paired = runCatching { store.read() != null }.getOrDefault(false)))
    val ui = state.asStateFlow()
    private val found = java.util.concurrent.ConcurrentHashMap<String, BluetoothDevice>()
    private val names = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val bluetooth get() = context.getSystemService(BluetoothManager::class.java).adapter
    private var scan: ScanCallback? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopScanning = Runnable { stopScan() }
    private var ip: String? = null
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var label: dev.hram.androsaur.stand.StandSetupLabel? = null
    fun bluetoothEnabled(): Boolean = runCatching { bluetooth?.isEnabled == true }.getOrDefault(false)
    fun savedSsid(): String = runCatching { store.read()?.optString("ssid").orEmpty() }.getOrDefault("")

    /** Public telemetry only: no setup, owner or delegated credentials leave the binding. */
    fun hubReport(): JSONObject? = runCatching {
        val current = state.value
        val binding = store.read()
        if (!current.paired || binding?.has("id") != true) JSONObject().put("state", "unpaired")
        else {
            val age = current.seenAt?.let { (android.os.SystemClock.elapsedRealtime() - it).coerceAtLeast(0) }
            val status = when {
                current.online && age != null && age <= 45_000 -> "online"
                !current.checked -> "checking"
                else -> "offline"
            }
            JSONObject().put("id", binding.getString("id")).put("state", status)
                .put("seenAgoMs", age ?: JSONObject.NULL)
        }
    }.getOrNull()

    fun acceptLabel(raw: String): Boolean {
        return try {
            require(raw.length <= 512)
            val json = JSONObject(raw)
            require(json.getString("kind") == "androsaur-stand" && json.getInt("v") == 1)
            val parsed = dev.hram.androsaur.stand.StandSetupLabel(
                dev.hram.androsaur.stand.StandIdentity(json.getString("id"), json.getString("fingerprint")),
                json.getString("name"), json.getString("pop"))
            label = parsed
            state.value = state.value.copy(labelName = parsed.name, message = "QR прочитан. Укажите домашнюю сеть Wi-Fi")
            true
        } catch (_: Exception) { state.value = state.value.copy(message = "Это не QR-код подставки Androsaur"); false }
    }

    private suspend fun <T> withSession(address: String, code: String, action: suspend (Session) -> T): T {
        val transport = BLETransport(context)
        try {
            val device = found[address] ?: bluetooth?.getRemoteDevice(address) ?: error("Bluetooth недоступен")
            transport.connect(device, UUID.fromString(StandProtocol.BLE_SERVICE))
            withTimeout(20_000) { while (transport.versionInfo == null) delay(100) }
            val version = JSONObject(transport.versionInfo).getJSONObject("prov")
            val caps = version.optJSONArray("cap")
            check(StandProtocol.secureProvisioning(version.optInt("sec_ver", -1), if (caps == null) emptyList() else List(caps.length()) { caps.getString(it) }, code, version.optInt("sec_patch_ver", -1))) { "Подставке нужно обновление прошивки" }
            val session = Session(transport, Security2("androsaur", code, 1))
            withTimeout(15_000) { suspendCancellableCoroutine<Unit> { continuation ->
                session.init(null, object : Session.SessionListener {
                    override fun OnSessionEstablished() { if (continuation.isActive) continuation.resume(Unit) }
                    override fun OnSessionEstablishFailed(e: Exception) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("QR не подошёл к этой подставке или связь прервалась")) }
                })
            } }
            return action(session)
        } finally { runCatching { transport.disconnect() } }
    }

    private suspend fun exchange(session: Session, request: JSONObject): JSONObject {
        val bytes = withTimeout(15_000) { suspendCancellableCoroutine<ByteArray> { continuation ->
            session.sendDataToDevice("stand-owner", request.toString().toByteArray(Charsets.UTF_8), object : ResponseListener {
                override fun onSuccess(data: ByteArray) { if (continuation.isActive) continuation.resume(data) }
                override fun onFailure(e: Exception) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Связь прервалась. Повторите подключение")) }
            })
        } }
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    suspend fun networks(address: String) {
        val chosen = label ?: return
        mutex.lock()
        try {
            check(names[address] == chosen.name)
            state.value = state.value.copy(busy = true, message = "Подставка ищет сети Wi-Fi 2,4 ГГц…")
            stopScan()
            withSession(address, chosen.secret) { session ->
                val info = exchange(session, JSONObject().put("op", "info"))
                check(info.optString("id") == chosen.identity.id && info.optString("fingerprint") == chosen.identity.fingerprint)
                val reply = exchange(session, JSONObject().put("op", "networks"))
                val values = reply.optJSONArray("networks")
                state.value = state.value.copy(networks = if (values == null) emptyList() else List(values.length()) { values.getString(it) }, message = "Выберите домашнюю сеть и введите пароль")
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { state.value = state.value.copy(message = "Можно ввести имя сети Wi-Fi вручную") }
        finally { state.value = state.value.copy(busy = false); mutex.unlock() }
    }

    /** Read-only hardware diagnostic, invoked only by the DUMP-protected debug receiver. */
    internal suspend fun probeLabel(): Boolean {
        mutex.lock()
        try {
            val chosen = checkNotNull(label)
            android.util.Log.i("StandProbe", "stage: scan")
            scan()
            val address = withTimeout(15_000) {
                while (names.values.none { it == chosen.name }) delay(100)
                names.entries.first { it.value == chosen.name }.key
            }
            stopScan()
            android.util.Log.i("StandProbe", "stage: authenticated identity")
            suspend fun validProbe(code: String): Boolean = withSession(address, code) { session ->
                val info = exchange(session, JSONObject().put("op", "info"))
                info.optBoolean("ok") && info.optString("id") == chosen.identity.id && info.optString("fingerprint") == chosen.identity.fingerprint
            }
            check(validProbe(chosen.secret))
            delay(700)
            val wrong = (if (chosen.secret[0] == 'a') "b" else "a") + chosen.secret.drop(1)
            android.util.Log.i("StandProbe", "stage: reject wrong secret")
            val incorrectlyAccepted = try { validProbe(wrong) } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                false
            } catch (_: Exception) { false }
            check(!incorrectlyAccepted)
            delay(700)
            android.util.Log.i("StandProbe", "stage: reconnect")
            check(validProbe(chosen.secret))
            return true
        } finally { stopScan(); mutex.unlock() }
    }

    fun scan() {
        try {
            stopScan(); found.clear(); names.clear()
            val scanner = bluetooth?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: error("Bluetooth выключен")
            val callback = object : ScanCallback() {
                override fun onScanResult(type: Int, result: ScanResult) {
                    val name = result.scanRecord?.deviceName ?: return
                    if (!name.startsWith("ANDRO_")) return
                    found[result.device.address] = result.device
                    names[result.device.address] = name
                    state.value = state.value.copy(devices = names.entries.map { it.key to it.value }.sortedBy { it.second })
                }
                override fun onScanFailed(code: Int) { state.value = state.value.copy(message = "Поиск недоступен ($code)") }
            }
            scan = callback
            scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(StandProtocol.BLE_SERVICE))).build()), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            handler.postDelayed(stopScanning, 20_000)
            state.value = state.value.copy(message = "Ищу подставки рядом…", devices = emptyList())
        } catch (_: Exception) { state.value = state.value.copy(message = "Включите Bluetooth и разрешите поиск устройств") }
    }
    fun stopScan() { handler.removeCallbacks(stopScanning); scan?.let { runCatching { bluetooth?.bluetoothLeScanner?.stopScan(it) } }; scan = null }

    suspend fun pair(address: String, ssid: String, password: String, recover: Boolean = false): Boolean {
        mutex.lock()
        var connected = false
        try {
            require(ssid.toByteArray().size in 1..32 && password.toByteArray().size <= 63) { "Проверьте имя и пароль сети 2,4 ГГц" }
            val saved = store.read()
            check(saved == null || saved.optString("ble") == address) { "Сначала отвяжите прежнюю подставку" }
            val chosen = label ?: saved?.takeIf { it.has("setupCode") && it.has("id") }?.let {
                dev.hram.androsaur.stand.StandSetupLabel(StandStore.identity(it), "ANDRO_" + it.getString("id").takeLast(6), it.getString("setupCode"))
            } ?: error("Отсканируйте QR-код на подставке")
            val device = found[address] ?: bluetooth?.getRemoteDevice(address) ?: error("Включите Bluetooth")
            check((names[address] ?: device.name) == chosen.name) { "QR относится к другой подставке. Выберите ${chosen.name}" }
            stopScan(); state.value = state.value.copy(busy = true, recoveryAvailable = false, message = "Подключаю подставку…")
            withSession(address, chosen.secret) { session ->
                val info = exchange(session, JSONObject().put("op", "info"))
                check(info.optBoolean("ok") && info.getString("id") == chosen.identity.id && info.getString("fingerprint") == chosen.identity.fingerprint) { "QR не соответствует этой подставке" }
                val owner = info.optString("phoneId")
                if (owner.isNotEmpty() && owner != hub.identity && !recover) {
                    state.value = state.value.copy(recoveryAvailable = info.optBoolean("recovery"))
                    error("Подставка связана с другим телефоном. Для переноса трижды выключите и включите питание с паузами 3–5 секунд, затем повторите подключение")
                }
                check(!recover || info.optBoolean("recovery")) { "Сначала трижды выключите и включите питание подставки с паузами 3–5 секунд" }
                val binding = saved ?: JSONObject().put("key", StandStore.randomKey()).put("hubKey", StandStore.randomKey()).put("ble", address).put("phoneId", hub.identity)
                check(StandProtocol.mayClaim(saved?.takeIf { it.has("id") }?.let(StandStore::identity), chosen.identity)) { "Сначала отвяжите прежнюю подставку" }
                store.write(binding.put("id", chosen.identity.id).put("fingerprint", chosen.identity.fingerprint).put("setupCode", chosen.secret).put("ssid", ssid))
                val reply = exchange(session, JSONObject().put("phoneId", hub.identity).put("key", binding.getString("key"))
                    .put("ssid", ssid).put("password", password).put("recover", recover))
                check(reply.optBoolean("ok")) { "Не удалось сохранить настройки подставки" }
                check(reply.getString("id") == chosen.identity.id && reply.getString("fingerprint") == chosen.identity.fingerprint)
                state.value = StandUi(message = "Подключено. Ожидаю подставку в домашней сети…", paired = true, labelName = chosen.name)
                connected = true
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { state.value = state.value.copy(message = e.message?.takeIf { e is IllegalArgumentException || e is IllegalStateException } ?: "Не удалось подключить подставку") }
        finally { state.value = state.value.copy(busy = false); mutex.unlock() }
        return connected
    }

    @Suppress("DEPRECATION")
    private suspend fun discover(id: String): String = withTimeoutOrNull(8000) {
        suspendCancellableCoroutine { continuation ->
            val nsd = context.getSystemService(NsdManager::class.java)
            var resolving = false
            lateinit var listener: NsdManager.DiscoveryListener
            fun finish(address: String) {
                if (continuation.isActive) continuation.resume(address)
                runCatching { nsd.stopServiceDiscovery(listener) }
            }
            listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) {}
                override fun onDiscoveryStopped(type: String) {}
                override fun onStartDiscoveryFailed(type: String, code: Int) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Поиск недоступен")) }
                override fun onStopDiscoveryFailed(type: String, code: Int) {}
                override fun onServiceLost(info: NsdServiceInfo) {}
                override fun onServiceFound(info: NsdServiceInfo) {
                    if (resolving || info.serviceName != id) return
                    resolving = true
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(service: NsdServiceInfo, code: Int) { resolving = false }
                        override fun onServiceResolved(service: NsdServiceInfo) {
                            val address = service.host?.hostAddress
                            if (service.port == StandProtocol.PORT && address != null && !address.contains(':')) finish(address) else resolving = false
                        }
                    })
                }
            }
            continuation.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(listener) } }
            nsd.discoverServices(StandProtocol.SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    } ?: throw StandDiscoveryException()

    private suspend fun addressOverBle(binding: JSONObject): String {
        check(bluetoothEnabled()) { "Для поиска адреса подставки включите Bluetooth" }
        val address = binding.getString("ble")
        val code = binding.getString("setupCode")
        return withSession(address, code) { session ->
            val info = exchange(session, JSONObject().put("op", "info"))
            check(info.optBoolean("ok") && info.optString("id") == binding.getString("id") &&
                info.optString("fingerprint") == binding.getString("fingerprint") &&
                info.optString("phoneId") == binding.getString("phoneId")) { "Подставка не подтвердила привязку" }
            info.optString("ip").takeIf { it.isNotEmpty() } ?: throw StandDiscoveryException()
        }
    }

    /** Addresses are hints only: every candidate must pass pinned TLS, owner auth and identity checks. */
    private suspend fun readStatus(binding: JSONObject): Pair<String, JSONObject> {
        val attempted = mutableSetOf<String>()
        suspend fun attempt(address: String): Pair<String, JSONObject>? {
            if (address.isEmpty() || !attempted.add(address)) return null
            return try { address to client.call(address, "/v1/status") }
            catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                android.util.Log.w("StandConnection", "Address attempt failed: ${e.javaClass.simpleName}")
                null
            }
        }
        for (address in listOf(ip.orEmpty(), binding.optString("lastIp"))) {
            attempt(address)?.let { return it }
        }
        val discovered = try { discover(binding.getString("id")) }
        catch (e: Exception) { currentCoroutineContext().ensureActive(); null }
        if (discovered != null) attempt(discovered)?.let { return it }
        // Works without a hub and without multicast, including after DHCP changes.
        android.util.Log.i("StandConnection", "Resolving stand address over authenticated BLE")
        val address = addressOverBle(binding)
        return address to client.call(address, "/v1/status")
    }

    /** Uses the production lookup and pinned client; no ownership/Wi-Fi writes or credential logs. */
    internal suspend fun probeConnection(address: String?, viaBle: Boolean = false): Boolean {
        mutex.lock()
        try {
        val binding = checkNotNull(store.read()) { "No binding" }
        android.util.Log.i("StandProbe", if (viaBle) "stage: authenticated BLE address" else "stage: production network lookup")
        if (address != null || viaBle) client.call(address ?: addressOverBle(binding), "/v1/status")
        else readStatus(binding)
        return true
        } finally { mutex.unlock() }
    }

    suspend fun run() {
        try {
            while (currentCoroutineContext().isActive) {
                if (mutex.tryLock()) try {
                    val binding = store.read()
                    if (binding?.has("id") == true) {
                        val (address, status) = readStatus(binding)
                        if (binding.optString("lastIp") != address) store.write(binding.put("lastIp", address))
                        if (!state.value.online) android.util.Log.i("StandConnection", "Stand connected: authenticated HTTPS status")
                        ip = address
                        state.value = state.value.copy(paired = true, online = true, checked = true, seenAt = android.os.SystemClock.elapsedRealtime(), message = "Подставка на связи · $address · ${status.optString("firmware")}")
                        if (binding.optString("hub") == hub.url && hub.paired) hub.attachStand(binding, address)
                    }
                } catch (e: Exception) {
                    // A timeout inside discovery is a failed attempt, not cancellation of the polling lifecycle.
                    currentCoroutineContext().ensureActive()
                    ip = null
                    android.util.Log.w("StandConnection", "Status unavailable: ${e.javaClass.simpleName}; cause=${e.cause?.javaClass?.simpleName.orEmpty()}")
                    val message = when (e) {
                        is StandDiscoveryException -> "Не нахожу подставку в Wi-Fi · повторю поиск автоматически"
                        is javax.net.ssl.SSLException -> "Не удалось проверить защищённое соединение с подставкой · повторю автоматически"
                        else -> "Подставка не отвечает по Wi-Fi · повторю подключение автоматически"
                    }
                    state.value = state.value.copy(online = false, checked = true, message = message)
                }
                finally { mutex.unlock() }
                delay(15_000)
            }
        } finally { stopScan() }
    }
    suspend fun shareWithHub() {
        mutex.lock()
        try {
            check(hub.paired) { "Сначала подключите телефон к хабу" }
            val address = checkNotNull(ip) { "Дождитесь связи с подставкой" }
            val binding = checkNotNull(store.read())
            check(binding.optString("hub").isEmpty() || binding.optString("hub") == hub.url) { "Сначала отключите прежний хаб" }
            if (binding.optString("hubTarget") != hub.url) {
                // A lost enrollment response must not cause the same key to be reused in another home.
                binding.put("hubKey", StandStore.randomKey()).put("hubTarget", hub.url)
                store.write(binding)
            }
            client.call(address, "/v1/hub", JSONObject().put("key", binding.getString("hubKey")))
            check(hub.attachStand(binding, address)) { "Хаб не принял подставку; повторите подключение" }
            store.write(binding.put("hub", hub.url)); state.value = state.value.copy(message = "Подставка доступна хабу")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { state.value = state.value.copy(message = e.message ?: "Не удалось подключить хаб") }
        finally { mutex.unlock() }
    }
    suspend fun forget() {
        mutex.lock()
        try {
            val binding = store.read() ?: return
            val code = binding.optString("setupCode").ifEmpty { label?.secret ?: error("Сначала отсканируйте QR подставки") }
            state.value = state.value.copy(busy = true, message = "Отвязываю по Bluetooth…")
            stopScan()
            withSession(binding.getString("ble"), code) { session ->
                check(exchange(session, JSONObject().put("op", "release").put("key", binding.getString("key"))).optBoolean("ok")) { "Подставка не подтвердила отвязку" }
            }
            if (binding.optString("hub") == hub.url && hub.paired) hub.detachStand()
            store.forget(); ip = null; label = null; state.value = StandUi(message = "Подставка отвязана и готова к подключению")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { state.value = state.value.copy(message = e.message?.takeIf { e is IllegalStateException } ?: "Подойдите к включённой подставке и повторите отвязку") }
        finally { state.value = state.value.copy(busy = false); mutex.unlock() }
    }
}

private class StandDiscoveryException : java.io.IOException("Stand discovery timed out")
