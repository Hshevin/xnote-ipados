package com.xnotes.pen

import org.json.JSONObject

/** Additive preferences only; never touches the document format. Unknown actions are disabled. */
object PenBindingsJson {
    fun read(value: JSONObject?): Map<Int, PenBinding> = ProPenButtons.masks.associateWith { mask ->
        val entry = value?.optJSONObject(mask.toString())
        val action = entry?.optString("action")?.takeIf { it in PenBinding.actions } ?: "none"
        val mode = if (entry?.optString("mode") == "SELECT") PenBindingMode.SELECT else PenBindingMode.HOLD
        PenBinding(action, if (action in PenBinding.commands) PenBindingMode.SELECT else mode)
    }

    fun write(bindings: Map<Int, PenBinding>): JSONObject = JSONObject().apply {
        ProPenButtons.masks.forEach { mask ->
            val b = bindings[mask] ?: PenBinding()
            put(mask.toString(), JSONObject().put("action", b.action).put("mode", b.mode.name))
        }
    }
}
