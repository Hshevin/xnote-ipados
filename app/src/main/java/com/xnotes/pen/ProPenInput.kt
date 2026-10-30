package com.xnotes.pen

import android.view.InputDevice
import android.view.MotionEvent
import com.xnotes.core.tools.Tool

/** Shared by the paged and infinite editors. Never consumes or rewrites the ink samples. */
class ProPenInput(
    private val bindings: () -> Map<Int, PenBinding>,
    private val currentTool: () -> Tool,
    private val armTool: (Tool) -> Unit,
    private val command: (String) -> Unit,
) {
    private val buttons by lazy { ProPenButtons(currentTool().id) }
    private var applying = false
    private var contact = false
    private var device: Int? = null
    private var pendingTool: String? = null
    private val pendingCommands = mutableListOf<String>()

    fun selected(tool: Tool) {
        if (!applying) {
            buttons.select(tool.id)
            pendingTool = null
        }
    }

    fun reset() {
        contact = false
        pendingCommands.clear()
        pendingTool = null
        apply(buttons.reset())
    }

    private fun stylusIndex(e: MotionEvent): Int =
        if (!e.isFromSource(InputDevice.SOURCE_STYLUS)) -1
        else (0 until e.pointerCount).firstOrNull {
            e.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS ||
                e.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER
        } ?: -1

    fun touch(e: MotionEvent, dispatch: () -> Boolean): Boolean {
        val index = stylusIndex(e)
        if (index < 0) return dispatch()
        val action = e.actionMasked
        if (device != e.deviceId) {
            reset()
            device = e.deviceId
        }
        // A cancelled stroke must reach the drawing engine before restoring its tool.
        if (action == MotionEvent.ACTION_CANCEL) {
            val handled = dispatch()
            reset()
            return handled
        }
        apply(buttons.update(e.buttonState, bindings()))
        if (action == MotionEvent.ACTION_DOWN ||
            (action == MotionEvent.ACTION_POINTER_DOWN && e.actionIndex == index)) contact = true
        val handled = dispatch()
        if (action == MotionEvent.ACTION_UP ||
            (action == MotionEvent.ACTION_POINTER_UP && e.actionIndex == index)) {
            contact = false
            if ((e.flags and MotionEvent.FLAG_CANCELED) != 0) reset()
            else flush()
        }
        return handled
    }

    fun motion(e: MotionEvent) {
        if (stylusIndex(e) < 0) return
        if (device != e.deviceId) {
            reset()
            device = e.deviceId
        }
        if (e.actionMasked == MotionEvent.ACTION_HOVER_EXIT && !contact) {
            apply(buttons.suspend())
        } else {
            apply(buttons.update(e.buttonState, bindings()))
        }
    }

    private fun apply(result: PenButtonResult) {
        pendingTool = result.tool
        pendingCommands.addAll(result.commands)
        if (!contact) flush()
    }

    private fun flush() {
        applying = true
        try {
            pendingTool?.let { id -> Tool.fromId(id)?.let { if (it != currentTool()) armTool(it) } }
            pendingTool = null
            val commands = pendingCommands.toList()
            pendingCommands.clear()
            commands.forEach(command)
        } finally {
            applying = false
        }
    }
}
