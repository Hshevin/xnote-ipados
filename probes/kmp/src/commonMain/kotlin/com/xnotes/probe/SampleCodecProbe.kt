package com.xnotes.probe

import com.xnotes.format.*

/** Representative sample serialization through the Android parser/writer algorithms. */
class SampleCodecProbe {
    @Throws(Exception::class)
    fun roundTrip(json: String): String {
        val input = JsonPull(Reader(json))
        val samples = RawSamples()
        input.beginArray()
        while (input.hasNext()) {
            input.beginArray()
            val x = input.nextDouble()
            val y = input.nextDouble()
            val p = input.nextDouble()
            val time = if (input.hasNext()) input.nextDouble() else 0.0
            input.endArray()
            samples.add(x, y, p, time)
        }
        input.endArray()
        input.requireDocumentEnd()
        val out = Writer()
        val writer = JsonWrite(out).beginArray()
        for (i in 0 until samples.n) writer.samplePoint(samples.xs[i], samples.ys[i], samples.ps[i], samples.ts?.get(i))
        writer.endArray()
        return out.toString()
    }
}
