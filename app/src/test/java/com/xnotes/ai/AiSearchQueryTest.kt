package com.xnotes.ai
import org.junit.Assert.*
import org.junit.Test
class AiSearchQueryTest {
    @Test fun plannerReceivesImageAndQuestionWithoutOldSearchSnippets() {
        val messages = AiSearchQuery.messages(listOf(AiMessage("user", "old", searchContext = "unrelated")), AiMessage("user", "这篇论文的有关情况", "YWJj"))
        assertEquals("system", messages.first().role)
        assertEquals("YWJj", messages.last().image)
        assertEquals("这篇论文的有关情况", messages.last().text)
        assertTrue(messages.all { it.searchContext.isEmpty() })
    }
    @Test fun parsesTitleQuery() { assertEquals("Attention Is All You Need", AiSearchQuery.parse("""{"query":"Attention Is All You Need"}""")) }
    @Test fun unclearOrInvalidOutputStopsSearch() {
        listOf("{}", "{\"query\":null}", "{\"query\":\"\"}", "unstructured answer").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { AiSearchQuery.parse(value) }
        }
    }
}
