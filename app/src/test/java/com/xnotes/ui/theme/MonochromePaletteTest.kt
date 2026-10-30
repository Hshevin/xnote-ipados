package com.xnotes.ui.theme

import com.xnotes.core.model.Rgba
import org.junit.Assert.*
import org.junit.Test

class MonochromePaletteTest {
    @Test fun lightAndDarkUseExactOppositeColours() {
        val light = monochromePalette(false)
        val dark = monochromePalette(true)
        assertEquals(Rgba(255, 255, 255, 255), light.bg)
        assertEquals(Rgba(0, 0, 0, 255), dark.bg)
        assertEquals(light.bg, dark.text)
        assertEquals(dark.bg, light.text)
        assertEquals(light.text, light.selectionBackground)
        assertEquals(light.bg, light.selectionForeground)
        assertEquals(dark.text, dark.selectionBackground)
        assertEquals(dark.bg, dark.selectionForeground)
    }
}
