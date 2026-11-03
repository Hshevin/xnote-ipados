package com.xnotes.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xnotes.ui.theme.Space
import androidx.compose.ui.unit.IntOffset
import com.xnotes.ai.*
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import java.io.File
import kotlinx.coroutines.*

@Composable
internal fun AiSettingsButton() {
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }) { Text("AI 设置") }
    if (show) AiSettings { show = false }
}

@Composable
private fun AiSettings(close: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AiConfigStore(context) }
    var error by remember { mutableStateOf("") }
    var config by remember { mutableStateOf(runCatching { store.load() }.getOrElse { error = "配置无法读取，请重新填写并保存"; AiConfig() }) }
    AlertDialog(onDismissRequest = close, title = { Text("AI 设置") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s8)) {
            Text("兼容 Chat Completions 接口。请选择支持图片的模型；仅在点击发送时上传截图和当前会话。")
            OutlinedTextField(config.url, { config = config.copy(url = it) }, label = { Text("供应商 URL（包含 /v1 等前缀）") }, singleLine = true)
            OutlinedTextField(config.model, { config = config.copy(model = it) }, label = { Text("模型名称") }, singleLine = true)
            OutlinedTextField(config.key, { config = config.copy(key = it) }, label = { Text("API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(config.searchKey, { config = config.copy(searchKey = it) }, label = { Text("DeepSeek 搜索 API Key（可选）") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Text("搜索固定使用 DeepSeek 官方接口，单独计费；搜索词会发送给 DeepSeek，结果会发送给聊天供应商。开启搜索会先结合截图和问题识别搜索对象，会增加一次模型调用。")
            Text("密钥仅保存在本机，不随笔记导出。会话保存在本机 AI 历史中；关闭窗口不会停止生成。")
            if (error.isNotEmpty()) Text(error)
            TextButton(onClick = { store.clear(); config = AiConfig(); error = "已清除配置" }) { Text("清除配置") }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching { store.save(config) }.onSuccess { close() }.onFailure { error = if (it is IllegalArgumentException) it.message ?: "配置无效" else "保存失败，原配置保留" }
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = close) { Text("取消") } })
}

@Composable
internal fun rememberAskAi(source: String, sourceUri: String?, currentPdf: File? = null, documentId: String = ""): (File) -> Unit {
    val sessions = AiSessions.get(LocalContext.current)
    var failure by remember { mutableStateOf("") }
    if (failure.isNotEmpty()) AlertDialog(onDismissRequest = { failure = "" }, text = { Text(failure) }, confirmButton = { TextButton(onClick = { failure = "" }) { Text("确定") } })
    return { file ->
        runCatching {
            check(sessions.ready) { "AI 历史正在载入，请稍后重试" }
            require(file.length() in 1..(6 * 1024 * 1024)) { "截图过大，请缩小选区" }
            val bytes = file.readBytes()
            val id = sessions.newChat(source, sourceUri.orEmpty(), documentId.ifBlank { sourceUri ?: source }, documentId)
            sessions.setScreenshot(id, bytes)
            currentPdf?.let { sessions.currentPdfs[id] = android.net.Uri.fromFile(it).toString() to "$source.pdf" }
        }.onFailure { failure = it.message ?: "无法载入截图" }
    }
}

/** Exactly one host at the application root; survives editor and backstage navigation. */
@Composable
fun AiChatHost(visibleDocuments: Set<String>) {
    val sessions = AiSessions.get(LocalContext.current)
    val chat = sessions.activeId?.let { sessions.chats[it] }
    LaunchedEffect(visibleDocuments, sessions.floatingOwner) { sessions.reconcileFloating(visibleDocuments) }
    if (chat != null && sessions.floatingOwner in visibleDocuments) key(chat.id) { AskAiWindow(sessions, chat) }
}

@Composable
private fun AskAiWindow(sessions: AiSessions, chat: AiChat, floating: Boolean = true, onHistory: () -> Unit = {}) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val importState = sessions.importStates[chat.id]
    val importing = importState?.loading == true
    var clipboardError by remember { mutableStateOf("") }
    fun attachPdf(uri: android.net.Uri, name: String? = null) {
        clipboardError = ""; sessions.importPdf(chat.id, uri.toString(), name)
    }
    // Capture the requesting chat: picker results must never attach to a newly selected conversation.
    var pickerChat by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val pdfPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val target = pickerChat; pickerChat = null
        if (uri != null && target != null) sessions.importPdf(target, uri.toString())
    }
    val scroll = remember(chat.id) { ScrollState(sessions.scrollPositions[chat.id] ?: 0) }
    var follow by remember(chat.id) { mutableStateOf(sessions.followLatest[chat.id] ?: true) }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.value to scroll.isScrollInProgress }.collect { (value, scrolling) ->
            sessions.scrollPositions[chat.id] = value
            if (scrolling) { follow = scroll.maxValue - value < 80; sessions.followLatest[chat.id] = follow }
        }
    }
    LaunchedEffect(scroll.maxValue) { if (follow && !scroll.isScrollInProgress) scroll.scrollTo(scroll.maxValue) }
    var settings by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    var x by remember { mutableStateOf(0f) }
    var y by remember { mutableStateOf(0f) }
    val webSearch = sessions.searchEnabled[chat.id] ?: false
    val prompt = sessions.drafts[chat.id] ?: ""
    val attachment = sessions.attachments[chat.id]
    val busy = sessions.isRunning(chat.id)
    var retryQuestion by remember { mutableStateOf<AiMessage?>(null) }
    retryQuestion?.let { question ->
        AlertDialog(onDismissRequest = { retryQuestion = null }, title = { Text("替换当前草稿？") },
            text = { Text("输入框已有未发送内容。重新提问会用这条问题及其附件替换当前草稿。") },
            confirmButton = { TextButton(onClick = { sessions.restoreQuestion(chat.id, question); retryQuestion = null }) { Text("替换") } },
            dismissButton = { TextButton(onClick = { retryQuestion = null }) { Text("保留草稿") } })
    }
    if (settings) AiSettings { settings = false }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val width = if (floating) minOf(maxWidth, 460.dp) else maxWidth
        val height = if (floating) minOf(maxHeight, if (collapsed) 56.dp else 640.dp) else maxHeight
        val maxX = with(density) { (maxWidth - width).toPx() }.coerceAtLeast(0f)
        val maxY = with(density) { (maxHeight - height).toPx() }.coerceAtLeast(0f)
        Column(Modifier.offset { IntOffset(x.coerceIn(0f, maxX).toInt(), y.coerceIn(0f, maxY).toInt()) }
            .width(width).height(height).background(palette.menuBg.toComposeColor())
            .then(if (floating) Modifier.border(1.dp, palette.text.toComposeColor()) else Modifier).pointerInput(Unit) { detectTapGestures { } }.padding(Space.s8)) {
            Row(Modifier.fillMaxWidth().pointerInput(maxX, maxY) {
                if (floating) detectDragGestures { change, drag -> change.consume(); x = (x + drag.x).coerceIn(0f, maxX); y = (y + drag.y).coerceIn(0f, maxY) }
            }, horizontalArrangement = Arrangement.SpaceBetween) {
                if (floating) TextButton(onClick = { collapsed = !collapsed }) { Text(if (collapsed) "展开 AI" else "收起 AI") }
                else TextButton(onClick = onHistory) { Text("历史会话") }
                TextButton(onClick = { settings = true }) { Text("设置") }
                if (floating) TextButton(onClick = { sessions.close() }) { Text("关闭") }
            }
            if (!collapsed) {
                Text(chat.title, maxLines = 1, color = palette.text.toComposeColor())
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                    if (chat.turns.isEmpty()) Text("输入问题开始聊天；可附加截图或 PDF。", color = palette.text.toComposeColor())
                    SelectionContainer {
                        Column {
                            chat.turns.forEachIndexed { index, turn ->
                                Text("你：${turn.user.text}", modifier = Modifier.padding(top = Space.s12), color = palette.text.toComposeColor())
                                turn.user.image?.let { encoded ->
                                    val bitmap = remember(encoded) { runCatching { val bytes = Base64.decode(encoded, Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }
                                    bitmap?.let { Image(it.asImageBitmap(), "会话截图", Modifier.fillMaxWidth().heightIn(max = 180.dp)) }
                                }
                                turn.user.pdf?.let { Text("PDF：${it.name} · ${it.pageCount} 页", color = palette.text.toComposeColor()) }
                                Text("AI", color = palette.text.toComposeColor())
                                AiMarkdownText(turn.answer)
                                var details by remember(chat.id, index) { mutableStateOf(false) }
                                if (turn.state == "生成中" && turn.progress.isNotBlank()) Text(turn.progress, color = palette.text.toComposeColor())
                                if (turn.steps.isNotEmpty() || turn.user.searchContext.isNotBlank()) {
                                    TextButton(onClick = { details = !details }) { Text(if (details) "收起过程与来源" else "查看过程与来源") }
                                    if (details) Text((turn.steps + turn.user.searchContext).filter { it.isNotBlank() }.joinToString("\n"), color = palette.text.toComposeColor())
                                }
                                Text(turn.state + if (turn.error.isNotBlank()) " · ${turn.error}" else "", color = palette.text.toComposeColor())
                                if (turn.state in listOf("已停止", "已中断", "生成失败") && !busy) TextButton(onClick = {
                                    if (prompt.isNotBlank() || attachment != null || sessions.pdfAttachments[chat.id] != null || importing) retryQuestion = turn.user
                                    else sessions.restoreQuestion(chat.id, turn.user)
                                }) { Text("重新提问") }

                            }
                        }
                    }
                    if (sessions.storageError.isNotEmpty()) {
                        Text(sessions.storageError, color = palette.text.toComposeColor())
                        TextButton(onClick = sessions::retrySave) { Text("重试保存") }
                    }
                }
                if (!follow && scroll.maxValue > scroll.value) TextButton(onClick = {
                    follow = true; sessions.followLatest[chat.id] = true
                    uiScope.launch { scroll.scrollTo(scroll.maxValue) }
                }) { Text("回到最新回复") }
                attachment?.let { bytes ->
                    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    Row {
                        bitmap?.let { Image(it.asImageBitmap(), "待发送截图", Modifier.size(64.dp)) }
                        Text("截图 · 待发送", color = palette.text.toComposeColor())
                        TextButton(onClick = { sessions.setScreenshot(chat.id, null) }) { Text("移除截图") }
                    }
                }
                importState?.let { state ->
                    Column(Modifier.fillMaxWidth().border(1.dp, palette.text.toComposeColor()).padding(Space.s8)) {
                        Text(if (state.loading) "正在准备 ${state.name ?: "PDF"}… 切换会话后会继续" else state.error, color = palette.text.toComposeColor())
                        Row {
                            if (!state.loading) TextButton(onClick = { sessions.retryImport(chat.id) }) { Text("重试附件") }
                            TextButton(onClick = { sessions.removePdf(chat.id) }) { Text(if (state.loading) "取消准备" else "移除失败附件") }
                        }
                    }
                }
                if (clipboardError.isNotEmpty()) Text(clipboardError, color = palette.text.toComposeColor())
                sessions.pdfAttachments[chat.id]?.let { pdf ->
                    Row { Text("${pdf.name} · ${pdf.pageCount} 页 · 已就绪，待发送", modifier = Modifier.weight(1f), color = palette.text.toComposeColor())
                        TextButton(onClick = { sessions.removePdf(chat.id) }, enabled = !importing) { Text("移除 PDF") } }
                }
                Row {
                    sessions.currentPdfs[chat.id]?.let { (uri, name) ->
                        TextButton(onClick = { attachPdf(android.net.Uri.parse(uri), name) }, enabled = !importing) { Text("附加当前 PDF") }
                    }
                    TextButton(onClick = { pickerChat = chat.id; pdfPicker.launch(arrayOf("application/pdf")) }, enabled = !importing) { Text("附加 PDF") }
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = clipboard.primaryClip
                        val uri = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).uri else null
                        if (uri == null) clipboardError = "剪贴板中没有可读取的 PDF 文件，请使用附加 PDF"
                        else attachPdf(uri)
                    }, enabled = !importing) { Text("粘贴 PDF") }
                }
                OutlinedTextField(prompt, { sessions.editDraft(chat.id, text = it) }, modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp), label = { Text(if (busy) "下一条问题（草稿）" else "输入问题") })
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = webSearch, onCheckedChange = { sessions.editDraft(chat.id, search = it) })
                    Text("联网搜索 · DeepSeek", color = palette.text.toComposeColor())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { sessions.newChat(floatingOwner = if (floating) sessions.floatingOwner else null) }) { Text("新会话") }
                    if (busy) TextButton(onClick = { sessions.stop(chat.id) }) { Text("停止") }
                    else TextButton(onClick = {
                        sessions.send(chat.id, AiMessage("user", prompt.trim(), attachment?.let { Base64.encodeToString(it, Base64.NO_WRAP) }, pdf = sessions.pdfAttachments[chat.id]), webSearch)
                    }, enabled = prompt.isNotBlank() && !importing && importState?.error.isNullOrEmpty()) { Text("发送") }
                }
            }
        }
    }
}

@Composable
private fun AiHistoryList(onShowSidebar: () -> Unit, onOpenSource: (String) -> Unit, onSelected: () -> Unit) {
    val sessions = AiSessions.get(LocalContext.current)
    val palette = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceError by remember { mutableStateOf("") }
    var sourceErrorChat by remember { mutableStateOf<String?>(null) }
    var relocating by remember { mutableStateOf<AiChat?>(null) }
    val relocatePicker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val chat=relocating; relocating=null
        if(uri!=null && chat!=null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val storage=com.xnotes.storage.NotebookStorage.get(context)
                        val id=storage.identity(uri.toString())?.documentId
                        require(chat.sourceDocumentId.isBlank() || id==chat.sourceDocumentId) { "所选文件不是原会话的来源文档" }
                        if(!chat.sourceDocumentId.isBlank()) storage.ensure(uri.toString(),chat.source,"xnote")
                    }
                    sourceError=""; sourceErrorChat=null; onOpenSource(uri.toString())
                } catch(e:Exception) { sourceError=e.message ?: "无法重新定位原文"; sourceErrorChat=chat.id }
            }
        }
    }
    val query = sessions.historyQuery
    var deleting by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(Space.s16)) {
        Row {
            TextButton(onClick = onShowSidebar) { Text("菜单") }
            TextButton(onClick = { sessions.newChat(); onSelected() }, enabled = sessions.ready) { Text("新会话") }
            AiSettingsButton()
        }
        OutlinedTextField(query, { sessions.historyQuery = it }, label = { Text("搜索 AI 标题与聊天文字") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (sessions.storageError.isNotEmpty()) {
            Text(sessions.storageError, color = palette.text.toComposeColor())
            TextButton(onClick = sessions::retrySave) { Text("重试保存") }
        }
        if(sourceError.isNotEmpty()) Text(sourceError)
        sessions.undoChat?.let { Row { Text("已删除 ${it.title}", color = palette.text.toComposeColor()); TextButton(onClick = sessions::undoDelete) { Text("撤销删除") } } }
        val chats = sessions.chats.values.filter { it.matches(query) }.sortedByDescending { it.updated }
        if (!sessions.ready) Text("正在读取历史…") else if (chats.isEmpty()) Text("没有匹配的会话")
        androidx.compose.foundation.lazy.LazyColumn {
            items(chats.size, key = { chats[it].id }) { index ->
                val chat = chats[index]
                Column(Modifier.fillMaxWidth().then(if (sessions.activeId == chat.id) Modifier.border(1.dp, palette.text.toComposeColor()) else Modifier).clickable { sessions.open(chat.id); onSelected() }.padding(Space.s8)) {
                    Text(chat.title, color = palette.text.toComposeColor())
                    Text((if (sessions.isRunning(chat.id)) chat.turns.lastOrNull()?.progress?.ifBlank { "生成中" } ?: "正在准备" else chat.turns.lastOrNull()?.state ?: "未发送") + " · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(chat.updated)), color = palette.text.toComposeColor())
                    if (chat.draft.text.isNotBlank() || chat.draft.image != null || chat.draft.pdf != null) Text("有未发送草稿", color = palette.text.toComposeColor())
                    if (sessions.importStates[chat.id]?.loading == true) Text("附件准备中", color = palette.text.toComposeColor())
                    if (chat.source.isNotBlank()) Text("来源：${chat.source}", color = palette.text.toComposeColor())
                    Row {
                        TextButton(onClick = { sessions.open(chat.id); onSelected() }) { Text("继续") }
                        if (chat.sourceUri.isNotBlank() || chat.sourceDocumentId.isNotBlank()) TextButton(onClick = { scope.launch {
                            try {
                                val uri = withContext(Dispatchers.IO) {
                                    val storage=com.xnotes.storage.NotebookStorage.get(context)
                                    val path=if(chat.sourceDocumentId.isBlank()) chat.sourceUri else storage.registry.resolve(chat.sourceDocumentId) ?: chat.sourceUri
                                    check(path.isNotBlank()) { "来源文档尚未保存，请先保存笔记" }
                                    check(chat.sourceDocumentId.isBlank() || storage.identity(path)?.documentId == chat.sourceDocumentId) { "来源文档失联或已被替换，请重新定位原文档" }
                                    path
                                }
                                onOpenSource(uri)
                            } catch(e: Exception) { sourceError = e.message ?: "来源文档失联，请重新定位"; sourceErrorChat=chat.id }
                        } }) { Text("原文") }
                        TextButton(onClick = { renaming = chat.id; title = chat.title }) { Text("重命名") }
                        TextButton(onClick = { deleting = chat.id }) { Text("删除") }
                    }
                    if(sourceErrorChat==chat.id) TextButton(onClick = { relocating=chat; relocatePicker.launch(arrayOf("*/*")) }) { Text("重新定位原文") }
                    HorizontalDivider(color = palette.border.toComposeColor())
                }
            }
        }
    }
    deleting?.let { id -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除会话？") }, text = { Text("正在生成的回答会停止，记录与截图一并删除。删除后可在 8 秒内撤销。") }, confirmButton = { TextButton(onClick = { sessions.delete(id); deleting = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
    renaming?.let { id -> AlertDialog(onDismissRequest = { renaming = null }, title = { Text("重命名") }, text = { OutlinedTextField(title, { title = it.take(100) }) }, confirmButton = { TextButton(onClick = { sessions.rename(id, title); renaming = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } }) }
}


@Composable
internal fun AiHistoryPane(onShowSidebar: () -> Unit, onOpenSource: (String) -> Unit) {
    val sessions = AiSessions.get(LocalContext.current)
    var history by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sessions.ready) {
        if (sessions.ready && sessions.activeId == null) {
            val latest = sessions.chats.values.maxByOrNull { it.updated }
            if (latest == null) sessions.newChat() else sessions.open(latest.id)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        androidx.activity.compose.BackHandler(enabled = !wide && history) { history = false }
        Row(Modifier.fillMaxSize()) {
            if (wide || history) Box(if (wide) Modifier.width(300.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                AiHistoryList(onShowSidebar, onOpenSource, onSelected = { history = false })
            }
            if (wide) VerticalDivider(color = LocalPalette.current.text.toComposeColor())
            if (wide || !history) Column(Modifier.weight(1f).fillMaxHeight()) {
                if (!wide) Row { TextButton(onClick = onShowSidebar) { Text("菜单") } }
                val chat = sessions.activeId?.let { sessions.chats[it] }
                if (!sessions.ready) Text("正在载入会话…")
                else if (chat == null) {
                    Text("选择历史会话或开始新的聊天")
                    TextButton(onClick = { sessions.newChat() }) { Text("新会话") }
                    if (!wide) TextButton(onClick = { history = true }) { Text("历史会话") }
                } else key(chat.id) { AskAiWindow(sessions, chat, floating = false, onHistory = { history = !history }) }
            }
        }
    }
}
