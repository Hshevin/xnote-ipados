package com.xnotes.ui

/** Shared-note ownership can move an open to the other editor while preserving visual slots. */
internal fun finishPaneHomeOpen(homes: Map<Pane, String?>, requested: Pane, opened: Pane): Map<Pane, String?> {
    if (requested == opened) return homes - opened
    return homes.mapKeys { (pane, _) -> if (pane == requested) opened else requested } - opened
}

/** Remove a browser, preserving the other browser if shared-note ownership changes its editor. */
internal fun closePaneHomeState(homes: Map<Pane, String?>, closed: Pane, retained: Pane?): Map<Pane, String?> {
    val remaining = homes - closed
    if (retained == null) return remaining
    return remaining.mapKeys { (pane, _) -> if (pane == retained) closed else pane }
}
