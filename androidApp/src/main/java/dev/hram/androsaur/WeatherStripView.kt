package dev.hram.androsaur

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hram.androsaur.weather.StripColumn
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val LINE = Color(0xff63e6a4)
private val SUN = Color(0xffffc94a)
private val MOON = Color(0xffc9cfe0)
private val CLOUD = Color(0xffd5d9e0)
private val CLOUD_WET = Color(0xff9aa1ad)
private val RAIN = Color(0xff6fb1ff)
private val SNOW = Color(0xffcfe3ff)

/**
 * The forecast strip over the face, after the owner's picture: one temperature line across the columns with the degrees over its
 * points, under it the weather's icon, the wind and the time — «Сейчас» first. Temperature is the only line; rain, clouds and sun are
 * told by the icon.
 */
@Composable
fun WeatherStripView(columns: List<StripColumn>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val zone = ZoneId.systemDefault()
    Canvas(modifier.fillMaxWidth().height(156.dp)) {
        val n = columns.size
        // Side room inside the strip for the edge labels; the line runs to the strip's edges (the strip itself has margins).
        val pad = 4.dp.toPx()
        val col = (size.width - 2 * pad) / n
        val temps = columns.map { it.temperature }
        val lo = temps.min()
        val hi = temps.max()
        val top = 40.dp.toPx()
        val band = 26.dp.toPx()
        fun x(i: Int) = pad + col * i + col / 2
        fun y(t: Double) = top + if (hi == lo) band / 2 else (band * (1 - (t - lo) / (hi - lo))).toFloat()

        // A smooth line through the columns (Catmull-Rom as cubic Béziers), running to the edges of the screen.
        val pts = listOf(Offset(0f, y(temps.first()))) + columns.indices.map { Offset(x(it), y(temps[it])) } + Offset(size.width, y(temps.last()))
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val p0 = pts[maxOf(0, i - 1)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[minOf(pts.size - 1, i + 2)]
                cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
            }
        }
        drawPath(path, LINE, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

        val iconY = 92.dp.toPx()
        // «Сейчас»: its point on the line and a thin drop to its icon.
        drawLine(Color.White.copy(alpha = 0.35f), Offset(x(0), y(temps[0]) + 6.dp.toPx()), Offset(x(0), iconY - 16.dp.toPx()), 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        drawCircle(Color.White, 5.dp.toPx(), Offset(x(0), y(temps[0])))

        val big = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        val small = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        val time = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp)
        fun centered(text: String, style: TextStyle, cx: Float, top: Float) {
            val laid = measurer.measure(text, style)
            drawText(laid, topLeft = Offset(cx - laid.size.width / 2f, top))
        }
        columns.forEachIndexed { i, c ->
            val t = c.temperature.roundToInt()
            val label = (if (t < 0) "−" else "") + "${kotlin.math.abs(t)}°"
            val laid = measurer.measure(label, big)
            drawText(laid, topLeft = Offset(x(i) - laid.size.width / 2f, y(c.temperature) - 6.dp.toPx() - laid.size.height))
            val hour = Instant.ofEpochMilli(c.timeMs).atZone(zone).hour
            icon(c.symbol, Offset(x(i), iconY), night = hour < 7 || hour >= 19)
            c.wind?.let { centered(String.format(java.util.Locale.forLanguageTag("ru"), "%.1f м/с", it), small, x(i), 108.dp.toPx()) }
            centered(if (c.now) "Сейчас" else "%02d:00".format(hour), if (c.now) time.copy(color = Color.White.copy(alpha = 0.85f)) else time, x(i), 130.dp.toPx())
        }
    }
}

/** The weather of the hour as a small picture: sun or moon, clouds, rain, snow, lightning, fog. */
private fun DrawScope.icon(symbol: String, c: Offset, night: Boolean) {
    val s = symbol.ifEmpty { "cloudy" }
    val u = 1.dp.toPx()
    fun sun(o: Offset, r: Float) {
        if (night) {
            drawCircle(MOON, r, o)
            drawCircle(Color.Black, r * 0.85f, Offset(o.x + r * 0.45f, o.y - r * 0.35f))
            return
        }
        drawCircle(SUN, r, o)
        for (k in 0 until 8) {
            val a = k * PI / 4
            drawLine(SUN, Offset(o.x + (r + 3 * u) * cos(a).toFloat(), o.y + (r + 3 * u) * sin(a).toFloat()),
                Offset(o.x + (r + 6 * u) * cos(a).toFloat(), o.y + (r + 6 * u) * sin(a).toFloat()), 2 * u, cap = StrokeCap.Round)
        }
    }
    fun cloud(o: Offset, color: Color) {
        drawCircle(color, 6 * u, Offset(o.x - 6 * u, o.y + 1 * u))
        drawCircle(color, 8.5f * u, Offset(o.x + 1 * u, o.y - 3 * u))
        drawCircle(color, 6 * u, Offset(o.x + 8 * u, o.y + 1 * u))
        drawRoundRect(color, Offset(o.x - 12 * u, o.y), Size(26 * u, 7 * u), androidx.compose.ui.geometry.CornerRadius(3.5f * u))
    }
    when {
        s.startsWith("clearsky") -> { sun(c, 8 * u); return }
        s.startsWith("fair") || s.startsWith("partlycloudy") -> { sun(Offset(c.x - 6 * u, c.y - 6 * u), 6 * u); cloud(Offset(c.x + 3 * u, c.y + 2 * u), CLOUD); return }
        s.startsWith("fog") -> {
            for (k in 0 until 3) drawLine(CLOUD_WET, Offset(c.x - 13 * u + (k % 2) * 3 * u, c.y - 6 * u + k * 6 * u), Offset(c.x + 13 * u - (k % 2) * 3 * u, c.y - 6 * u + k * 6 * u), 2.5f * u, cap = StrokeCap.Round)
            return
        }
    }
    val wet = listOf("rain", "sleet", "snow", "hail", "thunder").any { it in s }
    cloud(Offset(c.x, c.y - 4 * u), if (wet) CLOUD_WET else CLOUD)
    if (!wet) return
    val thunder = "thunder" in s
    if (thunder) {
        val bolt = Path().apply { moveTo(c.x + 1 * u, c.y + 4 * u); lineTo(c.x - 4 * u, c.y + 11 * u); lineTo(c.x, c.y + 11 * u); lineTo(c.x - 2 * u, c.y + 17 * u)
            lineTo(c.x + 4 * u, c.y + 9 * u); lineTo(c.x, c.y + 9 * u); close() }
        drawPath(bolt, SUN)
    }
    val drops = if ("heavy" in s) 3 else if ("light" in s) 1 else 2
    for (k in 0 until drops) {
        val dx = (k - (drops - 1) / 2f) * 7 * u
        if (thunder && kotlin.math.abs(dx) < 4 * u) continue
        val o = Offset(c.x + dx, c.y + 9 * u)
        if ("snow" in s || ("sleet" in s && k % 2 == 1)) drawCircle(SNOW, 2 * u, Offset(o.x, o.y + 2 * u))
        else drawLine(RAIN, Offset(o.x + 1 * u, o.y), Offset(o.x - 1 * u, o.y + 5 * u), 2 * u, cap = StrokeCap.Round)
    }
}
