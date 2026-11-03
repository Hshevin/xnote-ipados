package com.xnotes.ai

import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AiInteractionTest {
    private val settings = AiConfig("https://example.com/v1", "vision", "key")
    private suspend fun until(condition: () -> Boolean) = withTimeout(5000) { while (!condition()) delay(5) }
    private suspend fun fixture(block: suspend (AiHistoryStore, CoroutineScope) -> Unit) = coroutineScope {
        val directory = Files.createTempDirectory("ai-interaction").toFile()
        val job = SupervisorJob()
        try { block(AiHistoryStore(directory), CoroutineScope(coroutineContext + job)) }
        finally { job.cancelAndJoin(); directory.deleteRecursively() }
    }
    @Test fun switchingAndReloadRestoreDraftAttachmentsAndSearchPerChat() = runBlocking {
        fixture { store, scope ->
            val sessions = AiSessions(store, scope, { settings })
            until { sessions.ready }
            val first = sessions.newChat()
            sessions.editDraft(first, "第一条草稿", true)
            sessions.addScreenshot(first, byteArrayOf(1, 2, 3)); sessions.addScreenshot(first, byteArrayOf(4))
            val second = sessions.newChat()
            sessions.editDraft(second, "第二条草稿")
            sessions.open(first)
            assertEquals("第一条草稿", sessions.drafts[first]); assertTrue(sessions.searchEnabled[first] == true)
            assertFalse(sessions.searchEnabled[second] == true)
            sessions.flush()
            val restored = AiSessions(store, scope, { settings })
            until { restored.ready }
            assertEquals("第二条草稿", restored.drafts[second])
            assertEquals("第一条草稿", restored.drafts[first])
            assertEquals(listOf(listOf<Byte>(1, 2, 3), listOf<Byte>(4)), restored.attachments[first]!!.map { it.toList() })
            assertTrue(restored.searchEnabled[first] == true)
        }
    }
    @Test fun importSurvivesSwitchAndRestoresAfterRestart() = runBlocking {
        fixture { store, scope ->
            val release = CompletableDeferred<Unit>(); val entered = CompletableDeferred<Unit>()
            val pdf = AiPdf("paper.pdf", id = "a".repeat(64), pageCount = 20)
            val sessions = AiSessions(store, scope, { settings }, preparePdf = { _, _ -> entered.complete(Unit); release.await(); pdf })
            until { sessions.ready }; val first = sessions.newChat()
            sessions.importPdf(first, "content://pdf")
            entered.await(); val second = sessions.newChat()
            assertTrue(sessions.importStates[first]?.loading == true)
            release.complete(Unit); until { sessions.pdfAttachments[first] != null }
            assertEquals(second, sessions.activeId); assertNull(sessions.pdfAttachments[second])
            sessions.flush()
            val restored = AiSessions(store, scope, { settings })
            until { restored.ready }; assertEquals(pdf, restored.pdfAttachments[first])
        }
    }
    @Test fun failedImportCanRetryAndDeletedSessionCannotBeRevived() = runBlocking {
        fixture { store, scope ->
            var calls = 0
            val sessions = AiSessions(store, scope, { settings }, preparePdf = { _, _ ->
                calls++; if (calls == 1) throw IllegalArgumentException("bad PDF")
                AiPdf("ok.pdf", id = "b".repeat(64), pageCount = 2)
            })
            until { sessions.ready }; val id = sessions.newChat()
            sessions.importPdf(id, "content://pdf")
            until { sessions.importStates[id]?.error == "bad PDF" }
            sessions.retryImport(id); until { sessions.pdfAttachments[id] != null }
            sessions.editDraft(id, "draft"); sessions.delete(id)
            until { sessions.undoChat != null }; sessions.editDraft(id, "late callback")
            sessions.flush(); assertFalse(sessions.chats.containsKey(id)); assertTrue(store.readAll().first.isEmpty())
            sessions.undoDelete(); sessions.flush()
            assertEquals("draft", sessions.drafts[id]); assertNotNull(sessions.pdfAttachments[id])
        }
    }
    @Test fun generationProgressAndNextDraftRemainWithOriginalChat() = runBlocking {
        fixture { store, scope ->
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val transport = object : AiTransport {
                override fun cancel() { release.cancel() }
                override fun search(key: String, query: String) = "sources"
                override suspend fun stream(config: AiConfig, messages: List<AiMessage>, delta: suspend (String) -> Unit) {
                    delta("partial"); entered.complete(Unit); release.await(); delta("done")
                }
            }
            val sessions = AiSessions(store, scope, { settings }, { transport })
            until { sessions.ready }; val first = sessions.newChat()
            sessions.editDraft(first, "question")
            sessions.send(first, AiMessage("user", "question"), false)
            entered.await()
            assertTrue(sessions.drafts[first].isNullOrEmpty())
            assertEquals("正在生成回答", sessions.chats[first]!!.turns.last().progress)
            sessions.editDraft(first, "next question")
            val second = sessions.newChat()
            release.complete(Unit); until { !sessions.isRunning(first) }; sessions.flush()
            assertEquals(second, sessions.activeId)
            val saved = store.readAll().first.single { it.id == first }
            assertEquals("next question", saved.draft.text)
            assertEquals("partialdone", saved.turns.single().answer)
            assertTrue(saved.turns.single().steps.contains("正在生成回答"))
        }
    }
    @Test fun screenshotsAccumulateUpToLimitAndAllAreSent() = runBlocking {
        fixture { store, scope ->
            val sent = CompletableDeferred<List<AiMessage>>()
            val transport = object : AiTransport {
                override suspend fun stream(config: AiConfig, messages: List<AiMessage>, delta: suspend (String) -> Unit) { sent.complete(messages); delta("ok") }
                override fun search(key: String, query: String) = ""
                override fun cancel() {}
            }
            val sessions = AiSessions(store, scope, { settings }, { transport })
            until { sessions.ready }; val id = sessions.newChat()
            repeat(AiProtocol.MAX_IMAGES) { assertTrue(sessions.addScreenshot(id, byteArrayOf(it.toByte()))) }
            assertFalse(sessions.addScreenshot(id, byteArrayOf(9)))
            assertEquals(AiProtocol.MAX_IMAGES, sessions.attachments[id]!!.size)
            sessions.removeScreenshot(id, 0)
            assertEquals((1 until AiProtocol.MAX_IMAGES).map { it.toByte() }, sessions.attachments[id]!!.map { it.single() })
            val images = sessions.attachments[id]!!.map { java.util.Base64.getEncoder().encodeToString(it) }
            sessions.editDraft(id, "看这几张")
            sessions.send(id, AiMessage("user", "看这几张", images), false)
            assertEquals(images, withTimeout(5000) { sent.await() }.last().images)
            until { !sessions.isRunning(id) }
            assertNull(sessions.attachments[id])
            assertEquals(images, sessions.chats[id]!!.turns.single().user.images)
        }
    }
    @Test fun floatingHistorySwitchesWithinTheDocument() = runBlocking {
        fixture { store, scope ->
            val sessions = AiSessions(store, scope, { settings })
            until { sessions.ready }
            val older = sessions.newChat("a", "uri-a", "doc-a", "doc-a")
            sessions.offerCurrentPdf(older, "file:///a.pdf", "a.pdf")
            sessions.newChat("b", "uri-b", "doc-b", "doc-b")
            val newer = sessions.newChat("a", "uri-a", "doc-a", "doc-a")
            assertEquals(listOf(newer, older).toSet(), sessions.chatsOf("doc-a").map { it.id }.toSet())
            sessions.openFloating(older)
            assertEquals(older, sessions.activeId); assertEquals("doc-a", sessions.floatingOwner)
            sessions.openFloating(newer)
            assertEquals("file:///a.pdf" to "a.pdf", sessions.currentPdfs[newer])
            sessions.close(); sessions.openFloating(older)
            assertNull(sessions.activeId)
        }
    }
    @Test fun configurationFailureKeepsComposer() = runBlocking {
        fixture { store, scope ->
            val sessions = AiSessions(store, scope, { AiConfig() })
            until { sessions.ready }; val id = sessions.newChat()
            sessions.editDraft(id, "unsent")
            sessions.addScreenshot(id, byteArrayOf(1))
            sessions.send(id, AiMessage("user", "unsent", listOf("AQ==")), false)
            until { !sessions.isRunning(id) }; sessions.flush()
            assertEquals("unsent", sessions.drafts[id]); assertArrayEquals(byteArrayOf(1), sessions.attachments[id]!!.single())
            assertEquals("unsent", store.readAll().first.single().draft.text)
        }
    }
    @Test fun deletedChatRejectsLateImportCompletion() = runBlocking {
        fixture { store, scope ->
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val sessions = AiSessions(store, scope, { settings }, preparePdf = { _, _ ->
                withContext(NonCancellable) { entered.complete(Unit); release.await() }
                AiPdf("late.pdf", id = "c".repeat(64), pageCount = 1)
            })
            until { sessions.ready }; val id = sessions.newChat()
            sessions.importPdf(id, "content://pdf"); entered.await()
            sessions.delete(id); release.complete(Unit)
            until { sessions.undoChat != null }; sessions.flush()
            assertFalse(sessions.chats.containsKey(id)); assertNull(sessions.pdfAttachments[id]); assertNull(sessions.importStates[id])
            assertTrue(store.readAll().first.isEmpty())
        }
    }

}
