package com.xnotes.ai
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
class AiPdfAttachmentTest {
    private val pdf = AiPdf("paper.pdf", "[第1页] Title\n[第2页] Body", listOf("YWJj", "ZGVm"))
    @Test fun allPdfPagesAndOriginalQuestionReachModelAndSearchPlanner() {
        val user = AiMessage("user", "这篇论文讲什么", pdf = pdf)
        val content = AiProtocol.request("vision", listOf(user)).getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        val images = (0 until content.length()).map { content.getJSONObject(it) }.filter { it.getString("type") == "image_url" }
        assertEquals(2, images.size)
        assertEquals("data:image/jpeg;base64,ZGVm", images[1].getJSONObject("image_url").getString("url"))
        assertTrue(content.toString().contains("Title"))
        assertEquals(user.text, content.getJSONObject(content.length() - 1).getString("text"))
        assertTrue(AiProtocol.request(AiConfig(model = "vision"), listOf(user), tools = AiWebTools.definitions()).toString().contains(pdf.id))
    }
    @Test fun noPdfIsAddedUnlessSelected() {
        val body = AiProtocol.request("vision", listOf(AiMessage("user", "hello"))).toString()
        assertFalse(body.contains("PDF 附件")); assertFalse(body.contains("image_url"))
    }
    @Test fun pdfSnapshotSurvivesHistoryAndDeletionUndo() {
        val dir = Files.createTempDirectory("ai-pdf-history").toFile()
        try {
            val store = AiHistoryStore(dir)
            val chat = AiChat(turns = listOf(AiTurn(AiMessage("user", "question", pdf = pdf), "answer", "已完成")))
            store.write(chat); assertEquals(pdf, store.readAll().first.single().context().first().pdf)
            store.delete(chat.id); assertTrue(store.readAll().first.isEmpty())
            store.write(chat); assertEquals(chat, store.readAll().first.single())
        } finally { dir.deleteRecursively() }
    }
}
