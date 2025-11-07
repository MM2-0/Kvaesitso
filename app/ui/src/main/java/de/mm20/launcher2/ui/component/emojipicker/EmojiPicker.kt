package de.mm20.launcher2.ui.component.emojipicker

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch

@Composable
fun EmojiPicker(
    modifier: Modifier = Modifier,
    onEmojiSelected: (String) -> Unit = {},
) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    var selectedCategory = remember {
        mutableIntStateOf(0)
    }

    val categories = remember {
        val typedArray =
            context.resources.obtainTypedArray(R.array.emoji_by_category_raw_resources_gender_inclusive)
        IntArray(typedArray.length()) { typedArray.getResourceId(it, 0) }.also {
            typedArray.recycle()
        }
    }
    val categoryNames = stringArrayResource(R.array.category_names)

    val categoryIcons = remember {
        listOf(
            R.drawable.mood_24px,
            R.drawable.emoji_people_24px,
            R.drawable.emoji_nature_24px,
            R.drawable.emoji_food_beverage_24px,
            R.drawable.emoji_transportation_24px,
            R.drawable.trophy_24px,
            R.drawable.emoji_objects_24px,
            R.drawable.emoji_symbols_24px,
            R.drawable.flag_24px,
        )
    }
    val categoryIconsFilled = remember {
        listOf(
            R.drawable.mood_24px_filled,
            R.drawable.emoji_people_24px_filled,
            R.drawable.emoji_nature_24px_filled,
            R.drawable.emoji_food_beverage_24px_filled,
            R.drawable.emoji_transportation_24px_filled,
            R.drawable.trophy_24px_filled,
            R.drawable.emoji_objects_24px_filled,
            R.drawable.emoji_symbols_24px_filled,
            R.drawable.flag_24px_filled,
        )
    }

    val emojis = remember {
        mutableStateMapOf<Int, Array<Array<String>>>()
    }

    LaunchedEffect(selectedCategory.intValue) {
        if (selectedCategory.intValue in emojis) return@LaunchedEffect
        val categoryEmojis =
            context.resources.openRawResource(categories[selectedCategory.intValue])
                .bufferedReader().use {
                    it.readLines().map { line ->
                        line.split(",").toTypedArray()
                    }.toTypedArray()
                }
        emojis[selectedCategory.intValue] = categoryEmojis
    }

    LazyVerticalGrid(
        GridCells.Adaptive(48.dp),
        modifier = modifier,
    ) {
        stickyHeader {
            Row(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium
                    )
                    .clip(MaterialTheme.shapes.medium)
                    .horizontalScroll(rememberScrollState())

            ) {
                for (i in categories.indices) {
                    IconToggleButton(
                        checked = selectedCategory.intValue == i,
                        onCheckedChange = {
                            if (it) selectedCategory.intValue = i
                        },
                    ) {
                        if (i < categoryIcons.size && categoryIcons[i] != 0) {
                            Icon(
                                painterResource(
                                    if (selectedCategory.intValue == i) categoryIconsFilled[i]
                                    else categoryIcons[i]
                                ),
                                contentDescription = categoryNames[i]
                            )
                        }
                    }
                }
            }
        }
        items(
            emojis[selectedCategory.intValue]?.size ?: 0,
            key = { selectedCategory.intValue.toString() + "-" + emojis[selectedCategory.intValue]?.get(it)?.get(0) }
        ) { index ->
            val emoji = emojis[selectedCategory.intValue]?.get(index) ?: return@items
            if (emoji.size > 1) {
                val tooltipState = rememberTooltipState(
                    isPersistent = true,
                )
                TooltipBox(
                    modifier = Modifier.animateItem(),
                    positionProvider = TooltipDefaults
                        .rememberTooltipPositionProvider(),
                    state = tooltipState,
                    tooltip = {
                        RichTooltip {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                for (e in emoji) {
                                    EmojiButton(
                                        emoji = e,
                                        onClick = {
                                            onEmojiSelected(e)
                                        },
                                    )
                                }
                            }
                        }
                    },
                ) {
                    EmojiButton(
                        emoji = emoji[0],
                        onClick = {
                            onEmojiSelected(emoji[0])
                        },
                        onLongClick = {
                            scope.launch {
                                tooltipState.show()
                            }
                        },
                    )
                }
            } else {
                EmojiButton(
                    modifier = Modifier.animateItem(),
                    emoji = emoji[0],
                    onClick = {
                        onEmojiSelected(emoji[0])
                    },
                )
            }
        }
    }
}

@Composable
private fun EmojiButton(
    emoji: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}


@Preview
@Composable
fun EmojiPickerPreview() {
    EmojiPicker(
        modifier = Modifier
            .height(500.dp)
            .fillMaxWidth()
    )
}