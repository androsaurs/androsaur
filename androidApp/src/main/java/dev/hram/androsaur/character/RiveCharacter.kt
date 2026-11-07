package dev.hram.androsaur.character

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive
import dev.hram.androsaur.R
import dev.hram.androsaur.face.Expression
import kotlinx.coroutines.delay

private const val MACHINE = "Character"
private val INPUTS = setOf("lookX", "lookY", "mouthOpen", "expression", "interaction", "speaking", "sleeping")

/** The containing Compose screen owns taps, including taps directly on the character. */
private class CharacterRiveView(context: Context) : RiveAnimationView(context) {
    @SuppressLint("ClickableViewAccessibility") // No gestures here; the accessible Compose parent owns the click.
    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        super.onSurfaceTextureAvailable(surface, width, height)
        Log.d("AndrosaurCharacter", "Surface available ${width}x$height, drawable=${controller.isActive}")
        // A sleeping machine is paused, but a new texture still needs its static artboard.
        if (controller.isActive) artboardRenderer?.start()
    }

    @SuppressLint("MissingSuperCall")
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        // Rive's default callback recreates/releases Surface before its render thread has
        // finished unlockCanvasAndPost (crashes on Android 15). TextureView resizes the
        // existing SurfaceTexture itself; keep its Surface alive and update only layout.
        controller.targetBounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        if (controller.isActive) artboardRenderer?.start()
    }
}

/** Initialize native code only on first use of the complex mode. Simple mode can survive a load failure. */
private object RiveRuntime {
    private var initialized = false
    @Synchronized fun initialize(context: Context) {
        if (!initialized) {
            Rive.init(context.applicationContext, RendererType.Canvas)
            initialized = true
        }
    }
}

internal val CharacterId.resource: Int get() = when (this) {
    CharacterId.DINOSAUR -> R.raw.androsaur_character
    CharacterId.CAT -> R.raw.androsaur_cat
    CharacterId.DOG -> R.raw.androsaur_dog
    CharacterId.PUF -> R.raw.puf_character
    CharacterId.HAMSTER -> R.raw.hamster_character
    CharacterId.PENGUIN -> R.raw.penguin_character
    CharacterId.PENGUIN2 -> R.raw.penguin2_character
}

internal val CharacterId.artboardName: String get() = when (this) {
    CharacterId.PUF -> "Пуф"
    CharacterId.HAMSTER -> "Хомяк"
    CharacterId.PENGUIN, CharacterId.PENGUIN2 -> "Пингвин"
    else -> "Androsaur"
}

internal val CharacterId.outfitCount: Int get() = when (this) {
    CharacterId.PUF -> 5
    CharacterId.HAMSTER -> 4
    CharacterId.PENGUIN, CharacterId.PENGUIN2 -> 5
    else -> 0
}

internal fun loadCharacterView(context: Context, character: CharacterId = CharacterId.DINOSAUR): RiveAnimationView {
    RiveRuntime.initialize(context)
    val view = CharacterRiveView(context)
    try {
        view.setRiveResource(character.resource, artboardName = character.artboardName, stateMachineName = MACHINE, autoplay = false)
        val artboard = requireNotNull(view.controller.activeArtboard) { "Нет artboard ${character.artboardName}" }
        val machine = artboard.stateMachine(MACHINE)
        // Validate the complete contract before a render thread can consume any input.
        val expected = INPUTS + if (character.outfitCount > 0) setOf("outfit") else emptySet()
        require(machine.inputNames.toSet() == expected) { "Неверные параметры Character" }
        require(machine.inputs.all { it.isNumber }) { "Параметры Character должны быть числовыми" }
        // The artboard owns this inspection instance and releases it along with the file.
        return view
    } catch (e: Exception) {
        view.controller.file = null
        throw e
    }
}

/** Rive owns the rig and timelines; this adapter only translates the shared visual state. */
@Composable
internal fun RiveCharacter(
    state: CharacterState,
    onError: (String?) -> Unit,
    modifier: Modifier = Modifier,
    load: (Context) -> RiveAnimationView = { loadCharacterView(it) },
    outfit: Int? = null,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val report by rememberUpdatedState(onError)
    val loaded = remember(context, load) {
        try { Result.success(load(context)) }
        catch (e: Exception) { Log.w("AndrosaurCharacter", "Rive load failed", e); Result.failure(e) }
        catch (e: LinkageError) { Log.w("AndrosaurCharacter", "Rive runtime unavailable", e); Result.failure(e) }
    }
    var failure by remember(loaded) { mutableStateOf(loaded.exceptionOrNull()?.let { "Персонаж Rive недоступен. Показано простое лицо." }) }
    LaunchedEffect(failure) { report(failure) }
    val view = loaded.getOrNull()
    DisposableEffect(view) {
        onDispose {
            view?.pause()
            view?.controller?.isActive = false
            view?.controller?.file = null
        }
    }
    if (view == null || failure != null) {
        SimpleFace(state, modifier)
        return
    }

    val lookX by animateFloatAsState(state.lookX, tween(180), label = "rive lookX")
    val lookY by animateFloatAsState(state.lookY, tween(180), label = "rive lookY")
    val mouth by animateFloatAsState(state.mouthOpen, tween(70), label = "rive mouth")
    // A category code, not a scale: the rig crossfades between expressions itself.
    // Animating the number would pass through the codes in between.
    val expression = when (state.expression) {
        Expression.NEUTRAL -> 0f
        Expression.ATTENTIVE -> 1f
        Expression.LISTENING -> 2f
        Expression.SLEEPY -> 3f
        Expression.SAD -> 4f
        // The rig has no poses of its own for these yet: the nearest ones (sad; the sleepy pose) stand in until they are drawn.
        Expression.TIRED -> 4f
        Expression.EXHAUSTED -> 3f
    }
    val sleeping by animateFloatAsState(if (state.sleeping) 1f else 0f, tween(300), label = "rive sleeping")
    var visible by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val sent = remember(view) { mutableMapOf<String, Float>() }
    // Stop rendering outside the visible Activity. RiveAnimationView itself only frees on destroy.
    DisposableEffect(view, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { visible = false; view.pause(); view.controller.isActive = false }
                Lifecycle.Event.ON_START -> { visible = true }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); view.pause() }
    }
    LaunchedEffect(view, state.sleeping, visible) {
        if (!visible) return@LaunchedEffect
        view.controller.isActive = true
        view.play(MACHINE, isStateMachine = true)
        if (!state.sleeping) return@LaunchedEffect
        delay(450)
        // Pause motion, not drawing. Rive clears the canvas before draw(); setting isActive=false
        // skips the artboard on a later queued frame and leaves the character completely blank.
        view.pause()
        Log.d("AndrosaurCharacter", "Sleeping pose paused, artboard remains drawable")
    }
    AndroidView(
        factory = { view }, modifier = modifier.testTag("character-rive"),
        onRelease = { it.pause(); it.controller.isActive = false; it.controller.file = null },
        update = {
            try {
                // Setters queue work on Rive's rendering thread. Do not share native input handles with it.
                val values = mapOf(
                    "lookX" to lookX, "lookY" to lookY, "mouthOpen" to mouth, "expression" to expression,
                    "interaction" to when (state.interaction) {
                        Interaction.IDLE -> 0f; Interaction.COMMAND -> 1f; Interaction.RECORDING -> 2f; Interaction.NOTE -> 3f
                    },
                    "speaking" to if (state.speaking) 1f else 0f, "sleeping" to sleeping,
                ) + if (outfit != null) mapOf("outfit" to outfit.toFloat()) else emptyMap()
                // Identical inputs must not wake a sleeping render loop on each clock tick.
                for ((name, value) in values) if (sent[name] != value) {
                    it.setNumberState(MACHINE, name, value)
                    sent[name] = value
                }
            } catch (e: Exception) {
                Log.w("AndrosaurCharacter", "Rive update failed", e)
                failure = "Ошибка персонажа Rive. Показано простое лицо."
            }
        },
    )
}
