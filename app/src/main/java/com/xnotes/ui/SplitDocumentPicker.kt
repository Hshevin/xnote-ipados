package com.xnotes.ui

/** Folder ids from the granted root to a document's parent. Opaque provider ids fall back to root. */
internal fun splitFolderPath(root: String, document: String?): List<String> {
    val prefix = if (root.endsWith(':')) root else "$root/"
    if (document == null || !document.startsWith(prefix)) return listOf(root)
    val folders = document.removePrefix(prefix).split('/').dropLast(1)
    return folders.runningFold(root) { parent, name -> if (parent.endsWith(':')) "$parent$name" else "$parent/$name" }
}
