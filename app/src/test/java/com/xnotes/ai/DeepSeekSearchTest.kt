package com.xnotes.ai
import org.junit.Assert.*
import org.junit.Test
class DeepSeekSearchTest {
    @Test fun queryBoundsUseUnicodeCharacters() {
        assertEquals(5, DeepSeekSearch.request("新闻").getInt("count"))
        DeepSeekSearch.request("😀".repeat(128))
        assertThrows(IllegalArgumentException::class.java) { DeepSeekSearch.request("😀".repeat(129)) }
        assertThrows(IllegalArgumentException::class.java) { DeepSeekSearch.request("  ") }
    }
    @Test fun emptySearchIsExplicit() { assertTrue(DeepSeekSearch.context("{\"results\":[]}").contains("未找到")) }
    @Test fun unsafeSourcesAreNotUsed() { assertTrue(DeepSeekSearch.context("{\"results\":[{\"url\":\"javascript:alert(1)\",\"title\":\"bad\"}]}").contains("未找到")) }
    @Test fun sourcesRetainUrlsAndSummaries() {
        val result = DeepSeekSearch.context("{\"results\":[{\"url\":\"https://example.com/page\",\"title\":\"Title\",\"summary\":\"Evidence\"}]}")
        assertTrue(result.contains("https://example.com/page")); assertTrue(result.contains("Evidence"))
    }
    @Test fun ordinaryRequestsHaveNoSearchData() {
        val body = AiProtocol.request("model", listOf(AiMessage("user", "hello"))).toString()
        assertFalse(body.contains("search")); assertFalse(body.contains("tools"))
    }
    @Test fun groundingIsIncludedAlongsideImage() {
        val text = AiProtocol.request("model", listOf(AiMessage("user", "hello", listOf("YWJj"), "source evidence"))).toString()
        assertTrue(text.contains("source evidence")); assertTrue(text.contains("data:image/png"))
    }
}
