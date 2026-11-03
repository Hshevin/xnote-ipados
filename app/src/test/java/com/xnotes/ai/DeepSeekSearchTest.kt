package com.xnotes.ai
import org.junit.Assert.*
import org.junit.Test
class DeepSeekSearchTest {
    @Test fun queryBoundsUseUnicodeCharacters() {
        assertEquals(DeepSeekSearch.COUNT, DeepSeekSearch.request("新闻").getInt("count"))
        DeepSeekSearch.request("😀".repeat(128))
        assertThrows(IllegalArgumentException::class.java) { DeepSeekSearch.request("😀".repeat(129)) }
        assertThrows(IllegalArgumentException::class.java) { DeepSeekSearch.request("  ") }
    }
    @Test fun originalQuestionIsFoldedAndBounded() {
        assertFalse(DeepSeekSearch.request("q").has("ori_question"))
        assertFalse(DeepSeekSearch.request("q", " \n ").has("ori_question"))
        assertEquals("这篇 论文", DeepSeekSearch.request("q", " 这篇\n\n论文 ").getString("ori_question"))
        assertEquals("😀".repeat(128), DeepSeekSearch.request("q", "😀".repeat(200)).getString("ori_question"))
    }
    @Test fun unsafeSourcesAreSkipped() {
        assertTrue(DeepSeekSearch.sources("{\"results\":[{\"url\":\"javascript:alert(1)\",\"title\":\"bad\",\"snippet\":\"\"}]}").isEmpty())
        assertTrue(DeepSeekSearch.sources("{\"results\":[{\"url\":\"https://user:pw@example.com\",\"title\":\"bad\",\"snippet\":\"\"}]}").isEmpty())
    }
    @Test fun sourcesKeepFieldsAndNullDates() {
        val source = DeepSeekSearch.sources("{\"results\":[{\"url\":\"https://example.com/page\",\"title\":\"Title\",\"snippet\":\"s\",\"summary\":\"Evidence\",\"date\":null}]}").single()
        assertEquals(AiWebSource("https://example.com/page", "Title", "s", "Evidence", ""), source)
    }
    @Test fun errorRepliesAreErrors() {
        assertThrows(IllegalStateException::class.java) { DeepSeekSearch.sources("{\"error\":\"quota\"}") }
        assertThrows(IllegalStateException::class.java) { DeepSeekSearch.content("{\"error\":\"blocked\"}") }
        assertEquals("# Page", DeepSeekSearch.content("{\"content\":\"# Page\"}"))
    }
    @Test fun fetchOnlyTakesPublicHttpUrls() {
        assertEquals("https://example.com/a", DeepSeekSearch.fetchRequest("https://example.com/a").getString("url"))
        listOf("file:///etc/passwd", "ftp://example.com", "https://u:p@example.com", "not a url").forEach {
            assertThrows(IllegalArgumentException::class.java) { DeepSeekSearch.fetchRequest(it) }
        }
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
