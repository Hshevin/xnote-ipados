package com.xnotes.ai

import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AiInteractionTest {
    private val settings = AiConfig("https://example.com/v1", "vision", "key")
    private suspend fun until(condition: () -> Boolean) = withTimeout(5000) { while (!condition()) delay(5) }
    private fun started(sessions: AiSessions, id: String) {
        sessions.chats[id] = sessions.chats.getValue(id).copy(turns = listOf(AiTurn(AiMessage("user", "question"), "answer", "已完成")))
    }
    private suspend fun fixture(block: suspend (AiHistoryStore, CoroutineScope) -> Unit) = coroutineScope {
        val directory = Files.createTempDirectory("ai-interaction").toFile()
        val job = SupervisorJob()
        try { block(AiHistoryStore(directory), CoroutineScope(coroutineContext + job)) }
        finally { job.cancelAndJoin(); directory.deleteRecursively() }
    }
    @Test fun newChatClearsUnsentPromptAndAttachmentsWithoutCreatingAnotherChat() = runBlocking {
        fixture { store, scope ->
            val pdf = AiPdf("paper.pdf", id = "d".repeat(64), pageCount = 1)
            val sessions = AiSessions(store, scope, { settings }, preparePdf = { _, _ -> pdf })
            until { sessions.ready }
            val id = sessions.newChat()
            sessions.editDraft(id, "not sent")
            sessions.setScreenshot(id, byteArrayOf(1))
            sessions.importPdf(id, "content://paper")
            until { sessions.pdfAttachments[id] != null }
            sessions.flush()
            val version = sessions.composerVersion(id)
            repeat(3) { assertEquals(id, sessions.newChat()) }
            sessions.flush()
            assertTrue(store.readAll().first.isEmpty())
            assertEquals(setOf(id), sessions.chats.keys)
            assertTrue(sessions.drafts[id].isNullOrEmpty())
            assertNull(sessions.attachments[id]); assertNull(sessions.pdfAttachments[id]); assertNull(sessions.importStates[id])
            assertTrue(sessions.composerVersion(id) > version)
            sessions.close(); sessions.flush()
            assertTrue(sessions.chats.isEmpty()); assertTrue(store.readAll().first.isEmpty())
            assertNull(sessions.undoChat)
        }
    }
    @Test fun closingUnstartedChatDiscardsEvenANonblankPromptAndPersistsRemoval() = runBlocking {
        fixture { store, scope ->
            val sessions = AiSessions(store, scope, { settings })
            until { sessions.ready }; val id = sessions.newChat()
            sessions.editDraft(id, "unfinished prompt")
            sessions.setScreenshot(id, byteArrayOf(2))
            sessions.flush()
            sessions.close(); sessions.flush()
            assertNull(sessions.activeId); assertFalse(sessions.chats.containsKey(id))
            assertNull(sessions.drafts[id]); assertNull(sessions.attachments[id])
            assertTrue(store.readAll().first.isEmpty())
            val restored = AiSessions(store, scope, { settings })
            until { restored.ready }; assertTrue(restored.chats.isEmpty())
        }
    }
    @Test fun establishedChatKeepsDraftAndAttachmentsOnCloseAndNewChat() = runBlocking {
        fixture { store, scope ->
            val pdf = AiPdf("paper.pdf", id = "e".repeat(64), pageCount = 2)
            val sessions = AiSessions(store, scope, { settings }, preparePdf = { _, _ -> pdf })
            until { sessions.ready }; val id = sessions.newChat(); started(sessions, id)
            sessions.editDraft(id, "next question", true)
            sessions.setScreenshot(id, byteArrayOf(3))
            sessions.importPdf(id, "content://paper"); until { sessions.pdfAttachments[id] != null }
            sessions.close(); sessions.open(id)
            val next = sessions.newChat()
            assertNotEquals(id, next)
            assertEquals("next question", sessions.drafts[id])
            assertArrayEquals(byteArrayOf(3), sessions.attachments[id]); assertEquals(pdf, sessions.pdfAttachments[id])
            // A composer with only attachments must also survive once the chat has a turn.
            sessions.editDraft(id, ""); sessions.open(id); sessions.close(); sessions.flush()
            val saved = store.readAll().first.single { it.id == id }
            assertEquals("", saved.draft.text); assertEquals("Aw==", saved.draft.image); assertEquals(pdf, saved.draft.pdf)
            assertTrue(saved.draft.search); assertEquals(1, saved.turns.size)
        }
    }
    @Test fun resettingUnstartedChatRejectsLateImportWithoutDroppingReplacement() = runBlocking {
        fixture { store, scope ->
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val returned = CompletableDeferred<Unit>()
            val sessions = AiSessions(store, scope, { settings }, preparePdf = { uri, _ ->
                if (uri.endsWith("old")) withContext(NonCancellable) { entered.complete(Unit); release.await(); returned.complete(Unit) }
                AiPdf(uri, id = "f".repeat(64), pageCount = 1)
            })
            until { sessions.ready }; val id = sessions.newChat()
            sessions.importPdf(id, "content://old"); entered.await()
            assertEquals(id, sessions.newChat())
            sessions.importPdf(id, "content://new")
            until { sessions.pdfAttachments[id]?.name == "content://new" }
            release.complete(Unit); returned.await(); sessions.flush()
            assertEquals("content://new", sessions.pdfAttachments[id]?.name)
            assertNull(sessions.importStates[id]); assertTrue(sessions.drafts[id].isNullOrEmpty())
        }
    }
    @Test fun failedDiscardReportsStorageErrorAndCanSaveClearedComposerAgain() = runBlocking {
        val directory = Files.createTempDirectory("ai-discard-failure").toFile()
        val job = SupervisorJob()
        try {
            val scope = CoroutineScope(coroutineContext + job)
            val store = AiHistoryStore(directory)
            val sessions = AiSessions(store, scope, { settings })
            until { sessions.ready }; val id = sessions.newChat()
            sessions.editDraft(id, "not sent"); sessions.flush()
            val file = java.io.File(directory, "$id.json")
            assertTrue(file.delete()); assertTrue(file.mkdir())
            java.io.File(file, "blocker").writeText("keep")
            sessions.close(); sessions.flush()
            until { sessions.storageError.isNotEmpty() && sessions.chats.containsKey(id) }
            assertEquals("keep", java.io.File(file, "blocker").readText())
            assertTrue(sessions.drafts[id].isNullOrEmpty()); assertNull(sessions.undoChat)
            file.deleteRecursively()
            sessions.retrySave(); sessions.flush()
            assertEquals("", sessions.storageError)
            assertTrue(store.readAll().first.single().draft.text.isEmpty())
        } finally { job.cancelAndJoin(); directory.deleteRecursively() }
    }
    @Test fun switchingAndReloadRestoreDraftAttachmentsAndSearchPerChat() = runBlocking {
        fixture { store, scope ->
            val sessions = AiSessions(store, scope, { settings })
            until { sessions.ready }
            val first = sessions.newChat()
            started(sessions, first)
            sessions.editDraft(first, "第一条草稿", true)
            sessions.setScreenshot(first, byteArrayOf(1, 2, 3))
            val second = sessions.newChat()
            started(sessions, second)
            sessions.editDraft(second, "第二条草稿")
            sessions.open(first)
            assertEquals("第一条草稿", sessions.drafts[first]); assertTrue(sessions.searchEnabled[first] == true)
            assertFalse(sessions.searchEnabled[second] == true)
            sessions.flush()
            val restored = AiSessions(store, scope, { settings })
            until { restored.ready }
            assertEquals("第二条草稿", restored.drafts[second])
            assertEquals("第一条草稿", restored.drafts[first])
            assertArrayEquals(byteArrayOf(1, 2, 3), restored.attachments[first])
            assertTrue(restored.searchEnabled[first] == true)
        }
    }
    @Test fun importSurvivesSwitchAndRestoresAfterRestart() = runBlocking {
        fixture { store, scope ->
            val release = CompletableDeferred<Unit>(); val entered = CompletableDeferred<Unit>()
            val pdf = AiPdf("paper.pdf", id = "a".repeat(64), pageCount = 20)
            val sessions = AiSessions(store, scope, { settings }, preparePdf = { _, _ -> entered.complete(Unit); release.await(); pdf })
            until { sessions.ready }; val first = sessions.newChat()
            started(sessions, first)
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
    @Test fun configurationFailureKeepsComposer() = runBlocking {
        fixture { store, scope ->
            val sessions = AiSessions(store, scope, { AiConfig() })
            until { sessions.ready }; val id = sessions.newChat()
            sessions.editDraft(id, "unsent")
            sessions.setScreenshot(id, byteArrayOf(1))
            sessions.send(id, AiMessage("user", "unsent", "AQ=="), false)
            until { !sessions.isRunning(id) }; sessions.flush()
            assertEquals("unsent", sessions.drafts[id]); assertArrayEquals(byteArrayOf(1), sessions.attachments[id])
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
