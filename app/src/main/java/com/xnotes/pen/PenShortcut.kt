package com.xnotes.pen

/** Persistent input identity. Android's transient deviceId is deliberately not stored. */
data class PenTrigger(
    val kind: Kind,
    val code: Int,
    val descriptor: String,
    val source: Int,
    val scanCode: Int = 0,
) {
    enum class Kind { MOTION, KEY }
    val valid: Boolean get() = if (kind == Kind.MOTION) Integer.bitCount(code) == 1 else code > 0
}

data class PenShortcut(
    val name: String,
    val triggers: List<PenTrigger>,
    val binding: PenBinding = PenBinding(),
) {
    companion object {
        const val MAX_SHORTCUTS = 32
        const val MAX_SIGNALS = 8
        fun valid(shortcuts: List<PenShortcut>): Boolean =
            shortcuts.size <= MAX_SHORTCUTS && shortcuts.all {
                it.name.isNotBlank() && it.name.length <= 80 &&
                    it.triggers.isNotEmpty() && it.triggers.size <= MAX_SIGNALS &&
                    it.triggers.all(PenTrigger::valid) && it.binding.action in PenBinding.actions
            } && shortcuts.flatMap { it.triggers }.let { it.distinct().size == it.size }
    }
}

/** Records one press including aliases reported by different Android event routes. */
class PenRecording {
    private val pressed = linkedSetOf<PenTrigger>()
    private val seen = linkedSetOf<PenTrigger>()
    private var canceled = false
    val released: Boolean get() = !canceled && seen.isNotEmpty() && pressed.isEmpty()

    fun event(trigger: PenTrigger, down: Boolean) {
        if (canceled || !trigger.valid) return
        if (down) {
            if (seen.size >= PenShortcut.MAX_SIGNALS && trigger !in seen) { cancel(); return }
            seen.add(trigger)
            pressed.add(trigger)
        } else pressed.remove(trigger)
    }
    fun finish(): List<PenTrigger>? = if (released) seen.toList() else null
    fun cancel() { canceled = true; pressed.clear(); seen.clear() }
}

/** Maps recorded identities to transient bits for the existing pure tool state machine. */
class PenShortcutState {
    private val held = linkedSetOf<PenTrigger>()
    fun keyOrButton(trigger: PenTrigger, down: Boolean) {
        if (down) held.add(trigger) else held.remove(trigger)
    }
    fun motion(triggers: Set<PenTrigger>) {
        held.removeAll { it.kind == PenTrigger.Kind.MOTION }
        held.addAll(triggers)
    }
    fun clear() = held.clear()
    fun mask(shortcuts: List<PenShortcut>, motionOverride: Set<PenTrigger>? = null): Int {
        val signals = if (motionOverride == null) held else held.filter { it.kind == PenTrigger.Kind.KEY }.toSet() + motionOverride
        return shortcuts.foldIndexed(0) { index, mask, shortcut ->
            if (shortcut.triggers.any { it in signals }) mask or (1 shl index) else mask
        }
    }
    fun bindings(shortcuts: List<PenShortcut>): Map<Int, PenBinding> =
        shortcuts.mapIndexed { index, shortcut -> (1 shl index) to shortcut.binding }.toMap()
}
