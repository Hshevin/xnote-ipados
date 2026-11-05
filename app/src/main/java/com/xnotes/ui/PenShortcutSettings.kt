package com.xnotes.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.xnotes.R
import com.xnotes.pen.*
import com.xnotes.ui.theme.*

@Composable
fun PenShortcutSettings(shortcuts: List<PenShortcut>, onChange: (List<PenShortcut>) -> Boolean) {
    var recording by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<List<PenTrigger>?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    val defaultName = stringResource(R.string.pen_shortcut_default, shortcuts.size + 1)
    var name by remember { mutableStateOf("") }
    var binding by remember { mutableStateOf(PenBinding()) }
    val actions = listOf(
        "none" to R.string.none, "pen" to R.string.tool_pen,
        "highlighter" to R.string.tool_highlighter, "eraser" to R.string.tool_eraser,
        "pan" to R.string.tool_pan, "select" to R.string.tool_select,
        "lasso" to R.string.tool_lasso, "undo" to R.string.undo, "redo" to R.string.redo,
    ).map { (id, label) -> id to stringResource(label) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
        FieldLabel(stringResource(R.string.pen_shortcuts_title))
        Text(stringResource(R.string.pen_shortcuts_help), fontSize = TextSize.body)
        if (shortcuts.isEmpty()) Text(stringResource(R.string.pen_shortcuts_empty), fontSize = TextSize.label)
        shortcuts.forEachIndexed { index, shortcut ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                FieldLabel(shortcut.name)
                if (shortcut.primary.kind == PenTrigger.Kind.MOTION) {
                    Text(stringResource(R.string.pen_shortcut_near_screen), fontSize = TextSize.label)
                }
                BindingFields(actions, shortcut.binding) { next ->
                    onChange(shortcuts.toMutableList().apply { set(index, shortcut.copy(binding = next)) })
                }
                TextButton(modifier = Modifier.heightIn(min = MinTouchTarget), onClick = {
                    onChange(shortcuts.filterIndexed { i, _ -> i != index })
                }) { Text(stringResource(R.string.pen_shortcut_delete), fontSize = TextSize.body) }
            }
        }
        message?.let { Text(stringResource(it), fontSize = TextSize.body) }
        if (recording) {
            PenRecorder(onRecorded = { triggers ->
                recording = false
                if (shortcuts.any { s -> s.triggers.any { it in triggers } }) message = R.string.pen_record_duplicate
                else { pending = triggers; name = defaultName; binding = PenBinding() }
            }, onFailed = { recording = false; message = R.string.pen_record_failed })
            TextButton(modifier = Modifier.heightIn(min = MinTouchTarget), onClick = { recording = false }) {
                Text(stringResource(R.string.cancel), fontSize = TextSize.body)
            }
        } else if (pending != null) {
            Text(stringResource(R.string.pen_record_success), fontSize = TextSize.body)
            if (pending!!.any { it.kind == PenTrigger.Kind.MOTION }) {
                Text(stringResource(R.string.pen_shortcut_near_screen), fontSize = TextSize.label)
            }
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 80) name = it },
                label = { Text(stringResource(R.string.pen_shortcut_name), fontSize = TextSize.label) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = TextSize.body), modifier = Modifier.fillMaxWidth())
            BindingFields(actions, binding) { binding = it }
            TextButton(modifier = Modifier.heightIn(min = MinTouchTarget), enabled = name.isNotBlank(), onClick = {
                val next = shortcuts + PenShortcut(name.trim(), pending!!, binding)
                if (onChange(next)) { pending = null; message = null }
                else message = R.string.pro_pen_save_failed
            }) { Text(stringResource(R.string.save), fontSize = TextSize.body) }
            TextButton(modifier = Modifier.heightIn(min = MinTouchTarget), onClick = { pending = null }) {
                Text(stringResource(R.string.cancel), fontSize = TextSize.body)
            }
        } else {
            TextButton(modifier = Modifier.heightIn(min = MinTouchTarget), onClick = {
                if (shortcuts.size >= PenShortcut.MAX_SHORTCUTS) message = R.string.pen_shortcuts_limit
                else { message = null; recording = true }
            }) { Text(stringResource(R.string.pen_record_add), fontSize = TextSize.body) }
        }
    }
}

@Composable
private fun BindingFields(actions: List<Pair<String, String>>, binding: PenBinding, onChange: (PenBinding) -> Unit) {
    OptionDropdown(actions, binding.action, Modifier.sizeIn(minWidth = MinTouchTarget, minHeight = MinTouchTarget)) { action ->
        onChange(binding.copy(action = action,
            mode = if (action in PenBinding.commands) PenBindingMode.SELECT else binding.mode))
    }
    if (binding.action in PenBinding.tools) {
        OptionDropdown(listOf("HOLD" to stringResource(R.string.pro_pen_hold), "SELECT" to stringResource(R.string.pro_pen_select)), binding.mode.name, Modifier.sizeIn(minWidth = MinTouchTarget, minHeight = MinTouchTarget)) {
            onChange(binding.copy(mode = PenBindingMode.valueOf(it)))
        }
    } else if (binding.action in PenBinding.commands) {
        Text(stringResource(R.string.pro_pen_once), fontSize = TextSize.label)
    }
}
