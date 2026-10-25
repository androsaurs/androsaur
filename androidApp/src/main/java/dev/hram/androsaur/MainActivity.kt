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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.hram.androsaur.face.Expression
import dev.hram.androsaur.face.Reflexes
import dev.hram.androsaur.weather.WeatherReading
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
        // Where the phone is, coarsely, for the weather; asked once. Refused, the weather comes only for a place set in the hub.
        if (!brain.weatherSource.hasLocationPermission() && savedInstanceState == null) requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { withContext(Dispatchers.IO) { brain.run() } } }
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xff63e6a4))) { Screen(brain, ::dim) { work -> lifecycleScope.launch(Dispatchers.IO) { work() } } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeHub(intent)
    }

    /** The hub address handed from outside: `adb shell am start -n dev.hram.androsaur/.MainActivity --es hub http://…` (empty to forget). */
    private fun takeHub(intent: Intent?) {
        val url = intent?.getStringExtra("hub") ?: return
        if (!brain.hub.setUrl(url)) Log.w("Androsaur", "hub address refused: not an http(s) address")
    }

    private fun dim(on: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (on) Reflexes.DIM_LEVEL else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
}

@Composable
private fun Screen(brain: Brain, dim: (Boolean) -> Unit, background: (() -> Unit) -> Unit) {
    // Only the face by default; a touch shows the panel with the modules and the buttons (for looking and for the odd manual start).
    var panel by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(100) } }
    val dimmed = Reflexes.dimmed(now, brain.lastActivityAt, brain.dimAfterMs)
    LaunchedEffect(dimmed) { dim(dimmed) }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { brain.touch(); panel = !panel }) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.weight(if (panel) 0.5f else 1f).fillMaxWidth()) {
                Clock(now, compact = panel, weather = brain.weather)
                Box(Modifier.weight(1f).fillMaxWidth()) { Face(brain, now) }
            }
            if (panel) Panel(brain, background, Modifier.weight(0.5f))
        }
    }
}

@Composable
private fun Face(brain: Brain, now: Long) {
    val badge = brain.badgeText(now)
    // The name stays on screen while the text of the last non-empty badge fades out, instead of vanishing in one frame.
    var shown by remember { mutableStateOf("") }
    if (badge.isNotEmpty()) shown = badge
    val alpha by animateFloatAsState(if (badge.isNotEmpty()) 1f else 0f, tween(500), label = "badge")
    val expression = brain.expression(now)
    val gaze = brain.face?.let { Reflexes.gaze(it.first, it.second) } ?: (0.0 to 0.0)
    val eyeScale by animateFloatAsState(when (expression) { Expression.LISTENING -> 1.2f; Expression.ATTENTIVE -> 1.1f; Expression.SLEEPY -> 0.9f; Expression.SAD -> 0.95f; else -> 1f }, tween(300), label = "eye")
    val lid by animateFloatAsState(when (expression) { Expression.SLEEPY -> 0.6f; Expression.SAD -> 0.25f; else -> 0f }, tween(600), label = "lid")
    val mouthOpen by animateFloatAsState(if (brain.speaking) brain.mouth else 0f, tween(70), label = "mouth")
    val gx by animateFloatAsState(gaze.first.toFloat(), tween(180), label = "gx")
    val gy by animateFloatAsState(gaze.second.toFloat(), tween(180), label = "gy")
    val (dx, dy) = Reflexes.drift(now)
    val driftX by animateFloatAsState(dx.toFloat(), tween(4000), label = "dx")
    val driftY by animateFloatAsState(dy.toFloat(), tween(4000), label = "dy")
    Box(Modifier.fillMaxSize()) {
    Canvas(Modifier.fillMaxSize()) {
        val room = 14.dp.toPx()
        val c = Offset(size.width / 2 + driftX * room, size.height / 2 + driftY * room)
        val r = minOf(size.width * 0.17f, size.height * 0.28f) * eyeScale
        val gap = size.width * 0.22f
        for (side in listOf(-1f, 1f)) {
            val eye = Offset(c.x + side * gap, c.y - size.height * 0.08f)
            drawCircle(Color.White, r, eye)
            drawCircle(Color.Black, r * 0.45f, Offset(eye.x + gx * r * 0.45f, eye.y + gy * r * 0.45f))
            if (lid > 0f) drawRect(Color.Black, Offset(eye.x - r - 1, eye.y - r - 1), Size(2 * r + 2, 2 * r * lid + 1))
        }
        val mouth = Offset(c.x, c.y + size.height * 0.26f)
        val w = size.width * 0.16f
        when {
            // Speaking: the mouth opens as far as the sound is loud.
            brain.speaking -> {
                val h = w * (0.12f + 0.8f * mouthOpen)
                drawRoundRect(Color.White, Offset(mouth.x - w * 0.5f, mouth.y - h / 2), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2))
            }
            else -> when (expression) {
            Expression.LISTENING -> drawOval(Color.White, Offset(mouth.x - w * 0.45f, mouth.y - w * 0.2f), Size(w * 0.9f, w * 0.8f))
            Expression.ATTENTIVE -> drawArc(Color.White, 20f, 140f, false, Offset(mouth.x - w, mouth.y - w * 0.7f), Size(2 * w, 1.4f * w), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            Expression.SAD -> drawArc(Color.White, 200f, 140f, false, Offset(mouth.x - w, mouth.y - w * 0.2f), Size(2 * w, 1.4f * w), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            else -> drawLine(Color.White, Offset(mouth.x - w * 0.6f, mouth.y), Offset(mouth.x + w * 0.6f, mouth.y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
    // What to do now, in words: the person should never wonder whether to talk.
    when (brain.mode) {
        "command" -> Caption("Слушаю", "скажите: «передай жене …»", Modifier.align(Alignment.BottomCenter))
        "recording" -> Caption("● Говорите, идёт запись", "после паузы запись закончится сама", Modifier.align(Alignment.BottomCenter), Color(0xffff6b5e))
        "note" -> Caption("Играет записка", null, Modifier.align(Alignment.BottomCenter))
        else -> if (alpha > 0.01f) Text(shown, color = Color.White.copy(alpha = alpha), fontSize = 34.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
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
private fun Clock(now: Long, compact: Boolean, weather: WeatherReading?) {
    val moment = Instant.now().atZone(ZoneId.systemDefault())
    val (dx, dy) = Reflexes.drift(now)
    val driftX by animateFloatAsState(dx.toFloat(), tween(4000), label = "clock dx")
    val driftY by animateFloatAsState(dy.toFloat(), tween(4000), label = "clock dy")
    Column(
        Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 18.dp)
            .offset { androidx.compose.ui.unit.IntOffset((driftX * 14.dp.toPx()).toInt(), (driftY * 6.dp.toPx()).toInt()) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(moment.format(TIME), color = Color.White.copy(alpha = 0.8f), fontSize = if (compact) 30.sp else 56.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light)
        if (!compact) Text(moment.format(DATE), color = Color.White.copy(alpha = 0.55f), fontSize = 20.sp)
        if (!compact && weather != null) Text(WeatherText.line(weather, System.currentTimeMillis()), color = Color.White.copy(alpha = 0.55f), fontSize = 20.sp)
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
private fun Panel(brain: Brain, background: (() -> Unit) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().background(Color(0xff111815)).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Androsaur · каркас", color = Color.White, fontSize = 18.sp)
        brain.note?.let { Text(it, color = Color(0xffffb454), fontSize = 14.sp) }
        Text("${brain.hubState.ifEmpty { "Хаб: …" }} · затемнение через ${brain.dimAfterMs / 1000} с", color = Color(0xffc5cfc9), fontSize = 13.sp)
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
