package de.mm20.launcher2.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import de.mm20.launcher2.ui.ktx.splitLeadingEmoji
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
fun OutlinedTagsInputField(
    modifier: Modifier = Modifier,
    tags: List<String>,
    onTagsChange: (tags: List<String>) -> Unit,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    textStyle: TextStyle = LocalTextStyle.current,
    textColor: Color = LocalContentColor.current,
    onAutocomplete: (suspend (query: String) -> List<String>)? = null
) {
    var value by remember { mutableStateOf("") }
    var lastTagFocused by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var completions by remember(onAutocomplete) { mutableStateOf<List<String>>(emptyList()) }

    BasicTextField(
        modifier = modifier
            .onKeyEvent {
                if (it.key == Key.Backspace && value.isEmpty() && tags.isNotEmpty()) {
                    if (!lastTagFocused) {
                        lastTagFocused = true
                    } else {
                        onTagsChange(tags.dropLast(1))
                        lastTagFocused = false
                    }
                    return@onKeyEvent true
                }
                lastTagFocused = false
                false
            }
            .onFocusChanged {
                if (!it.hasFocus && value.isNotBlank()) {
                    onTagsChange((tags + value).toImmutableList())
                    value = ""
                } else if (it.hasFocus) {
                    scope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                }
            },
        value = value, onValueChange = {
            val newTags = it.split(",")
            if (newTags.size > 1) {
                onTagsChange(tags + newTags.dropLast(1).filter { it.isNotBlank() })
            }
            value = newTags.last()
            if (value.isNotBlank()) {
                onAutocomplete?.let {
                    scope.launch {
                        completions = it(value)
                    }
                }
            } else {
                completions = emptyList()
            }
            lastTagFocused = false
        },
        textStyle = textStyle.copy(
            color = textColor
        ),
        interactionSource = interactionSource,
        singleLine = true,
        keyboardActions = KeyboardActions(onDone = {
            if (value.isNotBlank()) {
                onTagsChange(tags + value)
                value = ""
            }
        }),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 0.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
                value = tags.joinToString() + value,
                label = label,
                leadingIcon = leadingIcon,
                innerTextField = {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(
                            modifier = Modifier
                                .requiredHeight(32.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for ((i, tag) in tags.withIndex()) {
                                val (emoji, tagName) = remember(tag) {
                                    tag.splitLeadingEmoji()
                                }
                                InputChip(
                                    selected = i == tags.lastIndex && lastTagFocused,
                                    modifier = Modifier.padding(end = 12.dp),
                                    onClick = { },
                                    leadingIcon = {
                                        if (emoji != null && tagName != null) {
                                            Text(
                                                emoji,
                                                modifier = Modifier.width(FilterChipDefaults.IconSize),
                                                textAlign = TextAlign.Center,
                                            )
                                        } else if (tagName != null) {
                                            Icon(
                                                modifier = Modifier
                                                    .size(InputChipDefaults.IconSize),
                                                imageVector = Icons.Rounded.Tag,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    label = { Text(tagName ?: emoji ?: "") },
                                    trailingIcon = {
                                        Icon(
                                            modifier = Modifier
                                                .size(InputChipDefaults.IconSize)
                                                .clickable {
                                                    onTagsChange(tags.filterIndexed { index, _ -> index != i })
                                                },
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = null
                                        )
                                    },
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .height(56.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (value.isEmpty()) {
                                    CompositionLocalProvider(
                                        LocalTextStyle provides textStyle,
                                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                                    ) {
                                        placeholder?.invoke()
                                    }
                                }
                                innerTextField()

                            }
                        }
                        if (completions.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                DropdownMenu(
                                    expanded = true,
                                    onDismissRequest = { completions = emptyList() },
                                    properties = PopupProperties(focusable = false)
                                ) {
                                    for (completion in completions) {
                                        DropdownMenuItem(
                                            text = { Text(completion) },
                                            onClick = {
                                                onTagsChange(tags + completion)
                                                value = ""
                                                completions = emptyList()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
            )
        },
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
    )
}