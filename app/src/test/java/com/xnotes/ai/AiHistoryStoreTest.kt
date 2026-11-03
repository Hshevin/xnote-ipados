package com.xnotes.ai
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
class AiHistoryStoreTest {
    @Test fun roundTripIncludesScreenshotSourcesAndPartialAnswer() {
        val dir = Files.createTempDirectory("ai-history").toFile()
        try {
            val store = AiHistoryStore(dir)
            val chat = AiChat(title = "问题", source = "book.pdf", turns = listOf(AiTurn(AiMessage("user", "解释", listOf("YWJj"), "来源"), "部分回答")))
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
    @Test fun legacySingleImageRecordsLoadAndMultipleImagesRoundTrip() {
        val dir = Files.createTempDirectory("ai-history").toFile()
        try {
            val id = java.util.UUID.randomUUID().toString()
            // The pre-multi-screenshot shape: one optional "image" per turn and per draft.
            java.io.File(dir, "$id.json").writeText("""{"version":1,"id":"$id","title":"旧","updated":1,"turns":[{"user":"q","image":"YWJj","answer":"a","state":"已完成"},
                {"user":"q2","image":null,"answer":"b","state":"已完成"}],"draft":{"text":"d","image":"AQ=="}}""")
            val legacy = AiHistoryStore(dir).readAll().first.single()
            assertEquals(listOf("YWJj"), legacy.turns[0].user.images); assertEquals(emptyList<String>(), legacy.turns[1].user.images)
            assertEquals(listOf("AQ=="), legacy.draft.images)
            val many = legacy.copy(turns = legacy.turns + AiTurn(AiMessage("user", "三张", listOf("AQ==", "Ag==", "Aw==")), "c", "已完成"),
                draft = legacy.draft.copy(images = listOf("BA==", "BQ==")))
            AiHistoryStore(dir).write(many)
            assertEquals(many, AiHistoryStore(dir).readAll().first.single())
        } finally { dir.deleteRecursively() }
    }
    @Test fun chatsBelongToTheirDocumentKey() {
        assertTrue(AiChat(sourceDocumentId = "doc", sourceUri = "uri").belongsTo("doc"))
        assertFalse(AiChat(sourceDocumentId = "doc", sourceUri = "uri").belongsTo("uri"))
        assertTrue(AiChat(sourceUri = "uri", source = "t").belongsTo("uri"))
        assertTrue(AiChat(source = "t").belongsTo("t"))
        assertFalse(AiChat().belongsTo(""))
        val joined = AiChat(sourceDocumentId = "doc-a", relatedDocuments = listOf("doc-b"))
        assertTrue(joined.belongsTo("doc-a")); assertTrue(joined.belongsTo("doc-b")); assertFalse(joined.belongsTo("doc-c"))
        assertEquals("doc-a", joined.documentKey); assertEquals("uri", AiChat(sourceUri = "uri", source = "t").documentKey)
    }
    @Test fun relatedDocumentsRoundTripAndOldRecordsHaveNone() {
        val dir = Files.createTempDirectory("ai-history").toFile()
        try {
            val chat = AiChat(sourceDocumentId = "doc-a", relatedDocuments = listOf("doc-b"), trashedAt = 1234L)
            AiHistoryStore(dir).write(chat)
            assertEquals(chat, AiHistoryStore(dir).readAll().first.single())
            val id = java.util.UUID.randomUUID().toString()
            java.io.File(dir, "$id.json").writeText("""{"version":1,"id":"$id","title":"旧","updated":1,"turns":[]}""")
            assertEquals(emptyList<String>(), AiHistoryStore(dir).readAll().first.single { it.id == id }.relatedDocuments)
            assertEquals(0L, AiHistoryStore(dir).readAll().first.single { it.id == id }.trashedAt)
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
