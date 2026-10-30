package com.xnotes.pen

import org.junit.Assert.*
import org.junit.Test

class ProPenButtonsTest {
    private val bindings = mapOf(
        0x20 to PenBinding("eraser", PenBindingMode.HOLD),
        0x40 to PenBinding("lasso", PenBindingMode.SELECT),
        0x04 to PenBinding("undo", PenBindingMode.SELECT),
    )

    @Test fun allThreeMasksAreIndependentAndRepeatedMotionIsDeduplicated() {
        val p = ProPenButtons("pen")
        assertEquals("eraser", p.update(0x20, bindings).tool)
        assertEquals("pen", p.update(0, bindings).tool)
        assertEquals("lasso", p.update(0x40, bindings).tool)
        assertEquals("lasso", p.update(0, bindings).tool)
        assertEquals(listOf("undo"), p.update(0x04, bindings).commands)
        assertTrue(p.update(0x04, bindings).commands.isEmpty())
        p.update(0, bindings)
        assertEquals(listOf("undo"), p.update(0x04, bindings).commands)
    }

    @Test fun selectAndExplicitSelectionSupersedeOlderHold() {
        val p = ProPenButtons("pen")
        p.update(0x20, bindings)
        assertEquals("lasso", p.update(0x60, bindings).tool)
        assertEquals("lasso", p.update(0x40, bindings).tool)
        p.update(0, bindings)
        p.update(0x20, bindings)
        p.select("highlighter")
        assertEquals("highlighter", p.update(0, bindings).tool)
    }

    @Test fun overlappingHoldsRestoreBaseRegardlessOfReleaseOrder() {
        val b = bindings + (0x40 to PenBinding("pan", PenBindingMode.HOLD))
        val p = ProPenButtons("pen")
        p.update(0x20, b)
        assertEquals("pan", p.update(0x60, b).tool)
        assertEquals("pan", p.update(0x40, b).tool)
        assertEquals("pen", p.update(0, b).tool)
        p.update(0x20, b)
        p.update(0x60, b)
        assertEquals("eraser", p.update(0x20, b).tool)
        assertEquals("pen", p.update(0, b).tool)
    }

    @Test fun resetRestoresBaseAndDoesNotRetriggerAStillHeldButton() {
        val p = ProPenButtons("pen")
        p.update(0x20, bindings)
        assertEquals("pen", p.reset().tool)
        assertEquals("pen", p.update(0x20, bindings).tool)
        p.update(0, bindings)
        assertEquals("eraser", p.update(0x20, bindings).tool)
    }

    @Test fun hoverExitRestoresToolButContactDoesNotRepeatCommand() {
        val p = ProPenButtons("pen")
        p.update(0x20, bindings)
        assertEquals("pen", p.suspend().tool)
        assertEquals("eraser", p.update(0x20, bindings).tool)
        p.update(0, bindings)
        p.update(0x04, bindings)
        p.suspend()
        assertTrue(p.update(0x04, bindings).commands.isEmpty())
    }

    @Test fun unknownBitsAndUnboundButtonsHaveNoAction() {
        val p = ProPenButtons("pen")
        assertEquals(PenButtonResult("pen"), p.update(0x100, bindings))
        assertEquals(PenButtonResult("pen"), p.update(0x64, emptyMap()))
    }
}
