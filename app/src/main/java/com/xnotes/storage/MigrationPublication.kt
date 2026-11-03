package com.xnotes.storage

import com.xnotes.format.BundleMigration
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** SAF may not support atomic replacement. Durable original + candidate are kept until read-back verifies success. */
class MigrationPublication(private val directory: File) {
    interface Target {
        fun read(): InputStream
        /** Must only be called after the journal has been synced. */
        fun write(source: File)
    }
    class RecoveryRequired(message: String) : IllegalStateException(message)
    private val original get() = File(directory, "original.zip")
    private val candidate get() = File(directory, "candidate.zip")
    private val marker get() = File(directory, "pending")
    val pending get() = marker.isFile
    fun stage(source: File, replacement: File) {
        check(!pending) { "Migration already pending" }
        check(directory.isDirectory || directory.mkdirs())
        copyDurable(source, original); copyDurable(replacement, candidate)
        check(BundleMigration.hash(source) == BundleMigration.hash(original))
        check(BundleMigration.hash(replacement) == BundleMigration.hash(candidate))
        FileOutputStream(marker).use { it.write("1".toByteArray()); it.fd.sync() }
    }
    fun publish(target: Target) {
        check(pending && original.isFile && candidate.isFile) { "Migration recovery files missing" }
        val current = fingerprint(target)
        if (current == BundleMigration.hash(candidate)) { clear(); return }
        if (current != BundleMigration.hash(original)) throw RecoveryRequired("升级曾被中断或文件已被外部修改；原笔记备份已保留，请选择恢复备份或保留当前文件")
        try {
            target.write(candidate)
            check(fingerprint(target) == BundleMigration.hash(candidate)) { "升级写入验证失败" }
            clear()
        } catch (e: Exception) {
            throw RecoveryRequired("升级写入未完成，原笔记备份已保留。请恢复备份后重试")
        }
    }
    /** Explicit user action; never silently overwrite a concurrently edited external file. */
    fun restore(target: Target) {
        check(pending && original.isFile) { "没有可恢复的旧笔记" }
        target.write(original)
        check(fingerprint(target) == BundleMigration.hash(original)) { "恢复验证失败，备份仍保留" }
        clear()
    }
    /** User chose the external file over the backup. Caller validates that file before this action. */
    fun keepCurrent() { clear() }
    private fun fingerprint(target: Target): String? = runCatching {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        target.read().use { input -> val b = ByteArray(65536); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
    private fun copyDurable(source: File, destination: File) {
        val tmp = File(directory, destination.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out -> source.inputStream().use { it.copyTo(out) }; out.fd.sync() }
            Files.move(tmp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { tmp.delete() }
    }
    private fun clear() {
        // Marker first: crashes during cleanup leave harmless retained bytes, never an incomplete transaction.
        check(marker.delete() || !marker.exists()) { "无法完成迁移记录" }
        original.delete(); candidate.delete()
    }
}
