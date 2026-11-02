package com.xnotes.pen

import android.view.MotionEvent

/** Tracks each finger by id: lifting one must not look like a jump of the centroid. */
class FourFingerTap(private val slopPx: Float, private val timeoutMs: Long = 650L) {
    enum class Result { PASS, CAPTURE, CONSUME, TRIGGER }
    private var started = 0L
    private var captured = false
    private var rejected = false
    private var moved = false
    private var maximum = 0
    private val origins = HashMap<Int, Pair<Float, Float>>()

    fun track(e: MotionEvent, allowed: Boolean = true): Result {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            started = e.eventTime
            captured = false
            rejected = !allowed
            moved = false
            maximum = 1
            origins.clear()
        }
        if (!allowed || (0 until e.pointerCount).any { e.getToolType(it) != MotionEvent.TOOL_TYPE_FINGER }) rejected = true
        maximum = maxOf(maximum, e.pointerCount)
        if (e.actionMasked == MotionEvent.ACTION_CANCEL) {
            val result = if (captured) Result.CONSUME else Result.PASS
            captured = false
            rejected = true
            return result
        }
        for (i in 0 until e.pointerCount) {
            val start = origins.getOrPut(e.getPointerId(i)) { e.getX(i) to e.getY(i) }
            if (kotlin.math.hypot(e.getX(i) - start.first, e.getY(i) - start.second) > slopPx) moved = true
        }
        if (!captured && !rejected && e.actionMasked == MotionEvent.ACTION_POINTER_DOWN && e.pointerCount == 4) {
            captured = true
            return Result.CAPTURE
        }
        if (!captured) return Result.PASS
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            captured = false
            return if (!rejected && !moved && maximum == 4 && e.eventTime - started <= timeoutMs) Result.TRIGGER else Result.CONSUME
        }
        return Result.CONSUME
    }
}
