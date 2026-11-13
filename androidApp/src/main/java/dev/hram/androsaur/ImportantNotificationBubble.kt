package dev.hram.androsaur

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hram.androsaur.notifications.ImportantNotification
import dev.hram.androsaur.notifications.NotificationStatus

@Composable
internal fun ImportantNotificationBubble(notification: ImportantNotification, compact: Boolean, modifier: Modifier = Modifier) {
    val confirmed = notification.status == NotificationStatus.CONFIRMED
    val background = if (confirmed) Color(0xff12392c) else Color(0xff202e26)
    val accent = if (confirmed) Color(0xff63e6a4) else Color(0xffffd18a)
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        .testTag("important-notification").semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = background, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, accent.copy(alpha = .3f)),
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(Modifier.size(28.dp)) {
                    rotate(-35f) {
                        drawRoundRect(accent, Offset(size.width * .1f, size.height * .28f), Size(size.width * .8f, size.height * .44f),
                            CornerRadius(size.height * .22f), style = Stroke(2.dp.toPx()))
                        drawLine(accent, Offset(size.width * .5f, size.height * .28f), Offset(size.width * .5f, size.height * .72f), 2.dp.toPx())
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (!compact) Text("Лекарство · ${notification.slot.takeLast(5)}", color = accent, fontSize = 12.sp)
                    Text(notification.title, color = Color(0xfff4faf5), fontSize = if (compact) 16.sp else 18.sp,
                        fontWeight = FontWeight.Medium, maxLines = if (compact) 1 else 3, overflow = TextOverflow.Ellipsis)
                    if (!compact && !confirmed) Text("После приёма скажи «принял»", color = Color(0xffc1cec4), fontSize = 13.sp)
                }
            }
        }
        Canvas(Modifier.size(width = 20.dp, height = 10.dp)) {
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width / 2, size.height); lineTo(size.width, 0f); close() }, background)
        }
    }
}
