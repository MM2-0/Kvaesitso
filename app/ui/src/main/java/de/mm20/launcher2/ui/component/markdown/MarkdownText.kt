package de.mm20.launcher2.ui.component.markdown

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    onTextChange: (String) -> Unit,
) {
    val parsed = remember(text) {
        MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(text)
    }
    MarkdownText(parsed, text, modifier, onTextChange)
}

@Composable
fun MarkdownText(
    rootNode: ASTNode, text: String, modifier: Modifier = Modifier,
    onTextChange: (String) -> Unit,
) {
    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
        Column(
            modifier = modifier,
        ) {
            for (child in rootNode.children) {
                MarkdownNode(child, text, onTextChange)
            }
        }
    }
}

@Composable
fun MarkdownNode(
    node: ASTNode, text: String,
    onTextChange: (String) -> Unit,
) {
    when (node.type) {
        MarkdownTokenTypes.TEXT -> TextNode(node, text)
        MarkdownTokenTypes.CODE_FENCE_CONTENT -> TextNode(node, text)
        MarkdownTokenTypes.CODE_LINE -> TextNode(node, text)
        MarkdownElementTypes.PARAGRAPH -> ParagraphNode(node, text)
        MarkdownElementTypes.ATX_1 -> AtxNode(node, text, 1, onTextChange)
        MarkdownElementTypes.ATX_2 -> AtxNode(node, text, 2, onTextChange)
        MarkdownElementTypes.ATX_3 -> AtxNode(node, text, 3, onTextChange)
        MarkdownElementTypes.ATX_4 -> AtxNode(node, text, 4, onTextChange)
        MarkdownElementTypes.ATX_5 -> AtxNode(node, text, 5, onTextChange)
        MarkdownElementTypes.ATX_6 -> AtxNode(node, text, 6, onTextChange)
        MarkdownTokenTypes.HORIZONTAL_RULE -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        MarkdownElementTypes.UNORDERED_LIST -> UnorderedListNode(node, text, onTextChange)
        MarkdownElementTypes.ORDERED_LIST -> OrderedListNode(node, text, onTextChange)
        MarkdownElementTypes.BLOCK_QUOTE -> BlockQuoteNode(node, text, onTextChange)
        MarkdownElementTypes.CODE_BLOCK -> CodeBlockNode(node, text, onTextChange)
        MarkdownElementTypes.CODE_FENCE -> CodeBlockNode(node, text, onTextChange)

        else -> {
            ChildNodes(node, text, onTextChange)
        }
    }
}

@Composable
fun ChildNodes(node: ASTNode, text: String, onTextChange: (String) -> Unit) {
    for (child in node.children) {
        MarkdownNode(child, text, onTextChange)
    }
}

@Composable
fun AtxNode(node: ASTNode, text: String, level: Int, onTextChange: (String) -> Unit) {
    ProvideTextStyle(
        when (level) {
            1 -> MaterialTheme.typography.titleLarge
            2 -> MaterialTheme.typography.titleMedium
            3 -> MaterialTheme.typography.titleSmall
            4 -> MaterialTheme.typography.labelLarge
            5 -> MaterialTheme.typography.labelMedium
            else -> MaterialTheme.typography.labelSmall
        }
    ) {
        ParagraphNode(node, text)
    }
}

@Composable
fun TextNode(node: ASTNode, text: String) {
    val start = node.startOffset
    val end = node.endOffset
    val substring = text.substring(start, end)
    Text(
        text = substring,
    )
}

@Composable
fun ParagraphNode(node: ASTNode, text: String) {
    val start = node.startOffset
    val end = node.endOffset
    val substring = text.substring(start, end)
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val text = buildAnnotatedString {
        append(substring)
        applyStyles(
            node,
            colorScheme,
            typography,
            SpanStyle(fontSize = 0.sp),
            text,
            node.startOffset
        )
    }

    val context = LocalContext.current

    Text(
        text = text,
        onTextLayout = {
            layoutResult.value = it
        },
        modifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(true)
                val offset = down.position
                val position =
                    layoutResult.value?.getOffsetForPosition(offset) ?: return@awaitEachGesture
                val downUrlAnnotation = text.getUrlAnnotations(position, position).firstOrNull()
                val downUrl = downUrlAnnotation?.item?.url ?: return@awaitEachGesture
                val up =
                    waitForUpOrCancellation()?.takeIf { !it.isConsumed } ?: return@awaitEachGesture
                val upPosition =
                    layoutResult.value?.getOffsetForPosition(offset) ?: return@awaitEachGesture
                val upAnnotation = text.getUrlAnnotations(upPosition, upPosition).firstOrNull()
                val url = upAnnotation?.item?.url ?: return@awaitEachGesture
                if (url == downUrl) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    up.consume()
                }
            }
        }
    )
}

@Composable
fun UnorderedListNode(node: ASTNode, text: String, onTextChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        var counter = 1
        for (child in node.children) {
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                ListItemNode(child, counter, text, onTextChange)
                counter++
            }
        }
    }
}

@Composable
fun OrderedListNode(node: ASTNode, text: String, onTextChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        var counter = 1
        for (child in node.children) {
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                ListItemNode(child, counter, text, onTextChange)
                counter++
            }
        }
    }
}

@Composable
fun ListItemNode(
    node: ASTNode,
    index: Int,
    text: String,
    onTextChange: (String) -> Unit
) {

    Row {
        val checkbox = node.children.find { it.type == GFMTokenTypes.CHECK_BOX }
        if (checkbox != null) {
            CheckboxNode(checkbox, text, onTextChange)
        } else {
            val ordinal = node.children.find { it.type == MarkdownTokenTypes.LIST_NUMBER }
            if (ordinal != null) {
                Text(
                    text = "${index}.",
                    modifier = Modifier
                        .width(32.dp)
                        .padding(end = 4.dp),
                    textAlign = TextAlign.End,
                )
            } else {
                Text(
                    text = "•",
                    modifier = Modifier
                        .width(32.dp)
                        .padding(end = 4.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = if (checkbox != null) 4.dp else 0.dp)
        ) {
            ChildNodes(node, text, onTextChange)
        }

    }
}

@Composable
fun BlockQuoteNode(node: ASTNode, text: String, onTextChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                ChildNodes(node, text, onTextChange)
            }
        }
    }
}

@Composable
fun CodeBlockNode(node: ASTNode, text: String, onTextChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                ProvideTextStyle(
                    LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                ) {
                    ChildNodes(node, text, onTextChange)
                }

            }
        }
    }
}

@Composable
fun CheckboxNode(node: ASTNode, text: String, onTextChange: (String) -> Unit = {}) {
    val checkbox = text.substring(node.startOffset, node.endOffset)
    val checked = checkbox.startsWith("[x]")


    Checkbox(
        checked = checked, onCheckedChange = {
            val newCheckbox = if (it) "[x] " else "[ ] "
            val newText = text.replaceRange(node.startOffset, node.endOffset, newCheckbox)
            onTextChange(newText)
        }, modifier = Modifier
            .padding(top = 4.dp, bottom = 4.dp, end = 8.dp, start = 6.dp)
            .requiredSize(18.dp)
    )
}

private fun ASTNode.print(indent: Int = 0) {
    Log.d("MM20", "${" ".repeat(indent)}${this.type}")
    for (child in this.children) {
        child.print(indent + 2)
    }
}