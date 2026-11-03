package com.xnotes.ai

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File

/** App-owned jobs and a serialized disk queue; UI disposal never cancels generation. */
class AiSessions(
    private val store: AiHistoryStore,
    private val scope: CoroutineScope,
    private val config: () -> AiConfig,
    private val transport: () -> AiTransport = { AiConnection() },
    private val pdfAccess: AiPdfAccess? = null,
    private val holdPdfs: (Set<String>) -> AutoCloseable = { AutoCloseable {} },
    private val preparePdf: suspend (String, String?) -> AiPdf = { _, _ -> error(AiText.t(AiMsg.PDF_SERVICE_UNAVAILABLE)) },
) {
    val attachments = mutableStateMapOf<String, ByteArray>()
    val pdfAttachments = mutableStateMapOf<String, AiPdf>()
    val currentPdfs = mutableStateMapOf<String, Pair<String, String>>()
    val drafts = mutableStateMapOf<String, String>()
    var historyQuery by mutableStateOf("")
    val chats = mutableStateMapOf<String, AiChat>()
    var floatingOwner by mutableStateOf<String?>(null); private set
    var activeId by mutableStateOf<String?>(null)
    var ready by mutableStateOf(false); private set
    var storageError by mutableStateOf(""); private set
    var undoChat by mutableStateOf<AiChat?>(null); private set
    val searchEnabled = mutableStateMapOf<String, Boolean>()
    val importStates = mutableStateMapOf<String, AiImportState>()
    val scrollPositions = mutableMapOf<String, Int>()
    /** Where the floating card was last left, in px of its host; a new chat opens there too. Null until moved. */
    var windowOffset by mutableStateOf<Pair<Float, Float>?>(null)
    /** Bumped when AI settings are saved, so open chats re-read their model choices. */
    var configRevision by mutableStateOf(0)
    val followLatest = mutableMapOf<String, Boolean>()
    private val imports = mutableMapOf<String, Job>()
    private val draftSaves = mutableMapOf<String, Job>()
    private val requests = mutableStateMapOf<String, Pair<AiTransport, Job>>()
    private data class Disk(val write: AiChat? = null, val delete: String? = null, val done: CompletableDeferred<Boolean> = CompletableDeferred())
    private val disk = Channel<Disk>(Channel.UNLIMITED)
    init {
        scope.launch {
            try {
                val (loaded, damaged) = withContext(Dispatchers.IO) { store.readAll() }
                loaded.forEach { chat -> chats[chat.id] = chat.recover(); restoreDraft(chat) }
                if (damaged > 0) storageError = AiText.t(AiMsg.HISTORY_DAMAGED, damaged)
                loaded.filter { it != it.recover() }.forEach { persist(it.recover()) }
            } catch (_: Exception) { storageError = AiText.t(AiMsg.HISTORY_READ_FAILED) }
            ready = true
            for (operation in disk) {
                val ok = try {
                    withContext(Dispatchers.IO) {
                        operation.write?.let(store::write)
                        operation.delete?.let(store::delete)
                    }
                    true
                } catch (_: Exception) { storageError = AiText.t(AiMsg.HISTORY_SAVE_FAILED); false }
                operation.done.complete(ok)
            }
        }
    }
    private fun persist(chat: AiChat): Deferred<Boolean> = Disk(write = chat).also { disk.trySend(it) }.done
    suspend fun flush(): Boolean {
        draftSaves.values.toList().forEach { it.join() }
        return Disk().also { disk.send(it) }.done.await()
    }
    private fun restoreDraft(chat: AiChat) {
        drafts[chat.id] = chat.draft.text
        searchEnabled[chat.id] = chat.draft.search
        chat.draft.image?.let { encoded ->
            runCatching { java.util.Base64.getDecoder().decode(encoded) }.onSuccess { attachments[chat.id] = it }.onFailure { storageError = AiText.t(AiMsg.DRAFT_SCREENSHOT_UNREADABLE) }
        }
        chat.draft.pdf?.let { pdfAttachments[chat.id] = it }
    }
    fun editDraft(id: String, text: String? = null, search: Boolean? = null) {
        if (!chats.containsKey(id)) return
        text?.let { drafts[id] = it.take(16000) }; search?.let { searchEnabled[id] = it }
        saveDraft(id)
    }
    fun setScreenshot(id: String, bytes: ByteArray?) {
        if (!chats.containsKey(id)) return
        if (bytes == null) attachments.remove(id) else attachments[id] = bytes
        saveDraft(id)
    }
    fun removePdf(id: String) {
        imports.remove(id)?.cancel(); importStates.remove(id); pdfAttachments.remove(id); saveDraft(id)
    }
    fun restoreQuestion(id: String, user: AiMessage) {
        if (!chats.containsKey(id) || isRunning(id)) return
        removePdf(id)
        drafts[id] = user.text
        if (user.pdf != null) pdfAttachments[id] = user.pdf
        attachments.remove(id)
        user.image?.let { attachments[id] = java.util.Base64.getDecoder().decode(it) }
        saveDraft(id)
    }
    private fun saveDraft(id: String) {
        val chat = chats[id] ?: return
        val draft = AiDraft(drafts[id].orEmpty(), attachments[id]?.let { java.util.Base64.getEncoder().encodeToString(it) }, pdfAttachments[id], searchEnabled[id] ?: false)
        chats[id] = chat.copy(draft = draft)
        draftSaves.remove(id)?.cancel()
        draftSaves[id] = scope.launch {
            delay(350)
            chats[id]?.let { persist(it) }
            draftSaves.remove(id)
        }
    }
    fun importPdf(id: String, uri: String, name: String? = null) {
        if (!chats.containsKey(id)) return
        imports.remove(id)?.cancel()
        val token = Any()
        importStates[id] = AiImportState(uri, name, loading = true, token = token)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val pdf = withContext(Dispatchers.IO) { preparePdf(uri, name) }
                if (chats.containsKey(id) && importStates[id]?.token === token) {
                    pdfAttachments[id] = pdf; importStates.remove(id); saveDraft(id)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (chats.containsKey(id) && importStates[id]?.token === token)
                    importStates[id] = AiImportState(uri, name, error = if (e is IllegalArgumentException) e.message ?: AiText.t(AiMsg.PDF_UNREADABLE) else AiText.t(AiMsg.PDF_UNREADABLE_CHECK), token = token)
            } finally { if (importStates[id]?.token === token || importStates[id] == null) imports.remove(id) }
        }
        imports[id] = job; job.start()
    }
    fun retryImport(id: String) { importStates[id]?.let { importPdf(id, it.uri, it.name) } }
    private suspend fun progress(id: String, label: String) = withContext(scope.coroutineContext.minusKey(Job)) {
        update(id) { c -> c.copy(turns = c.turns.dropLast(1) + c.turns.last().let { it.copy(progress = label, steps = (it.steps + label).takeLast(20)) }) }?.let { persist(it) }
    }
    fun retrySave() { storageError = ""; chats.values.forEach { persist(it) } }
    fun newChat(source: String = "", sourceUri: String = "", floatingOwner: String? = null, sourceDocumentId: String = ""): String {
        check(ready)
        val previous=if(floatingOwner!=null && this.floatingOwner==floatingOwner) activeId?.let { chats[it] } else null
        val chat = AiChat(source = source.ifBlank { previous?.source.orEmpty() }, sourceUri = sourceUri.ifBlank { previous?.sourceUri.orEmpty() }, sourceDocumentId = sourceDocumentId.ifBlank { previous?.sourceDocumentId.orEmpty() })
        val previousPdf = if (floatingOwner != null && this.floatingOwner == floatingOwner) activeId?.let { currentPdfs[it] } else null
        this.floatingOwner = floatingOwner
        // Unreadable settings only lose the defaults; the chat itself still opens.
        val defaults = runCatching { config() }.getOrNull()
        val started = if (defaults?.searchByDefault == true) chat.copy(draft = chat.draft.copy(search = true)) else chat
        if (started.draft.search) searchEnabled[chat.id] = true
        chats[chat.id] = started; activeId = chat.id; persist(started)
        previousPdf?.let { (uri, name) -> offerCurrentPdf(chat.id, uri, name, defaults) }
        return chat.id
    }
    /** The document's own PDF, offered in the ＋ menu; attached at once when the settings ask for it. */
    fun offerCurrentPdf(id: String, uri: String, name: String, defaults: AiConfig? = runCatching { config() }.getOrNull()) {
        if (!chats.containsKey(id)) return
        currentPdfs[id] = uri to name
        if (defaults?.attachPdfByDefault == true && pdfAttachments[id] == null && importStates[id] == null) importPdf(id, uri, name)
    }
    fun close() { activeId = null; floatingOwner = null }
    fun reconcileFloating(visibleDocuments: Set<String>) {
        if (floatingOwner != null && floatingOwner !in visibleDocuments) close()
    }
    fun open(id: String) { if (chats.containsKey(id)) { activeId = id; floatingOwner = null } }
    fun rename(id: String, title: String) {
        chats[id]?.let { val next = it.copy(title = title.trim().take(100).ifBlank { "新会话" }); chats[id] = next; persist(next) }
    }
    /** Pick this chat's model and thinking level for its next questions; a running answer keeps its own. */
    fun choose(id: String, model: String? = null, effort: String? = null) {
        val chat = chats[id] ?: return
        val next = chat.copy(model = model?.trim() ?: chat.model, effort = effort?.takeIf { it in AiThinking.LEVELS } ?: chat.effort)
        if (next != chat) { chats[id] = next; persist(next) }
    }
    fun isRunning(id: String) = requests.containsKey(id)
    private fun update(id: String, change: (AiChat) -> AiChat): AiChat? = chats[id]?.let { change(it).also { next -> chats[id] = next } }
    fun send(id: String, user: AiMessage, useSearch: Boolean) {
        if (!ready || isRunning(id) || importStates[id]?.loading == true || importStates[id]?.error?.isNotEmpty() == true) return
        val original = chats[id] ?: return
        val client = transport()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var started = false
            var pdfLease: AutoCloseable? = null
            try {
                val settings = original.settings(config())
                val tools = settings.forTools()
                require(settings.key.isNotBlank()) { AiText.t(AiMsg.PROVIDER_NOT_CONFIGURED) }
                AiProtocol.endpoint(settings.url)
                AiProtocol.request(settings, original.context() + user)
                if (useSearch) { require(settings.searchKey.isNotBlank()) { AiText.t(AiMsg.SEARCH_KEY_NOT_CONFIGURED) } }
                val next = original.copy(draft = chats[id]?.draft ?: original.draft, title = if (original.turns.isEmpty() && original.title == "新会话") user.text.take(40) else original.title,
                    updated = System.currentTimeMillis(), turns = original.turns + AiTurn(user))
                chats[id] = next; started = true
                check(persist(next).await()) { AiText.t(AiMsg.QUESTION_SAVE_FAILED) }
                // Clear only the submitted composer; edits made while saving belong to the next turn.
                if (drafts[id].orEmpty().trim() == user.text && pdfAttachments[id] == user.pdf &&
                    attachments[id]?.let { java.util.Base64.getEncoder().encodeToString(it) } == user.image) {
                    drafts.remove(id); attachments.remove(id); pdfAttachments.remove(id); saveDraft(id)
                }
                val pdfIds=(original.context()+user).mapNotNull { it.pdf?.id?.takeIf(String::isNotBlank) }.toSet()
                pdfLease=withContext(Dispatchers.IO) { holdPdfs(pdfIds) }
                progress(id, AiText.t(AiMsg.PROGRESS_PREPARING))
                val prepared = if ((original.context() + user).any { !it.pdf?.id.isNullOrEmpty() }) {
                    withContext(Dispatchers.IO) { AiPdfTools.prepare(tools, client, original.context(), user, checkNotNull(pdfAccess) { AiText.t(AiMsg.PDF_SERVICE_UNAVAILABLE) }) { label -> progress(id, label) } }
                } else user
                var outgoing = prepared
                if (useSearch) {
                    progress(id, AiText.t(AiMsg.PROGRESS_RESOLVING_SEARCH))
                    val resolvedQuery = run {
                        val planned = StringBuilder()
                        withContext(Dispatchers.IO) {
                            client.stream(tools, AiSearchQuery.messages(original.context(), prepared)) { chunk ->
                                require(planned.length + chunk.length <= 4096) { AiText.t(AiMsg.SEARCH_TARGET_TOO_LONG) }
                                planned.append(chunk)
                            }
                        }
                        AiSearchQuery.parse(planned.toString())
                    }
                    outgoing = prepared.copy(searchContext = "本次搜索词：$resolvedQuery\n正在检索…")
                    update(id) { c -> c.copy(turns = c.turns.dropLast(1) + c.turns.last().copy(user = user.copy(searchContext = outgoing.searchContext))) }?.let { persist(it) }
                    progress(id, AiText.t(AiMsg.PROGRESS_SEARCHING_WEB, resolvedQuery))
                    val sources = withContext(Dispatchers.IO) { client.search(settings.searchKey, resolvedQuery) }
                    outgoing = prepared.copy(searchContext = "本次搜索词：$resolvedQuery\n$sources")
                    update(id) { c -> c.copy(turns = c.turns.dropLast(1) + c.turns.last().copy(user = user.copy(searchContext = outgoing.searchContext))) }?.let { persist(it) }
                }
                progress(id, AiText.t(AiMsg.PROGRESS_GENERATING))
                var lastSave = System.nanoTime()
                withContext(Dispatchers.IO) {
                    client.stream(settings, original.context() + outgoing) { chunk ->
                        withContext(scope.coroutineContext.minusKey(Job)) {
                            val chat = update(id) { c -> c.copy(turns = c.turns.dropLast(1) + c.turns.last().copy(answer = c.turns.last().answer + chunk)) }
                            if (chat != null && System.nanoTime() - lastSave > 1_000_000_000L) { persist(chat); lastSave = System.nanoTime() }
                        }
                    }
                }
                check(chats[id]?.turns?.lastOrNull()?.answer?.isNotBlank() == true) { AiText.t(AiMsg.NO_TEXT_RETURNED) }
                finish(id, "已完成", "")
            } catch (e: CancellationException) {
                if (started) finish(id, "已停止", AiText.t(AiMsg.STOPPED_BY_USER))
                throw e
            } catch (e: Exception) {
                val message = if (e is IllegalArgumentException || e is IllegalStateException) e.message ?: AiText.t(AiMsg.REQUEST_FAILED) else AiText.t(AiMsg.NETWORK_ERROR)
                if (started) finish(id, "生成失败", message)
                else update(id) { c -> c.copy(turns = c.turns + AiTurn(user, state = "生成失败", error = message)) }?.let { persist(it) }
            } finally {
                try { withContext(NonCancellable + Dispatchers.IO) { pdfLease?.close() } }
                finally { requests.remove(id) }
            }
        }
        requests[id] = client to job; job.start()
    }
    private fun finish(id: String, state: String, error: String) {
        update(id) { c -> c.copy(updated = System.currentTimeMillis(), turns = c.turns.dropLast(1) + c.turns.last().copy(state = state, error = error)) }?.let { persist(it) }
    }
    fun stop(id: String) { requests[id]?.let { (client, job) -> client.cancel(); job.cancel() } }
    fun delete(id: String) {
        val chat = chats[id] ?: return
        stop(id)
        imports.remove(id)?.cancel(); importStates.remove(id); draftSaves.remove(id)?.cancel()
        searchEnabled.remove(id); scrollPositions.remove(id); followLatest.remove(id)
        attachments.remove(id); drafts.remove(id); pdfAttachments.remove(id); currentPdfs.remove(id)
        chats.remove(id) // Late callbacks cannot recreate a deleted record.
        if (activeId == id) close()
        val deleted = chat.recover()
        scope.launch {
            val op = Disk(delete = id); disk.send(op)
            if (op.done.await()) {
                undoChat = deleted
                delay(8000)
                if (undoChat?.id == id) undoChat = null
                withContext(Dispatchers.IO) { store.forgetDeleted(id) }
            } else { chats[id] = deleted }
        }
    }
    fun undoDelete() { undoChat?.let { chats[it.id] = it; restoreDraft(it); persist(it); undoChat = null } }
    companion object {
        @Volatile private var instance: AiSessions? = null
        fun get(context: Context): AiSessions {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: AiSessions(AiHistoryStore(File(app.noBackupFilesDir, "ai-history"), com.xnotes.storage.PdfResources.get(app)),
                    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), { AiConfigStore(app).load() }, pdfAccess = LocalAiPdfAccess(app), holdPdfs = { com.xnotes.storage.PdfResources.get(app).hold(it) }, preparePdf = { uri, name -> AiPdfReader.read(app, android.net.Uri.parse(uri), name) }).also { instance = it }
            }
        }
    }
}

data class AiImportState(val uri: String, val name: String?, val loading: Boolean = false, val error: String = "", val token: Any = Any())
