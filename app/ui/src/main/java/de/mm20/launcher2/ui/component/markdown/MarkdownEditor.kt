package de.mm20.launcher2.ui.component.markdown

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

@Composable
fun MarkdownEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focus: Boolean,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null
) {
    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme
    val delimiterColor = MaterialTheme.colorScheme.secondary
    val interactionSource = remember { MutableInteractionSource() }

    val focusRequester = remember { FocusRequester() }


    BackHandler(
        enabled = focus
    ) {
        onFocusChange(false)
    }

    if (focus) {
        var hadFocus by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }


        BasicTextField(
            value = value,
            onValueChange = {
                val cursorPosition = if (it.selection.collapsed) it.selection.start else null
                // If:
                // - multiple chars are selected
                // - the last action was not an insert
                // - the cursor char before the selection is not a newline
                // do nothing.
                if (cursorPosition == null || it.text.length <= value.text.length || it.text.getOrNull(
                        cursorPosition - 1
                    ) != '\n'
                ) {
                    onValueChange(it)
                } else {
                    // else check if the previous line was a list, if yes, add a list item
                    val prevLine = it.text.substring(0, cursorPosition - 1).substringAfterLast('\n')
                    val leadingSpaces = prevLine.takeWhile { it == ' ' }
                    val prevLineWithoutLeadingSpaces = prevLine.trimStart()
                    val listMarker = leadingSpaces + when {
                        prevLineWithoutLeadingSpaces.startsWith("- [ ] ") -> "- [ ] "
                        prevLineWithoutLeadingSpaces.startsWith("- [x] ") -> "- [ ] "
                        prevLineWithoutLeadingSpaces.startsWith("- ") -> "- "
                        prevLineWithoutLeadingSpaces.startsWith("* ") -> "* "
                        prevLineWithoutLeadingSpaces.startsWith("+ ") -> "+ "
                        prevLineWithoutLeadingSpaces.startsWith("1. ") -> "1. "
                        else -> {
                            onValueChange(it)
                            return@BasicTextField
                        }
                    }
                    onValueChange(
                        it.copy(
                            text = it.text.substring(
                                0,
                                cursorPosition
                            ) + listMarker + it.text.substring(cursorPosition),
                            selection = TextRange(cursorPosition + listMarker.length)
                        )
                    )
                }
            },
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) hadFocus = true
                    if (!it.isFocused && hadFocus) onFocusChange(false)
                },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = LocalContentColor.current,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = remember(
                typography,
                colorScheme,
                delimiterColor
            ) { MarkdownTransformation(colorScheme, typography, delimiterColor) },
            interactionSource = interactionSource,
        )

    } else {
        if (placeholder != null && value.text.isBlank()) {
            Box(
                modifier = modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    onFocusChange(true)
                },
            ) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.secondary
                    ) {
                        placeholder()
                    }
                }
            }
        } else {
            MarkdownText(
                value.text,
                modifier = modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    onFocusChange(true)
                },
                onTextChange = { onValueChange(TextFieldValue(it)) },
            )
        }
    }
}


class MarkdownTransformation(
    private val colorScheme: ColorScheme,
    private val typography: Typography,
    delimiterColor: Color,
) : VisualTransformation {

    private val parser = MarkdownParser(GFMFlavourDescriptor())
    private val delimiterStyle = SpanStyle(
        color = delimiterColor,
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val tree = parser.buildMarkdownTreeFromString(text.text)
        return TransformedText(
            buildAnnotatedString {
                append(text)
                applyStyles(tree, colorScheme, typography, delimiterStyle)
            },
            OffsetMapping.Identity,
        )
    }
}
