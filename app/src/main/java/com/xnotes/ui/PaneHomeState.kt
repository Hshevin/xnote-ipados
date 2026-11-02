package com.xnotes.ui

/** Shared-note ownership can move an open to the other editor while preserving visual slots. */
internal fun finishPaneHomeOpen(homes: Map<Pane, String?>, requested: Pane, opened: Pane): Map<Pane, String?> {
    if (requested == opened) return homes - opened
    return homes.mapKeys { (pane, _) -> if (pane == requested) opened else requested } - opened
}
