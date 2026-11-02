package com.xnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PaneHomeStateTest {
    @Test fun closingOneBrowserLeavesTheOtherBrowser() {
        assertEquals(mapOf(Pane.PRIMARY to null), closePaneHomeState(
            mapOf(Pane.PRIMARY to null, Pane.SECONDARY to "folder"), Pane.SECONDARY, null))
    }

    @Test fun closingSharedOwnerKeepsOtherBrowserOnRetainedEditor() {
        assertEquals(mapOf(Pane.PRIMARY to "other-folder"), closePaneHomeState(
            mapOf(Pane.PRIMARY to "owner-folder", Pane.SECONDARY to "other-folder"), Pane.PRIMARY, Pane.SECONDARY))
    }

    @Test fun closingLastBrowserLeavesNoHiddenHome() {
        assertEquals(emptyMap<Pane, String?>(), closePaneHomeState(
            mapOf(Pane.PRIMARY to "folder"), Pane.PRIMARY, Pane.SECONDARY))
    }

    @Test fun openingOneOfTwoHomesKeepsTheOtherHome() {
        assertEquals(mapOf(Pane.SECONDARY to null), finishPaneHomeOpen(
            mapOf(Pane.PRIMARY to null, Pane.SECONDARY to null), Pane.PRIMARY, Pane.PRIMARY))
    }

    @Test fun sharedOwnerRedirectKeepsTheOtherVisualSlotBrowsing() {
        assertEquals(mapOf(Pane.PRIMARY to "other-folder"), finishPaneHomeOpen(
            mapOf(Pane.PRIMARY to "selected-folder", Pane.SECONDARY to "other-folder"), Pane.PRIMARY, Pane.SECONDARY))
    }

    @Test fun replacingSharedOwnerCompletesTheOnlyHome() {
        assertEquals(emptyMap<Pane, String?>(), finishPaneHomeOpen(
            mapOf(Pane.PRIMARY to null), Pane.PRIMARY, Pane.SECONDARY))
    }
}
