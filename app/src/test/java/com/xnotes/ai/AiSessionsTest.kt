package com.xnotes.ai

import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AiSessionsTest {
    private class HeldReply : AiTransport {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var cancelled = false
        override fun cancel() { cancelled = true; release.cancel() }
        override fun search(key: String, query: String) = "sources"
        override suspend fun stream(config: AiConfig, messages: List<AiMessage>, delta: suspend (String) -> Unit) {
            delta("部分"); entered.complete(Unit); release.await(); delta("完成")
        }
    }
    private suspend fun until(condition: () -> Boolean) = withTimeout(5000) { while (!condition()) delay(5) }
    @Test fun closingAndOpeningAnotherChatDoesNotCancelAndPersistsOriginal() = runBlocking {
        val dir = Files.createTempDirectory("sessions").toFile()
        val job = SupervisorJob(); val scope = CoroutineScope(coroutineContext + job)
        try {
            val reply = HeldReply(); val store = AiHistoryStore(dir)
            val sessions = AiSessions(store, scope, { AiConfig("https://example.com/v1", "vision", "key") }, { reply })
            until { sessions.ready }
            val first = sessions.newChat("source.pdf", floatingOwner = "source.pdf")
            sessions.send(first, AiMessage("user", "问题", listOf("YWJj")), false)
            reply.entered.await()
            sessions.reconcileFloating(setOf("source.pdf"))
            assertEquals(first, sessions.activeId)
            sessions.reconcileFloating(emptySet())
            assertNull(sessions.activeId)
            assertNull(sessions.floatingOwner)
            val second = sessions.newChat()
            assertFalse(reply.cancelled)
            reply.release.complete(Unit)
            until { !sessions.isRunning(first) }; sessions.flush()
            assertEquals(second, sessions.activeId)
            assertEquals("部分完成", sessions.chats[first]!!.turns.single().answer)
            assertEquals("已完成", store.readAll().first.first { it.id == first }.turns.single().state)
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }
    @Test fun deleteCancelsAndCannotResurrectThenUndoRestoresPartial() = runBlocking {
        val dir = Files.createTempDirectory("sessions").toFile()
        val job = SupervisorJob(); val scope = CoroutineScope(coroutineContext + job)
        try {
            val reply = HeldReply(); val store = AiHistoryStore(dir)
            val sessions = AiSessions(store, scope, { AiConfig("https://example.com/v1", "vision", "key") }, { reply })
            until { sessions.ready }; val id = sessions.newChat()
            sessions.send(id, AiMessage("user", "问题"), false)
            reply.entered.await(); sessions.delete(id)
            until { sessions.undoChat != null }; sessions.flush()
            assertTrue(reply.cancelled); assertFalse(sessions.chats.containsKey(id)); assertTrue(store.readAll().first.isEmpty())
            sessions.undoDelete(); sessions.flush()
            assertEquals("部分", store.readAll().first.single().turns.single().answer)
            assertNotEquals("生成中", store.readAll().first.single().turns.single().state)
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }
    @Test fun failedInitialSaveDoesNotSendNetworkRequest() = runBlocking {
        val dir = Files.createTempDirectory("sessions").toFile()
        val blocked = java.io.File(dir, "not-directory").apply { writeText("existing") }
        val job = SupervisorJob(); val scope = CoroutineScope(coroutineContext + job)
        try {
            val reply = HeldReply()
            val sessions = AiSessions(AiHistoryStore(blocked), scope, { AiConfig("https://example.com/v1", "vision", "key") }, { reply })
            until { sessions.ready }; val id = sessions.newChat()
            sessions.send(id, AiMessage("user", "question"), false)
            until { !sessions.isRunning(id) }; sessions.flush()
            assertFalse(reply.entered.isCompleted); assertTrue(sessions.storageError.isNotEmpty())
            assertEquals("existing", blocked.readText())
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }
    @Test fun screenshotIsInterpretedBeforeSearchAndOriginalQuestionIsAnswered() = runBlocking {
        val dir = Files.createTempDirectory("sessions").toFile()
        val job = SupervisorJob(); val scope = CoroutineScope(coroutineContext + job)
        try {
            val calls = mutableListOf<String>()
            val fake = object : AiTransport {
                override fun cancel() {}
                override fun search(key: String, query: String): String {
                    calls.add("search")
                    assertEquals("Attention Is All You Need", query)
                    return "source evidence"
                }
                override suspend fun stream(config: AiConfig, messages: List<AiMessage>, delta: suspend (String) -> Unit) {
                    assertEquals("YWJj", messages.last().images.single())
                    assertEquals("这篇论文的有关情况", messages.last().text)
                    if (calls.isEmpty()) {
                        calls.add("identify")
                        assertEquals("system", messages.first().role)
                        delta("""{"query":"Attention Is All You Need"}""")
                    } else {
                        calls.add("answer")
                        assertTrue(messages.last().searchContext.contains("source evidence"))
                        delta("论文介绍")
                    }
                }
            }
            val sessions = AiSessions(AiHistoryStore(dir), scope, { AiConfig("https://example.com/v1", "vision", "key", "search-key") }, { fake })
            until { sessions.ready }; val id = sessions.newChat()
            sessions.send(id, AiMessage("user", "这篇论文的有关情况", listOf("YWJj")), true)
            until { !sessions.isRunning(id) }; sessions.flush()
            assertEquals(listOf("identify", "search", "answer"), calls)
            assertEquals("论文介绍", sessions.chats[id]!!.turns.single().answer)
            assertEquals("已完成", sessions.chats[id]!!.turns.single().state)
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }
    @Test fun unclearImageDoesNotSearchOrInventAnswer() = runBlocking {
        val dir = Files.createTempDirectory("sessions").toFile()
        val job = SupervisorJob(); val scope = CoroutineScope(coroutineContext + job)
        try {
            var searched = false
            val fake = object : AiTransport {
                override fun cancel() {}
                override fun search(key: String, query: String): String { searched = true; return "wrong paper" }
                override suspend fun stream(config: AiConfig, messages: List<AiMessage>, delta: suspend (String) -> Unit) { delta("""{"query":""}""") }
            }
            val sessions = AiSessions(AiHistoryStore(dir), scope, { AiConfig("https://example.com/v1", "vision", "key", "search-key") }, { fake })
            until { sessions.ready }; val id = sessions.newChat()
            sessions.send(id, AiMessage("user", "这篇论文", listOf("YWJj")), true)
            until { !sessions.isRunning(id) }; sessions.flush()
            assertFalse(searched)
            assertEquals("生成失败", sessions.chats[id]!!.turns.single().state)
            assertTrue(sessions.chats[id]!!.turns.single().error.contains("清晰截图"))
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }
}
