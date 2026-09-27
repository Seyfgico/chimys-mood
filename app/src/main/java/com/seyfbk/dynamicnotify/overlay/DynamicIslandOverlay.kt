package com.seyfbk.dynamicnotify.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.data.PushNotification

private enum class PillState { HIDDEN, COLLAPSED, EXPANDED }

/**
 * Draws the "dynamic island" pill as a system overlay window that floats
 * above every app, top-center of the screen, cat-first.
 *
 * Interaction model for a regular message:
 *  - show() → a small, tap-only icon appears (cat, idle animation)
 *  - tap the icon → it smoothly morphs into a black fish and expands to
 *    reveal the message, and the window grows to cover the full screen so
 *    it can catch a tap anywhere else
 *  - tap anywhere outside the pill → it disappears entirely; the next
 *    scheduled message (mood-ask or random push) brings it back later
 *
 * The mood-ask pill (3x/day check-in) stays a small, always-expanded
 * window with its own buttons — no outside-tap-to-dismiss, since it's
 * waiting on an answer rather than a passive message.
 */
class DynamicIslandOverlay(private val context: Context) : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var composeView: ComposeView? = null
    private val pillState = mutableStateOf(PillState.HIDDEN)
    private val notificationState = mutableStateOf<PushNotification?>(null)
    private val askGreetingState = mutableStateOf<String?>(null)
    private val isFishState = mutableStateOf(false)
    private var onMoodPicked: ((Mood) -> Unit)? = null
    private var collapsedTimeoutRunnable: Runnable? = null

    /** Shows a passive message as a small tap-to-open icon. */
    fun show(notification: PushNotification) {
        mainHandler.post {
            ensureViewAttached()
            askGreetingState.value = null
            notificationState.value = notification
            isFishState.value = false
            pillState.value = PillState.COLLAPSED
            setWindowSmall()

            collapsedTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            val runnable = Runnable { if (pillState.value == PillState.COLLAPSED) hideMessage() }
            collapsedTimeoutRunnable = runnable
            mainHandler.postDelayed(runnable, COLLAPSED_TIMEOUT_MS)
        }
    }

    /** Shows the interactive mood check-in. Stays up until the user taps a mood. */
    fun showMoodAsk(greeting: String, onPick: (Mood) -> Unit) {
        mainHandler.post {
            ensureViewAttached()
            collapsedTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            pillState.value = PillState.HIDDEN
            notificationState.value = null
            isFishState.value = false
            onMoodPicked = onPick
            askGreetingState.value = greeting
            setWindowSmall()
        }
    }

    private fun expandMessage() {
        collapsedTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        pillState.value = PillState.EXPANDED
        isFishState.value = true
        setWindowFullscreen()
    }

    private fun hideMessage() {
        pillState.value = PillState.HIDDEN
        notificationState.value = null
        isFishState.value = false
        setWindowSmall()
    }

    private fun handleMoodPick(mood: Mood) {
        askGreetingState.value = null
        onMoodPicked?.invoke(mood)
        onMoodPicked = null
    }

    private fun ensureViewAttached() {
        if (composeView != null) return

        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@DynamicIslandOverlay)
            setViewTreeSavedStateRegistryOwner(this@DynamicIslandOverlay)
            setContent {
                val greeting = askGreetingState.value
                if (greeting != null) {
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        AskPill(
                            greeting = greeting,
                            critter = if (isFishState.value) CritterKind.FISH else CritterKind.CAT,
                            onPick = { mood -> handleMoodPick(mood) }
                        )
                    }
                } else {
                    when (pillState.value) {
                        PillState.HIDDEN -> Box {}
                        PillState.COLLAPSED -> Box(modifier = Modifier.padding(top = 8.dp)) {
                            IslandPill(
                                notification = notificationState.value,
                                expanded = false,
                                critter = if (isFishState.value) CritterKind.FISH else CritterKind.CAT,
                                onClick = { expandMessage() }
                            )
                        }
                        PillState.EXPANDED -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { hideMessage() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                            ) {
                                IslandPill(
                                    notification = notificationState.value,
                                    expanded = true,
                                    critter = if (isFishState.value) CritterKind.FISH else CritterKind.CAT,
                                    onClick = { /* consume the tap; only the background dismisses */ }
                                )
                            }
                        }
                    }
                }
            }
        }

        windowManager.addView(view, buildParams(fullscreen = false))
        composeView = view
    }

    private fun setWindowSmall() = updateWindow(fullscreen = false)
    private fun setWindowFullscreen() = updateWindow(fullscreen = true)

    private fun updateWindow(fullscreen: Boolean) {
        val view = composeView ?: return
        runCatching { windowManager.updateViewLayout(view, buildParams(fullscreen)) }
    }

    private fun buildParams(fullscreen: Boolean): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val size = if (fullscreen) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }

        // Fullscreen (expanded) drops FLAG_NOT_TOUCH_MODAL so the window
        // itself catches a tap anywhere on screen, which is how "tap
        // elsewhere to dismiss" is detected.
        val flags = if (fullscreen) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        return WindowManager.LayoutParams(size, size, type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
    }

    fun detach() {
        composeView?.let {
            runCatching { windowManager.removeView(it) }
        }
        composeView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    companion object {
        private const val COLLAPSED_TIMEOUT_MS = 20_000L
    }
}
