package de.mm20.launcher2.ui.component.markdown

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.space.SFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlin.math.min

@Composable
fun MarkdownEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = MaterialTheme.typography
    val delimiterColor = MaterialTheme.colorScheme.secondary
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val focusManager = LocalFocusManager.current

    BackHandler(
        enabled = focused
    ) {
        focusManager.clearFocus()
    }


    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = LocalContentColor.current,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = remember(
            typography,
            focused,
            delimiterColor
        ) { MarkdownTransformation(typography, focused, delimiterColor) },
        interactionSource = interactionSource,
    )
}

class MarkdownTransformation(
    private val typography: Typography,
    renderDelimiters: Boolean,
    delimiterColor: Color,
) : VisualTransformation {

    private val parser = MarkdownParser(SFMFlavourDescriptor())
    private val delimiterStyle = SpanStyle(
        color = delimiterColor,
        fontSize = if (renderDelimiters) TextUnit.Unspecified else 0.sp
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

private fun AnnotatedString.Builder.applyStyles(
    node: ASTNode,
    typography: Typography,
    delimiterStyle: SpanStyle
) {
    when (node.type) {
        MarkdownElementTypes.STRONG -> {
            addStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
                node.startOffset,
                node.endOffset
            )
        }

        MarkdownElementTypes.EMPH -> {
            addStyle(
                SpanStyle(fontStyle = FontStyle.Italic),
                node.startOffset,
                node.endOffset
            )
        }

        MarkdownElementTypes.CODE_SPAN -> {
            addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace),
                node.startOffset,
                node.endOffset
            )
        }

        MarkdownElementTypes.ATX_1 -> {
            addStyle(
                typography.headlineLarge.toSpanStyle(),
                node.startOffset,
                node.endOffset
            )
            addStyle(
                typography.headlineLarge.toParagraphStyle(),
                node.startOffset,
                min(node.endOffset + 1, length)
            )
        }

        MarkdownElementTypes.ATX_2 -> {
            addStyle(
                typography.headlineMedium.toSpanStyle(),
                node.startOffset,
                node.endOffset
            )
            addStyle(
                typography.headlineMedium.toParagraphStyle(),
                node.startOffset,
                min(node.endOffset + 1, length)
            )
        }

        MarkdownElementTypes.ATX_3 -> {
            addStyle(
                typography.headlineSmall.toSpanStyle(),
                node.startOffset,
                node.endOffset
            )
            addStyle(
                typography.headlineSmall.toParagraphStyle(),
                node.startOffset,
                min(node.endOffset + 1, length)
            )
        }

        MarkdownElementTypes.ATX_4 -> {
            addStyle(
                typography.titleLarge.toSpanStyle(),
                node.startOffset,
                node.endOffset
            )
            addStyle(
                typography.titleLarge.toParagraphStyle(),
                node.startOffset,
                min(node.endOffset + 1, length)
            )
        }

        MarkdownElementTypes.ATX_5 -> {
            addStyle(
                typography.titleMedium.toSpanStyle(),
                node.startOffset,
                node.endOffset
            )
            addStyle(
                typography.titleMedium.toParagraphStyle(),
                node.startOffset,
                min(node.endOffset + 1, length)
            )
        }

        MarkdownElementTypes.ATX_6 -> {
            addStyle(
                typography.titleSmall.toSpanStyle(),
                node.startOffset,
                node.endOffset
            )
            addStyle(
                typography.titleSmall.toParagraphStyle(),
                node.startOffset,
                min(node.endOffset + 1, length)
            )
        }
    }
    for (child in node.children) {
        applyStyles(child, typography, delimiterStyle)
    }

    if (node.children.isEmpty() && node.type != MarkdownTokenTypes.TEXT
        && node.children.isEmpty() && node.type != MarkdownTokenTypes.LIST_BULLET
        && node.children.isEmpty() && node.type != MarkdownTokenTypes.LIST_NUMBER
        && node.children.isEmpty() && node.type != MarkdownTokenTypes.WHITE_SPACE
    ) {
        addStyle(
            delimiterStyle,
            node.startOffset,
            node.endOffset,
        )
    }
}