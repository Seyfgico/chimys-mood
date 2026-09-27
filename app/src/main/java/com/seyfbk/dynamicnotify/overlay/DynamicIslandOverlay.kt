package com.seyfbk.dynamicnotify.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
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

/**
 * Draws the "dynamic island" pill as a system overlay window that floats
 * above every app, top-center of the screen, cat-first.
 */
class DynamicIslandOverlay(private val context: Context) : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var composeView: ComposeView? = null
    private val notificationState = mutableStateOf<PushNotification?>(null)
    private val expandedState = mutableStateOf(false)
    private val askGreetingState = mutableStateOf<String?>(null)
    private val critterState = mutableStateOf(CritterKind.CAT)
    private var onMoodPicked: ((Mood) -> Unit)? = null
    private var collapseRunnable: Runnable? = null
    private var pendingUrl: String? = null

    fun show(notification: PushNotification) {
        mainHandler.post {
            ensureViewAttached()
            pendingUrl = notification.url
            notificationState.value = notification
            critterState.value = CritterKind.random()
            expandedState.value = true

            collapseRunnable?.let { mainHandler.removeCallbacks(it) }
            val runnable = Runnable { expandedState.value = false }
            collapseRunnable = runnable
            mainHandler.postDelayed(runnable, AUTO_COLLAPSE_MS)
        }
    }

    /** Shows the interactive mood check-in. Stays up until the user taps a mood. */
    fun showMoodAsk(greeting: String, onPick: (Mood) -> Unit) {
        mainHandler.post {
            ensureViewAttached()
            collapseRunnable?.let { mainHandler.removeCallbacks(it) }
            notificationState.value = null
            critterState.value = CritterKind.random()
            onMoodPicked = onPick
            askGreetingState.value = greeting
        }
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
                Box(modifier = androidx.compose.ui.Modifier.padding(top = 8.dp)) {
                    val greeting = askGreetingState.value
                    if (greeting != null) {
                        AskPill(
                            greeting = greeting,
                            critter = critterState.value,
                            onPick = { mood -> handleMoodPick(mood) }
                        )
                    } else {
                        IslandPill(
                            notification = notificationState.value,
                            expanded = expandedState.value,
                            critter = critterState.value,
                            onClick = { openPendingUrl() }
                        )
                    }
                }
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        windowManager.addView(view, params)
        composeView = view
    }

    private fun openPendingUrl() {
        val url = pendingUrl ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        expandedState.value = false
    }

    fun detach() {
        composeView?.let {
            runCatching { windowManager.removeView(it) }
        }
        composeView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    companion object {
        private const val AUTO_COLLAPSE_MS = 4500L
    }
}
