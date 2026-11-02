package com.xnotes.settings

import com.xnotes.platform.JsonStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspacePersistenceTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun switchingRootsAndRestartingPreservesNotesAndOtherSettings() {
        val app = tmp.newFolder("app")
        val external = tmp.newFolder("external")
        val appNote = File(app, "one.xnote").apply { writeText("app note") }
        val externalNote = File(external, "two.xnote").apply { writeText("external note") }
        val path = File(tmp.root, "settings.json")
        val original = Settings(browseRoot = app.toURI().toString(), startOnHome = true)
        assertTrue(JsonStore(path).writeChecked(original.toJson()))
        for (root in listOf(external, app, external, app)) {
            val previous = Settings.fromJson(JsonStore(path).read()!!)
            assertTrue(JsonStore(path).writeChecked(previous.copy(browseRoot = root.toURI().toString()).toJson()))
            val restarted = Settings.fromJson(JsonStore(path).read()!!)
            assertEquals(root.toURI().toString(), restarted.browseRoot)
            assertTrue(restarted.startOnHome)
            assertEquals("app note", appNote.readText())
            assertEquals("external note", externalNote.readText())
        }
    }

    @Test fun failedReplacementLeavesPreviousWorkspaceRecoverable() {
        val path = File(tmp.root, "settings.json")
        val original = Settings(browseRoot = "content://notes/tree/original")
        assertTrue(JsonStore(path).writeChecked(original.toJson()))
        // A non-empty pending directory prevents the atomic store from staging the new file.
        File(tmp.root, "settings.json.pending").mkdir()
        File(tmp.root, "settings.json.pending/keep").writeText("occupied")
        assertFalse(JsonStore(path).writeChecked(original.copy(browseRoot = "content://notes/tree/other").toJson()))
        assertEquals(original.browseRoot, Settings.fromJson(JsonStore(path).read()!!).browseRoot)
    }
}
