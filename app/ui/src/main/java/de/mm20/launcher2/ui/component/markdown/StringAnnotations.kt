package de.mm20.launcher2.ui.component.markdown

import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import kotlin.math.min

fun AnnotatedString.Builder.applyStyles(
    node: ASTNode,
    typography: Typography,
    delimiterStyle: SpanStyle,
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
                typography.headlineLarge.toSpanStyle(),
                node.startOffset - rootOffset,
                node.endOffset - rootOffset,
            )
            addStyle(
                typography.headlineLarge.toParagraphStyle(),
                node.startOffset - rootOffset,
                min(node.endOffset + 1 - rootOffset, length)
            )
        }

        MarkdownElementTypes.ATX_2 -> {
            addStyle(
                typography.headlineMedium.toSpanStyle(),
                node.startOffset - rootOffset,
                node.endOffset - rootOffset,
            )
            addStyle(
                typography.headlineMedium.toParagraphStyle(),
                node.startOffset - rootOffset,
                min(node.endOffset + 1 - rootOffset, length)
            )
        }

        MarkdownElementTypes.ATX_3 -> {
            addStyle(
                typography.headlineSmall.toSpanStyle(),
                node.startOffset - rootOffset,
                node.endOffset - rootOffset,
            )
            addStyle(
                typography.headlineSmall.toParagraphStyle(),
                node.startOffset - rootOffset,
                min(node.endOffset + 1 - rootOffset, length)
            )
        }

        MarkdownElementTypes.ATX_4 -> {
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

        MarkdownElementTypes.ATX_5 -> {
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

        MarkdownElementTypes.ATX_6 -> {
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
    }
    for (child in node.children) {
        applyStyles(child, typography, delimiterStyle, rootOffset)
    }

    if (node.children.isEmpty() &&
        node.type != MarkdownTokenTypes.TEXT &&
        node.type != MarkdownTokenTypes.WHITE_SPACE &&
        node.type != MarkdownTokenTypes.CODE_FENCE_CONTENT &&
        node.type != MarkdownTokenTypes.CODE_LINE
    ) {
        addStyle(
            delimiterStyle,
            node.startOffset - rootOffset,
            node.endOffset - rootOffset,
        )
    }
}