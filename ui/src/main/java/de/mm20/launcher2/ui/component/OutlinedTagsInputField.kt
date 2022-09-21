package de.mm20.launcher2.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
fun OutlinedTagsInputField(
    modifier: Modifier = Modifier,
    tags: List<String>,
    onTagsChange: (tags: List<String>) -> Unit,
    placeholder: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    textColor: Color = LocalContentColor.current,
) {
    var value by remember { mutableStateOf("") }
    var lastTagFocused by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

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
            TextFieldDefaults.OutlinedTextFieldDecorationBox(
                contentPadding = PaddingValues(0.dp),
                value = value,
                innerTextField = { Box {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for ((i, tag) in tags.withIndex()) {
                            InputChip(
                                selected = i == tags.lastIndex && lastTagFocused,
                                modifier = Modifier.padding(end = 12.dp),
                                onClick = { },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Rounded.Tag, contentDescription = null)
                                },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        modifier = Modifier.clickable {
                                            onTagsChange(tags.filter { it != tag })
                                        }, imageVector = Icons.Rounded.Clear, contentDescription = null
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

                } },
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
            )
            /*Box {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for ((i, tag) in tags.withIndex()) {
                        InputChip(
                            selected = i == tags.lastIndex && lastTagFocused,
                            modifier = Modifier.padding(end = 12.dp),
                            onClick = { },
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.Tag, contentDescription = null)
                            },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    modifier = Modifier.clickable {
                                        onTagsChange(tags.filter { it != tag })
                                    }, imageVector = Icons.Rounded.Clear, contentDescription = null
                                )
                            },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .padding(
                                start = if (tags.isEmpty()) 16.dp else 0.dp,
                                end = 16.dp
                            )
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

            }*/
        },
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
    )
}