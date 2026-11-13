package dev.hram.androsaur

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import dev.hram.androsaur.character.CharacterId
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.hram.androsaur.character.CharacterMode
import dev.hram.androsaur.character.CharacterState
import dev.hram.androsaur.character.CharacterView
import dev.hram.androsaur.character.Interaction
import dev.hram.androsaur.face.Reflexes
import dev.hram.androsaur.commands.Answers
import dev.hram.androsaur.weather.WeatherReading
import dev.hram.androsaur.weather.WeatherChoice
import dev.hram.androsaur.weather.WeatherStrip
import dev.hram.androsaur.weather.WeatherText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The face and the control panel of the main app: always on screen, the modules are started from here and watched from here. */
class MainActivity : ComponentActivity() {
    private lateinit var brain: Brain

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        brain = Brain(applicationContext)
        takeHub(intent)
        // Diagnostics without a person: `adb shell am broadcast -a dev.hram.androsaur.DEBUG_FACE --es expression exhausted --es character dinosaur --ei seconds 60`.
        // Only the system permission DUMP (adb shell has it, ordinary apps do not) may send it; it shows a pose for a while and changes nothing else.
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: Intent) { brain.debugFace(i.getStringExtra("character"), i.getStringExtra("expression"), i.getIntExtra("seconds", 30)) }
        }, android.content.IntentFilter("dev.hram.androsaur.DEBUG_FACE"), "android.permission.DUMP", null, android.content.Context.RECEIVER_EXPORTED)
        // Staged scene for demo recordings: `adb shell am broadcast -a dev.hram.androsaur.DEBUG_SCENE --es step face_in --es name Женя` (steps: face_in, face_out, wake, command, say, note, mode, sleep, reset). DUMP only.
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: Intent) {
                val data = i.getStringExtra("data").orEmpty().split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
                brain.scene(i.getStringExtra("step").orEmpty(), i.getStringExtra("name"), i.getStringExtra("id"), i.getStringExtra("text"), i.getStringExtra("intent"), data)
            }
        }, android.content.IntentFilter("dev.hram.androsaur.DEBUG_SCENE"), "android.permission.DUMP", null, android.content.Context.RECEIVER_EXPORTED)
        // Where the phone is, coarsely, for the weather; asked once. Refused, the weather comes only for a place set in the hub.
        if (!brain.weatherSource.hasLocationPermission() && savedInstanceState == null) requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { withContext(Dispatchers.IO) { brain.run() } } }
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xff63e6a4))) { Screen(brain, ::dim, ::openWeather) { work -> lifecycleScope.launch(Dispatchers.IO) { work() } } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeHub(intent)
    }

    /**
     * The hub handed from outside: `adb shell am start -n dev.hram.androsaur/.MainActivity --es hub http://… --es pair CODE`
     * pairs this phone with a one-time code from the admin panel; `--es hub ""` forgets the hub.
     */
    private fun takeHub(intent: Intent?) {
        val url = intent?.getStringExtra("hub") ?: return
        val code = intent.getStringExtra("pair")
        if (code == null) {
            if (!brain.hub.setUrl(url)) Log.w("Androsaur", "hub address refused: not an http(s) address")
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val problem = brain.hub.pair(url, code)
            Log.i("Androsaur", problem?.let { "hub pairing failed: $it" } ?: "paired with the hub")
        }
    }

    /** A tap on the weather: the hub's forecast by the hour in a WebView (it says so when there is no hub). */
    private fun openWeather() {
        brain.touch()
        openHubPage(this, brain, "/weather?embed=1", "Подробный прогноз по часам — в хабе. На главном экране погода есть и без него.")
    }

    private fun dim(on: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (on) Reflexes.DIM_LEVEL else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
}

private val SCREEN_BASE = Color(0xff040a08)

/** The glow colour of each character (the screen background fades from it to the dark base). */
private fun glowOf(character: CharacterId): Color = when (character) {
    CharacterId.DINOSAUR -> Color(0xff165a3e)
    CharacterId.CAT -> Color(0xff583c78)
    CharacterId.DOG -> Color(0xff6e461e)
    CharacterId.PUF -> Color(0xff463c82)
    CharacterId.HAMSTER -> Color(0xff6e5532)
    CharacterId.PENGUIN -> Color(0xff1e4682)
    CharacterId.PENGUIN2 -> Color(0xff1e5f6e)
}

@Composable
private fun Screen(brain: Brain, dim: (Boolean) -> Unit, openWeather: () -> Unit, background: (() -> Unit) -> Unit) {
    // Only the face by default; a touch shows the panel with the modules and the buttons (for looking and for the odd manual start).
    var panel by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val character by brain.character.collectAsStateWithLifecycle()
    val stand by brain.stand.ui.collectAsStateWithLifecycle()
    var characterError by remember(brain.characterMode, brain.shownCharacter) { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(100) } }
    val dimmed = character.dimmed
    LaunchedEffect(dimmed) { dim(dimmed) }
    // The glow behind the character takes the colour of the one on the screen and changes smoothly with it.
    val glow by animateColorAsState(glowOf(brain.shownCharacter), tween(700), label = "glow")
    Box(Modifier.fillMaxSize().testTag("assistant-screen").background(SCREEN_BASE).drawBehind {
        // A dark background with a glow behind the character, as in the demo videos (scripts/demo/layouts/tall-b.json).
        drawRect(Brush.radialGradient(listOf(lerp(SCREEN_BASE, glow, 0.67f), lerp(SCREEN_BASE, glow, 0.22f), SCREEN_BASE), center = Offset(size.width / 2, size.height * 0.62f), radius = size.height * 0.6f))
    }.clickable { brain.touch(); panel = !panel }) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.weight(if (panel) 0.5f else 1f).fillMaxWidth()) {
                Clock(now, compact = panel, weather = brain.weather, timerEndsAt = brain.timerEndsAt, ringing = brain.ringing, onWeather = openWeather,
                    hub = brain.hubConnection, hubConfigured = brain.hub.paired, hubUrl = brain.hub.url, hubRefused = brain.hub.refused,
                    stand = stand, onConnection = { brain.touch() })
                Box(Modifier.weight(1f).fillMaxWidth()) { Face(brain, character, now) { characterError = it } }
            }
            if (panel) Panel(brain, background, Modifier.weight(0.5f), characterError)
        }
    }
}

@Composable
private fun Face(brain: Brain, state: CharacterState, now: Long, onCharacterError: (String?) -> Unit) {
    val badge = brain.badgeText(now)
    // The name stays on screen while the text of the last non-empty badge fades out, instead of vanishing in one frame.
    var shown by remember { mutableStateOf("") }
    if (badge.isNotEmpty()) shown = badge
    val alpha by animateFloatAsState(if (badge.isNotEmpty()) 1f else 0f, tween(500), label = "badge")
    val notification = brain.importantNotification(now)
    Column(Modifier.fillMaxSize()) {
        notification?.let { ImportantNotificationBubble(it, compact = state.interaction != Interaction.IDLE) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CharacterView(state, brain.characterMode, now, onCharacterError, brain.shownCharacter)
            // What to do now, in words: the person should never wonder whether to talk.
            when (state.interaction) {
                Interaction.COMMAND -> Caption("Слушаю", "скажите: «передай жене …»", Modifier.align(Alignment.BottomCenter))
                Interaction.RECORDING -> Caption("● Говорите, идёт запись", "после паузы запись закончится сама", Modifier.align(Alignment.BottomCenter), Color(0xffff6b5e))
                Interaction.NOTE -> Caption("Играет записка", null, Modifier.align(Alignment.BottomCenter))
                else -> if (alpha > 0.01f) Text(shown, color = Color.White.copy(alpha = alpha), fontSize = 34.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))

/**
 * The time and the date above the face («14:35», «суббота, 25 октября»), in the phone's time zone. It takes its own strip, so the
 * face below shrinks instead of overlapping it, and moves with the face to keep the always-on screen from burning in.
 * [now] is the screen's tick (it only makes the clock redraw); the time itself is the wall clock.
 */
@Composable
private fun Clock(now: Long, compact: Boolean, weather: WeatherReading?, timerEndsAt: Long?, ringing: Boolean, onWeather: () -> Unit,
                  hub: HubConnectionUi, hubConfigured: Boolean, hubUrl: String, hubRefused: Boolean, stand: StandUi, onConnection: () -> Unit) {
    // Stream ACKs can be newer than the last screen tick. Sample the same monotonic
    // clock now, otherwise fresh traffic appears to have a negative age and flashes red.
    val connectionNow = SystemClock.elapsedRealtime()
    val moment = Instant.now().atZone(ZoneId.systemDefault())
    val (dx, dy) = Reflexes.drift(now)
    val driftX by animateFloatAsState(dx.toFloat(), tween(4000), label = "clock dx")
    val driftY by animateFloatAsState(dy.toFloat(), tween(4000), label = "clock dy")
    Column(
        // Narrower than the screen and with air between the lines: the clock, the date, the forecast and the timer each stand apart.
        Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 22.dp, start = if (compact) 0.dp else 16.dp, end = if (compact) 0.dp else 16.dp)
            .offset { androidx.compose.ui.unit.IntOffset((driftX * 14.dp.toPx()).toInt(), (driftY * 6.dp.toPx()).toInt()) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                ConnectionIndicator("Хаб", stand = false,
                    state = connectionIndicatorState(hubConfigured, hub.online && !hubRefused && hub.url == hubUrl,
                        hub.checked && hub.url == hubUrl, hub.seenAt, connectionNow), onClick = onConnection)
            }
            Text(moment.format(TIME), color = Color.White.copy(alpha = 0.8f), fontSize = if (compact) 30.sp else 56.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                ConnectionIndicator("Подставка", stand = true,
                    state = connectionIndicatorState(stand.paired, stand.online, stand.checked, stand.seenAt, connectionNow), onClick = onConnection)
            }
        }
        if (!compact) Text(moment.format(DATE), color = Color.White.copy(alpha = 0.55f), fontSize = 20.sp, modifier = Modifier.padding(top = 6.dp))
        if (!compact && weather != null) {
            // The forecast strip when the hours are known; the one-line weather until then (or for weather without hours).
            val nowMs = System.currentTimeMillis()
            val columns = WeatherStrip.columns(weather, nowMs, zoneOffsetMs = java.util.TimeZone.getDefault().getOffset(nowMs).toLong())
            // A tap on the weather opens the forecast by the hour (and does not toggle the panel, as a tap elsewhere does).
            val tap = Modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onWeather)
            if (columns.size >= 2 && WeatherChoice.forecastFresh(weather, nowMs)) WeatherStripView(columns, Modifier.padding(top = 28.dp).then(tap))
            else Text(WeatherText.line(weather, nowMs), color = Color.White.copy(alpha = 0.55f), fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp).then(tap))
        }
        // The timer: the countdown while it runs, «Время вышло» while the bell rings (a touch on the screen silences it).
        when {
            ringing -> {
                Text("⏰ Время вышло", color = Color(0xffffb454), fontSize = if (compact) 18.sp else 30.sp, modifier = Modifier.padding(top = if (compact) 0.dp else 20.dp))
                if (!compact) Text("коснитесь экрана или скажите «стоп»", color = Color(0xffffb454).copy(alpha = 0.8f), fontSize = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
            }
            timerEndsAt != null -> Text("Таймер ${Answers.countdown(((timerEndsAt - System.currentTimeMillis() + 999) / 1000).toInt())}", color = Color(0xff63e6a4), fontSize = if (compact) 18.sp else 30.sp,
                modifier = Modifier.padding(top = if (compact) 0.dp else 20.dp))
        }
    }
}

@Composable
private fun Caption(title: String, hint: String?, modifier: Modifier, color: Color = Color.White) {
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = color, fontSize = 28.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (hint != null) Text(hint, color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun Panel(brain: Brain, background: (() -> Unit) -> Unit, modifier: Modifier, characterError: String?) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().background(Color(0xff111815)).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Androsaur · каркас", color = Color.White, fontSize = 18.sp)
        Text("Персонаж", color = Color.White, fontSize = 15.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((mode, title) in listOf(CharacterMode.SIMPLE to "Простой", CharacterMode.COMPLEX to "Сложный")) {
                FilterChip(
                    selected = brain.characterMode == mode, onClick = { brain.chooseCharacterMode(mode) }, label = { Text(title) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xff234c3b), selectedLabelColor = Color(0xff63e6a4)),
                )
            }
        }
        Text(if (brain.characterMode == CharacterMode.SIMPLE) "Глаза и рот" else brain.characterId.title, color = Color(0xffc5cfc9), fontSize = 13.sp)
        TextButton(
            onClick = {
                brain.touch()
                openHubPage(context, brain, "/characters?embed=1", "Выбрать персонажа можно, когда хаб на связи.")
            },
            enabled = brain.hub.paired,
        ) {
            Text("Каталог · выбрать для себя")
        }
        if (!brain.hub.paired) Text("Для выбора персонажа подключи хаб", color = Color(0xffc5cfc9), fontSize = 13.sp)
        characterError?.let { Text(it, color = Color(0xffffb454), fontSize = 13.sp) }
        brain.note?.let { Text(it, color = Color(0xffffb454), fontSize = 14.sp) }
        Text("${brain.hubState.ifEmpty { "Хаб: …" }} · затемнение через ${brain.dimAfterMs / 1000} с", color = Color(0xffc5cfc9), fontSize = 13.sp)
        HubPairing(brain, background)
        StandPanel(brain.stand, brain.hub.paired)
        if (brain.wakeSettingsState.isNotEmpty()) Text(brain.wakeSettingsState, color = Color(0xffc5cfc9), fontSize = 13.sp)
        if (brain.wakeText.isNotEmpty() && brain.lastWakeAt != null) Text("Последнее срабатывание: ${brain.wakeText}", color = Color(0xff63e6a4), fontSize = 13.sp)
        for (m in listOf(brain.audio, brain.camera)) {
            Text("${m.label}: ${m.link}", color = if (m.running) Color.White else Color(0xffffb454), fontSize = 15.sp)
            m.lines.forEach { Text(it, color = Color(0xffc5cfc9), fontSize = 13.sp) }
            m.error?.let { Text("ошибка: $it", color = Color(0xffffb454), fontSize = 13.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { background { brain.startModules() } }, modifier = Modifier.weight(1f)) { Text("Запустить модули", fontSize = 13.sp) }
            OutlinedButton(onClick = { background { brain.switchTracking(!brain.tracking) } }, modifier = Modifier.weight(1f)) {
                Text(if (brain.tracking) "Не следить" else "Следить за лицом", fontSize = 13.sp)
            }
        }
        Text("Нажмите на лицо, чтобы скрыть панель.", color = Color(0xff7d8a83), fontSize = 12.sp)
    }
}

/**
 * «Подключить к хабу»: the hub address and a one-time code from the admin panel («Добавить телефон»). Shown open while the
 * phone is not paired or the hub refused it; folded behind a button otherwise. Every phone of the house pairs with its own code.
 */
@Composable
private fun HubPairing(brain: Brain, background: (() -> Unit) -> Unit) {
    val needed = !brain.hub.paired || brain.hub.refused
    var open by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(brain.hub.url.ifEmpty { "http://" }) }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    if (!needed && !open) {
        TextButton(onClick = { open = true }) { Text("Подключить к другому хабу") }
        return
    }
    Text(if (brain.hub.refused) "Хаб отключил этот телефон: нужен новый код" else "Подключить к хабу", color = Color.White, fontSize = 15.sp)
    Text("В админке хаба: «Устройства» → «Добавить телефон», затем введите адрес и код.", color = Color(0xffc5cfc9), fontSize = 13.sp)
    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Адрес хаба") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = code, onValueChange = { code = it.uppercase().take(12) }, label = { Text("Код привязки") }, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy, onClick = {
        busy = true; message = null
        background {
            val problem = brain.hub.pair(address, code)
            message = problem ?: "Подключено"
            if (problem == null) { code = ""; open = false }
            busy = false
        }
    }) { Text(if (busy) "Подключаю…" else "Подключить") }
    message?.let { Text(it, color = if (it == "Подключено") Color(0xff63e6a4) else Color(0xffffb454), fontSize = 13.sp) }
}

/** A page of the hub inside the app (see [HubPageActivity]): opened with this phone's hub token, no login for the person holding it. */
private fun openHubPage(context: android.content.Context, brain: Brain, path: String, offline: String) {
    context.startActivity(
        Intent(context, HubPageActivity::class.java).putExtra(HubPageActivity.EXTRA_HUB, brain.hub.url)
            .putExtra(HubPageActivity.EXTRA_PATH, path).putExtra(HubPageActivity.EXTRA_OFFLINE, offline)
            .putExtra(HubPageActivity.EXTRA_AUTH, brain.hub.authorization()),
    )
}
