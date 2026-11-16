/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.drawing.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.drawing.DrawingEditorState
import com.vitorpamplona.amethyst.commons.drawing.DrawingTool
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drawing_color_black
import com.vitorpamplona.amethyst.commons.resources.drawing_color_blue
import com.vitorpamplona.amethyst.commons.resources.drawing_color_green
import com.vitorpamplona.amethyst.commons.resources.drawing_color_orange
import com.vitorpamplona.amethyst.commons.resources.drawing_color_purple
import com.vitorpamplona.amethyst.commons.resources.drawing_color_red
import com.vitorpamplona.amethyst.commons.resources.drawing_color_white
import com.vitorpamplona.amethyst.commons.resources.drawing_color_yellow
import com.vitorpamplona.amethyst.commons.resources.drawing_mirror
import com.vitorpamplona.amethyst.commons.resources.drawing_redo
import com.vitorpamplona.amethyst.commons.resources.drawing_size
import com.vitorpamplona.amethyst.commons.resources.drawing_tool_brush
import com.vitorpamplona.amethyst.commons.resources.drawing_tool_eraser
import com.vitorpamplona.amethyst.commons.resources.drawing_tool_pencil
import com.vitorpamplona.amethyst.commons.resources.drawing_tool_spray
import com.vitorpamplona.amethyst.commons.resources.drawing_undo
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.stringRes
import org.jetbrains.compose.resources.StringResource

/**
 * The floating pen dock under the paper: a tray of pens tipped with the current
 * ink, the selected one lifted out of the tray, then mirror and undo/redo within
 * thumb reach, and below them the inks and the pen size.
 */
@Composable
fun DrawingDock(
    state: DrawingEditorState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = DOCK_MAX_WIDTH),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp)) {
            Row(
                // The pens stand in a tray: their lower ends are cut off by its edge.
                Modifier.fillMaxWidth().height(TRAY_HEIGHT).clipToBounds(),
                verticalAlignment = Alignment.Bottom,
            ) {
                PEN_TOOLS.forEach { (tool, label) -> PenInTray(state, tool, label) }
                Spacer(Modifier.weight(1f))
                Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val mirror = stringRes(Res.string.drawing_mirror)
                    HoverTooltip(mirror) {
                        IconToggleButton(checked = state.mirror, onCheckedChange = { state.mirror = it }) {
                            Icon(MaterialSymbols.Flip, mirror, tint = toggleTint(state.mirror))
                        }
                    }
                    val undo = stringRes(Res.string.drawing_undo)
                    HoverTooltip(undo) {
                        IconButton(onClick = state::undo, enabled = state.canUndo) { Icon(MaterialSymbols.AutoMirrored.Undo, undo) }
                    }
                    val redo = stringRes(Res.string.drawing_redo)
                    HoverTooltip(redo) {
                        IconButton(onClick = state::redo, enabled = state.canRedo) { Icon(MaterialSymbols.AutoMirrored.Redo, redo) }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    INKS.forEach { (argb, label) -> InkDot(state, argb, label) }
                }
                Spacer(Modifier.width(8.dp))
                SizeButton(state)
            }
        }
    }
}

@Composable
private fun toggleTint(selected: Boolean) = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

/** One pen in the tray. The selected one springs up out of it. */
@Composable
private fun PenInTray(
    state: DrawingEditorState,
    tool: DrawingTool,
    label: StringResource,
) {
    val text = stringRes(label)
    val selected = state.tool == tool
    val sink by animateDpAsState(
        targetValue = if (selected) 0.dp else PEN_SINK,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
    )
    val ink = Color(state.color)
    val outline = MaterialTheme.colorScheme.outline
    HoverTooltip(text) {
        Box(
            Modifier
                .width(PEN_WIDTH)
                .height(TRAY_HEIGHT)
                .selectable(selected = selected, role = Role.RadioButton, onClick = { state.tool = tool })
                .semantics { contentDescription = text },
        ) {
            // Read in the layout phase, so the spring moves the pen without recomposing it every frame.
            Canvas(Modifier.width(PEN_WIDTH).height(PEN_HEIGHT).offset { IntOffset(0, (TRAY_HEIGHT - PEN_HEIGHT + sink + PEN_OVERHANG).roundToPx()) }) {
                when (tool) {
                    DrawingTool.BRUSH -> drawBrushPen(ink, outline)
                    DrawingTool.PENCIL -> drawPencil(ink, outline)
                    DrawingTool.SPRAY -> drawSprayCan(ink, outline)
                    DrawingTool.ERASER -> drawEraser(outline)
                }
            }
        }
    }
}

// The pen drawings: a flat, slightly playful set, each tipped with the current ink.

private fun DrawScope.drawBrushPen(
    ink: Color,
    outline: Color,
) {
    val w = size.width
    val h = size.height
    drawRoundRect(BRUSH_HANDLE, Offset(w * 0.32f, h * 0.42f), Size(w * 0.36f, h * 0.6f), CornerRadius(w * 0.1f))
    drawRoundRect(FERRULE, Offset(w * 0.3f, h * 0.3f), Size(w * 0.4f, h * 0.14f), CornerRadius(w * 0.04f))
    val tip =
        Path().apply {
            moveTo(w * 0.32f, h * 0.31f)
            cubicTo(w * 0.3f, h * 0.18f, w * 0.44f, h * 0.06f, w * 0.5f, h * 0.02f)
            cubicTo(w * 0.56f, h * 0.06f, w * 0.7f, h * 0.18f, w * 0.68f, h * 0.31f)
            close()
        }
    drawPath(tip, ink)
    drawPath(tip, outline.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
}

private fun DrawScope.drawPencil(
    ink: Color,
    outline: Color,
) {
    val w = size.width
    val h = size.height
    drawRect(PENCIL_BODY, Offset(w * 0.3f, h * 0.36f), Size(w * 0.4f, h * 0.66f))
    drawRect(PENCIL_STRIPE, Offset(w * 0.46f, h * 0.36f), Size(w * 0.08f, h * 0.66f))
    val wood =
        Path().apply {
            moveTo(w * 0.3f, h * 0.36f)
            lineTo(w * 0.5f, h * 0.06f)
            lineTo(w * 0.7f, h * 0.36f)
            close()
        }
    drawPath(wood, PENCIL_WOOD)
    val lead =
        Path().apply {
            moveTo(w * 0.42f, h * 0.18f)
            lineTo(w * 0.5f, h * 0.06f)
            lineTo(w * 0.58f, h * 0.18f)
            close()
        }
    drawPath(lead, ink)
    drawPath(lead, outline.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
}

private fun DrawScope.drawSprayCan(
    ink: Color,
    outline: Color,
) {
    val w = size.width
    val h = size.height
    drawRoundRect(ink, Offset(w * 0.22f, h * 0.28f), Size(w * 0.56f, h * 0.74f), CornerRadius(w * 0.14f))
    drawRoundRect(outline.copy(alpha = 0.6f), Offset(w * 0.22f, h * 0.28f), Size(w * 0.56f, h * 0.74f), CornerRadius(w * 0.14f), style = Stroke(1.dp.toPx()))
    drawRect(CAN_LABEL, Offset(w * 0.22f, h * 0.48f), Size(w * 0.56f, h * 0.16f))
    drawRoundRect(CAN_CAP, Offset(w * 0.34f, h * 0.14f), Size(w * 0.32f, h * 0.16f), CornerRadius(w * 0.06f))
    drawRect(CAN_CAP, Offset(w * 0.44f, h * 0.08f), Size(w * 0.12f, h * 0.07f))
    // A puff of paint leaving the nozzle.
    drawCircle(ink.copy(alpha = 0.7f), w * 0.045f, Offset(w * 0.68f, h * 0.06f))
    drawCircle(ink.copy(alpha = 0.5f), w * 0.035f, Offset(w * 0.8f, h * 0.02f))
    drawCircle(ink.copy(alpha = 0.4f), w * 0.03f, Offset(w * 0.78f, h * 0.11f))
}

private fun DrawScope.drawEraser(outline: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(ERASER_SLEEVE, Offset(w * 0.24f, h * 0.4f), Size(w * 0.52f, h * 0.62f), CornerRadius(w * 0.08f))
    drawRoundRect(ERASER_RUBBER, Offset(w * 0.24f, h * 0.12f), Size(w * 0.52f, h * 0.32f), CornerRadius(w * 0.14f))
    drawRoundRect(outline.copy(alpha = 0.3f), Offset(w * 0.24f, h * 0.12f), Size(w * 0.52f, h * 0.32f), CornerRadius(w * 0.14f), style = Stroke(1.dp.toPx()))
}

/** An ink: a dot that grows a ring when it is the one on the pen. */
@Composable
private fun InkDot(
    state: DrawingEditorState,
    argb: Int,
    label: StringResource,
) {
    val description = stringRes(label)
    val selected = state.color == argb && state.tool != DrawingTool.ERASER
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    val ring = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(INK_TARGET)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { state.pickColor(argb) })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(INK_DOT).scale(scale)) {
            val r = size.minDimension / 2f
            if (selected) drawCircle(ring, r, style = Stroke(2.5.dp.toPx()))
            val inner = if (selected) r - 5.dp.toPx() else r - 2.dp.toPx()
            drawCircle(Color(argb), inner)
            drawCircle(Color.Black.copy(alpha = 0.15f), inner, style = Stroke(1.dp.toPx()))
        }
    }
}

/** Shows the pen at its size and colour; opens a slider with a live stroke preview. */
@Composable
private fun SizeButton(state: DrawingEditorState) {
    var open by remember { mutableStateOf(false) }
    val label = stringRes(Res.string.drawing_size)
    val ink = if (state.tool == DrawingTool.ERASER) MaterialTheme.colorScheme.onSurfaceVariant else Color(state.color)
    Box {
        HoverTooltip(label) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .selectable(selected = open, role = Role.Button, onClick = { open = true })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(30.dp)) {
                    val r = (state.width * PREVIEW_UNIT.toPx()).coerceIn(2.dp.toPx(), size.minDimension / 2f)
                    drawCircle(ink, r)
                    drawCircle(Color.Black.copy(alpha = 0.15f), r, style = Stroke(1.dp.toPx()))
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.width(240.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                    val stroke = (state.width * PREVIEW_UNIT.toPx() * 2f).coerceAtLeast(1.5.dp.toPx())
                    val path =
                        Path().apply {
                            moveTo(stroke, size.height * 0.65f)
                            cubicTo(size.width * 0.3f, size.height * 0.05f, size.width * 0.6f, size.height * 1.0f, size.width - stroke, size.height * 0.35f)
                        }
                    drawPath(path, ink, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Slider(
                    value = state.width,
                    onValueChange = { state.width = it },
                    valueRange = DrawingEditorState.MIN_WIDTH..DrawingEditorState.MAX_WIDTH,
                )
            }
        }
    }
}

private val PEN_TOOLS =
    listOf(
        DrawingTool.BRUSH to Res.string.drawing_tool_brush,
        DrawingTool.PENCIL to Res.string.drawing_tool_pencil,
        DrawingTool.SPRAY to Res.string.drawing_tool_spray,
        DrawingTool.ERASER to Res.string.drawing_tool_eraser,
    )

private val INKS =
    listOf(
        DrawingEditorState.DEFAULT_COLOR to Res.string.drawing_color_black,
        DrawingEditorState.WHITE to Res.string.drawing_color_white,
        0xFFE5484D.toInt() to Res.string.drawing_color_red,
        0xFFF5A524.toInt() to Res.string.drawing_color_orange,
        0xFFF7D54A.toInt() to Res.string.drawing_color_yellow,
        0xFF30A46C.toInt() to Res.string.drawing_color_green,
        0xFF2F6FDF.toInt() to Res.string.drawing_color_blue,
        0xFF8E4EC6.toInt() to Res.string.drawing_color_purple,
    )

private val BRUSH_HANDLE = Color(0xFF9C7BD1)
private val FERRULE = Color(0xFFC3C7D0)
private val PENCIL_BODY = Color(0xFFF2C14E)
private val PENCIL_STRIPE = Color(0xFFE0AC37)
private val PENCIL_WOOD = Color(0xFFEBCB9F)
private val CAN_LABEL = Color(0x66FFFFFF)
private val CAN_CAP = Color(0xFF9AA0AB)
private val ERASER_RUBBER = Color(0xFFF4A3B5)
private val ERASER_SLEEVE = Color(0xFF5B8DEF)

private val DOCK_MAX_WIDTH = 560.dp
private val TRAY_HEIGHT = 76.dp
private val PEN_WIDTH = 50.dp
private val PEN_HEIGHT = 88.dp

/** How far a pen's lower end hangs below the tray's edge even when lifted. */
private val PEN_OVERHANG = 14.dp

/** How much lower a pen that is not in hand sits in the tray. */
private val PEN_SINK = 18.dp
private val INK_TARGET = 40.dp

private val INK_DOT = 30.dp

/** Pen width is a fraction of the paper; this is the paper width the size preview assumes. */
private val PREVIEW_UNIT = 360.dp
