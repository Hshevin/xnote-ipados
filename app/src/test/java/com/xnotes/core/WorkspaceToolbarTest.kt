package com.xnotes.core

import com.xnotes.core.tools.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceToolbarTest {
    @Test fun workspaceKeepsOneOrderAndPartitionsDocumentControls() {
        val layout = workspaceToolbarLayout(ToolbarLayout.DEFAULT, ToolbarLayout.CANVAS_DEFAULT)
        val all = layout.visibleSections.flatMap { it.visibleEntries }.map { it.item }
        assertEquals(all.size, all.distinct().size)
        val common = layout.forDocumentControls(false).visibleSections.flatMap { it.visibleEntries }.map { it.item }
        val document = layout.forDocumentControls(true).visibleSections.flatMap { it.visibleEntries }.map { it.item }
        assertTrue(common.containsAll(listOf(ToolbarItem.TEXT_BOX, ToolbarItem.RULER, ToolbarItem.SCREENSHOT)))
        assertTrue(document.containsAll(listOf(ToolbarItem.ZOOM, ToolbarItem.FIT, ToolbarItem.ZOOM_LOCK)))
        assertTrue(ToolbarItem.PEN in common)
        assertTrue(ToolbarItem.ERASER in common)
        assertTrue(ToolbarItem.MARKUP in document)
        assertTrue(ToolbarItem.MARKUP !in common)
        assertTrue(common.intersect(document.toSet()).isEmpty())
        assertEquals(all.toSet(), (common + document).toSet())
    }
    @Test fun documentBarsOnlyContainApplicableControls() {
        val layout = workspaceToolbarLayout(ToolbarLayout.DEFAULT, ToolbarLayout.CANVAS_DEFAULT)
        fun items(canvas: Boolean, pdf: Boolean) = documentToolbarLayout(layout, canvas, pdf).visibleSections.flatMap { it.visibleEntries }.map { it.item }
        assertTrue(ToolbarItem.MARKUP in items(false, true))
        assertFalse(ToolbarItem.MARKUP in items(false, false))
        assertFalse(ToolbarItem.PAGE_NAV in items(true, false))
        assertTrue(ToolbarItem.MINIMAP in items(true, false))
        assertTrue(items(true, false).containsAll(listOf(ToolbarItem.ZOOM, ToolbarItem.FIT, ToolbarItem.ZOOM_LOCK)))
        assertFalse(ToolbarItem.MINIMAP in items(false, true))
    }

    @Test fun insertSpaceOnlyAppearsInPagedDocumentControls() {
        val layout = workspaceToolbarLayout(ToolbarLayout.DEFAULT, ToolbarLayout.CANVAS_DEFAULT)
        fun items(layout: ToolbarLayout) = layout.visibleSections.flatMap { it.visibleEntries }.map { it.item }
        assertFalse(ToolbarItem.INSERT_SPACE in items(layout.forDocumentControls(false)))
        assertTrue(ToolbarItem.INSERT_SPACE in items(documentToolbarLayout(layout, false, false)))
        assertTrue(ToolbarItem.INSERT_SPACE in items(documentToolbarLayout(layout, false, true)))
        assertFalse(ToolbarItem.INSERT_SPACE in items(documentToolbarLayout(layout, true, false)))
    }

}
