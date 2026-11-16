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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol

/** What a [PillTextField]'s border says: nothing, it works, or it doesn't. */
enum class PillTone { Neutral, Good, Bad }

/** The green a [PillTone.Good] field and its caption use. */
val PillGood = Color(0xFF22C55E)

/** Colours of a [PillTextField]. [onCard] for a card surface; [onAccent] on a gradient or accent fill. */
@Immutable
data class PillColors(
    val container: Color,
    val content: Color,
    val placeholder: Color,
    val icon: Color,
    val focus: Color,
) {
    companion object {
        @Composable
        fun onCard(): PillColors {
            val scheme = MaterialTheme.colorScheme
            return PillColors(scheme.surfaceContainerHighest, scheme.onSurface, scheme.onSurfaceVariant.copy(alpha = 0.7f), scheme.onSurfaceVariant, scheme.primary)
        }

        /** Translucent, for a field sitting directly on [onColor]'s accent background. */
        fun onAccent(onColor: Color) = PillColors(onColor.copy(alpha = 0.16f), onColor, onColor.copy(alpha = 0.7f), onColor.copy(alpha = 0.85f), onColor)
    }
}

/**
 * A soft filled pill instead of an outlined box: it sits inside a rounded card without a
 * second frame, and its border is the status: [PillColors.focus] on focus, green when it
 * works, red when it doesn't. Text is monospace, for keys, URLs and ids.
 */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leading: MaterialSymbol,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.Neutral,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    colors: PillColors = PillColors.onCard(),
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val error = MaterialTheme.colorScheme.error
    val border by animateColorAsState(
        when {
            tone == PillTone.Bad -> error
            tone == PillTone.Good -> PillGood
            focused -> colors.focus
            else -> Color.Transparent
        },
        label = "pillBorder",
    )
    val shape = RoundedCornerShape(16.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, color = colors.content),
        cursorBrush = SolidColor(colors.focus),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false, imeAction = imeAction),
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clip(shape)
                    .background(colors.container)
                    .border(1.5.dp, border, shape)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(leading, contentDescription = null, tint = if (focused) colors.focus else colors.icon, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.placeholder, maxLines = 1)
                    }
                    inner()
                }
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        },
    )
}
