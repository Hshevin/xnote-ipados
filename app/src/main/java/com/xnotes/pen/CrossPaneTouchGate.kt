package com.xnotes.pen

import android.view.MotionEvent

/** Reject a whole touch stream while the other pane owns pen contact, before gesture recognition. */
class CrossPaneTouchGate {
    var shouldBlock: () -> Boolean = { false }
    private var blocked = false
    private var active = false

    fun dispatch(event: MotionEvent, deliver: (MotionEvent) -> Boolean): Boolean {
        val action = event.actionMasked
        val end = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
        if (action == MotionEvent.ACTION_DOWN) { blocked = false; active = false }
        if (blocked) {
            if (end) blocked = false
            return true
        }
        if (shouldBlock()) {
            if (active) {
                val cancel = MotionEvent.obtain(event)
                try {
                    cancel.action = MotionEvent.ACTION_CANCEL
                    deliver(cancel)
                } finally { cancel.recycle() }
            }
            active = false
            blocked = !end
            return true
        }
        active = !end
        return deliver(event)
    }
}
