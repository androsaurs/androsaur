package dev.hram.androsaur

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hram.androsaur.stand.WifiQr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun StandPanel(connection: StandConnection, hubPaired: Boolean) {
    val state by connection.ui.collectAsState()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf(connection.savedSsid()) }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var confirmForget by remember { mutableStateOf(false) }
    var confirmRecover by remember { mutableStateOf(false) }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) connection.scan() else localError = "Для подключения нужен Bluetooth"
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            if (connection.bluetoothEnabled()) connection.scan()
            else enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else localError = "Разрешите поиск устройств Bluetooth"
    }
    fun find() {
        localError = null
        permission.launch(if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }
    val qr = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringExtra("qr")?.let { raw ->
            if (connection.acceptLabel(raw)) { open = true; find() }
        }
    }
    val wifiQr = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringExtra("qr")?.let { raw ->
            try {
                val network = WifiQr.parse(raw)
                ssid = network.ssid
                password = network.password
                localError = null
            } catch (error: IllegalArgumentException) { localError = error.message }
        }
    }
    DisposableEffect(Unit) { onDispose { connection.stopScan() } }
    LaunchedEffect(state.devices, state.labelName) {
        val match = state.devices.firstOrNull { it.second == state.labelName }
        if (match != null) selected = match.first
        else if (selected.isEmpty() && state.devices.size == 1) selected = state.devices.first().first
    }
    LaunchedEffect(selected, state.labelName) {
        if (selected.isNotEmpty() && state.labelName.isNotEmpty()) kotlinx.coroutines.withContext(Dispatchers.IO) { connection.networks(selected) }
    }
    LaunchedEffect(state.networks) {
        if (ssid.isEmpty() && state.networks.size == 1) ssid = state.networks.first()
    }
    Text("Подставка", color = Color.White, fontSize = 15.sp)
    Text(state.message, color = if (state.online) Color(0xff63e6a4) else Color(0xffc5cfc9), fontSize = 13.sp)
    if (state.paired) {
        if (hubPaired) TextButton(enabled = state.online && !state.busy, onClick = { scope.launch(Dispatchers.IO) { connection.shareWithHub() } }) { Text("Разрешить управление из хаба") }
        Row {
            TextButton(enabled = !state.busy, onClick = { open = !open; if (open) find() }) { Text("Изменить Wi-Fi") }
            TextButton(enabled = !state.busy, onClick = { confirmForget = true }) { Text("Отвязать") }
        }
    } else Button(enabled = !state.busy, onClick = { open = true; find() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Добавить подставку") }
    if (open) {
        Text("Включите подставку и держите телефон рядом. Подключение проходит по Bluetooth.", color = Color(0xffc5cfc9), fontSize = 13.sp)
        if (state.devices.isEmpty()) TextButton(onClick = { find() }, enabled = !state.busy) { Text("Повторить поиск") }
        state.devices.forEach { (address, name) ->
            FilterChip(selected = selected == address, onClick = { selected = address }, label = { Text(name) })
        }
        if (state.labelName.isNotEmpty()) Text("QR прочитан · ${state.labelName}", color = Color(0xff63e6a4), fontSize = 13.sp)
        OutlinedButton(enabled = !state.busy, onClick = {
            try {
                qr.launch(Intent().setComponent(ComponentName("dev.hram.androsaur.camera", "dev.hram.androsaur.camera.SetupQrActivity")))
            } catch (_: Exception) { localError = "Для чтения QR обновите приложение «Androsaur Камера»" }
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (state.labelName.isEmpty()) "Сканировать QR на подставке" else "Сканировать другой QR") }
        if (selected.isNotEmpty()) {
            OutlinedButton(enabled = !state.busy, onClick = {
                localError = null
                try {
                    wifiQr.launch(Intent().setComponent(ComponentName("dev.hram.androsaur.camera", "dev.hram.androsaur.camera.SetupQrActivity")).putExtra("purpose", "wifi"))
                } catch (_: Exception) { localError = "Для чтения QR обновите приложение «Androsaur Камера»" }
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Сканировать QR Wi-Fi") }
            Text("QR с роутера или экрана другого телефона заполнит сеть и пароль. Нужна сеть 2,4 ГГц.", color = Color(0xffc5cfc9), fontSize = 13.sp)
            state.networks.forEach { network ->
                FilterChip(selected = ssid == network, onClick = { ssid = network }, label = { Text(network) })
            }
            OutlinedTextField(value = ssid, onValueChange = { ssid = it }, enabled = !state.busy, label = { Text("Домашняя сеть Wi-Fi · 2,4 ГГц") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, enabled = !state.busy, label = { Text("Пароль Wi-Fi") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            fun connect(recover: Boolean = false) {
                val wifiPassword = password
                val network = ssid; val target = selected
                scope.launch(Dispatchers.IO) {
                    if (connection.pair(target, network, wifiPassword, recover)) kotlinx.coroutines.withContext(Dispatchers.Main) { password = "" }
                }
            }
            Button(enabled = !state.busy && (state.labelName.isNotEmpty() || state.paired) && ssid.isNotEmpty(), onClick = { connect() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (state.busy) "Подключаю…" else "Подключить")
            }
            if (state.recoveryAvailable) {
                TextButton(enabled = !state.busy && ssid.isNotEmpty(), onClick = { confirmRecover = true }) { Text("Привязать к этому телефону вместо прежнего") }
                if (confirmRecover) AlertDialog(onDismissRequest = { confirmRecover = false }, title = { Text("Заменить прежний телефон?") },
                    text = { Text("Подставка забудет прежний телефон и доступ его хаба. QR-наклейка продолжит работать.") },
                    confirmButton = { TextButton(onClick = { confirmRecover = false; connect(recover = true) }) { Text("Заменить") } },
                    dismissButton = { TextButton(onClick = { confirmRecover = false }) { Text("Отмена") } })
            }
        }
        localError?.let { Text(it, color = Color(0xffffb454), fontSize = 13.sp) }
        TextButton(onClick = { open = false; connection.stopScan() }) { Text("Закрыть подключение") }
    }
    if (confirmForget) AlertDialog(onDismissRequest = { confirmForget = false }, title = { Text("Отвязать подставку?") },
        text = { Text("Подставка забудет этот телефон, его хаб и Wi-Fi. Просто оставьте её включённой рядом — компьютер и кнопки не нужны.") },
        confirmButton = { TextButton(onClick = { confirmForget = false; scope.launch(Dispatchers.IO) { connection.forget() } }) { Text("Отвязать по Bluetooth") } },
        dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Отмена") } })
    Spacer(Modifier.height(8.dp))
}
