package de.mm20.launcher2.ui.utils

import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

fun htmlToAnnotatedString(html: String): AnnotatedString {
    val document = Jsoup.parse(html.replace("\n", ""))
    return buildAnnotatedString {
        addNodes(this, document)
    }
}

private val blockElements = arrayOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "pre")

private val inlineElements = arrayOf("i", "b", "strong", "em", "sup", "sub", "u", "s", "code", "br")

/**
 * @param inParagraph: ParagraphStyles may not be nested, this parameter indicates if we are already inside a paragraph
 */
private fun addNodes(builder: AnnotatedString.Builder, element: Element, inParagraph: Boolean = false) {
    val tagName = element.tag().normalName()
    when {
        blockElements.contains(tagName) -> {
            val spanStyle = SpanStyle(
                fontSize = when (tagName) {
                    "h1" -> 2.em
                    "h2" -> 1.75.em
                    "h3" -> 1.5.em
                    "h4" -> 1.25.em
                    "h5" -> 1.125.em
                    "h6" -> 1.em
                    else -> 1.em
                },
                fontWeight = if (tagName.matches(Regex("h[1-6]"))) FontWeight.Bold else FontWeight.Normal,
                fontFamily = if (tagName == "pre") FontFamily.Monospace else null
            )

            if (!inParagraph) {
                builder.withStyle(ParagraphStyle(
                    textIndent = if (tagName == "li") TextIndent(0.em, 1.em) else TextIndent.None
                )) {
                    withStyle(
                        spanStyle
                    ) {
                        if (tagName == "li") builder.append(" • ")
                        children(builder, element, true)
                    }
                }
            } else {
                builder.append("\n")
                builder.withStyle(
                    spanStyle
                ) {
                    if (tagName == "li") builder.append(" • ")
                    children(builder, element, true)
                }
            }
        }
        inlineElements.contains(tagName) -> {
            builder.withStyle(
                SpanStyle(
                    fontStyle = if (tagName == "i" || tagName == "em") FontStyle.Italic else FontStyle.Normal,
                    fontWeight = if (tagName == "b" || tagName == "strong") FontWeight.Bold else FontWeight.Normal,
                    textDecoration = when (tagName) {
                        "u" -> TextDecoration.Underline
                        "s" -> TextDecoration.LineThrough
                        else -> TextDecoration.None
                    },
                    baselineShift = when (tagName) {
                        "sup" -> BaselineShift.Superscript
                        "sub" -> BaselineShift.Subscript
                        else -> BaselineShift.None
                    },
                    fontFamily = if (tagName == "code") FontFamily.Monospace else null
                )
            ) {
                if(tagName == "br") builder.append("\n")
                children(builder, element, inParagraph)
            }
        }
        else -> {
            children(builder, element, inParagraph)
        }
    }
}

private fun children(builder: AnnotatedString.Builder, element: Element, inParagraph: Boolean) {
    for (node in element.childNodes()) {
        if (node is TextNode) {
            builder.append(node.text())
        }
        if (node is Element) {
            addNodes(builder, node, inParagraph)
        }
    }
}