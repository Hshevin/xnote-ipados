package com.xnotes.pen

import org.junit.Assert.*
import org.junit.Test

class ProPenTraceTest {
    @Test fun recordedMovinkPadMotionHasExactlyOneCommandPerPress() {
        val rows = javaClass.getResourceAsStream("/pro-pen-3-motion.csv")!!
            .bufferedReader().use { it.readLines() }.filterNot { it.startsWith("#") }
        assertEquals(3907, rows.size)
        for ((mask, expected) in mapOf(32 to 12, 64 to 11, 4 to 6)) {
            val tracker = ProPenButtons("pen")
            val binding = mapOf(mask to PenBinding("undo", PenBindingMode.SELECT))
            val count = rows.sumOf { row ->
                tracker.update(row.substringAfterLast(',').toInt(), binding).commands.size
            }
            assertEquals("button mask $mask", expected, count)
        }
    }
}
