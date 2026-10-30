package com.xnotes.ui.theme

import com.xnotes.core.model.Rgba

/** UI colours only. PDF pixels and the stored colours of strokes are not transformed. */
fun monochromePalette(dark: Boolean): Palette {
    val black = Rgba(0, 0, 0, 255)
    val white = Rgba(255, 255, 255, 255)
    val bg = if (dark) black else white
    val fg = if (dark) white else black
    val m = MaterialColors(
        primary = fg, onPrimary = bg, primaryContainer = fg, onPrimaryContainer = bg,
        inversePrimary = bg,
        secondary = fg, onSecondary = bg, secondaryContainer = fg, onSecondaryContainer = bg,
        tertiary = fg, onTertiary = bg, tertiaryContainer = fg, onTertiaryContainer = bg,
        background = bg, onBackground = fg, surface = bg, onSurface = fg,
        surfaceVariant = bg, onSurfaceVariant = fg, surfaceTint = bg,
        inverseSurface = fg, inverseOnSurface = bg,
        error = fg, onError = bg, errorContainer = fg, onErrorContainer = bg,
        outline = fg, outlineVariant = fg, scrim = black,
        surfaceBright = bg, surfaceDim = bg, surfaceContainerLowest = bg,
        surfaceContainerLow = bg, surfaceContainer = bg, surfaceContainerHigh = bg,
        surfaceContainerHighest = bg,
        primaryFixed = fg, primaryFixedDim = fg, onPrimaryFixed = bg, onPrimaryFixedVariant = bg,
        secondaryFixed = fg, secondaryFixedDim = fg, onSecondaryFixed = bg, onSecondaryFixedVariant = bg,
        tertiaryFixed = fg, tertiaryFixedDim = fg, onTertiaryFixed = bg, onTertiaryFixedVariant = bg,
    )
    return Palette(bg, bg, bg, fg, fg, fg, fg, fg, bg, bg, bg, dark, m)
}
