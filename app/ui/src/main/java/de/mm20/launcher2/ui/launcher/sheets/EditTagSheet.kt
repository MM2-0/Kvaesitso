package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.data.customattrs.CustomTextIcon
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.IconPicker
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.SmallMessage
import de.mm20.launcher2.ui.component.emojipicker.EmojiPicker
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.locals.LocalGridSettings

@Composable
fun EditTagSheet(
    tag: String?,
    onDismiss: () -> Unit,
    onTagSaved: (String) -> Unit = {},
) {
    val viewModel: EditTagSheetVM = viewModel()

    val density = LocalDensity.current

    LaunchedEffect(tag) {
        viewModel.init(tag, with(density) { 56.dp.toPx().toInt() })
    }

    if (viewModel.loading) return

    DismissableBottomSheet(
        onDismissRequest = {
            if (viewModel.page == EditTagSheetPage.CustomizeTag) {
                viewModel.save()
                onTagSaved(viewModel.tagName)
            }
            onDismiss()
        },
    ) {
        when (viewModel.page) {
            EditTagSheetPage.CreateTag -> CreateNewTagPage(viewModel, it)
            EditTagSheetPage.PickItems -> PickItems(viewModel, it)
            EditTagSheetPage.CustomizeTag -> CustomizeTag(viewModel, it)
            EditTagSheetPage.PickIcon -> PickIcon(viewModel, it)
        }
    }
}

@Composable
fun CreateNewTagPage(viewModel: EditTagSheetVM, paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(paddingValues)
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardActions = KeyboardActions(
                onDone = {
                    viewModel.onClickContinue()
                }
            ),
            isError = viewModel.tagNameExists,
            supportingText = { if (viewModel.tagNameExists) Text(stringResource(id = R.string.tag_exists_error)) },
            label = { Text(stringResource(R.string.tag_name)) },
            value = viewModel.tagName,
            onValueChange = { viewModel.tagName = it }
        )

        Button(
            modifier = Modifier.align(Alignment.End),
            enabled = (viewModel.tagName.isNotBlank() && viewModel.page == EditTagSheetPage.CreateTag && !viewModel.tagNameExists)
                    || (viewModel.page == EditTagSheetPage.PickItems && viewModel.taggedItems.isNotEmpty()),
            onClick = { viewModel.onClickContinue() }) {
            Text(stringResource(R.string.action_next))
        }
    }
}

@Composable
fun PickItems(viewModel: EditTagSheetVM, paddingValues: PaddingValues) {
    val columns = LocalGridSettings.current.columnCount - 1

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = Color.Transparent,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {

                ExtendedFloatingActionButton(
                    modifier = Modifier.padding(paddingValues),
                    onClick = { viewModel.closeItemPicker() }
                ) {
                    Text(stringResource(R.string.action_next))
                }
            }
        }
    ) {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxWidth(),
            columns = GridCells.Fixed(columns),
            contentPadding = it
        ) {
            item(span = { GridItemSpan(columns) }) {
                Text(
                    stringResource(id = R.string.tag_select_items),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(viewModel.taggableApps) {
                val iconSize = 32.dp.toPixels()
                val icon by remember(it.item.key) {
                    viewModel.getIcon(it.item, iconSize.toInt())
                }.collectAsState(null)
                ListItem(item = it, icon = icon, onTagChanged = { tagged ->
                    if (tagged) viewModel.tagItem(it.item)
                    else viewModel.untagItem(it.item)
                })
            }

            if (viewModel.taggableOther.isNotEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .fillMaxWidth()
                            .height(1.dp)
                    )
                }

                items(viewModel.taggableOther) {
                    val iconSize = 32.dp.toPixels()
                    val icon by remember(it.item.key) {
                        viewModel.getIcon(it.item, iconSize.toInt())
                    }.collectAsState(null)
                    ListItem(item = it, icon = icon, onTagChanged = { tagged ->
                        if (tagged) viewModel.tagItem(it.item)
                        else viewModel.untagItem(it.item)
                    })
                }
            }
        }
    }
}

@Composable
fun ListItem(
    item: TaggableItem,
    icon: LauncherIcon?,
    onTagChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            ShapedLauncherIcon(
                icon = { icon },
                size = 48.dp,
                modifier = Modifier
                    .padding(4.dp)
                    .clickable {
                        onTagChanged(!item.isTagged)
                    },
            )
            if (item.isTagged) {
                Surface(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.BottomEnd),
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    onClick = {
                        onTagChanged(false)
                    }
                ) {
                    Icon(
                        painterResource(R.drawable.check_20px),
                        null,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
        Text(
            modifier = Modifier
                .padding(top = 8.dp)
                .padding(horizontal = 12.dp),
            text = item.item.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


@Composable
fun CustomizeTag(viewModel: EditTagSheetVM, paddingValues: PaddingValues) {
    val iconSize = 32.dp.toPixels()
    val tagIcon by remember(viewModel.tagCustomIcon) { viewModel.tagCustomIcon }.collectAsState()
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(paddingValues)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val outlineVariant = MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .clip(CircleShape)
                    .clickable {
                        viewModel.openIconPicker()
                    }
                    .size(72.dp)
                        then (
                        if (tagIcon != null) {
                            Modifier
                        } else {
                            Modifier.drawBehind {
                                val w = with(density) { 2.dp.toPx() }
                                drawCircle(
                                    color = outlineVariant,
                                    style = Stroke(
                                        width = w,
                                        pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(
                                                w * 2,
                                                w * 2
                                            )
                                        )
                                    )
                                )
                            }
                        }
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (tagIcon != null) {
                    var icon =
                        remember(viewModel.tagIcon) { viewModel.tagIcon }.collectAsState(null)
                    ShapedLauncherIcon(
                        size = 56.dp,
                        icon = { icon.value },
                        shape = CircleShape,
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.tag_24px),
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.tag_name)) },
                value = viewModel.tagName,
                onValueChange = { viewModel.tagName = it },
            )
        }

        val icon1 = remember(viewModel.taggedItems.getOrNull(0)?.key) {
            viewModel.taggedItems.getOrNull(0)?.let {
                viewModel.getIcon(it, iconSize.toInt())
            }
        }?.collectAsState(null)
        val icon2 = remember(viewModel.taggedItems.getOrNull(1)?.key) {
            viewModel.taggedItems.getOrNull(1)?.let {
                viewModel.getIcon(it, iconSize.toInt())
            }
        }?.collectAsState(null)
        val icon3 = remember(viewModel.taggedItems.getOrNull(2)?.key) {
            viewModel.taggedItems.getOrNull(2)?.let {
                viewModel.getIcon(it, iconSize.toInt())
            }
        }?.collectAsState(null)
        TextButton(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            onClick = { viewModel.openItemPicker() }) {
            Text(
                modifier = Modifier.weight(1f),
                text = pluralStringResource(
                    R.plurals.tag_selected_items,
                    viewModel.taggedItems.size,
                    viewModel.taggedItems.size
                )
            )
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .width(64.dp)
                    .height(32.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                icon1?.value?.let {
                    ShapedLauncherIcon(
                        size = 32.dp,
                        icon = { it },
                        modifier = Modifier.offset(x = -0.dp)
                    )
                }
                icon2?.value?.let {
                    ShapedLauncherIcon(
                        size = 32.dp,
                        icon = { it },
                        modifier = Modifier.offset(x = -16.dp)
                    )
                }
                icon3?.value?.let {
                    ShapedLauncherIcon(
                        size = 32.dp,
                        icon = { it },
                        modifier = Modifier.offset(x = -32.dp)
                    )
                }
            }
        }
        AnimatedVisibility(viewModel.tagNameExists || viewModel.taggedItems.isEmpty() || viewModel.tagName.isEmpty()) {
            SmallMessage(
                modifier = Modifier.fillMaxWidth(),
                icon = R.drawable.warning_24px,
                text = stringResource(
                    if (viewModel.taggedItems.isEmpty()) R.string.tag_no_items_message
                    else if (viewModel.tagNameExists) R.string.tag_exists_message
                    else R.string.tag_empty_name
                )
            )
        }
    }
}

@Composable
fun PickIcon(
    viewModel: EditTagSheetVM,
    paddingValues: PaddingValues
) {
    val icon by remember(viewModel.tagCustomIcon) { viewModel.tagCustomIcon }.collectAsState()
    val tag = Tag(viewModel.tagName)
    var selectedTabIndex = remember {
        mutableIntStateOf(
            when (icon) {
                is CustomTextIcon -> 1
                else -> 0
            }
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            ToggleButton(
                modifier = Modifier.weight(1f),
                checked = selectedTabIndex.intValue == 0,
                onCheckedChange = { selectedTabIndex.intValue = 0 },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
            ) {
                Icon(
                    painterResource(
                        if (selectedTabIndex.intValue == 0) R.drawable.check_20px else R.drawable.apps_20px
                    ),
                    null,
                    modifier = Modifier.padding(end = ToggleButtonDefaults.IconSpacing).size(
                        ToggleButtonDefaults.IconSize)
                )
                Text(stringResource(R.string.tag_icon_customicon))
            }
            ToggleButton(
                modifier = Modifier.weight(1f),
                checked = selectedTabIndex.intValue == 1,
                onCheckedChange = { selectedTabIndex.intValue = 1 },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
            ) {
                Icon(
                    painterResource(
                        if (selectedTabIndex.intValue == 1) R.drawable.check_20px else R.drawable.mood_20px
                    ),
                    null,
                    modifier = Modifier
                        .padding(end = ToggleButtonDefaults.IconSpacing)
                        .size(ToggleButtonDefaults.IconSize)
                )
                Text(stringResource(R.string.tag_icon_emoji))
            }
        }
        AnimatedContent(
            selectedTabIndex.intValue,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            when (it) {
                0 -> {
                    IconPicker(
                        searchable = tag,
                        onSelect = { viewModel.selectIcon(it) },
                        contentPadding = paddingValues
                    )
                }

                1 -> {
                    EmojiPicker(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onEmojiSelected = {
                            viewModel.selectIcon(CustomTextIcon(text = it))
                        },
                        contentPadding = paddingValues
                    )
                }
            }
        }
    }

}