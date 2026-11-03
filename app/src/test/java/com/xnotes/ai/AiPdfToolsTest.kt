package com.xnotes.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AiPdfToolsTest {
    private val pdf = AiPdf("paper.pdf", id = "a".repeat(64), pageCount = 100)
    private val config = AiConfig("https://example.com/v1", "model", "key")
    private class Access : AiPdfAccess {
        val pages = mutableListOf<Int>()
        override suspend fun read(pdf: AiPdf, start: Int, end: Int): String { pages.addAll(start..end); return "第 $start 页：Exact Paper Title" }
        override suspend fun image(pdf: AiPdf, page: Int) = "YWJj"
    }
    private class Client(val replies: List<String>) : AiTransport {
        var count = 0
        override fun cancel() {}
        override fun search(key: String, query: String) = error("not expected")
        override suspend fun stream(config: AiConfig, messages: List<AiMessage>, delta: suspend (String) -> Unit) { delta(replies[count++]) }
    }
    @Test fun readsOnlyRequestedPagesAndSuppliesSearchGrounding() = runBlocking {
        val access = Access()
        val user = AiMessage("user", "这篇论文的相关研究", pdf = pdf)
        val result = AiPdfTools.prepare(config, Client(listOf("""{"tool":"read","id":"${pdf.id}","start":1,"end":1}""", """{"tool":"done"}""")), emptyList(), user, access)
        assertEquals(listOf(1), access.pages)
        assertTrue(AiSearchQuery.messages(emptyList(), result).last().text.contains("Exact Paper Title"))
        assertTrue(result.text.endsWith(user.text))
        assertTrue(AiProtocol.request("model", listOf(user)).toString().contains(pdf.id))
        assertFalse(AiProtocol.request("model", listOf(user)).toString().contains("image_url"))
    }
    @Test fun historyAttachmentCanBeReadOnFollowup() = runBlocking {
        val access = Access()
        AiPdfTools.prepare(config, Client(listOf("""{"tool":"read","id":"${pdf.id}","start":50,"end":51}""", """{"tool":"done"}""")), listOf(AiMessage("user", "paper", pdf = pdf)), AiMessage("user", "继续"), access)
        assertEquals(listOf(50, 51), access.pages)
    }
    @Test fun rejectsForeignAttachmentAndOutOfRangeBeforeReading() = runBlocking {
        for (call in listOf("""{"tool":"read","id":"../private","start":1,"end":1}""", """{"tool":"read","id":"${pdf.id}","start":99,"end":101}""")) {
            val access = Access()
            try { AiPdfTools.prepare(config, Client(listOf(call)), emptyList(), AiMessage("user", "q", pdf = pdf), access); fail("must reject") } catch (_: IllegalArgumentException) {}
            assertTrue(access.pages.isEmpty())
        }
    }
    @Test fun referenceRoundTripsWithoutEmbeddingPdfBytes() {
        val dir = java.nio.file.Files.createTempDirectory("pdf-ref").toFile()
        try {
            val store = AiHistoryStore(dir); val chat = AiChat(turns = listOf(AiTurn(AiMessage("user", "q", pdf = pdf))))
            store.write(chat); assertEquals(pdf, store.readAll().first.single().turns.single().user.pdf)
            assertTrue(dir.listFiles()!!.single().length() < 2000)
        } finally { dir.deleteRecursively() }
    }
}
