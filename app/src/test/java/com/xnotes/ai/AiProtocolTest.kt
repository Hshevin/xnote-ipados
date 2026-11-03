package com.xnotes.ai
import org.junit.Assert.*
import org.junit.Test
class AiProtocolTest {
    @Test fun endpointKeepsPrefix() {
        assertEquals("https://example.com/api/v1/chat/completions", AiProtocol.endpoint("https://example.com/api/v1/"))
        assertEquals("https://example.com/v1/chat/completions", AiProtocol.endpoint("https://example.com/v1/chat/completions"))
    }
    @Test fun rejectsUnsafeEndpoints() {
        listOf("http://example.com", "https://user:secret@example.com", "https://example.com?key=x", "https://example.com/#x").forEach {
            assertThrows(IllegalArgumentException::class.java) { AiProtocol.endpoint(it) }
        }
    }
    @Test fun preservesImageAndFollowup() {
        val messages = AiProtocol.request("vision", listOf(AiMessage("user", "解释", "YWJj"), AiMessage("assistant", "回答"), AiMessage("user", "为什么"))).getJSONArray("messages")
        assertEquals(3, messages.length())
        assertEquals("data:image/png;base64,YWJj", messages.getJSONObject(0).getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url"))
        assertEquals("为什么", messages.getJSONObject(2).getJSONArray("content").getJSONObject(0).getString("text"))
    }
    @Test fun parsesDelta() { assertEquals("你好", AiProtocol.delta("{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}")) }
    @Test fun emptyDelta() { assertEquals("", AiProtocol.delta("{\"choices\":[{\"delta\":{}}]}")) }
    @Test fun ignoresNullReasoningAndFinishFrames() {
        val frames = listOf(
            """{"choices":[{"delta":{"role":"assistant","content":null}}]}""",
            """{"choices":[{"delta":{"content":null,"reasoning_content":"thinking"}}]}""",
            """{"choices":[{"delta":{"content":"回答"}}]}""",
            """{"choices":[{"delta":{"content":null},"finish_reason":"stop"}]}""",
            """{"choices":[],"usage":{"total_tokens":42}}""",
        )
        assertEquals("回答", frames.joinToString("") { AiProtocol.delta(it) })
    }
    @Test fun ignoresNonTextContentWithoutCoercion() {
        listOf("42", "true", "{}", "[]", "null").forEach { value ->
            assertEquals("", AiProtocol.delta("""{"choices":[{"delta":{"content":$value}}]}"""))
        }
    }
    @Test fun preservesActualWordNull() {
        assertEquals("null", AiProtocol.delta("""{"choices":[{"delta":{"content":"null"}}]}"""))
    }
}
