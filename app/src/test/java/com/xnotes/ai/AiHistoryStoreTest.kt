package com.xnotes.ai
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
class AiHistoryStoreTest {
    @Test fun roundTripIncludesScreenshotSourcesAndPartialAnswer() {
        val dir = Files.createTempDirectory("ai-history").toFile()
        try {
            val store = AiHistoryStore(dir)
            val chat = AiChat(title = "问题", source = "book.pdf", turns = listOf(AiTurn(AiMessage("user", "解释", "YWJj", "来源"), "部分回答")))
            store.write(chat)
            assertEquals(chat, store.readAll().first.single())
            assertEquals("已中断", store.readAll().first.single().recover().turns.single().state)
            assertTrue(chat.matches("解释")); assertTrue(chat.matches("部分回答"))
            assertEquals(emptyList<AiMessage>(), chat.context())
            store.write(chat.copy(title = "重命名"))
            assertEquals(1, store.readAll().first.size)
            store.delete(chat.id); assertTrue(store.readAll().first.isEmpty())
            store.write(chat); assertEquals(chat, store.readAll().first.single())
        } finally { dir.deleteRecursively() }
    }
    @Test fun damagedRecordIsPreservedAndDoesNotHideHealthyRecords() {
        val dir = Files.createTempDirectory("ai-history").toFile()
        try {
            val store = AiHistoryStore(dir); store.write(AiChat())
            val bad = java.io.File(dir, "bad.json"); bad.writeText("invalid")
            assertEquals(1, store.readAll().second); assertEquals(1, store.readAll().first.size); assertEquals("invalid", bad.readText())
        } finally { dir.deleteRecursively() }
    }
    @Test fun onlyCompletedTurnsEnterContext() {
        val chat = AiChat(turns = listOf(AiTurn(AiMessage("user", "first"), "answer", "已完成"), AiTurn(AiMessage("user", "second"), "partial", "已停止")))
        assertEquals(listOf("first", "answer"), chat.context().map { it.text })
    }
    @Test fun pathTraversalRejected() {
        val dir = Files.createTempDirectory("ai-history").toFile()
        try { assertThrows(IllegalArgumentException::class.java) { AiHistoryStore(dir).delete("../other") } } finally { dir.deleteRecursively() }
    }
    @Test fun failedReplacementPreservesPreviousRecord() {
        val dir = Files.createTempDirectory("ai-history").toFile()
        try {
            val store = AiHistoryStore(dir); val chat = AiChat(title = "original"); store.write(chat)
            val obstacle = java.io.File(dir, "${chat.id}.tmp").apply { mkdir() }
            java.io.File(obstacle, "block").writeText("block")
            assertThrows(java.io.IOException::class.java) { store.write(chat.copy(title = "changed")) }
            assertEquals(chat, store.readAll().first.single())
        } finally { dir.deleteRecursively() }
    }
}
