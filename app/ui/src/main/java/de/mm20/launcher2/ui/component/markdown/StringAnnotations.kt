package de.mm20.launcher2.ui.component.markdown

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import kotlin.math.min

fun AnnotatedString.Builder.applyStyles(
    node: ASTNode,
    colorScheme: ColorScheme,
    typography: Typography,
    delimiterStyle: SpanStyle,
    fullText: String? = null,
    rootOffset: Int = 0,
) {
    require(node.startOffset >= rootOffset) {
        "Node start offset ${node.startOffset} is smaller than root offset $rootOffset"
    }
    when (node.type) {
        MarkdownElementTypes.STRONG -> {
            addStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
                node.startOffset - rootOffset,
                node.endOffset - rootOffset,
            )
        }

        MarkdownElementTypes.EMPH -> {
            addStyle(
                SpanStyle(fontStyle = FontStyle.Italic),
                node.startOffset - rootOffset,
                node.endOffset - rootOffset,
            )
        }

        MarkdownElementTypes.CODE_SPAN -> {
            addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace),
                node.startOffset - rootOffset,
                node.endOffset - rootOffset,
            )
        }
        MarkdownElementTypes.ATX_1 -> {
            addStyle(
                typography.titleLarge.toSpanStyle(),
                node.startOffset - rootOffset,
                node.endOffset - rootOffset,
            )
            addStyle(
                typography.titleLarge.toParagraphStyle(),
                node.startOffset - rootOffset,
                min(node.endOffset + 1 - rootOffset, length)
            )
        }

        MarkdownElementTypes.ATX_2 -> {
            addStyle(
                typography.titleMedium.toSpanStyle(),
                node.startOffset - rootOffset,
                node.endOffset - rootOffset,
            )
            addStyle(
                typography.titleMedium.toParagraphStyle(),
                node.startOffset - rootOffset,
                min(node.endOffset + 1 - rootOffset, length)
            )
        }

        MarkdownElementTypes.ATX_3 -> {
            addStyle(
                typography.titleSmall.toSpanStyle(),
                node.startOffset - rootOffset,
                node.endOffset - rootOffset,
            )
            addStyle(
                typography.titleSmall.toParagraphStyle(),
                node.startOffset - rootOffset,
                min(node.endOffset + 1 - rootOffset, length)
            )
        }

        MarkdownElementTypes.ATX_4 -> {
            addStyle(
                typography.labelLarge.toSpanStyle(),
                node.startOffset - rootOffset,
                node.endOffset - rootOffset,
            )
            addStyle(
                typography.labelLarge.toParagraphStyle(),
                node.startOffset - rootOffset,
                min(node.endOffset + 1 - rootOffset, length)
            )
        }

        MarkdownElementTypes.ATX_5 -> {
            addStyle(
                typography.labelMedium.toSpanStyle(),
                node.startOffset - rootOffset,
                node.endOffset - rootOffset,
            )
            addStyle(
                typography.labelMedium.toParagraphStyle(),
                node.startOffset - rootOffset,
                min(node.endOffset + 1 - rootOffset, length)
            )
        }

        MarkdownElementTypes.ATX_6 -> {
            addStyle(
                typography.labelSmall.toSpanStyle(),
                node.startOffset - rootOffset,
                node.endOffset - rootOffset,
            )
            addStyle(
                typography.labelSmall.toParagraphStyle(),
                node.startOffset - rootOffset,
                min(node.endOffset + 1 - rootOffset, length)
            )
        }
        MarkdownElementTypes.INLINE_LINK -> {
            val text = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
            val destination = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }

            if (text != null && destination != null) {
                addStyle(
                    SpanStyle(
                        color = colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    ),
                    text.startOffset - rootOffset,
                    text.endOffset - rootOffset,
                )
                addStyle(
                    delimiterStyle,
                    destination.startOffset - rootOffset,
                    destination.endOffset - rootOffset,
                )
                if (fullText != null) {
                    val url = fullText.substring(destination.startOffset, destination.endOffset)
                    addUrlAnnotation(
                        UrlAnnotation(url),
                        text.startOffset - rootOffset,
                        text.endOffset - rootOffset,
                    )
                }
            }
        }
    }
    for (child in node.children) {
        applyStyles(child, colorScheme, typography, delimiterStyle, fullText, rootOffset)
    }

    if (node.children.isEmpty() &&
        node.type != MarkdownTokenTypes.TEXT &&
        node.type != MarkdownTokenTypes.WHITE_SPACE &&
        node.type != MarkdownTokenTypes.CODE_FENCE_CONTENT &&
        node.type != MarkdownTokenTypes.CODE_LINE &&
        node.parent?.type != MarkdownElementTypes.PARAGRAPH
    ) {
        addStyle(
            delimiterStyle,
            node.startOffset - rootOffset,
            node.endOffset - rootOffset,
        )
    }
}