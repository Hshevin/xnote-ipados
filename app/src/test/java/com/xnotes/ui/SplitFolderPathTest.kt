package com.xnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitFolderPathTest {
    @Test fun opensAtDocumentParentWithEveryAncestorAvailable() {
        assertEquals(listOf("primary:Notes", "primary:Notes/Books", "primary:Notes/Books/Physics"),
            splitFolderPath("primary:Notes", "primary:Notes/Books/Physics/chapter.xnote"))
    }
    @Test fun volumeRootUsesProviderColonWithoutExtraSlash() {
        assertEquals(listOf("primary:", "primary:Books", "primary:Books/Physics"),
            splitFolderPath("primary:", "primary:Books/Physics/chapter.xnote"))
    }
    @Test fun rootFilesAndUnrelatedOrOpaqueIdsStartAtRoot() {
        for (id in listOf("primary:Notes/a.xcanvas", "primary:Notes2/a.xnote", "opaque-id", null)) {
            assertEquals(listOf("primary:Notes"), splitFolderPath("primary:Notes", id))
        }
    }
}
