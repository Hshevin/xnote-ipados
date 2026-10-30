package com.xnotes.pen

enum class PenBindingMode { HOLD, SELECT }

data class PenBinding(val action: String = "none", val mode: PenBindingMode = PenBindingMode.HOLD) {
    companion object {
        val tools = setOf("pen", "highlighter", "eraser", "pan", "select", "lasso")
        val commands = setOf("undo", "redo")
        val actions = tools + commands + "none"
    }
}

data class PenButtonResult(val tool: String, val commands: List<String> = emptyList())

/** Pure edge tracking. Physical numbering follows the MovinkPad evidence, not bit order. */
class ProPenButtons(initialTool: String) {
    private var baseTool = initialTool
    private var previousMask = 0
    private val holds = linkedMapOf<Int, String>()

    fun update(rawMask: Int, bindings: Map<Int, PenBinding>): PenButtonResult {
        val pressed = rawMask and previousMask.inv()
        previousMask = rawMask // keep unknown bits; only the three known bits dispatch actions
        holds.keys.removeAll { rawMask and it == 0 }
        val commands = mutableListOf<String>()
        for (mask in masks) {
            if (pressed and mask == 0) continue
            val binding = bindings[mask] ?: continue
            when (binding.action) {
                in PenBinding.commands -> commands.add(binding.action)
                in PenBinding.tools -> if (binding.mode == PenBindingMode.HOLD) {
                    holds[mask] = binding.action
                } else {
                    select(binding.action)
                }
            }
        }
        return PenButtonResult(holds.values.lastOrNull() ?: baseTool, commands)
    }

    /** An explicit selection wins over all older temporary selections, even the same tool. */
    fun select(tool: String) {
        baseTool = tool
        holds.clear()
    }

    /** Losing focus cancels holds, retaining edges so a still-held command cannot fire twice. */
    fun reset(): PenButtonResult {
        holds.clear()
        return PenButtonResult(baseTool)
    }

    /** Hover exit also precedes contact: hide the temporary tool without inventing a release. */
    fun suspend(): PenButtonResult = PenButtonResult(baseTool)

    companion object {
        val masks = listOf(0x20, 0x40, 0x04)
    }
}
