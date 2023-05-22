package de.mm20.launcher2.ui.settings.tags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.SmallMessage
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.locals.LocalGridSettings

@Composable
fun EditTagSheet(
    tag: String?,
    onDismiss: () -> Unit,
    onTagSaved: (String) -> Unit = {},
) {
    val viewModel: EditTagSheetVM = viewModel()

    val isCreatingNewTag = tag == null

    LaunchedEffect(tag) {
        viewModel.init(tag)
    }

    if (viewModel.loading) return

    BottomSheetDialog(
        title = {
            Text(
                stringResource(
                    if (viewModel.page == EditTagSheetPage.CustomizeTag || !isCreatingNewTag) R.string.edit_tag_title
                    else R.string.create_tag_title
                )
            )
        },
        confirmButton = {
            if (viewModel.page == EditTagSheetPage.CustomizeTag) {
                OutlinedButton(onClick = {
                    viewModel.save()
                    onTagSaved(viewModel.tagName)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.close))
                }
            } else if (isCreatingNewTag) {
                Button(
                    enabled = (viewModel.tagName.isNotBlank() && viewModel.page == EditTagSheetPage.CreateTag && !viewModel.tagNameExists)
                            || (viewModel.page == EditTagSheetPage.PickItems && viewModel.taggedItems.isNotEmpty()),
                    onClick = { viewModel.onClickContinue() }) {
                    Text(stringResource(R.string.action_next))
                }
            } else {
                OutlinedButton(onClick = { viewModel.closeItemPicker() }) {
                    Text(stringResource(id = R.string.ok))
                }
            }

        },
        onDismissRequest = {
            if (viewModel.page == EditTagSheetPage.CustomizeTag) viewModel.save()
            onDismiss()
        },
        dismissible = {
            !(!isCreatingNewTag && viewModel.page == EditTagSheetPage.PickItems)
        },
    ) {
        when (viewModel.page) {
            EditTagSheetPage.CreateTag -> CreateNewTagPage(viewModel, it)
            EditTagSheetPage.PickItems -> PickItems(viewModel, it)
            EditTagSheetPage.CustomizeTag -> CustomizeTag(viewModel, it)
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
    }
}

@Composable
fun PickItems(viewModel: EditTagSheetVM, paddingValues: PaddingValues) {
    val columns = LocalGridSettings.current.columnCount - 1
    LazyVerticalGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = GridCells.Fixed(columns),
        contentPadding = paddingValues,
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
                modifier = Modifier.padding(4.dp),
                onClick = {
                    onTagChanged(!item.isTagged)
                }
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
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.padding(4.dp))
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
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(paddingValues)
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.tag_name)) },
            value = viewModel.tagName,
            onValueChange = { viewModel.tagName = it }
        )
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
                ShapedLauncherIcon(
                    size = 32.dp,
                    icon = { icon1?.value },
                    modifier = Modifier.offset(x = -0.dp)
                )
                ShapedLauncherIcon(
                    size = 32.dp,
                    icon = { icon2?.value },
                    modifier = Modifier.offset(x = -16.dp)
                )
                ShapedLauncherIcon(
                    size = 32.dp,
                    icon = { icon3?.value },
                    modifier = Modifier.offset(x = -32.dp)
                )
            }
        }
        AnimatedVisibility(viewModel.tagNameExists || viewModel.taggedItems.isEmpty()) {
            SmallMessage(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Warning,
                text = stringResource(
                    if (viewModel.taggedItems.isEmpty()) R.string.tag_no_items_message else R.string.tag_exists_message
                )
            )
        }
    }
}