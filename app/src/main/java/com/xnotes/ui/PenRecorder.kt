package com.xnotes.ui

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xnotes.MainActivity
import com.xnotes.R
import com.xnotes.pen.*
import com.xnotes.ui.theme.*
import kotlinx.coroutines.delay

/** Scoped to this visible recording surface. Disposal removes the Activity key interceptor. */
@Composable
internal fun PenRecorder(onRecorded: (List<PenTrigger>) -> Unit, onFailed: () -> Unit) {
    val recorder = remember { PenRecording() }
    val started = remember { SystemClock.uptimeMillis() }
    var changedAt by remember { mutableLongStateOf(started) }
    val recorded by rememberUpdatedState(onRecorded)
    val failed by rememberUpdatedState(onFailed)
    val activity = LocalContext.current.mainActivity()
    val palette = LocalPalette.current
    DisposableEffect(activity) {
        val intercept: (KeyEvent) -> Boolean = { event ->
            val trigger = PenSignals.key(event)
            if (trigger == null) false else {
                if (event.isCanceled) { recorder.cancel(); failed() }
                else if (event.repeatCount == 0) {
                    recorder.event(trigger, event.action == KeyEvent.ACTION_DOWN)
                    changedAt = SystemClock.uptimeMillis()
                }
                true
            }
        }
        activity?.penKeyRecorder = intercept
        activity?.cancelPenRecording = { recorder.cancel(); failed() }
        onDispose {
            recorder.cancel()
            if (activity?.penKeyRecorder === intercept) {
                activity.penKeyRecorder = null
                activity.cancelPenRecording = null
            }
        }
    }
    LaunchedEffect(recorder) {
        while (true) {
            delay(100)
            val now = SystemClock.uptimeMillis()
            val result = recorder.finish()
            if (result != null && now - changedAt >= 250) { recorded(result); break }
            if (now - started >= 15000) { recorder.cancel(); failed(); break }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
        Text(stringResource(R.string.pen_record_help), fontSize = TextSize.body)
        Box(Modifier.fillMaxWidth().height(MinTouchTarget * 3)
            .background(palette.surface.toComposeColor()).border(1.dp, palette.border.toComposeColor())) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                object : View(context) {
                    private var previous = emptySet<PenTrigger>()
                    private fun record(event: MotionEvent): Boolean {
                        if (!PenSignals.isPen(event)) return false
                        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                            recorder.cancel(); failed(); return true
                        }
                        // Leaving hover is not a physical release (it often precedes tip-down).
                        if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) return true
                        val current = PenSignals.motion(event)
                        (current - previous).forEach { recorder.event(it, true) }
                        (previous - current).forEach { recorder.event(it, false) }
                        if (current != previous) changedAt = SystemClock.uptimeMillis()
                        previous = current
                        return true
                    }
                    override fun onTouchEvent(event: MotionEvent) = record(event)
                    override fun onHoverEvent(event: MotionEvent) = record(event)
                    override fun onGenericMotionEvent(event: MotionEvent) = record(event)
                }.apply { contentDescription = context.getString(R.string.pen_record_area) }
            })
        }
        Text(stringResource(R.string.pen_record_area), fontSize = TextSize.label)
    }
}

private fun Context.mainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.mainActivity()
    else -> null
}
