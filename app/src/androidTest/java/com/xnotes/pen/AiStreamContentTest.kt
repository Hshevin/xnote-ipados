package com.xnotes.pen

import com.xnotes.ai.AiProtocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Uses platform org.json; the JVM dependency handles JSON null differently. */
class AiStreamContentTest {
    @Test fun androidNullCoercionDoesNotReachAnswer() {
        assertEquals("null", JSONObject("""{"content":null}""").optString("content", ""))
        val reasoning = """{"choices":[{"delta":{"content":null,"reasoning_content":"thinking"}}]}"""
        val answer = """{"choices":[{"delta":{"content":"你好"}}]}"""
        assertEquals("你好", (List(20) { reasoning } + answer).joinToString("") { AiProtocol.delta(it) })
    }
    @Test fun actualTextNullIsPreserved() {
        assertEquals("null", AiProtocol.delta("""{"choices":[{"delta":{"content":"null"}}]}"""))
    }
}
