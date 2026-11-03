package com.xnotes.format

import com.xnotes.storage.MigrationPublication
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class MigrationPublicationTest {
    private fun scenario(block: (File, File, File, MigrationPublication) -> Unit) {
        val dir = Files.createTempDirectory("publication").toFile()
        try {
            val old = File(dir,"old").apply { writeText("original ink bytes") }
            val next = File(dir,"next").apply { writeText("same ink plus identity") }
            val target = File(dir,"target").apply { writeBytes(old.readBytes()) }
            val journal = MigrationPublication(File(dir,"recovery")); journal.stage(old,next)
            block(old,next,target,journal)
        } finally { dir.deleteRecursively() }
    }
    private fun target(file: File, fail: Boolean = false) = object : MigrationPublication.Target {
        override fun read() = file.inputStream()
        override fun write(source: File) {
            if (fail) { file.writeText("partial"); throw java.io.IOException("disk full") }
            file.writeBytes(source.readBytes())
        }
    }
    @Test fun verifiedWriteClearsJournal() = scenario { _, next, file, journal ->
        journal.publish(target(file)); assertArrayEquals(next.readBytes(),file.readBytes()); assertFalse(journal.pending)
    }
    @Test fun failedWriteKeepsOriginalAndExplicitRecoveryRestoresIt() = scenario { old, _, file, journal ->
        try { journal.publish(target(file,true)); fail() } catch (_: MigrationPublication.RecoveryRequired) {}
        assertTrue(journal.pending)
        try { journal.publish(target(file)); fail() } catch (_: MigrationPublication.RecoveryRequired) {}
        journal.restore(target(file)); assertArrayEquals(old.readBytes(), file.readBytes()); assertFalse(journal.pending)
    }
    @Test fun externalChangeIsNotOverwrittenAutomatically() = scenario { _, _, file, journal ->
        file.writeText("external edits")
        try { journal.publish(target(file)); fail() } catch (_: MigrationPublication.RecoveryRequired) {}
        assertEquals("external edits",file.readText()); assertTrue(journal.pending)
    }
    @Test fun restartAfterCompletedWriteRecognizesCandidate() = scenario { _, next, file, journal ->
        file.writeBytes(next.readBytes()); journal.publish(target(file,true)); assertFalse(journal.pending)
    }
    @Test fun readOnlyFailureKeepsOldBytesAndCanRetryAfterPermissionReturns() = scenario { old, next, file, journal ->
        val denied=object:MigrationPublication.Target {
            override fun read()=file.inputStream()
            override fun write(source:File) { throw java.io.IOException("read only") }
        }
        try { journal.publish(denied); fail() } catch(_:MigrationPublication.RecoveryRequired) {}
        assertArrayEquals(old.readBytes(),file.readBytes()); assertTrue(journal.pending)
        journal.publish(target(file)); assertArrayEquals(next.readBytes(),file.readBytes())
    }

}
