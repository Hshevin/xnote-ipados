package com.xnotes.core.model

import com.xnotes.core.geometry.Rect

/** Sidebar overview: hidden layers keep their preview, and every row uses the same coordinates. */
class LayerPreview(items: List<CanvasItem>, pageFrame: Rect? = null) {
    private val byLayer = items.groupBy { it.layerId ?: DrawingLayers.DEFAULT }
    val frame: Rect = (pageFrame ?: items.map { it.paintBounds() }.filter { it.valid() }
        .reduceOrNull(Rect::union) ?: Rect(0.0, 0.0, 1.0, 1.0)).let {
        if (it.valid()) it.copy(w = it.w.coerceAtLeast(1.0), h = it.h.coerceAtLeast(1.0))
        else Rect(0.0, 0.0, 1.0, 1.0)
    }
    fun itemsFor(id: String): List<CanvasItem> = byLayer[id].orEmpty()

    private fun Rect.valid() = x.isFinite() && y.isFinite() && w.isFinite() && h.isFinite() &&
        right.isFinite() && bottom.isFinite() && w >= 0 && h >= 0
}
