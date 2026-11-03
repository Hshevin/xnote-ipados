package com.xnotes.pen

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.xnotes.ai.AiPdfReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AiPdfReaderTest {
    @Test fun preparesAllPagesWithoutModifyingPdf() = runBlocking {
        val context = PenTestRunner.instance.targetContext
        val source = File.createTempFile("ai-pdf-test", ".pdf", context.cacheDir)
        try {
            val doc = PdfDocument()
            try {
                for (index in 1..2) {
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(400, 600, index).create())
                    page.canvas.drawText("Paper page $index", 20f, 40f, Paint().apply { textSize = 18f })
                    doc.finishPage(page)
                }
                source.outputStream().use { doc.writeTo(it) }
            } finally { doc.close() }
            val before = source.readBytes()
            val pdf = AiPdfReader.read(context, Uri.fromFile(source), "paper.pdf")
            assertEquals(2, pdf.pageCount); assertTrue(pdf.pageImages.isEmpty())
            assertTrue(AiPdfReader.image(context, pdf, 2).isNotBlank())
            assertTrue(AiPdfReader.text(context, pdf, 1, 1).contains("Paper page 1"))
            assertArrayEquals(before, source.readBytes())
            source.delete()
            assertTrue(AiPdfReader.image(context, pdf, 1).isNotBlank())
            assertTrue(before.isNotEmpty())
        } finally { source.delete() }
    }
    @Test fun rejectsNonPdfAndCleansTemporaryFiles() = runBlocking {
        val context = PenTestRunner.instance.targetContext
        val source = File.createTempFile("bad-pdf", ".pdf", context.cacheDir)
        val before = File(context.noBackupFilesDir, "ai-pdfs").listFiles().orEmpty().filter { it.name.startsWith("pending-") }.map { it.name }.toSet()
        try {
            source.writeText("not a PDF")
            try { AiPdfReader.read(context, Uri.fromFile(source)); fail("must reject") } catch (_: IllegalArgumentException) {}
            assertEquals(before, File(context.noBackupFilesDir, "ai-pdfs").listFiles().orEmpty().filter { it.name.startsWith("pending-") }.map { it.name }.toSet())
        } finally { source.delete() }
    }
}
