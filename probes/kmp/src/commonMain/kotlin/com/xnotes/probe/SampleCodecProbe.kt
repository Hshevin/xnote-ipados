package com.xnotes.probe

import com.xnotes.format.*
import com.xnotes.core.stroke.Sample
import com.xnotes.core.geometry.Affine

/** Representative sample serialization through the Android parser/writer algorithms. */
class SampleCodecProbe {
    @Throws(Exception::class)
    fun roundTrip(json: String): String = translate(json, 0.0, 0.0)

    @Throws(Exception::class)
    fun translate(json: String, dx: Double, dy: Double): String {
        require(dx.isFinite() && dy.isFinite())
        val transform = Affine(1.0, 0.0, 0.0, 1.0, dx, dy)
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
            val model = Sample(x, y, p, time)
            val position = transform.apply(model.pos)
            val moved = model.copy(x = position.x, y = position.y)
            samples.add(moved.x, moved.y, moved.pressure, moved.t)
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
