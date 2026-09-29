package com.seyfbk.dynamicnotify.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.seyfbk.dynamicnotify.data.CatPrefs
import com.seyfbk.dynamicnotify.data.Mood

private sealed class Bubble {
    data class Message(val text: String, val colorHex: String) : Bubble()
    data class Ask(val greeting: String, val onPick: (Mood) -> Unit) : Bubble()
}

/**
 * A persistent, draggable cat that sits on top of every app. Tapping
 * elsewhere on the screen never dismisses it — it's only ever hidden by
 * turning it off in the app. Bubbles (a message, or the mood check-in)
 * appear docked below it and don't move the cat.
 */
class CatOverlay(private val context: Context) : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val catPrefs = CatPrefs(context)

    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    private val moodState = mutableStateOf<Mood?>(null)
    private val hopSignal = mutableIntStateOf(0)
    private val bubbleState = mutableStateOf<Bubble?>(null)
    private var dismissRunnable: Runnable? = null

    private val catSizeDp = 92
    private val bubbleMaxWidthDp = 230

    // ------------------------------------------------------------ lifecycle

    /** Attaches the cat if the user has it switched on and overlay permission is granted. */
    fun refresh() {
        mainHandler.post {
            if (catPrefs.enabled && hasOverlayPermission()) ensureAttached() else detach()
        }
    }

    fun setEnabled(enabled: Boolean) {
        catPrefs.enabled = enabled
        refresh()
    }

    fun updateMood(mood: Mood?) {
        moodState.value = mood
    }

    /** A little hop — used when a fresh message arrives, or the cat is tapped. */
    fun hop() {
        hopSignal.intValue++
    }

    fun showMessage(text: String, colorHex: String) {
        mainHandler.post {
            if (!ensureAttached()) return@post
            dismissRunnable?.let { mainHandler.removeCallbacks(it) }
            bubbleState.value = Bubble.Message(text, colorHex)
            hop()
            resizeAfterLayout()

            val readMs = (1800L + text.length * 45L).coerceIn(4000L, 12000L)
            val runnable = Runnable { dismissBubble() }
            dismissRunnable = runnable
            mainHandler.postDelayed(runnable, readMs)
        }
    }

    fun showAsk(greeting: String, onPick: (Mood) -> Unit) {
        mainHandler.post {
            if (!ensureAttached()) return@post
            dismissRunnable?.let { mainHandler.removeCallbacks(it) }
            bubbleState.value = Bubble.Ask(greeting, onPick)
            hop()
            resizeAfterLayout()
        }
    }

    private fun dismissBubble() {
        bubbleState.value = null
        resizeAfterLayout()
    }

    fun detach() {
        composeView?.let { runCatching { windowManager.removeView(it) } }
        composeView = null
        params = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    // ------------------------------------------------------------ window plumbing

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(context)

    /** Returns false (and does nothing) if the cat can't be shown right now. */
    private fun ensureAttached(): Boolean {
        if (!catPrefs.enabled || !hasOverlayPermission()) return false
        if (composeView != null) return true

        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val metrics = context.resources.displayMetrics
        val catPx = dpToPx(catSizeDp, metrics)
        val startX = if (catPrefs.posX >= 0) catPrefs.posX else (metrics.widthPixels - catPx - dpToPx(16, metrics))
        val startY = if (catPrefs.posY >= 0) catPrefs.posY else (metrics.heightPixels / 3)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }
        params = lp

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@CatOverlay)
            setViewTreeSavedStateRegistryOwner(this@CatOverlay)
            setContent {
                Column(modifier = Modifier.width(bubbleMaxWidthDp.dp).padding(4.dp)) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(catSizeDp.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { },
                                    onDragEnd = {
                                        val p = params ?: return@detectDragGestures
                                        catPrefs.savePosition(p.x, p.y)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val p = params ?: return@detectDragGestures
                                        val m = context.resources.displayMetrics
                                        val cPx = dpToPx(catSizeDp, m)
                                        p.x = (p.x + dragAmount.x.toInt()).coerceIn(0, (m.widthPixels - cPx).coerceAtLeast(0))
                                        p.y = (p.y + dragAmount.y.toInt()).coerceIn(0, (m.heightPixels - cPx).coerceAtLeast(0))
                                        runCatching { windowManager.updateViewLayout(composeView, p) }
                                    }
                                )
                            }
                    ) {
                        CatCharacter(mood = moodState.value, hopSignal = hopSignal.intValue, modifier = Modifier.size(catSizeDp.dp))
                    }

                    when (val bubble = bubbleState.value) {
                        is Bubble.Message -> MessageBubble(
                            text = bubble.text,
                            accentHex = bubble.colorHex,
                            onClick = { dismissBubble() }
                        )
                        is Bubble.Ask -> AskBubble(
                            greeting = bubble.greeting,
                            onPick = { mood ->
                                val cb = bubble.onPick
                                bubbleState.value = null
                                resizeAfterLayout()
                                cb(mood)
                            }
                        )
                        null -> Unit
                    }
                }
            }
        }

        windowManager.addView(view, lp)
        composeView = view
        return true
    }

    /** Lets Compose finish measuring the new content, then re-fits the WRAP_CONTENT window. */
    private fun resizeAfterLayout() {
        val view = composeView ?: return
        val p = params ?: return
        view.post {
            runCatching { windowManager.updateViewLayout(view, p) }
        }
    }

    private fun dpToPx(dp: Int, metrics: DisplayMetrics): Int =
        (dp * metrics.density).toInt()
}
