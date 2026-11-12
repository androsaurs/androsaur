package dev.hram.androsaur

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class ConnectionIndicatorState(val label: String, val color: Color) {
    ONLINE("на связи", Color(0xff63e6a4)),
    OFFLINE("нет связи", Color(0xffff897b)),
    CHECKING("проверяю связь", Color(0xffffb454)),
    UNPAIRED("не подключён", Color(0xff7d8a83)),
}

/** A stored binding is not proof of connectivity; only a recent successful exchange is green. */
internal fun connectionIndicatorState(configured: Boolean, online: Boolean, checked: Boolean, seenAt: Long?, now: Long) = when {
    !configured -> ConnectionIndicatorState.UNPAIRED
    online && seenAt != null && now - seenAt in 0..45_000 -> ConnectionIndicatorState.ONLINE
    !checked -> ConnectionIndicatorState.CHECKING
    else -> ConnectionIndicatorState.OFFLINE
}

@Composable
internal fun ConnectionIndicator(title: String, stand: Boolean, state: ConnectionIndicatorState, onClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val description = if (state == ConnectionIndicatorState.UNPAIRED && stand) "не подключена" else state.label
    Box {
        IconButton(onClick = { onClick(); expanded = true }, modifier = Modifier.width(64.dp).height(52.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title: $description"; stateDescription = description }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(28.dp)) {
                    scale(size.width / 28f, size.height / 28f, pivot = Offset.Zero) {
                        val color = state.color
                        val stroke = Stroke(1.8f)
                        fun line(x1: Float, y1: Float, x2: Float, y2: Float, tint: Color = color, width: Float = 1.8f) =
                            drawLine(tint, Offset(x1, y1), Offset(x2, y2), width, StrokeCap.Round)
                        if (stand) {
                            drawRoundRect(color, Offset(8f, 2f), Size(12f, 17f), CornerRadius(2f), style = stroke)
                            line(12f, 16f, 16f, 16f)
                            line(14f, 19f, 14f, 24f)
                            line(6f, 24f, 22f, 24f)
                        } else {
                            for (point in listOf(Offset(5f, 5f), Offset(23f, 5f), Offset(5f, 23f))) {
                                drawLine(color, Offset(14f, 13f), point, 1.8f, StrokeCap.Round)
                                drawCircle(Color(0xff040a08), 3f, point)
                                drawCircle(color, 3f, point, style = stroke)
                            }
                            drawRoundRect(Color(0xff040a08), Offset(10f, 9f), Size(8f, 8f), CornerRadius(2f))
                            drawRoundRect(color, Offset(10f, 9f), Size(8f, 8f), CornerRadius(2f), style = stroke)
                        }
                        drawCircle(Color(0xff040a08), 6f, Offset(23f, 23f))
                        drawCircle(color, 4.5f, Offset(23f, 23f), style = Stroke(1.5f))
                        when (state) {
                            ConnectionIndicatorState.ONLINE -> { line(20.5f, 23f, 22f, 24.5f); line(22f, 24.5f, 25.5f, 21f) }
                            ConnectionIndicatorState.OFFLINE -> { line(21.3f, 21.3f, 24.7f, 24.7f); line(21.3f, 24.7f, 24.7f, 21.3f) }
                            ConnectionIndicatorState.UNPAIRED -> line(21f, 23f, 25f, 23f)
                            ConnectionIndicatorState.CHECKING -> { line(23f, 20.7f, 23f, 23f); line(23f, 23f, 24.5f, 24f) }
                        }
                    }
                }
                Text(title, color = state.color, fontSize = 10.sp, maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text("$title: $description", color = state.color, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Text(if (stand) "Связь этого телефона с подставкой" else "Связь этого телефона с домашним хабом",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(max = 260.dp).padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}
