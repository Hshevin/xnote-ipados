package com.xnotes.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import android.content.ClipData
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xnotes.ai.AiMarkdown
import com.xnotes.core.text.Paragraph
import com.xnotes.core.text.ParaAlign
import com.xnotes.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Native Compose text: selectable, no WebView, HTML execution or remote image fetches. */
@Composable
internal fun AiMarkdownText(source: String) {
    val palette = LocalPalette.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    fun copy(text: String) { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("AI", text))) } }
    val blocks by produceState<List<AiMarkdown.Block>>(emptyList(), source) {
        value = withContext(Dispatchers.Default) {
            runCatching { AiMarkdown.parse(source) }.getOrElse {
                listOf(AiMarkdown.Block.Code(source, ""))
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s6)) {
        blocks.forEach { block ->
            when (block) {
                is AiMarkdown.Block.Prose -> {
                    val p = block.paragraph
                    if (p.rule) HorizontalDivider(color = palette.border.toComposeColor())
                    else Row(Modifier.fillMaxWidth().padding(start = Space.s12 * p.indent)) {
                        if (p.quote != null) Text("│ ", color = palette.textDim.toComposeColor())
                        if (block.marker.isNotEmpty()) Text("${block.marker} ", color = palette.text.toComposeColor(), fontSize = TextSize.bodyLarge)
                        MarkdownParagraph(p, Modifier.weight(1f))
                    }
                }
                is AiMarkdown.Block.Code -> Column(Modifier.fillMaxWidth().background(palette.surface.toComposeColor()).padding(Space.s8)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(block.language.ifBlank { "代码" }, fontSize = TextSize.caption, color = palette.textDim.toComposeColor())
                        TextButton(onClick = { copy(block.text) }) { Text("复制代码") }
                    }
                    Text(block.text, Modifier.horizontalScroll(rememberScrollState()), softWrap = false,
                        fontFamily = FontFamily.Monospace, fontSize = TextSize.body, color = palette.text.toComposeColor())
                }
                is AiMarkdown.Block.Table -> Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    block.rows.forEachIndexed { rowIndex, row ->
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            row.forEach { cell ->
                                Column(Modifier.width(180.dp).fillMaxHeight().border(1.dp, palette.border.toComposeColor())
                                    .background(if (rowIndex == 0) palette.surface.toComposeColor() else palette.menuBg.toComposeColor()).padding(Space.s8)) {
                                    cell.forEach { MarkdownParagraph(it, header = rowIndex == 0) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (source.isNotBlank()) TextButton(onClick = { copy(source) }) { Text("复制 Markdown") }
    }
}

@Composable
private fun MarkdownParagraph(p: Paragraph, modifier: Modifier = Modifier, header: Boolean = false) {
    val palette = LocalPalette.current
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf(false) }
    val text = buildAnnotatedString {
        p.runs.forEach { run ->
            val style = run.style
            withStyle(SpanStyle(
                fontWeight = if (style.bold || header) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = if (style.code || style.math) FontFamily.Monospace else FontFamily.Default,
                background = if (style.code || style.highlight != null) palette.surface.toComposeColor() else androidx.compose.ui.graphics.Color.Unspecified,
                textDecoration = TextDecoration.combine(buildList {
                    if (style.strike) add(TextDecoration.LineThrough)
                    if (style.underline || style.link != null) add(TextDecoration.Underline)
                }),
            )) {
                val url = style.link
                if (url == null) append(run.text)
                else withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = palette.text.toComposeColor()))) {
                    runCatching { uriHandler.openUri(url) }.onFailure { linkError = true }
                }) { append(run.text) }
            }
        }
    }
    Text(text, modifier, color = palette.text.toComposeColor(),
        fontSize = when (p.headingLevel) { 1 -> TextSize.display; 2 -> TextSize.headline; 3 -> TextSize.heading; 4, 5, 6 -> TextSize.title; else -> TextSize.bodyLarge },
        textAlign = when (p.align) { ParaAlign.CENTER -> TextAlign.Center; ParaAlign.RIGHT -> TextAlign.Right; else -> TextAlign.Start })
    if (linkError) AlertDialog(onDismissRequest = { linkError = false }, text = { Text("无法打开此链接") },
        confirmButton = { TextButton(onClick = { linkError = false }) { Text("确定") } })
}
