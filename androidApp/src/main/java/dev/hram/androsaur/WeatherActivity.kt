package dev.hram.androsaur

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The detailed forecast: the hub's weather charts (`/weather?embed=1`) in a WebView, opened by a tap on the weather over the face.
 * Nothing of its own here — the page is the hub's. A «Назад» bar on top, since a kiosk has no navigation buttons; the system bars are
 * hidden as on the face screen, and nothing goes under the camera cutout. Without the hub it says so. Left alone, it closes after a
 * minute, so the stand comes back to the face.
 */
class WeatherActivity : ComponentActivity() {
    private val idle = Handler(Looper.getMainLooper())
    private val close = Runnable { finish() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        val hub = intent.getStringExtra(EXTRA_HUB).orEmpty()
        setContent {
            Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { finish() }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("←  Назад", color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp)
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.javaScriptEnabled = true // the charts are drawn by the page's script
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (request.isForMainFrame) view.loadDataWithBaseURL(null, OFFLINE, "text/html", "utf-8", null)
                                }
                                // Links of the page stay in the hub, not in some other app.
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = !request.url.toString().startsWith(hub)
                            }
                            if (hub.isEmpty()) loadDataWithBaseURL(null, OFFLINE, "text/html", "utf-8", null) else loadUrl("$hub/weather?embed=1")
                        }
                    },
                )
            }
        }
        restartIdle()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        restartIdle()
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        idle.removeCallbacks(close)
        super.onDestroy()
    }

    private fun restartIdle() {
        idle.removeCallbacks(close)
        idle.postDelayed(close, IDLE_MS)
    }

    companion object {
        const val EXTRA_HUB = "hub"
        private const val IDLE_MS = 60_000L
        private const val OFFLINE = """<html><body style="font-family:sans-serif;background:#000;color:#c3c2b7;padding:32px 20px;font-size:18px">
            <p><b style="color:#f0efec">Хаб недоступен.</b></p><p>Подробный прогноз по часам — в хабе. На главном экране погода есть и без него.</p>
            </body></html>"""
    }
}
