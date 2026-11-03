package com.xnotes.canvas

import com.xnotes.core.FakeSurfaceFactory
import com.xnotes.core.model.Document
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.Rgba
import com.xnotes.ui.theme.monochromePalette
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPaletteTest {
    private val black = Rgba(0, 0, 0, 255)
    private val white = Rgba(255, 255, 255, 255)

    private fun state() = CanvasState(Document.blank(1), FakeSurfaceFactory(), monochromePalette(true)).apply {
        viewportW = 1000
        viewportH = 1400
        relayout()
    }

    @Test fun pageStyleChangesRefreshOverlayWithoutChangingPageOrTheme() {
        val state = state()
        assertEquals(white, state.palette.accent)
        state.document.pages[0].style = PageStyle(pageColor = white)
        assertEquals(black, state.palette.accent)
        state.document.pages[0].style = PageStyle(pageColor = black)
        assertEquals(white, state.palette.accent)
    }

    @Test fun documentStyleAndGlobalPaperChangesRefreshOverlay() {
        val state = state()
        assertEquals(white, state.palette.accent)
        state.pageColorOverride = white
        assertEquals(black, state.palette.accent)
        state.document.style = PageStyle(pageColor = black)
        assertEquals(white, state.palette.accent)
        state.document.style = PageStyle()
        assertEquals(black, state.palette.accent)
    }

    @Test fun pdfAlwaysUsesDarkOverlayEvenOnNightPaper() {
        val state = state()
        assertEquals(white, state.palette.accent)
        state.document.pages[0].pdfPage = 0
        assertEquals(black, state.palette.accent)
    }
}
