package com.xnotes.platform

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Reads and atomically writes a single UTF-8 JSON file (PAL §11). Failure
 * tolerant: a missing/corrupt file yields an empty object; a failed write is
 * ignored by legacy callers; writeChecked reports failures to new settings UI.
 */
class JsonStore(private val file: File) {

    /** Checked settings update: a failed replacement must leave the previous file intact. */
    fun writeChecked(obj: JSONObject): Boolean {
        val tmp = File(file.parentFile, "${file.name}.pending")
        return try {
            file.parentFile?.mkdirs()
            java.io.FileOutputStream(tmp).use { stream ->
                stream.write(obj.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (_: Exception) {
            false
        } finally {
            tmp.delete()
        }
    }

    fun read(): JSONObject = try {
        if (file.exists()) JSONObject(file.readText(Charsets.UTF_8)) else JSONObject()
    } catch (_: Exception) {
        JSONObject()
    }

    fun write(obj: JSONObject) { writeChecked(obj) }

    companion object {
        /** The app's per-user settings file under the private config directory. */
        fun settings(context: Context): JsonStore =
            JsonStore(File(File(context.filesDir, "config"), "settings.json"))

        /** Per-note view state (zoom/scroll) for folder notes, under the private config directory. */
        fun viewStates(context: Context): JsonStore =
            JsonStore(File(File(context.filesDir, "config"), "view_states.json"))

        /** App-tracked creation times for explorer items, under the private config directory. */
        fun createdTimes(context: Context): JsonStore =
            JsonStore(File(File(context.filesDir, "config"), "created_times.json"))

        /** Where playback stood in each recording, under the private config directory. */
        fun audioPositions(context: Context): JsonStore =
            JsonStore(File(File(context.filesDir, "config"), "audio_positions.json"))

        /** Page counts and PDF flags the explorer read from notes, under the private config directory. */
        fun docMeta(context: Context): JsonStore =
            JsonStore(File(File(context.filesDir, "config"), "doc_meta.json"))
    }
}
