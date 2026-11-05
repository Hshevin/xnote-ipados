package com.xnotes.probe

import com.xnotes.core.geometry.Pt

/** Calls the actual Android geometry source, without copying or extracting it yet. */
class GeometryProbe {
    fun distance(x0: Double, y0: Double, x1: Double, y1: Double): Double =
        Pt(x0, y0).distanceTo(Pt(x1, y1))

    /** A batch boundary for measuring Kotlin/Native interop independently of rendering. */
    fun pathLength(coordinates: DoubleArray): Double {
        require(coordinates.size % 2 == 0) { "Coordinates must be x/y pairs" }
        var length = 0.0
        for (i in 2 until coordinates.size step 2) {
            length += distance(coordinates[i - 2], coordinates[i - 1], coordinates[i], coordinates[i + 1])
        }
        return length
    }
}
