package com.xnotes.pen

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.xnotes.core.tools.Tool
import org.junit.Assert.*
import org.junit.Test

/** Same event contracts run on the host and in device instrumentation. */
abstract class RecordedPenInputContract {
    private var tool = Tool.PEN
    private val commands = mutableListOf<String>()
    private var shortcuts = emptyList<PenShortcut>()
    private val input = ProPenInput({ shortcuts }, { tool }, { tool = it }, { commands.add(it) })

    private fun event(action: Int, mask: Int, source: Int = InputDevice.SOURCE_STYLUS,
                      type: Int = MotionEvent.TOOL_TYPE_STYLUS): MotionEvent {
        val p = MotionEvent.PointerProperties().apply { id = 0; toolType = type }
        val c = MotionEvent.PointerCoords().apply { x = 10f; y = 20f; pressure = .5f }
        return MotionEvent.obtain(1, 2, action, 1, arrayOf(p), arrayOf(c), 0, mask,
            1f, 1f, 9999, 0, source, 0)
    }
    private fun motion(action: Int, mask: Int, source: Int = InputDevice.SOURCE_STYLUS) {
        event(action, mask, source).let { try { input.motion(it) } finally { it.recycle() } }
    }
    private fun touch(action: Int, mask: Int, during: () -> Unit = {}) {
        event(action, mask).let { try { input.touch(it) { during(); true } } finally { it.recycle() } }
    }
    private fun key(action: Int, repeats: Int = 0, flags: Int = 0, code: Int = 600): KeyEvent =
        KeyEvent(1, 2, action, code, repeats, 0, 9999, 42, flags, InputDevice.SOURCE_KEYBOARD)
    private fun bindMotion(mask: Int, action: String, mode: PenBindingMode = PenBindingMode.HOLD) {
        val e = event(MotionEvent.ACTION_HOVER_MOVE, mask)
        try { shortcuts = listOf(PenShortcut("Button", PenSignals.motion(e).toList(), PenBinding(action, mode))) }
        finally { e.recycle() }
    }
    private fun bindKey(action: String) {
        shortcuts = listOf(PenShortcut("Remote", listOf(PenSignals.key(key(KeyEvent.ACTION_DOWN))!!), PenBinding(action)))
    }

    @Test fun recordedMotionFromTouchscreenSourceAlsoPlaysBack() {
        val e = event(MotionEvent.ACTION_HOVER_MOVE, 128, InputDevice.SOURCE_TOUCHSCREEN)
        try { shortcuts = listOf(PenShortcut("Pen", PenSignals.motion(e).toList(), PenBinding("eraser"))) }
        finally { e.recycle() }
        motion(MotionEvent.ACTION_HOVER_MOVE, 128, InputDevice.SOURCE_TOUCHSCREEN)
        assertEquals(Tool.ERASER, tool)
        motion(MotionEvent.ACTION_HOVER_MOVE, 0, InputDevice.SOURCE_TOUCHSCREEN)
        assertEquals(Tool.PEN, tool)
    }
    @Test fun arbitraryRecordedMaskDeduplicatesIndependentButtonAndHoverEvents() {
        bindMotion(128, "undo")
        motion(MotionEvent.ACTION_HOVER_MOVE, 128)
        motion(MotionEvent.ACTION_BUTTON_PRESS, 128)
        motion(MotionEvent.ACTION_HOVER_MOVE, 128)
        assertEquals(listOf("undo"), commands)
        motion(MotionEvent.ACTION_BUTTON_RELEASE, 0)
        motion(MotionEvent.ACTION_HOVER_MOVE, 128)
        assertEquals(listOf("undo", "undo"), commands)
    }
    @Test fun keyRepeatsDoNotRepeatCommandsAndUnboundKeysPassThrough() {
        bindKey("undo")
        assertTrue(input.key(key(KeyEvent.ACTION_DOWN)))
        input.key(key(KeyEvent.ACTION_DOWN, repeats = 2))
        assertFalse(input.key(key(KeyEvent.ACTION_DOWN, code = 601)))
        assertEquals(listOf("undo"), commands)
        input.key(key(KeyEvent.ACTION_UP))
        input.key(key(KeyEvent.ACTION_DOWN))
        assertEquals(2, commands.size)
    }
    @Test fun keyHoldSurvivesMotionWithZeroButtonsAndRestoresOriginalTool() {
        bindKey("eraser")
        input.key(key(KeyEvent.ACTION_DOWN))
        assertEquals(Tool.ERASER, tool)
        motion(MotionEvent.ACTION_HOVER_MOVE, 0)
        assertEquals(Tool.ERASER, tool)
        input.key(key(KeyEvent.ACTION_UP))
        assertEquals(Tool.PEN, tool)
    }
    @Test fun keyChangesDuringStrokeWaitUntilStrokeCompletes() {
        bindKey("eraser")
        touch(MotionEvent.ACTION_DOWN, 0)
        input.key(key(KeyEvent.ACTION_DOWN))
        assertEquals(Tool.PEN, tool)
        touch(MotionEvent.ACTION_UP, 0) { assertEquals(Tool.PEN, tool) }
        assertEquals(Tool.ERASER, tool)
        input.key(key(KeyEvent.ACTION_UP))
        assertEquals(Tool.PEN, tool)
    }
    @Test fun canceledStrokeDiscardsKeyCommandAndAllowsNextPress() {
        bindKey("undo")
        touch(MotionEvent.ACTION_DOWN, 0)
        input.key(key(KeyEvent.ACTION_DOWN))
        touch(MotionEvent.ACTION_CANCEL, 0)
        assertTrue(commands.isEmpty())
        input.key(key(KeyEvent.ACTION_UP))
        input.key(key(KeyEvent.ACTION_DOWN))
        assertEquals(listOf("undo"), commands)
    }
    @Test fun canceledKeyReleaseDiscardsPendingCommandAndRearmsNextPress() {
        bindKey("undo")
        touch(MotionEvent.ACTION_DOWN, 0)
        input.key(key(KeyEvent.ACTION_DOWN))
        input.key(key(KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_CANCELED))
        touch(MotionEvent.ACTION_UP, 0)
        assertTrue(commands.isEmpty())
        input.key(key(KeyEvent.ACTION_DOWN))
        assertEquals(listOf("undo"), commands)
    }
    @Test fun simultaneousKeyAndMotionAliasesDispatchOneCommand() {
        bindMotion(32, "undo")
        shortcuts = listOf(shortcuts.single().copy(triggers = shortcuts.single().triggers + PenSignals.key(key(KeyEvent.ACTION_DOWN))!!))
        motion(MotionEvent.ACTION_HOVER_MOVE, 32)
        input.key(key(KeyEvent.ACTION_DOWN))
        motion(MotionEvent.ACTION_BUTTON_PRESS, 32)
        motion(MotionEvent.ACTION_BUTTON_RELEASE, 0)
        input.key(key(KeyEvent.ACTION_UP))
        assertEquals(listOf("undo"), commands)
    }
    @Test fun focusResetRestoresHeldToolEvenWithoutKeyUp() {
        bindKey("eraser")
        input.key(key(KeyEvent.ACTION_DOWN))
        input.reset()
        assertEquals(Tool.PEN, tool)
        motion(MotionEvent.ACTION_HOVER_MOVE, 0)
        assertEquals(Tool.PEN, tool)
    }
    @Test fun inheritedMotionCommandDoesNotRepeatInNextPaneButHoldIsRearmed() {
        bindMotion(128, "undo")
        val e = event(MotionEvent.ACTION_HOVER_ENTER, 128)
        try { input.motion(e, 128) } finally { e.recycle() }
        assertTrue(commands.isEmpty())
        motion(MotionEvent.ACTION_HOVER_MOVE, 0)
        motion(MotionEvent.ACTION_HOVER_MOVE, 128)
        assertEquals(listOf("undo"), commands)
    }
    @Test fun freshKeyDownAfterFocusLossIsNotSwallowed() {
        bindKey("undo")
        input.key(key(KeyEvent.ACTION_DOWN))
        input.reset() // the system never delivers the first UP
        input.key(key(KeyEvent.ACTION_DOWN))
        assertEquals(listOf("undo", "undo"), commands)
    }
    @Test fun nonOverlappingAliasesDoNotDoubleExecuteOrThrottleNextRealPress() {
        bindMotion(32, "undo")
        shortcuts = listOf(shortcuts.single().copy(triggers = shortcuts.single().triggers + PenSignals.key(key(KeyEvent.ACTION_DOWN))!!))
        motion(MotionEvent.ACTION_HOVER_MOVE, 32)
        motion(MotionEvent.ACTION_HOVER_MOVE, 0)
        input.key(key(KeyEvent.ACTION_DOWN))
        input.key(key(KeyEvent.ACTION_UP))
        assertEquals(listOf("undo"), commands)
        motion(MotionEvent.ACTION_HOVER_MOVE, 32)
        assertEquals(listOf("undo", "undo"), commands)
    }
    @Test fun keyOnlyHoldTransfersToNextPaneAndReleasesThere() {
        bindKey("eraser")
        input.key(key(KeyEvent.ACTION_DOWN))
        val held = input.heldKeys
        input.reset()
        var nextTool = tool
        val next = ProPenInput({ shortcuts }, { nextTool }, { nextTool = it }, { commands.add(it) })
        next.inheritKeys(held)
        assertEquals(Tool.PEN, tool)
        assertEquals(Tool.ERASER, nextTool)
        next.key(key(KeyEvent.ACTION_UP))
        assertEquals(Tool.PEN, nextTool)
    }
    @Test fun keyOnlyCommandDoesNotRepeatOnPaneTransfer() {
        bindKey("undo")
        input.key(key(KeyEvent.ACTION_DOWN))
        val held = input.heldKeys
        input.reset()
        val next = ProPenInput({ shortcuts }, { Tool.PEN }, {}, { commands.add(it) })
        next.inheritKeys(held)
        assertEquals(listOf("undo"), commands)
        next.key(key(KeyEvent.ACTION_UP))
        next.key(key(KeyEvent.ACTION_DOWN))
        assertEquals(listOf("undo", "undo"), commands)
    }
    @Test fun aliasReleaseAfterHoverExitCannotReviveTemporaryTool() {
        bindMotion(32, "eraser")
        shortcuts = listOf(shortcuts.single().copy(triggers = shortcuts.single().triggers + PenSignals.key(key(KeyEvent.ACTION_DOWN))!!))
        motion(MotionEvent.ACTION_HOVER_MOVE, 32)
        assertEquals(Tool.ERASER, tool)
        motion(MotionEvent.ACTION_HOVER_EXIT, 0)
        assertEquals(Tool.PEN, tool)
        assertTrue(input.key(key(KeyEvent.ACTION_UP)))
        assertEquals(Tool.PEN, tool)
    }
    @Test fun tipFingerAndMouseDoNotRecordPenButtons() {
        for ((type, mask) in listOf(MotionEvent.TOOL_TYPE_STYLUS to 0,
                MotionEvent.TOOL_TYPE_FINGER to 32, MotionEvent.TOOL_TYPE_MOUSE to 32)) {
            val e = event(MotionEvent.ACTION_DOWN, mask, type = type)
            try { assertTrue(PenSignals.motion(e).isEmpty()) } finally { e.recycle() }
        }
    }
    @Test fun navigationKeysRemainAvailableWhileRecording() {
        assertNull(PenSignals.key(key(KeyEvent.ACTION_DOWN, code = KeyEvent.KEYCODE_BACK)))
        assertNull(PenSignals.key(key(KeyEvent.ACTION_DOWN, code = KeyEvent.KEYCODE_ESCAPE)))
    }
}
