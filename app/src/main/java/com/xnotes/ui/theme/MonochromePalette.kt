package com.xnotes.ui.theme

import com.xnotes.core.model.Rgba
import kotlin.math.roundToInt

/**
 * Neutral steps between the background and the foreground, as the share of foreground mixed in.
 * Every step is opaque and achromatic (r == g == b): hierarchy comes from tone, never from hue.
 * Dark steps sit a little further out because equal mixes read weaker on black.
 */
internal object MonoTone {
    const val BACKDROP_LIGHT = 0.04
    const val BACKDROP_DARK = 0.06
    const val FILL_LIGHT = 0.06
    const val FILL_DARK = 0.10
    const val FILL_HI_LIGHT = 0.10
    const val FILL_HI_DARK = 0.15
    const val HAIRLINE_LIGHT = 0.14
    const val HAIRLINE_DARK = 0.20
    const val OUTLINE = 0.54
    const val SECONDARY = 0.62
}

/** [fg] mixed into [bg] by [share], kept grey. */
internal fun monoTone(bg: Rgba, fg: Rgba, share: Double): Rgba {
    val v = (bg.r + (fg.r - bg.r) * share).roundToInt().coerceIn(0, 255)
    return Rgba(v, v, v, 255)
}

/**
 * UI colours only. PDF pixels and the stored colours of strokes are not transformed.
 * Paper, panels and menus stay pure white (light) or pure black (dark); the backdrop behind pages,
 * fills, hairlines and secondary text step towards the foreground so the chrome keeps its levels.
 * Selection is the full inversion.
 */
fun monochromePalette(dark: Boolean): Palette {
    val black = Rgba(0, 0, 0, 255)
    val white = Rgba(255, 255, 255, 255)
    val bg = if (dark) black else white
    val fg = if (dark) white else black
    fun tone(light: Double, darkShare: Double = light) = monoTone(bg, fg, if (dark) darkShare else light)
    val backdrop = tone(MonoTone.BACKDROP_LIGHT, MonoTone.BACKDROP_DARK)
    val fill = tone(MonoTone.FILL_LIGHT, MonoTone.FILL_DARK)
    val fillHi = tone(MonoTone.FILL_HI_LIGHT, MonoTone.FILL_HI_DARK)
    val hairline = tone(MonoTone.HAIRLINE_LIGHT, MonoTone.HAIRLINE_DARK)
    val outline = tone(MonoTone.OUTLINE)
    val secondary = tone(MonoTone.SECONDARY)
    val m = MaterialColors(
        primary = fg, onPrimary = bg, primaryContainer = fg, onPrimaryContainer = bg,
        inversePrimary = bg,
        secondary = fg, onSecondary = bg, secondaryContainer = fg, onSecondaryContainer = bg,
        tertiary = fg, onTertiary = bg, tertiaryContainer = fg, onTertiaryContainer = bg,
        background = bg, onBackground = fg, surface = bg, onSurface = fg,
        surfaceVariant = fill, onSurfaceVariant = secondary, surfaceTint = bg,
        inverseSurface = fg, inverseOnSurface = bg,
        error = fg, onError = bg, errorContainer = fg, onErrorContainer = bg,
        outline = outline, outlineVariant = hairline, scrim = black,
        surfaceBright = bg, surfaceDim = fill, surfaceContainerLowest = bg,
        surfaceContainerLow = bg, surfaceContainer = bg, surfaceContainerHigh = fill,
        surfaceContainerHighest = fillHi,
        primaryFixed = fg, primaryFixedDim = fg, onPrimaryFixed = bg, onPrimaryFixedVariant = bg,
        secondaryFixed = fg, secondaryFixedDim = fg, onSecondaryFixed = bg, onSecondaryFixedVariant = bg,
        tertiaryFixed = fg, tertiaryFixedDim = fg, onTertiaryFixed = bg, onTertiaryFixedVariant = bg,
    )
    return Palette(
        bg = backdrop,
        panel = bg,
        paper = bg,
        paperBorder = hairline,
        accent = fg,
        border = hairline,
        text = fg,
        textDim = secondary,
        surface = fill,
        surfaceHi = fillHi,
        menuBg = bg,
        isDark = dark,
        materialColors = m,
    )
}
