package com.xnotes.probe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeometryProbeTest {
    @Test fun batchMatchesGeometry() {
        val probe = GeometryProbe()
        assertEquals(10.0, probe.pathLength(doubleArrayOf(0.0, 0.0, 3.0, 4.0, 6.0, 8.0)))
        assertEquals(0.0, probe.pathLength(doubleArrayOf()))
        assertFailsWith<IllegalArgumentException> { probe.pathLength(doubleArrayOf(1.0)) }
    }
}
