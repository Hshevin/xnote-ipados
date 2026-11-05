package com.xnotes.format

import com.xnotes.probe.SampleCodecProbe
import kotlin.test.*

class FormatProbeTest {
    @Test fun samplesPreserveRoundedValuesAndTime() {
        val codec = SampleCodecProbe()
        val fixture = "[[1.25,-2.5,0.125,12],[3,4,1,15]]"
        assertEquals(fixture, codec.roundTrip(fixture))
        assertEquals("[[1.23,-2.35,0.126]]", codec.roundTrip("[[1.234,-2.346,0.1256]]"))
    }
    @Test fun rejectsMalformedSamplesAndTrailingInput() {
        for (fixture in listOf("[[1,2]]", "[[1,2,3,4,5]]", "[[1,2,NaN]]", "[] []", "[")) {
            assertFails(fixture) { SampleCodecProbe().roundTrip(fixture) }
        }
    }
    @Test fun stringEscapingAndParsing() {
        val text = "中文/\\\"\n\u0001"
        val out = Writer()
        JsonWrite(out).value(text)
        assertEquals("\"中文\\/\\\\\\\"\\n\\u0001\"", out.toString())
        assertEquals(text, JsonPull(Reader(out.toString())).nextString())
    }
    @Test fun roundingTiesAndSaturation() {
        assertEquals(-1L, roundLikeJava(-1.5))
        assertEquals(2L, roundLikeJava(1.5))
        assertEquals(0L, roundLikeJava(Double.NaN))
        assertEquals(Long.MAX_VALUE, roundLikeJava(Double.POSITIVE_INFINITY))
        assertEquals(Long.MIN_VALUE, roundLikeJava(Double.NEGATIVE_INFINITY))
    }
    @Test fun sampleArraysGrowWithoutLosingEarlierTimes() {
        val raw = RawSamples()
        repeat(200) { raw.add(it.toDouble(), -it.toDouble(), 0.5, if (it == 150) 42.0 else 0.0) }
        assertEquals(200, raw.n)
        assertEquals(0.0, raw.ts!![0])
        assertEquals(42.0, raw.ts!![150])
        assertEquals(199.0, raw.xs[199])
    }
}
