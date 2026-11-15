package de.mm20.launcher2.ui.media.docs.office

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Preview and editor of an Office document. */
@Composable
internal fun OfficeView(c: OfficeController, onConvert: () -> Unit, modifier: Modifier = Modifier) {
    val doc = c.doc
    Column(modifier.fillMaxSize()) {
        val note = when {
            doc is LegacyDoc -> null
            c.editing -> null
            doc.kind == OfficeKind.Slides -> stringResource(I18nR.string.od_slides_note)
            else -> stringResource(I18nR.string.od_layout_note)
        }
        if (doc is LegacyDoc) {
            Surface(Modifier.fillMaxWidth().padding(12.dp, 8.dp), color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp, 12.dp)) {
                    Text(stringResource(I18nR.string.od_legacy_readonly), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(stringResource(I18nR.string.od_convert_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = onConvert, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(I18nR.string.od_convert_action, doc.targetExt)) }
                }
            }
        } else if (note != null) {
            Text(note, Modifier.fillMaxWidth().padding(16.dp, 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val moreInFile = remember(doc, c.version) {
            when (doc.kind) {
                OfficeKind.Text -> { doc.blocks(); doc.truncated }
                OfficeKind.Sheet -> doc.sheets().any { it.truncated }
                OfficeKind.Slides -> false
            }
        }
        if (moreInFile) {
            Text(stringResource(I18nR.string.au_office_truncated), Modifier.fillMaxWidth().padding(16.dp, 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        Box(Modifier.weight(1f)) {
            when (doc.kind) {
                OfficeKind.Text -> TextDocView(c)
                OfficeKind.Sheet -> SheetView(c)
                OfficeKind.Slides -> SlidesView(c)
            }
        }
    }
}

// ---------------------------------------------------------------- images

private fun decodeImage(bytes: ByteArray?): Bitmap? {
    if (bytes == null) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1600) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun OfficeImage(doc: OfficeDoc, entry: String, modifier: Modifier = Modifier) {
    val bmp by produceState<Bitmap?>(null, doc, entry) {
        value = withContext(Dispatchers.IO) { runCatching { decodeImage(doc.imageBytes(entry)) }.getOrNull() }
    }
    bmp?.let {
        Image(it.asImageBitmap(), stringResource(I18nR.string.od_image), modifier.fillMaxWidth().heightIn(max = 420.dp).padding(vertical = 4.dp), contentScale = ContentScale.Fit)
    }
}

// ---------------------------------------------------------------- text documents

private fun flatten(blocks: List<OfficeBlock>): List<OfficeBlock.Para> = blocks.flatMap {
    when (it) { is OfficeBlock.Para -> listOf(it); is OfficeBlock.Table -> it.rows.flatten().flatten() }
}

private fun annotated(p: OfficeBlock.Para, forceBold: Boolean): AnnotatedString = buildAnnotatedString {
    for (r in p.runs) {
        val style = SpanStyle(
            fontWeight = if (r.bold || forceBold) FontWeight.Bold else null,
            fontStyle = if (r.italic) FontStyle.Italic else null,
            textDecoration = if (r.underline) TextDecoration.Underline else null,
        )
        withStyle(style) { append(r.text) }
    }
}

@Composable
private fun headingStyle(level: Int) = when (level) {
    -1 -> MaterialTheme.typography.headlineMedium
    1 -> MaterialTheme.typography.headlineSmall
    2 -> MaterialTheme.typography.titleLarge
    in 3..6 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.bodyLarge
}

@Composable
private fun TextDocView(c: OfficeController) {
    val doc = c.doc
    val blocks = remember(doc, c.version) { doc.blocks() }
    if (c.editing) {
        val paras = remember(blocks) { flatten(blocks) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(paras, key = { it.id }) { p -> ParaEditor(c, p) }
        }
        return
    }
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(blocks.size) { i ->
                when (val b = blocks[i]) {
                    is OfficeBlock.Para -> ParaView(doc, b, headingStyle(b.level))
                    is OfficeBlock.Table -> Column(Modifier.padding(vertical = 8.dp).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        b.rows.forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { cell ->
                                    Column(Modifier.weight(1f).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(6.dp)) {
                                        cell.forEach { p ->
                                            Text(annotated(p, false), style = MaterialTheme.typography.bodySmall)
                                            p.images.forEach { OfficeImage(doc, it) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParaView(doc: OfficeDoc, p: OfficeBlock.Para, style: androidx.compose.ui.text.TextStyle) {
    Column(Modifier.padding(start = (p.indent * 20).dp)) {
        if (p.text.isNotEmpty() || p.marker != null) {
            Row(Modifier.padding(top = if (p.level != 0) 12.dp else 4.dp, bottom = 4.dp)) {
                if (p.marker != null) Text(p.marker + " ", style = style)
                Text(annotated(p, p.level != 0), style = style, fontWeight = if (p.level != 0) FontWeight.SemiBold else null)
            }
        }
        p.images.forEach { OfficeImage(doc, it) }
    }
}

@Composable
private fun ParaEditor(c: OfficeController, p: OfficeBlock.Para) {
    var text by remember(c.version, p.id) { mutableStateOf(p.text) }
    val tableLabel: (@Composable () -> Unit)? = if (p.inTable) { { Text(stringResource(I18nR.string.od_table_cell)) } } else null
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                if (it == p.text) c.pendingParas.remove(p.id) else c.pendingParas[p.id] = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = tableLabel,
            textStyle = headingStyle(p.level).copy(fontWeight = if (p.level != 0) FontWeight.SemiBold else null),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (c.doc.kind == OfficeKind.Text) {
                FilterChip(selected = p.bold, onClick = { c.mutate { toggleBold(p.id) } }, label = { Text(stringResource(I18nR.string.od_bold), fontWeight = FontWeight.Bold) })
                FilterChip(selected = p.italic, onClick = { c.mutate { toggleItalic(p.id) } }, label = { Text(stringResource(I18nR.string.od_italic), fontStyle = FontStyle.Italic) })
            }
            TextButton(onClick = { c.mutate { addParagraphAfter(p.id) } }) { Text(stringResource(I18nR.string.od_add_paragraph)) }
            TextButton(onClick = { c.mutate { deleteParagraph(p.id) } }) { Text(stringResource(I18nR.string.od_delete_paragraph)) }
        }
        }
    }
}

// ---------------------------------------------------------------- spreadsheets

@Composable
private fun SheetView(c: OfficeController) {
    val sheets = remember(c.doc, c.version) { c.doc.sheets() }
    var selected by remember { mutableIntStateOf(0) }
    val shownRows = remember { mutableStateMapOf<Int, Int>() }
    var editCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    if (sheets.isEmpty()) {
        Text(stringResource(I18nR.string.od_nothing_to_show), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val index = selected.coerceIn(0, sheets.lastIndex)
    val sheet = sheets[index]
    val rows = maxOf(sheet.rows, shownRows[index] ?: 0, 1)
    val cols = maxOf(sheet.cols, 3)
    val cw = 112.dp
    val rw = 44.dp
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            sheets.forEachIndexed { i, s ->
                FilterChip(selected = i == index, onClick = { selected = i }, label = { Text(s.name.ifEmpty { stringResource(I18nR.string.od_sheet_n, i + 1) }, maxLines = 1) })
            }
            if (c.editing) TextButton(onClick = { shownRows[index] = rows + 1 }) { Text(stringResource(I18nR.string.od_add_row)) }
        }
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            LazyColumn(Modifier.width(rw + cw * cols)) {
                item {
                    Row {
                        Box(Modifier.width(rw).background(MaterialTheme.colorScheme.surfaceContainerHigh).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(6.dp))
                        for (col in 0 until cols) {
                            Box(Modifier.width(cw).background(MaterialTheme.colorScheme.surfaceContainerHigh).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(6.dp)) {
                                Text(colName(col), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                items(rows) { r ->
                    Row {
                        Box(Modifier.width(rw).heightIn(min = if (c.editing) 48.dp else 36.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(6.dp)) {
                            Text("${r + 1}", style = MaterialTheme.typography.labelMedium)
                        }
                        for (col in 0 until cols) {
                            Box(
                                Modifier.width(cw).heightIn(min = if (c.editing) 48.dp else 36.dp).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .then(if (c.editing) Modifier.clickable { editCell = r to col } else Modifier)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            ) {
                                Text(sheet.value(r, col), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    editCell?.let { (r, col) ->
        val formula = sheet.formula(r, col)
        val initial = if (formula != null && sheet.formulaEditable) formula else sheet.value(r, col)
        var text by remember(r, col, index) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { editCell = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.od_edit_cell, colName(col) + (r + 1)))  } },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, label = { Text(stringResource(I18nR.string.od_cell_value)) }, modifier = Modifier.fillMaxWidth())
                    if (formula != null && !sheet.formulaEditable) {
                        Text(stringResource(I18nR.string.od_formula_replaced, formula), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (sheet.formulaEditable) {
                        Text(stringResource(I18nR.string.od_cell_hint), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text != initial) c.mutate { setCell(index, r, col, text) }
                    editCell = null
                }) { Text(stringResource(I18nR.string.od_ok)) }
            },
            dismissButton = { TextButton(onClick = { editCell = null }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- presentations

@Composable
private fun SlidesView(c: OfficeController) {
    val doc = c.doc
    val slides = remember(doc, c.version) { doc.slides() }
    if (slides.isEmpty()) {
        Text(stringResource(I18nR.string.od_no_slides), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val pager = rememberPagerState { slides.size }
    val current = pager.currentPage.coerceIn(0, slides.lastIndex)
    LaunchedEffect(pager.currentPage) { if (c.editing) c.commit() }
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(I18nR.string.od_slide_of, current + 1, slides.size), Modifier.padding(16.dp, 4.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            slides.getOrNull(i)?.let { SlideCard(doc, it) }
        }
        if (c.editing) {
            val slide = slides[current]
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                slide.boxes.forEachIndexed { n, b ->
                    var t by remember(c.version, current, b.id) { mutableStateOf(b.text) }
                    OutlinedTextField(
                        value = t,
                        onValueChange = {
                            t = it
                            if (it == b.text) c.pendingBoxes.remove(current to b.id) else c.pendingBoxes[current to b.id] = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (b.title) stringResource(I18nR.string.od_title_box) else stringResource(I18nR.string.od_text_box_n, n + 1)) },
                    )
                }
                if (slides.size > 1) TextButton(onClick = { c.mutate { deleteSlide(current) } }) { Text(stringResource(I18nR.string.od_delete_slide)) }
            }
        }
    }
}

@Composable
private fun SlideCard(doc: OfficeDoc, slide: OfficeSlide) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        BoxWithConstraints {
            Surface(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = maxWidth / doc.slideAspect),
                shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                SelectionContainer {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        slide.boxes.filter { it.text.isNotBlank() }.sortedByDescending { it.title }.forEach { b ->
                            if (b.title) Text(b.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            else Text(b.text, style = MaterialTheme.typography.bodyMedium)
                        }
                        slide.images.forEach { OfficeImage(doc, it) }
                    }
                }
            }
        }
    }
}
