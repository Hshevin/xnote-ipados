package com.xnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.unit.sp
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.pen.PenBinding
import com.xnotes.pen.PenBindingMode
import com.xnotes.pen.ProPenButtons

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProPenSettings(bindings: Map<Int, PenBinding>, onChange: (Map<Int, PenBinding>) -> Unit) {
    val actions = listOf(
        "none" to R.string.none, "pen" to R.string.tool_pen,
        "highlighter" to R.string.tool_highlighter, "eraser" to R.string.tool_eraser,
        "pan" to R.string.tool_pan, "select" to R.string.tool_select,
        "lasso" to R.string.tool_lasso, "undo" to R.string.undo, "redo" to R.string.redo,
    ).map { (id, label) -> id to stringResource(label) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.pro_pen_title))
        Text(stringResource(R.string.pro_pen_help), color = LocalPalette.current.textDim.toComposeColor(), fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProPenButtons.masks.forEachIndexed { index, mask ->
                val binding = bindings[mask] ?: PenBinding()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.pro_pen_button, index + 1), color = LocalPalette.current.textDim.toComposeColor(), fontSize = 12.sp)
                    OptionDropdown(actions, binding.action) { action ->
                        onChange(bindings + (mask to binding.copy(action = action,
                            mode = if (action in PenBinding.commands) PenBindingMode.SELECT else binding.mode)))
                    }
                    if (binding.action in PenBinding.tools) {
                        OptionDropdown(listOf(
                            "HOLD" to stringResource(R.string.pro_pen_hold),
                            "SELECT" to stringResource(R.string.pro_pen_select),
                        ), binding.mode.name) { mode ->
                            onChange(bindings + (mask to binding.copy(mode = PenBindingMode.valueOf(mode))))
                        }
                    } else if (binding.action in PenBinding.commands) {
                        Text(stringResource(R.string.pro_pen_once), color = LocalPalette.current.textDim.toComposeColor(), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
