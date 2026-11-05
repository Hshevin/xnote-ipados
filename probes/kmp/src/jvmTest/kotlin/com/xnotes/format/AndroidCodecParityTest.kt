package com.xnotes.format

import kotlin.random.Random
import kotlin.test.*
import java.io.StringReader
import java.io.StringWriter

class AndroidCodecParityTest {
    @Test fun generatedAdapterMatchesAndroidWriter() {
        val random = Random(42)
        val portable = Writer()
        val android = StringWriter()
        val a = JsonWrite(portable).beginArray()
        val b = com.xnotes.original.JsonWrite(android).beginArray()
        repeat(10000) {
            val x = (random.nextDouble() - 0.5) * 20000000
            val y = (random.nextDouble() - 0.5) * 100
            val pressure = random.nextDouble()
            val time = if (it % 2 == 0) it.toDouble() else null
            a.samplePoint(x, y, pressure, time)
            b.samplePoint(x, y, pressure, time)
        }
        a.endArray(); b.endArray()
        assertEquals(android.toString(), portable.toString())
    }
    @Test fun generatedParserMatchesAndroidBits() {
        val literals = listOf("-0", "0.1", "2.675", "90071992547409921", "1e-22", "5e-324", "1.7976931348623157e308")
        for (literal in literals) {
            val actual = JsonPull(Reader(literal)).nextDouble()
            val expected = com.xnotes.original.JsonPull(StringReader(literal)).nextDouble()
            assertEquals(expected.toBits(), actual.toBits(), literal)
        }
    }
    @Test fun roundingAdapterMatchesJavaAtBoundaries() {
        val values = listOf(-1.5, 1.5, -0.5, 0.49999999999999994, 4503599627370497.0,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        values.forEach { assertEquals(Math.round(it), roundLikeJava(it), it.toString()) }
    }
}
