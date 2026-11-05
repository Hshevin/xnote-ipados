package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.R
import com.xnotes.core.model.DrawingLayers
import com.xnotes.core.model.Page
import com.xnotes.ui.theme.MinTouchTarget
import com.xnotes.ui.theme.Space
import com.xnotes.ui.theme.TextSize

@Composable
fun LayersButton(editor: Editor) {
    var page by remember(editor) { mutableStateOf<Page?>(null) }
    ToolbarIcon(XnotesIcons.sidebar, stringResource(R.string.layers_title)) {
        page = editor.state.document.pages.getOrNull(editor.pageIndex)
    }
    page?.let { target ->
        LayersPanel(target.layers, { edit -> editor.editLayers(target, edit) },
            { id -> target.items.removeAll { it.layerId == id }; target.layers.delete(id) },
            { id -> editor.moveSelectionToLayer(target, id) },
            editor.controller.layerSelection(target).isNotEmpty(), { page = null })
    }
}

@Composable
fun LayersButton(editor: InfiniteEditor) {
    var open by remember(editor) { mutableStateOf(false) }
    ToolbarIcon(XnotesIcons.sidebar, stringResource(R.string.layers_title)) { open = true }
    if (open) LayersPanel(editor.document.layers, editor::editLayers,
        { id -> editor.document.removeAll(editor.document.items.filter { it.layerId == id }); editor.document.layers.delete(id) },
        editor::moveSelectionToLayer, !editor.selection.isEmpty, { open = false })
}

@Composable
private fun LayersPanel(
    layers: DrawingLayers,
    edit: (() -> Unit) -> Boolean,
    delete: (String) -> Unit,
    moveSelection: (String) -> Boolean,
    hasSelection: Boolean,
    dismiss: () -> Unit,
) {
    var revision by remember { mutableIntStateOf(0) }
    var rename by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    fun change(action: () -> Unit) { if (edit(action)) revision++ }
    val entries = remember(revision) { layers.entries.asReversed() }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.layers_title), fontSize = TextSize.heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
                Text(stringResource(R.string.layers_scope), fontSize = TextSize.body)
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    itemsIndexed(entries, key = { _, layer -> layer.id }) { index, layer ->
                        val selected = layers.activeId == layer.id
                        val fg = if (selected) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface
                        val bg = if (selected) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surface
                        Column(Modifier.fillMaxWidth().background(bg).padding(Space.s4)) {
                            Text(layer.name.ifEmpty { stringResource(R.string.layers_default, entries.size - index) },
                                color = fg, fontSize = TextSize.bodyLarge,
                                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget)
                                    .clickable { change { layers.select(layer.id) } }.padding(Space.s12))
                            Row {
                                TextButton(onClick = { change { layers.update(layer.id) { it.copy(visible = !it.visible) } } }) {
                                    Text(stringResource(if (layer.visible) R.string.layers_hide else R.string.layers_show), color = fg, fontSize = TextSize.label)
                                }
                                TextButton(onClick = { change { layers.update(layer.id) { it.copy(locked = !it.locked) } } }) {
                                    Text(stringResource(if (layer.locked) R.string.layers_unlock else R.string.layers_lock), color = fg, fontSize = TextSize.label)
                                }
                                TextButton(onClick = { rename = layer.id; name = layer.name }) {
                                    Text(stringResource(R.string.layers_rename), color = fg, fontSize = TextSize.label)
                                }
                            }
                            Row {
                                TextButton(enabled = index > 0, onClick = { change { layers.move(layer.id, 1) } }) {
                                    Text(stringResource(R.string.layers_up), color = fg, fontSize = TextSize.label)
                                }
                                TextButton(enabled = index < entries.lastIndex, onClick = { change { layers.move(layer.id, -1) } }) {
                                    Text(stringResource(R.string.layers_down), color = fg, fontSize = TextSize.label)
                                }
                                TextButton(enabled = entries.size > 1, onClick = { deleting = layer.id }) {
                                    Text(stringResource(R.string.layers_delete), color = fg, fontSize = TextSize.label)
                                }
                            }
                            if (hasSelection) TextButton(enabled = layer.visible && !layer.locked,
                                onClick = { if (moveSelection(layer.id)) { revision++; dismiss() } }) {
                                Text(stringResource(R.string.layers_move_selection), color = fg, fontSize = TextSize.label)
                            }
                        }
                    }
                }
                TextButton(onClick = { change { layers.create("") } }) {
                    Text(stringResource(R.string.layers_add), fontSize = TextSize.body)
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.layers_close), fontSize = TextSize.body) } },
    )
    rename?.let { id ->
        AlertDialog(onDismissRequest = { rename = null }, title = { Text(stringResource(R.string.layers_rename), fontSize = TextSize.heading) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = TextSize.body)) },
            confirmButton = { TextButton(onClick = { change { layers.update(id) { it.copy(name = name.trim()) } }; rename = null }) {
                Text(stringResource(R.string.layers_save), fontSize = TextSize.body)
            } }, dismissButton = { TextButton(onClick = { rename = null }) { Text(stringResource(R.string.layers_cancel), fontSize = TextSize.body) } })
    }
    deleting?.let { id ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.layers_delete), fontSize = TextSize.heading) },
            text = { Text(stringResource(R.string.layers_delete_confirm), fontSize = TextSize.body) },
            confirmButton = { TextButton(onClick = { change { delete(id) }; deleting = null }) { Text(stringResource(R.string.layers_delete), fontSize = TextSize.body) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.layers_cancel), fontSize = TextSize.body) } })
    }
}
