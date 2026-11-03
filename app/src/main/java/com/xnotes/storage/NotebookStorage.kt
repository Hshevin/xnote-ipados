package com.xnotes.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.xnotes.format.BundleIdentity
import com.xnotes.format.BundleMigration
import com.xnotes.format.ZipTail
import com.xnotes.platform.AppStorageDocumentsProvider
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.ensureActive

/** On-demand, IO-only registration. Never walks storage on startup or a drawing callback. */
class NotebookStorage private constructor(private val context: Context) {
    val registry = DocumentRegistry(context)
    private val lock = Any()
    fun identity(uri: String): BundleIdentity? = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { pfd ->
        // Own only pfd, not a second close of the same file descriptor.
        ZipTail.readEntry(FileInputStream(pfd.fileDescriptor).channel, BundleIdentity.ENTRY) { BundleIdentity.read(it) }
    }
    private fun journal(uri: String): MigrationPublication {
        val id = java.security.MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        return MigrationPublication(File(context.noBackupFilesDir, "migrations/$id"))
    }
    private fun target(uri: String) = object : MigrationPublication.Target {
        override fun read() = requireNotNull(context.contentResolver.openInputStream(Uri.parse(uri))) { "无法读取笔记" }
        override fun write(source: File) {
            val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(Uri.parse(uri), "rwt")) { "笔记不可写" }
            android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { out ->
                source.inputStream().use { it.copyTo(out) }
                out.flush(); out.fd.sync()
            }
        }
    }
    fun hasRecovery(uri: String): Boolean = journal(uri).pending
    fun restore(uri: String) = synchronized(lock) { journal(uri).restore(target(uri)) }
    fun keepCurrent(uri: String) = synchronized(lock) {
        // Check the complete ZIP before discarding the only known-good original.
        val temp = File.createTempFile("validate-", ".zip", context.cacheDir)
        try { target(uri).read().use { input -> temp.outputStream().use { input.copyTo(it) } }; BundleMigration.fingerprints(temp); journal(uri).keepCurrent() }
        finally { temp.delete() }
    }
    fun ensure(uri: String, name: String, kind: String): BundleIdentity = synchronized(lock) {
        if (journal(uri).pending) journal(uri).publish(target(uri))
        val existing = identity(uri)
        val identity = existing ?: migrate(uri, copy = false)
        registry.register(identity.documentId, kind, name, uri)
        for(old in registry.locations(identity.documentId)) if(old!=uri) {
            val reachable=runCatching { context.contentResolver.openFileDescriptor(Uri.parse(old),"r")?.use { true } ?: false }.getOrDefault(false)
            if(!reachable) registry.markUnavailable(old)
        }
        identity.pdfResource?.let { id -> if(registry.resource(id)!=null) PdfResources.get(context).pin("note:${identity.documentId}",PdfResources.get(context).references("note:${identity.documentId}")+id) }
        identity
    }
    private fun migrate(uri: String, copy: Boolean): BundleIdentity {
        val local = ownedFile(uri)
        if (local != null) {
            check(local.canWrite()) { "旧笔记为只读，原文件未修改；请保存可写副本后重试" }
            val staged = BundleMigration.prepare(local, requireNotNull(local.parentFile), copy = copy)
            try { BundleMigration.publish(staged, local); return staged.identity } finally { staged.file.delete() }
        }
        // Check write permission without truncation before making recovery files.
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "rw")?.use { } ?: error("文件为只读，无法升级；请保存可写副本")
        val source = File.createTempFile("legacy-note-", ".zip", context.cacheDir)
        try {
            target(uri).read().use { input -> java.io.FileOutputStream(source).use { out -> input.copyTo(out); out.fd.sync() } }
            val staged = BundleMigration.prepare(source, context.cacheDir, copy = copy)
            try {
                journal(uri).stage(source, staged.file)
                journal(uri).publish(target(uri))
                return staged.identity
            } finally { staged.file.delete() }
        } finally { source.delete() }
    }
    /** Call only on a newly created copy, never the original. */
    fun assignCopy(uri: String) = synchronized(lock) {
        val identity=migrate(uri, copy = true)
        val name=context.contentResolver.query(Uri.parse(uri),arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else "" }.orEmpty()
        registry.register(identity.documentId,if(name.endsWith(".xcanvas",true)) "xcanvas" else "xnote",name,uri)
        identity.pdfResource?.let { id -> if(registry.resource(id)!=null) PdfResources.get(context).pin("note:${identity.documentId}",setOf(id)) }
        identity
    }
    /** Explicit user-triggered rediscovery, only inside granted workspace trees, with a bounded scan. */
    suspend fun rediscover(id: String, roots: List<String>): String? {
        require(id.isNotBlank()) { "旧分屏没有文档 ID，请手动重新定位" }
        val matches=linkedMapOf<String,String>()
        var inspected=0
        for(root in roots.distinct()) {
            val tree=Uri.parse(root)
            val queue=java.util.ArrayDeque<String>()
            queue.add(DocumentsContract.getTreeDocumentId(tree))
            val visited=hashSetOf<String>()
            while(queue.isNotEmpty()) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val parent=queue.removeFirst()
                if(!visited.add(parent)) continue
                val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent)
                context.contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use { c ->
                    while(c.moveToNext()) {
                        check(++inspected<=5000) { "已检查5000个条目，请缩小授权文件夹范围或手动重新定位" }
                        val child=c.getString(0); val name=c.getString(1).orEmpty()
                        if(c.getString(2)==DocumentsContract.Document.MIME_TYPE_DIR) queue.add(child)
                        else if(name.endsWith(".xnote",true)||name.endsWith(".xcanvas",true)) {
                            val uri=DocumentsContract.buildDocumentUriUsingTree(tree,child).toString()
                            if(runCatching { identity(uri)?.documentId==id }.getOrDefault(false)) {
                                registry.register(id,if(name.endsWith(".xcanvas",true)) "xcanvas" else "xnote",name,uri); matches["${tree.authority}|$child"]=uri
                            }
                        }
                    }
                }
            }
        }
        check(matches.size<=1) { "找到多份相同ID的文档，请手动选择原件或将副本设为独立文档" }
        return matches.values.singleOrNull()
    }
    fun ownedFile(uri: String): File? {
        val parsed = Uri.parse(uri)
        if (parsed.authority != context.packageName + ".documents") return null
        val id = DocumentsContract.getDocumentId(parsed)
        if (!id.startsWith("root/")) return null
        val root = AppStorageDocumentsProvider.rootDir(context).canonicalFile
        val file = File(root, id.removePrefix("root/")).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "非法笔记路径" }
        return file
    }
    companion object {
        @Volatile private var instance: NotebookStorage? = null
        fun get(context: Context): NotebookStorage = instance ?: synchronized(this) {
            instance ?: NotebookStorage(context.applicationContext).also { instance = it }
        }
    }
}
