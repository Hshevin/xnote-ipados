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

/** Pure edge tracking. Bits may be raw buttons in replay tests or recorded shortcut slots. */
class ProPenButtons(initialTool: String) {
    private var baseTool = initialTool
    private var previousMask = 0
    private val holds = linkedMapOf<Int, String>()

    fun update(rawMask: Int, bindings: Map<Int, PenBinding>, inheritedButtons: Int? = null): PenButtonResult {
        // Re-arm inherited HOLD tools, but only genuinely new physical presses run commands.
        val pressed = rawMask and (inheritedButtons ?: previousMask).inv()
        val rearmed = pressed or (rawMask and (inheritedButtons ?: 0))
        previousMask = rawMask // keep unknown bits; only the three known bits dispatch actions
        holds.keys.removeAll { rawMask and it == 0 }
        val commands = mutableListOf<String>()
        for (mask in bindings.keys) {
            if (rearmed and mask == 0) continue
            val binding = bindings[mask] ?: continue
            when (binding.action) {
                in PenBinding.commands -> if (mask and pressed != 0) commands.add(binding.action)
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

}
