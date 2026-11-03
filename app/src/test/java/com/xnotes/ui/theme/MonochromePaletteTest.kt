package com.xnotes.ui.theme

import com.xnotes.core.model.Rgba
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class MonochromePaletteTest {
    private val white = Rgba(255, 255, 255, 255)
    private val black = Rgba(0, 0, 0, 255)

    private fun Palette.all(): List<Rgba> = listOf(bg, panel, paper, paperBorder, accent, border, text, textDim, surface, surfaceHi, menuBg)

    private fun luminance(c: Rgba): Double {
        fun lin(v: Int) = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin(c.r) + 0.7152 * lin(c.g) + 0.0722 * lin(c.b)
    }

    private fun contrast(a: Rgba, b: Rgba): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    @Test fun lightAndDarkUseExactOppositeColours() {
        val light = monochromePalette(false)
        val dark = monochromePalette(true)
        assertEquals(white, light.paper)
        assertEquals(black, dark.paper)
        assertEquals(white, light.panel)
        assertEquals(black, dark.panel)
        assertEquals(light.paper, dark.text)
        assertEquals(dark.paper, light.text)
        assertEquals(light.text, light.selectionBackground)
        assertEquals(light.paper, light.selectionForeground)
        assertEquals(dark.text, dark.selectionBackground)
        assertEquals(dark.paper, dark.selectionForeground)
    }

    @Test fun everyColourIsOpaqueGrey() {
        for (dark in listOf(false, true)) {
            for (c in monochromePalette(dark).all()) {
                assertTrue("$c not grey", c.r == c.g && c.g == c.b)
                assertEquals(255, c.a)
            }
        }
    }

    @Test fun chromeKeepsVisibleLevels() {
        for (dark in listOf(false, true)) {
            val p = monochromePalette(dark)
            assertNotEquals(p.text, p.textDim)
            assertNotEquals(p.text, p.border)
            assertNotEquals(p.paper, p.bg)
            assertNotEquals(p.surface, p.surfaceHi)
            // Secondary text stays readable; hairlines stay quieter than text yet visible.
            assertTrue(contrast(p.textDim, p.paper) >= 4.5)
            assertTrue(contrast(p.materialColors.outline, p.paper) >= 3.0)
            assertTrue(contrast(p.border, p.paper) < contrast(p.textDim, p.paper))
            assertTrue(contrast(p.border, p.paper) > 1.1)
        }
    }

    @Test fun nightInkStillSeesBlackPaper() {
        assertTrue(com.xnotes.core.pal.NightInk.enabled(true, monochromePalette(true).paper))
    }
}
