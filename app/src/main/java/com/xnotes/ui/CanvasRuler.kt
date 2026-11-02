package com.xnotes.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.xnotes.canvas.Ruler
import com.xnotes.canvas.RulerButton
import com.xnotes.core.geometry.Pt
import kotlin.math.*

/** Viewport ruler; its geometry and edge projection are shared with the paged editor. */
internal class CanvasRuler {
    val ruler = Ruler()
    private var grab = 0
    private var offset = Pt.ZERO
    private var before = Pt.ZERO
    private var angleBefore = 0.0
    private var sign = 1.0
    private var centroid = Pt.ZERO
    private var fingerAngle = 0.0
    private var engaged = false
    private var positive = false
    fun reset() { grab = 0; engaged = false }
    fun beginStroke() { engaged = false }
    fun magnetize(p: Pt, density: Double): Pt {
        if (!ruler.visible) return p
        val across = ruler.signedAcross(p)
        val limit = ruler.thicknessPx / 2 + 12 * density
        if (engaged && (if (positive) across > limit else across < -limit)) engaged = false
        if (!engaged && abs(across) <= limit) { engaged = true; positive = across >= 0 }
        return if (engaged) ruler.projectToEdge(p, positive) else p
    }
    fun touch(e: MotionEvent, w: Int, h: Int, density: Double): Boolean {
        val p = Pt(e.x.toDouble(), e.y.toDouble())
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            grab = 0
            if (!ruler.visible) return false
            before = ruler.center; angleBefore = ruler.angleRad
            val handle = ruler.hitHandle(p, .3 * min(w, h), max(18 * density, ruler.handleRadiusPx()))
            val button = ruler.hitButton(p, ruler.buttonRadiusPx())
            when {
                handle != null && !ruler.lockAngle -> { grab = 2; sign = if (handle == 0) 1.0 else -1.0 }
                button != null -> {
                    if (button == RulerButton.LOCK_POS) ruler.lockPosition = !ruler.lockPosition else ruler.lockAngle = !ruler.lockAngle
                    grab = 4
                }
                abs(ruler.signedAcross(p)) <= (ruler.thicknessPx / 2 - if (e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) 12 * density else 0.0) -> {
                    grab = if (ruler.lockPosition) 4 else 1; offset = ruler.center - p
                }
            }
            return grab != 0
        }
        if (grab == 0) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2 && e.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER && grab != 4) {
                val b = Pt(e.getX(1).toDouble(), e.getY(1).toDouble())
                centroid = (p + b) * .5; fingerAngle = atan2(b.y - p.y, b.x - p.x)
                before = ruler.center; angleBefore = ruler.angleRad; grab = 3
            }
            MotionEvent.ACTION_MOVE -> when (grab) {
                1 -> if (!ruler.lockPosition) ruler.center = p + offset
                2 -> { val v = (p - ruler.center) * sign; if (v.length() > 1) ruler.angleRad = Ruler.snapToAxes(atan2(v.y, v.x)) }
                3 -> if (e.pointerCount >= 2) {
                    val b = Pt(e.getX(1).toDouble(), e.getY(1).toDouble())
                    if (!ruler.lockPosition) ruler.center = before + (p + b) * .5 - centroid
                    if (!ruler.lockAngle) ruler.angleRad = Ruler.snapToAxes(angleBefore + atan2(b.y - p.y, b.x - p.x) - fingerAngle)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> { grab = 4 } // swallow the rest of this gesture
            MotionEvent.ACTION_CANCEL -> { ruler.center = before; ruler.angleRad = angleBefore; grab = 0 }
            MotionEvent.ACTION_UP -> grab = 0
        }
        return true
    }

    fun draw(c: Canvas, density: Double, zoom: Double, dpi: Double, dark: Boolean) {
        if (!ruler.visible) return
        val fg = if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val bg = if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val span = hypot(c.width.toDouble(), c.height.toDouble()) * 2
        c.save(); c.translate(ruler.center.x.toFloat(), ruler.center.y.toFloat()); c.rotate(Math.toDegrees(ruler.angleRad).toFloat())
        val half = (ruler.thicknessPx / 2).toFloat()
        p.color = bg; p.alpha = 220
        c.drawRect(-span.toFloat(), -half, span.toFloat(), half, p)
        p.color = fg; p.strokeWidth = density.toFloat()
        c.drawLine(-span.toFloat(), -half, span.toFloat(), -half, p)
        c.drawLine(-span.toFloat(), half, span.toFloat(), half, p)
        val cm = dpi / 2.54 * zoom
        val stepCm = max(1.0, ceil(20 * density / cm))
        val step = cm * stepCm
        p.textSize = (11 * density).toFloat(); p.textAlign = Paint.Align.CENTER
        if (step.isFinite() && step > 0) for (i in -ceil(span / step).toInt()..ceil(span / step).toInt()) {
            val x = (i * step).toFloat()
            c.drawLine(x, -half, x, -half * .55f, p); c.drawLine(x, half, x, half * .55f, p)
            c.drawText("${abs(i * stepCm).toInt()}", x, -half * .25f, p)
        }
        c.restore()
        for ((button, at) in ruler.buttonCenters()) {
            val locked = if (button == RulerButton.LOCK_POS) ruler.lockPosition else ruler.lockAngle
            p.color = if (locked) fg else bg; p.style = Paint.Style.FILL
            c.drawCircle(at.x.toFloat(), at.y.toFloat(), ruler.buttonRadiusPx().toFloat(), p)
            p.color = if (locked) bg else fg; p.style = Paint.Style.STROKE
            c.drawCircle(at.x.toFloat(), at.y.toFloat(), ruler.buttonRadiusPx().toFloat(), p)
            p.style = Paint.Style.FILL
            c.drawText(if (button == RulerButton.LOCK_POS) "↔" else "∠", at.x.toFloat(), at.y.toFloat() + p.textSize / 3, p)
        }
        for (at in ruler.handleCenters(.3 * min(c.width, c.height))) {
            p.color = bg; c.drawCircle(at.x.toFloat(), at.y.toFloat(), ruler.handleRadiusPx().toFloat(), p)
            p.color = fg; p.style = Paint.Style.STROKE
            c.drawCircle(at.x.toFloat(), at.y.toFloat(), ruler.handleRadiusPx().toFloat(), p)
            p.style = Paint.Style.FILL
            c.drawText("${Math.toDegrees(ruler.angleRad).roundToInt()}°", at.x.toFloat(), at.y.toFloat() + p.textSize / 3, p)
        }
    }
}
