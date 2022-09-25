package de.mm20.launcher2.ui.settings.websearch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.godaddy.android.colorpicker.ClassicColorPicker
import de.mm20.launcher2.search.data.Websearch
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.ktx.toHexString
import de.mm20.launcher2.ui.ktx.toPixels
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun WebSearchSettingsScreen() {
    val viewModel: WebSearchSettingsScreenVM = viewModel()
    val websearches by viewModel.websearches.observeAsState(emptyList())
    var showNewDialog by remember { mutableStateOf(false) }
    PreferenceScreen(
        title = stringResource(R.string.preference_search_websearch),
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
            }
        }
    ) {
        item {
            PreferenceCategory {
                for (websearch in websearches) {
                    WebsearchPreference(
                        value = websearch,
                        onValueChanged = {
                            viewModel.updateWebsearch(it)
                        },
                        onValueDeleted = { viewModel.deleteWebsearch(it) }
                    )
                }
            }
        }
    }
    if (showNewDialog) {
        EditWebsearchDialog(
            title = stringResource(R.string.websearch_dialog_create_title),
            value = Websearch(
                label = "",
                urlTemplate = "",
                color = 0,
                icon = null
            ),
            onValueSaved = {
                viewModel.createWebsearch(it)
                showNewDialog = false
            },
            onCancel = {
                showNewDialog = false
            },
            enableImport = true
        )
    }
}

@Composable
fun WebsearchPreference(
    value: Websearch,
    onValueChanged: (Websearch) -> Unit,
    onValueDeleted: (Websearch) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(
        title = value.label,
        summary = value.urlTemplate,
        onClick = {
            showDialog = true
        },
        icon = {
            val icon = value.icon
            if (icon == null) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = value.color.takeIf { it != 0 }?.let { Color(it) }
                        ?: MaterialTheme.colorScheme.primary,
                )
            } else {
                AsyncImage(
                    model = File(icon),
                    contentDescription = null,
                    modifier = Modifier.sizeIn(maxWidth = 24.dp, maxHeight = 24.dp),
                    contentScale = ContentScale.Inside
                )
            }
        }
    )
    if (showDialog) {
        EditWebsearchDialog(
            title = stringResource(R.string.websearch_dialog_edit_title),
            value = value,
            onValueSaved = {
                onValueChanged(it)
                showDialog = false
            },
            onCancel = {
                showDialog = false
            },
            onValueDeleted = onValueDeleted
        )
    }
}

@Composable
fun EditWebsearchDialog(
    title: String,
    value: Websearch,
    onValueSaved: (Websearch) -> Unit,
    onValueDeleted: ((Websearch) -> Unit)? = null,
    onCancel: () -> Unit,
    enableImport: Boolean = false
) {
    var showDropdown by remember { mutableStateOf(false) }

    var label by remember { mutableStateOf(value.label) }
    var showError by remember { mutableStateOf(false) }
    var urlTemplate by remember { mutableStateOf(value.urlTemplate) }
    var encoding by remember { mutableStateOf(value.encoding) }
    var color by remember { mutableStateOf(value.color) }
    var icon by remember { mutableStateOf(value.icon) }

    val scope = rememberCoroutineScope()

    var showImport by remember { mutableStateOf(false) }
    var loadingImport by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf(false) }

    val viewModel: WebSearchSettingsScreenVM = viewModel()

    val iconSizePx = 32.dp.toPixels().toInt()

    val chooseIconLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) {
        if (it != null) {
            scope.launch {
                icon = viewModel.createIcon(it, iconSizePx)
            }
        }
    }


    BottomSheetDialog(onDismissRequest = onCancel,
        title = { Text(title) },
        actions = {
            if (enableImport) {
                Box {
                    IconButton(onClick = {
                        showImport = !showImport
                        importError = false
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = null
                        )
                    }

                }
            }
            if (onValueDeleted != null) {
                Box {
                    IconButton(onClick = {
                        showDropdown = true
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = null
                        )
                    }
                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.menu_delete)
                                )
                            },
                            onClick = {
                                onValueDeleted(value)
                                onCancel()
                            })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (urlTemplate.contains("\${1}")) {
                    value.label = label
                    value.urlTemplate = urlTemplate
                    if (value.icon != icon) {
                        value.icon?.let {
                            viewModel.deleteIcon(it)
                        }
                    }
                    value.icon = icon
                    value.color = color
                    value.encoding = encoding
                    onValueSaved(value)
                } else {
                    showError = true
                }
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                if (icon != value.icon) {
                    icon?.let { viewModel.deleteIcon(it) }
                }
                onCancel()
            }) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            AnimatedVisibility(showImport) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var importUrl by remember { mutableStateOf("") }
                            OutlinedTextField(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 16.dp, top = 8.dp, end = 8.dp),
                                label = { Text(stringResource(R.string.websearch_dialog_import_url)) },
                                value = importUrl,
                                onValueChange = {
                                    importUrl = it
                                    importError = false
                                },
                                textStyle = MaterialTheme.typography.bodyLarge,
                            )
                            if (loadingImport) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(24.dp)
                                )
                            } else {
                                IconButton(onClick = {
                                    scope.launch {
                                        loadingImport = true
                                        val websearch =
                                            viewModel.importWebsearch(
                                                importUrl,
                                                iconSizePx
                                            )
                                        if (websearch != null) {
                                            label = websearch.label
                                            icon = websearch.icon
                                            urlTemplate = websearch.urlTemplate
                                            color = websearch.color
                                            showImport = false
                                        } else {
                                            importError = true
                                        }
                                        loadingImport = false
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowForward,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(importError) {
                            Column(
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.websearch_dialog_import_error),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                TextButton(
                                    modifier = Modifier.align(Alignment.End),
                                    onClick = { showImport = false }) {
                                    Text(
                                        text = stringResource(android.R.string.ok),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (icon != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = icon?.let { File(it) },
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(48.dp)
                    )
                    TextButton(
                        onClick = {
                            chooseIconLauncher.launch("image/*")
                        },
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.websearch_dialog_replace_icon),
                        )
                    }
                    TextButton(
                        onClick = {
                            icon = null
                        },
                        modifier = Modifier.padding(4.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            stringResource(R.string.websearch_dialog_delete_icon),
                        )
                    }
                }
            } else {
                ColorPicker(
                    value = color,
                    onColorSelected = { color = it }
                )
                TextButton(
                    onClick = {
                        chooseIconLauncher.launch("image/*")
                    },
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.End)
                ) {
                    Text(
                        stringResource(R.string.websearch_dialog_custom_icon),
                    )
                }

            }

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                value = label,
                onValueChange = {
                    label = it
                },
                label = {
                    Text(text = stringResource(R.string.websearch_dialog_name))
                },
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                value = urlTemplate,
                onValueChange = {
                    urlTemplate = it
                },
                label = {
                    Text(text = stringResource(R.string.websearch_dialog_url))
                },
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            AnimatedVisibility(showError) {
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = stringResource(R.string.websearch_dialog_url_error),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = stringResource(R.string.websearch_dialog_url_description),
                style = MaterialTheme.typography.labelMedium
            )

            var showAdvanced by remember { mutableStateOf(false) }

            AnimatedVisibility(!showAdvanced) {
                TextButton(
                    modifier = Modifier.padding(vertical = 16.dp).align(Alignment.End),
                    onClick = { showAdvanced = true }) {
                    Text(stringResource(R.string.websearch_dialog_advanced))
                }
            }

            AnimatedVisibility(showAdvanced) {
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Divider()
                    ListPreference(
                        title = stringResource(R.string.websearch_dialog_query_endcoding),
                        items = listOf(
                            stringResource(R.string.websearch_dialog_query_endcoding_url) to Websearch.QueryEncoding.UrlEncode,
                            stringResource(R.string.websearch_dialog_query_endcoding_form) to Websearch.QueryEncoding.FormData,
                            stringResource(R.string.websearch_dialog_query_endcoding_none) to Websearch.QueryEncoding.None,
                        ),
                        iconPadding = false,
                        value = encoding,
                        onValueChanged = {
                            encoding = it
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPicker(
    value: Int,
    onColorSelected: (Int) -> Unit
) {
    var selectedColorIndex = -1
    val isCustomColor = !ColorPresets.contains(Color(value)) && value != 0
    val listState = rememberLazyListState()

    var showCustomColorPicker by remember { mutableStateOf(false) }

    Column {
        AnimatedVisibility(!showCustomColorPicker) {
            LazyRow(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(64.dp),
                state = listState
            ) {
                item {
                    if (value == 0) selectedColorIndex = 0
                    ColorSwatch(
                        color = MaterialTheme.colorScheme.primary,
                        checked = value == 0,
                        onClick = {
                            onColorSelected(0)
                        }
                    )
                }
                items(ColorPresets) {
                    ColorSwatch(
                        color = it,
                        checked = value == it.toArgb(),
                        onClick = {
                            onColorSelected(it.toArgb())
                        }
                    )
                }
                item {
                    CustomColorSwatch(
                        checked = isCustomColor,
                        onClick = {
                            showCustomColorPicker = true
                        }
                    )
                }
            }
            LaunchedEffect(null) {
                if (isCustomColor) listState.scrollToItem(ColorPresets.size + 1)
                else if (value != 0) listState.scrollToItem(ColorPresets.indexOf(Color(value)) + 1)
            }
        }
        AnimatedVisibility(showCustomColorPicker) {
            Column {
                ClassicColorPicker(
                    color = Color(value),
                    showAlphaBar = false,
                    modifier = Modifier.height(200.dp),
                    onColorChanged = {
                        onColorSelected(it.toColor().toArgb())
                    })
                Row(
                    modifier = Modifier
                        .padding(bottom = 24.dp, top = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var textFieldValue by remember(value) {
                        mutableStateOf(
                            Color(value).toHexString().substring(1)
                        )
                    }
                    TextField(
                        value = textFieldValue,
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Tag, contentDescription = null)
                        },
                        onValueChange = {
                            textFieldValue = it
                            if (it.length == 6) it.toLongOrNull(16)?.let {
                                onColorSelected((it or 0xFF000000).toInt())
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.width(150.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = { showCustomColorPicker = false }) {
                        Text(
                            stringResource(android.R.string.ok),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun ColorSwatch(
    color: Color,
    checked: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check, contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White
            )
        }
    }
}

@Composable
private fun CustomColorSwatch(
    checked: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .size(48.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val brush = Brush.sweepGradient(
                listOf(
                    Color.Red,
                    Color.Magenta,
                    Color.Blue,
                    Color.Cyan,
                    Color.Green,
                    Color.Yellow,
                    Color.Red
                )
            )

            drawRect(brush)
        }
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check, contentDescription = null,
                tint = Color.White
            )
        }
    }
}

private val ColorPresets = listOf(
    Color(0xFFEF5350),
    Color(0xFFEC407A),
    Color(0xFFAB47BC),
    Color(0xFF7E57C2),
    Color(0xFF5C6BC0),
    Color(0xFF42A5F5),
    Color(0xFF29B6F6),
    Color(0xFF26C6DA),
    Color(0xFF26A69A),
    Color(0xFF66BB6A),
    Color(0xFF9CCC65),
    Color(0xFFD4E157),
    Color(0xFFFFEE58),
    Color(0xFFFFCA28),
    Color(0xFFFFA726),
    Color(0xFFFF7043),
    Color(0xFF8D6E63),
    Color(0xFFBDBDBD),
    Color(0xFF78909C),
)
