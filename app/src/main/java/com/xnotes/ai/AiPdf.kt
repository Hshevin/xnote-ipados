package com.xnotes.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

data class AiPdf(
    val name: String, val text: String = "", val pageImages: List<String> = emptyList(),
    val id: String = "", val pageCount: Int = pageImages.size,
)

/** Immutable private snapshots; no source URI or arbitrary path is exposed to the model. */
object AiPdfReader {
    const val MAX_BYTES = 200 * 1024 * 1024
    private fun directory(context: Context) = File(context.noBackupFilesDir, "ai-pdfs").apply {
        check(isDirectory || mkdirs()) { "无法创建 PDF 附件目录" }
    }
    private fun file(context: Context, pdf: AiPdf): File {
        require(pdf.id.matches(Regex("[a-f0-9]{64}"))) { "PDF 附件标识无效" }
        return File(directory(context), "${pdf.id}.pdf").also { check(it.isFile) { "PDF 附件丢失，请重新附加" } }
    }
    suspend fun read(context: Context, uri: Uri, suggestedName: String? = null): AiPdf {
        require(uri.scheme in listOf("content", "file")) { "请选择本机 PDF 文件" }
        if (uri.scheme == "file") {
            val selected=File(requireNotNull(uri.path))
            val resources=com.xnotes.storage.PdfResources.get(context)
            if (resources.isOwned(selected)) {
                resources.register(selected,suggestedName ?: "附件.pdf")
                PDFBoxResourceLoader.init(context.applicationContext)
                val count=PDDocument.load(selected,MemoryUsageSetting.setupMixed(8L*1024*1024).setTempDir(context.cacheDir)).use {
                    require(!it.isEncrypted && it.numberOfPages > 0) { "PDF 无法读取" }; it.numberOfPages
                }
                return AiPdf(suggestedName ?: "附件.pdf",id=selected.nameWithoutExtension,pageCount=count)
            }
        }
        val temp = File.createTempFile("pending-", ".tmp", directory(context))
        try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val input = if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream() else context.contentResolver.openInputStream(uri)
            requireNotNull(input) { "无法读取 PDF" }.use { source ->
                java.io.FileOutputStream(temp).use { out ->
                    val buffer = ByteArray(16384); var count = 0
                    while (true) {
                        coroutineContext.ensureActive()
                        val size = source.read(buffer); if (size < 0) break
                        count += size; require(count <= MAX_BYTES) { "PDF 超过 200 MB" }
                        digest.update(buffer, 0, size); out.write(buffer, 0, size)
                    }
                    out.fd.sync()
                }
            }
            val header = ByteArray(5)
            temp.inputStream().use { require(it.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-") { "不是有效 PDF 文件" } }
            val name = suggestedName ?: if (uri.scheme == "content") {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            } else uri.lastPathSegment
            PDFBoxResourceLoader.init(context.applicationContext)
            val count = PDDocument.load(temp, MemoryUsageSetting.setupMixed(8L * 1024 * 1024).setTempDir(context.cacheDir)).use {
                require(!it.isEncrypted) { "请先解密 PDF 后再附加" }
                require(it.numberOfPages > 0) { "PDF 没有页面" }; it.numberOfPages
            }
            val id = digest.digest().joinToString("") { "%02x".format(it) }
            java.nio.file.Files.move(temp.toPath(), File(directory(context), "$id.pdf").toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            com.xnotes.storage.PdfResources.get(context).register(File(directory(context), "$id.pdf"), name ?: "附件.pdf")
            return AiPdf(name?.take(200) ?: "附件.pdf", id = id, pageCount = count)
        } finally { temp.delete() }
    }
    suspend fun text(context: Context, pdf: AiPdf, start: Int, end: Int): String {
        require(start >= 1 && end >= start && end <= pdf.pageCount && end - start < 5) { "每次可读取 1–5 页" }
        PDFBoxResourceLoader.init(context.applicationContext)
        val result = StringBuilder()
        val task = coroutineContext
        PDDocument.load(file(context, pdf), MemoryUsageSetting.setupMixed(8L * 1024 * 1024).setTempDir(context.cacheDir)).use { doc ->
            for (page in start..end) {
                task.ensureActive(); result.append("\n[第 $page 页]\n")
                val stripper = PDFTextStripper().apply { startPage = page; endPage = page }
                stripper.writeText(doc, object : java.io.Writer() {
                    override fun write(chars: CharArray, offset: Int, count: Int) {
                        task.ensureActive()
                        require(result.length + count <= 24000) { "页面文字过多，请缩小页码范围或查看页图" }
                        result.append(chars, offset, count)
                    }
                    override fun flush() {}
                    override fun close() {}
                })
            }
        }
        return result.toString()
    }
    suspend fun image(context: Context, pdf: AiPdf, number: Int): String {
        require(number in 1..pdf.pageCount) { "页码超出范围" }; coroutineContext.ensureActive()
        ParcelFileDescriptor.open(file(context, pdf), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(number - 1).use { page ->
                    val scale = 1600.0 / maxOf(page.width, page.height).coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap((page.width * scale).roundToInt().coerceAtLeast(1), (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return ByteArrayOutputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)); Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP) }
                    } finally { bitmap.recycle() }
                }
            }
        }
    }
}
