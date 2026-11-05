package com.xnotes.format

/** In-memory probe adapters, not production file I/O. */
internal class Reader(private val text: String) {
    private var offset = 0
    fun read(buffer: CharArray): Int {
        if (offset == text.length) return -1
        val count = minOf(buffer.size, text.length - offset)
        text.toCharArray(buffer, 0, offset, offset + count)
        offset += count
        return count
    }
}
internal class Writer {
    private val buffer = StringBuilder()
    fun write(value: String) { buffer.append(value) }
    fun write(value: Int) { buffer.append(value.toChar()) }
    fun write(value: CharArray, offset: Int, count: Int) { buffer.append(value.concatToString(offset, offset + count)) }
    override fun toString(): String = buffer.toString()
}
internal fun roundLikeJava(value: Double): Long {
    if (value.isNaN()) return 0L
    val floor = kotlin.math.floor(value)
    return (if (value - floor >= 0.5) floor + 1.0 else floor).toLong()
}
