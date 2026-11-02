package com.xnotes.pen

import android.view.MotionEvent
import android.view.InputDevice
import com.xnotes.core.geometry.Rect
import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.Stroke
import com.xnotes.core.tools.Tool
import com.xnotes.ui.InfiniteEditor
import org.junit.Assert.*
import org.junit.Test

class CanvasCommonToolsTest {
    private fun onMain(test: (InfiniteEditor) -> Unit) {
        var error: Throwable? = null
        PenTestRunner.instance.runOnMainSync {
            try {
                val ed = InfiniteEditor(PenTestRunner.instance.targetContext)
                ed.viewport.widthPx = 1000; ed.viewport.heightPx = 1400
                test(ed)
            } catch (t: Throwable) { error = t }
        }
        error?.let { throw it }
    }
    private fun event(ed: InfiniteEditor, action: Int, x: Float, y: Float) {
        val prop = MotionEvent.PointerProperties().apply { id = 7; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val at = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .5f }
        val now = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, 1, arrayOf(prop), arrayOf(at), 0, 0, 1f, 1f, 17, 0, InputDevice.SOURCE_STYLUS, 0)
        try { ed.interaction.onTouch(event) } finally { event.recycle() }
    }
    @Test fun textBoxIsEditableAndSurvivesCodecAndUndo() = onMain { ed ->
        ed.insertTextBox(Rect(50.0, 70.0, 300.0, 160.0))
        assertNotNull(ed.editingField)
        ed.updateEditingText("画布文字 hello")
        ed.commitText()
        val shape = ed.document.items.single() as ShapeItem
        assertEquals("画布文字 hello", shape.label!!.text)
        val codec = com.xnotes.format.CanvasCodec(com.xnotes.platform.AndroidImageCodec(), com.xnotes.platform.AndroidTextMeasurer())
        val bytes = java.io.ByteArrayOutputStream().also { codec.write(ed.document, it) }.toByteArray()
        val restored = codec.read(bytes.inputStream()).items.single() as ShapeItem
        assertEquals(shape.label!!.text, restored.label!!.text)
        assertEquals(0, restored.strokeRgba.a)
        ed.undo(); assertTrue(ed.document.items.isEmpty())
        ed.redo(); assertEquals("画布文字 hello", (ed.document.items.single() as ShapeItem).label!!.text)
        ed.insertTextBox(Rect(60.0, 80.0, 300.0, 160.0))
        assertEquals(1, ed.document.items.size)
        ed.updateEditingText("edited"); ed.commitText()
        assertEquals("edited", shape.label!!.text)
    }
    @Test fun screenshotExportsRegionAndCancelDoesNotEditDocument() = onMain { ed ->
        ed.armTool(Tool.SCREENSHOT)
        event(ed, MotionEvent.ACTION_DOWN, 100f, 200f)
        event(ed, MotionEvent.ACTION_MOVE, 400f, 500f)
        event(ed, MotionEvent.ACTION_CANCEL, 400f, 500f)
        assertNull(ed.screenshotMenu); assertNull(ed.interaction.captureRect)
        assertTrue(ed.document.items.isEmpty())
        event(ed, MotionEvent.ACTION_DOWN, 100f, 200f)
        event(ed, MotionEvent.ACTION_MOVE, 400f, 500f)
        event(ed, MotionEvent.ACTION_UP, 400f, 500f)
        assertNotNull(ed.screenshotMenu)
        val file = ed.screenshotImageForShare()!!
        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        assertEquals(600, bmp.width); assertEquals(600, bmp.height)
        bmp.recycle(); file.delete()
        assertNull(ed.screenshotMenu)
        assertTrue(ed.document.items.isEmpty())
    }
    @Test fun cancelledTextGestureCreatesNothingAndRulerSnapsInk() = onMain { ed ->
        ed.armTool(Tool.TEXT_BOX)
        event(ed, MotionEvent.ACTION_DOWN, 100f, 100f)
        event(ed, MotionEvent.ACTION_MOVE, 400f, 300f)
        event(ed, MotionEvent.ACTION_CANCEL, 400f, 300f)
        assertTrue(ed.document.items.isEmpty()); assertNull(ed.editingField)
        ed.insertTextBox(Rect(100.0, 100.0, 200.0, 100.0)); ed.commitText()
        assertTrue("empty text draft must leave no invisible shape", ed.document.items.isEmpty())
        ed.armTool(Tool.PEN); ed.toggleRuler()
        val r = ed.interaction.rulerController.ruler
        r.center = Pt(500.0, 500.0); r.angleRad = 0.0
        val edge = (500 + r.thicknessPx / 2).toFloat()
        event(ed, MotionEvent.ACTION_DOWN, 100f, edge + 2)
        event(ed, MotionEvent.ACTION_MOVE, 250f, edge + 3)
        event(ed, MotionEvent.ACTION_UP, 350f, edge + 2)
        val stroke = ed.document.items.single() as Stroke
        assertTrue(stroke.samples.all { kotlin.math.abs(it.y - edge) < .01 })
    }
}
