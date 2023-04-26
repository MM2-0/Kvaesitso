package de.mm20.launcher2.ui.component.markdown

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

@Composable
fun MarkdownEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null
) {
    val typography = MaterialTheme.typography
    val delimiterColor = MaterialTheme.colorScheme.secondary
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }


    BackHandler(
        enabled = focused
    ) {
        focused = false
    }

    if (focused) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = LocalContentColor.current,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = remember(
                typography,
                delimiterColor
            ) { MarkdownTransformation(typography, delimiterColor) },
            interactionSource = interactionSource,
        )

    } else {
        if (placeholder != null && value.isBlank()) {
            Box(
                modifier = modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    focused = true
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
                value,
                modifier = modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    focused = true
                },
                onTextChange = onValueChange,
            )
        }
    }
}


class MarkdownTransformation(
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
                applyStyles(tree, typography, delimiterStyle)
            },
            OffsetMapping.Identity,
        )
    }
}
