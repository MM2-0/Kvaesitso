package de.mm20.launcher2.ui.settings.searchactions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.builders.AppSearchActionBuilder
import de.mm20.launcher2.searchactions.builders.CustomIntentActionBuilder
import de.mm20.launcher2.searchactions.builders.CustomWebsearchActionBuilder
import de.mm20.launcher2.searchactions.builders.CustomizableSearchActionBuilder
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.ExperimentalBadge
import de.mm20.launcher2.ui.component.SearchActionIcon
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.ktx.toPixels

@Composable
fun EditSearchActionSheet(
    initialSearchAction: CustomizableSearchActionBuilder?,
    onSave: (CustomizableSearchActionBuilder) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: EditSearchActionSheetVM = viewModel()
    LaunchedEffect(initialSearchAction) {
        viewModel.init(initialSearchAction)
    }
    val page by viewModel.currentPage

    val searchAction by viewModel.searchAction
    BottomSheetDialog(
        onDismissRequest = {
            viewModel.onDismiss()
            onDismiss()
        }
    ) {
        Column(
            modifier = when (page) {
                EditSearchActionPage.InitAppSearch, EditSearchActionPage.PickIcon -> Modifier
                else -> Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
            }
        ) {
            when (page) {
                EditSearchActionPage.SelectType -> SelectTypePage(viewModel)
                EditSearchActionPage.InitWebSearch -> InitWebSearchPage(viewModel)
                EditSearchActionPage.InitAppSearch -> InitAppSearchPage(viewModel, it)
                EditSearchActionPage.CustomizeWebSearch -> CustomizeWebSearch(viewModel)
                EditSearchActionPage.CustomizeCustomIntent -> CustomizeCustomIntent(viewModel)
                EditSearchActionPage.CustomizeAppSearch -> CustomizeAppSearch(viewModel)
                EditSearchActionPage.PickIcon -> PickIcon(viewModel, it)
            }

            val button: (@Composable () -> Unit)? = when (page) {
                EditSearchActionPage.CustomizeAppSearch,
                EditSearchActionPage.CustomizeWebSearch,
                EditSearchActionPage.CustomizeCustomIntent -> {
                    {
                        Button(onClick = {
                            if (viewModel.validate()) {
                                viewModel.onSave()
                                searchAction?.let { onSave(it) }
                            }
                        }, enabled = !searchAction?.label.isNullOrBlank()) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }

                EditSearchActionPage.InitWebSearch -> {
                    {
                        val density = LocalDensity.current
                        Button(
                            onClick = {
                                if (viewModel.skipWebsearchImport.value) {
                                    viewModel.skipWebsearchImport()
                                } else {
                                    viewModel.importWebsearch(density)
                                }
                            },
                            enabled = !viewModel.loadingWebsearch.value
                        ) {
                            Text(
                                stringResource(
                                    if (viewModel.skipWebsearchImport.value) {
                                        R.string.skip
                                    } else {
                                        R.string.action_next
                                    }
                                )
                            )
                        }
                    }
                }

                EditSearchActionPage.PickIcon -> {
                    {
                        OutlinedButton(onClick = {
                            viewModel.applyIcon()
                        }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }

                else -> null
            }

            if (button != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    button()
                }
            }
        }
    }
}

@Composable
private fun SelectTypePage(viewModel: EditSearchActionSheetVM) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.create_search_action_type),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        OutlinedCard(
            onClick = { viewModel.initWebSearch() },
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.TravelExplore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.create_search_action_type_web),
                    modifier = Modifier.padding(start = 16.dp),
                    style = MaterialTheme.typography.labelLarge,

                    )
            }
        }
        OutlinedCard(
            onClick = { viewModel.initAppSearch() },
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ManageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.create_search_action_type_app),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                ExperimentalBadge(modifier = Modifier.padding(start = 16.dp))
            }
        }
        OutlinedCard(
            onClick = { viewModel.initCustomIntent() },
            modifier = Modifier
                .padding(top = 12.dp, bottom = 16.dp)
                .fillMaxWidth()
        ) {

            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.create_search_action_type_intent),
                    modifier = Modifier.padding(start = 16.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun InitAppSearchPage(viewModel: EditSearchActionSheetVM, paddingValues: PaddingValues) {
    val context = LocalContext.current
    val searchableApps by remember { viewModel.getSearchableApps(context) }.collectAsState(null)

    if (searchableApps != null) {
        LazyColumn(
            contentPadding = paddingValues
        ) {
            item {
                Text(
                    text = stringResource(R.string.create_search_action_pick_app),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            items(searchableApps!!) {
                OutlinedCard(
                    onClick = { viewModel.selectSearchableApp(it) },
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SearchActionIcon(
                            size = 24.dp,
                            componentName = it.componentName,
                            icon = SearchActionIcon.Custom,
                            color = 1,
                        )
                        Text(
                            text = it.label,
                            modifier = Modifier.padding(start = 16.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InitWebSearchPage(viewModel: EditSearchActionSheetVM) {
    var url by viewModel.initWebsearchUrl
    val importError by viewModel.websearchImportError
    val loading by viewModel.loadingWebsearch
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.create_search_action_website_url),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        val density = LocalDensity.current
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            value = url, onValueChange = { url = it },
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = { viewModel.importWebsearch(density) }),
            enabled = !loading,
            trailingIcon = {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        )
        if (importError) {
            Surface(
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(R.string.create_search_action_website_invalid_url),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun CustomizeWebSearch(viewModel: EditSearchActionSheetVM) {
    val searchAction by viewModel.searchAction

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {

        if (searchAction != null && searchAction is CustomWebsearchActionBuilder) {
            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                SearchActionIconTile(onClick = {
                    viewModel.openIconPicker()
                }) {
                    SearchActionIcon(
                        builder = searchAction!!, size = 24.dp
                    )
                }
                OutlinedTextField(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    value = searchAction!!.label,
                    onValueChange = { viewModel.setLabel(it) },
                    label = { Text(stringResource(R.string.search_action_label)) },
                    singleLine = true,
                )
            }

            val placeholderBackground = MaterialTheme.colorScheme.tertiary
            val placeholderColor = MaterialTheme.colorScheme.onTertiary
            val colorScheme = MaterialTheme.colorScheme
            val context = LocalContext.current
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth(),
                singleLine = true,
                value = (searchAction as CustomWebsearchActionBuilder).urlTemplate,
                onValueChange = { viewModel.setUrlTemplate(it) },
                label = { Text(stringResource(R.string.search_action_websearch_url)) },
                supportingText = {
                    if (viewModel.websearchInvalidUrlError.value) {
                        Text(stringResource(R.string.websearch_dialog_url_error))
                    } else {
                        Column {
                            Text(stringResource(R.string.search_action_websearch_url_hint))
                            Text(
                                stringResource(R.string.more_information),
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable {

                                        CustomTabsIntent
                                            .Builder()
                                            .setDefaultColorSchemeParams(
                                                CustomTabColorSchemeParams
                                                    .Builder()
                                                    .setToolbarColor(colorScheme.primaryContainer.toArgb())
                                                    .setSecondaryToolbarColor(colorScheme.secondaryContainer.toArgb())
                                                    .build()
                                            )
                                            .build()
                                            .launchUrl(
                                                context,
                                                Uri.parse("https://kvaesitso.mm20.de/docs/user-guide/search/quickactions#web-search")
                                            )
                                    },
                                color = MaterialTheme.colorScheme.secondary,
                                style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline)
                            )
                        }
                    }
                },
                isError = viewModel.websearchInvalidUrlError.value,
                visualTransformation = {
                    TransformedText(buildAnnotatedString {
                        append(it)
                        val placeholderIndex = it.indexOf("\${1}")
                        if (placeholderIndex != -1) {
                            addStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = placeholderColor,
                                    background = placeholderBackground,
                                ),
                                placeholderIndex, placeholderIndex + 4
                            )
                        }
                    }, OffsetMapping.Identity)
                }
            )

            var showAdvanced by remember {
                mutableStateOf(false)
            }

            AnimatedVisibility(!showAdvanced) {
                TextButton(
                    modifier = Modifier.padding(top = 16.dp),
                    onClick = { showAdvanced = true }) {
                    Text(stringResource(id = R.string.websearch_dialog_advanced))
                }
            }

            AnimatedVisibility(showAdvanced) {
                ListPreference(
                    title = stringResource(R.string.websearch_dialog_query_encoding),
                    items = listOf(
                        stringResource(id = R.string.websearch_dialog_query_encoding_url) to CustomWebsearchActionBuilder.QueryEncoding.UrlEncode,
                        stringResource(id = R.string.websearch_dialog_query_encoding_form) to CustomWebsearchActionBuilder.QueryEncoding.FormData,
                        stringResource(id = R.string.websearch_dialog_query_encoding_none) to CustomWebsearchActionBuilder.QueryEncoding.None,
                    ),
                    value = (searchAction as CustomWebsearchActionBuilder).encoding,
                    onValueChanged = {
                        viewModel.setQueryEncoding(it)
                    },
                    iconPadding = false,
                    containerColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
fun CustomizeAppSearch(viewModel: EditSearchActionSheetVM) {
    val searchAction by viewModel.searchAction
    val context = LocalContext.current

    val availableSearchApps by remember { viewModel.getSearchableApps(context) }.collectAsState(
        initial = emptyList()
    )
    val selectedApp =
        remember(
            availableSearchApps,
            (searchAction as? AppSearchActionBuilder)?.baseIntent?.component
        ) {
            availableSearchApps.find { it.componentName == (searchAction as? AppSearchActionBuilder)?.baseIntent?.component }
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {

        if (searchAction != null) {

            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                SearchActionIconTile(onClick = {
                    viewModel.openIconPicker()
                }) {
                    SearchActionIcon(
                        builder = searchAction!!, size = 24.dp
                    )
                }
                OutlinedTextField(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    value = searchAction!!.label,
                    onValueChange = { viewModel.setLabel(it) },
                    label = { Text(stringResource(R.string.search_action_label)) },
                    singleLine = true,
                )
            }

            var showAppDropdown by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAppDropdown = !showAppDropdown }) {
                OutlinedTextFieldDefaults.DecorationBox(
                    value = selectedApp?.label ?: "",
                    enabled = true,
                    label = { Text(stringResource(R.string.search_action_app)) },
                    innerTextField = {
                        Text(
                            selectedApp?.label ?: "",
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    interactionSource = remember { MutableInteractionSource() },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    leadingIcon = {
                        if (selectedApp != null) {
                            SearchActionIcon(
                                size = 24.dp,
                                componentName = selectedApp.componentName,
                                icon = SearchActionIcon.Custom,
                                color = 1,
                            )
                        }
                    },
                    trailingIcon = {
                        Icon(imageVector = Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                )
                DropdownMenu(
                    expanded = showAppDropdown,
                    onDismissRequest = { showAppDropdown = false }) {
                    for (app in availableSearchApps) {
                        DropdownMenuItem(
                            text = { Text(app.label) },
                            onClick = {
                                viewModel.setComponentName(app.componentName)
                                showAppDropdown = false
                            },
                            leadingIcon = {
                                SearchActionIcon(
                                    size = 24.dp,
                                    componentName = app.componentName,
                                    icon = SearchActionIcon.Custom,
                                    color = 1,
                                )
                            }
                        )
                    }
                }
            }

            var showAdvanced by remember {
                mutableStateOf(false)
            }

            AnimatedVisibility(!showAdvanced) {
                TextButton(
                    modifier = Modifier.padding(top = 16.dp),
                    onClick = { showAdvanced = true }) {
                    Text(stringResource(id = R.string.websearch_dialog_advanced))
                }
            }

            AnimatedVisibility(showAdvanced) {
                IntentExtrasEditor(viewModel)
            }

        }
    }
}

@Composable
fun CustomizeCustomIntent(viewModel: EditSearchActionSheetVM) {
    val searchAction by viewModel.searchAction

    val action = (searchAction as? CustomIntentActionBuilder) ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            SearchActionIconTile(onClick = {
                viewModel.openIconPicker()
            }) {
                SearchActionIcon(
                    builder = searchAction!!, size = 24.dp
                )
            }
            OutlinedTextField(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                value = action.label,
                onValueChange = { viewModel.setLabel(it) },
                label = { Text(stringResource(R.string.search_action_label)) },
                singleLine = true,
            )
        }

        Text(
            text = "Query",
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = action.queryKey == null,
                onClick = {
                    viewModel.setQueryKey(null)
                },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) {
                Text("Data")
            }
            SegmentedButton(
                selected = action.queryKey != null,
                onClick = {
                    viewModel.setQueryKey("")
                },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) {
                Text("String extra")
            }
        }

        if (action.queryKey != null) {

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                value = action.queryKey ?: "",
                onValueChange = { viewModel.setQueryKey(it) },
                label = { Text("Extra key") },
                singleLine = true,
                isError = viewModel.customIntentKeyError.value
            )
        }

        val placeholderBackground = MaterialTheme.colorScheme.tertiary
        val placeholderColor = MaterialTheme.colorScheme.onTertiary

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            value = action.queryTemplate ?: "",
            onValueChange = { viewModel.setIntentQueryTemplate(it) },
            label = { Text(if (action.queryKey == null) "Data template" else "String extra template") },
            supportingText = {
                Text(
                    if (action.queryKey == null) {
                        "The URI template that is used to construct the intent\\'s data URI. Use ‘\${1}’ as a placeholder for the actual search term, e.g. geo:0,0?q=,\${1}"
                    } else {
                        "The template that is used to construct the string that is passed to the intent as a string extra. Use ‘\${1}’ as a placeholder for the actual search term"
                    }
                )
            },
            singleLine = true,
            visualTransformation = {
                TransformedText(buildAnnotatedString {
                    append(it)
                    val placeholderIndex = it.indexOf("\${1}")
                    if (placeholderIndex != -1) {
                        addStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = placeholderColor,
                                background = placeholderBackground,
                            ),
                            placeholderIndex, placeholderIndex + 4
                        )
                    }
                }, OffsetMapping.Identity)
            },
            isError = viewModel.customIntentTemplateError.value
        )

        Text(
            text = "Base intent",
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
        )


        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            value = action.baseIntent.action ?: "",
            onValueChange = { viewModel.setIntentAction(it) },
            label = { Text("Action") },
            singleLine = true,
        )

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            value = action.baseIntent.categories?.firstOrNull() ?: "",
            onValueChange = { viewModel.setIntentCategory(it) },
            label = { Text("Category") },
            singleLine = true,
        )

        if (action.queryKey != null) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                value = action.baseIntent.dataString ?: "",
                onValueChange = { viewModel.setIntentData(it) },
                label = { Text("Data") },
                singleLine = true,
            )
        }

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            value = action.baseIntent.type ?: "",
            onValueChange = { viewModel.setIntentType(it) },
            label = { Text("Type") },
            singleLine = true,
        )

        val packageName = action.baseIntent.component?.packageName ?: action.baseIntent.`package`

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            value = packageName ?: "",
            onValueChange = { viewModel.setIntentPackage(it) },
            label = { Text("Package") },
            singleLine = true,
        )

        AnimatedVisibility(packageName != null) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                value = action.baseIntent.component?.className ?: "",
                onValueChange = { viewModel.setIntentClassName(it) },
                label = { Text("Class name") },
                singleLine = true,
            )
        }


        var showAdvanced by remember {
            mutableStateOf(false)
        }

        AnimatedVisibility(!showAdvanced) {
            TextButton(
                modifier = Modifier.padding(top = 16.dp),
                onClick = { showAdvanced = true }) {
                Text(stringResource(id = R.string.websearch_dialog_advanced))
            }
        }

        AnimatedVisibility(showAdvanced) {
            IntentExtrasEditor(viewModel)
        }
    }
}

@Composable
fun PickIcon(viewModel: EditSearchActionSheetVM, paddingValues: PaddingValues) {
    val action by viewModel.searchAction

    val iconSizePx = 20.dp.toPixels()

    val pickIconLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
            if (it != null) viewModel.importIcon(it, iconSizePx.toInt())
        }

    if (action?.customIcon == null) {

        val availableIcons =
            remember { SearchActionIcon.entries.filter { it != SearchActionIcon.Custom } }

        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            LazyVerticalGrid(columns = GridCells.Adaptive(64.dp)) {
                if (action is AppSearchActionBuilder) {
                    item {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val isSelected =
                                action?.icon == SearchActionIcon.Custom && action?.customIcon == null
                            SearchActionIconTile(isSelected, onClick = {
                                viewModel.setCustomIcon(null)
                            }) {
                                SearchActionIcon(
                                    icon = SearchActionIcon.Custom,
                                    componentName = (action as AppSearchActionBuilder).baseIntent.component,
                                    size = 24.dp,
                                    color = 1,
                                )
                            }
                        }
                    }
                }
                items(availableIcons) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val isSelected = action?.icon == it
                        SearchActionIconTile(isSelected, onClick = {
                            viewModel.setIcon(it)
                        }) {
                            SearchActionIcon(
                                icon = it,
                                size = 24.dp,
                                color = 0,
                            )
                        }
                    }
                }
            }
            TextButton(
                modifier = Modifier.padding(vertical = 8.dp),
                onClick = { pickIconLauncher.launch("image/*") }) {
                Text(stringResource(R.string.websearch_dialog_custom_icon))
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()

        ) {
            SearchActionIconTile {
                SearchActionIcon(builder = action!!, size = 24.dp)
            }
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.padding(end = 16.dp),
                    text = "Monochrome",
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium,
                )
                Switch(
                    checked = action?.iconColor == 0,
                    onCheckedChange = { viewModel.setIconColor(if (it) 0 else 1) })
            }
            Row(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 16.dp,
                    alignment = Alignment.End
                )
            ) {
                OutlinedButton(
                    onClick = { pickIconLauncher.launch("image/*") }) {
                    Text(stringResource(R.string.websearch_dialog_replace_icon))
                }
                OutlinedButton(
                    onClick = { viewModel.setIcon(SearchActionIcon.Search) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.websearch_dialog_delete_icon))
                }
            }
        }
    }
}

@Composable
private fun SearchActionIconTile(
    filled: Boolean = true,
    onClick: () -> Unit = {},
    icon: @Composable () -> Unit,
) {

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (filled) Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                else Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.medium
                )
            )
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            icon()
        }
    }
}

@Composable
private fun IntentExtrasEditor(viewModel: EditSearchActionSheetVM) {

    val action = viewModel.searchAction.value
    val extras = remember(action?.key) {
        when (action) {
            is CustomIntentActionBuilder -> action.baseIntent.extras
            is AppSearchActionBuilder -> action.baseIntent.extras
            else -> null
        }

    }

    val keys = remember(extras) { extras?.keySet()?.sorted() ?: emptyList() }

    Column(
        modifier = Modifier.padding(top = 24.dp)
    ) {
        Text("Extras", style = MaterialTheme.typography.titleSmall)
        for (key in keys) {
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val value = extras?.get(key)
                when (value) {
                    is String -> {
                        OutlinedTextField(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 8.dp),
                            value = value,
                            onValueChange = { viewModel.putStringExtra(key, it) },
                            label = { Text(key) },
                            leadingIcon = {
                                Text("ABC", style = MaterialTheme.typography.labelSmall)
                            },
                            singleLine = true,
                        )
                    }

                    is Long -> {
                        OutlinedTextField(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 8.dp),
                            value = value.toString(),
                            onValueChange = {
                                viewModel.putLongExtra(
                                    key,
                                    it.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0
                                )
                            },
                            label = { Text(key) },
                            leadingIcon = {
                                Text("1234", style = MaterialTheme.typography.labelSmall)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }

                    is Int -> {
                        OutlinedTextField(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 8.dp),
                            value = value.toString(),
                            onValueChange = {
                                viewModel.putIntExtra(
                                    key,
                                    it.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                                )
                            },
                            label = { Text(key) },
                            leadingIcon = {
                                Text("123", style = MaterialTheme.typography.labelSmall)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }

                    is Double -> {
                        OutlinedTextField(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 8.dp),
                            value = value.toString(),
                            onValueChange = {
                                viewModel.putDoubleExtra(
                                    key,
                                    it.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                                )
                            },
                            label = { Text(key) },
                            leadingIcon = {
                                Text("1.00", style = MaterialTheme.typography.labelSmall)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }

                    is Float -> {
                        OutlinedTextField(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 8.dp),
                            value = value.toString(),
                            onValueChange = {
                                viewModel.putFloatExtra(
                                    key,
                                    it.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
                                )
                            },
                            label = { Text(key) },
                            leadingIcon = {
                                Text("1.0", style = MaterialTheme.typography.labelSmall)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }

                    is Boolean -> {
                        Switch(
                            checked = value,
                            onCheckedChange = { viewModel.putBooleanExtra(key, it) })
                        Text(
                            text = key,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.removeExtra(key) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.RemoveCircleOutline, contentDescription = null)
                }
            }
        }


        OutlinedCard(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {

            var newKey by remember { mutableStateOf("") }
            var newType by remember { mutableStateOf("string") }
            var showTypeDropdown by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                FilledTonalIconButton(
                    modifier = Modifier.padding(end = 8.dp),
                    onClick = { showTypeDropdown = !showTypeDropdown }
                ) {
                    when (newType) {
                        "bool" -> {
                            Icon(Icons.Rounded.ToggleOn, contentDescription = null)
                        }

                        "string" -> {
                            Text("ABC", style = MaterialTheme.typography.labelSmall)
                        }

                        "int" -> {
                            Text("123", style = MaterialTheme.typography.labelSmall)
                        }

                        "long" -> {
                            Text("1234", style = MaterialTheme.typography.labelSmall)
                        }

                        "float" -> {
                            Text("1.0", style = MaterialTheme.typography.labelSmall)
                        }

                        "double" -> {
                            Text("1.00", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    DropdownMenu(
                        expanded = showTypeDropdown,
                        onDismissRequest = { showTypeDropdown = false }) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Text(
                                    "ABC",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            text = { Text("String") },
                            onClick = {
                                newType = "string"
                                showTypeDropdown = false
                            })
                        DropdownMenuItem(
                            leadingIcon = {
                                Text(
                                    "123",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            text = { Text("Integer") },
                            onClick = {
                                newType = "int"
                                showTypeDropdown = false
                            })
                        DropdownMenuItem(
                            leadingIcon = {
                                Text(
                                    "1234",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            text = { Text("Long") },
                            onClick = {
                                newType = "long"
                                showTypeDropdown = false
                            })
                        DropdownMenuItem(
                            leadingIcon = {
                                Text(
                                    "1.0",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            text = { Text("Float") },
                            onClick = {
                                newType = "float"
                                showTypeDropdown = false
                            })
                        DropdownMenuItem(
                            leadingIcon = {
                                Text(
                                    "1.00",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            text = { Text("Double") },
                            onClick = {
                                newType = "double"
                                showTypeDropdown = false
                            })
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Rounded.ToggleOn, null) },
                            text = { Text("Boolean") },
                            onClick = {
                                newType = "bool"
                                showTypeDropdown = false
                            })
                    }
                }
                OutlinedTextField(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 8.dp),
                    label = { Text("Key") },
                    value = newKey,
                    onValueChange = { newKey = it },
                    singleLine = true,
                )
                FilledIconButton(
                    modifier = Modifier.padding(start = 8.dp),
                    onClick = {
                        when (newType) {
                            "string" -> viewModel.putStringExtra(newKey)
                            "int" -> viewModel.putIntExtra(newKey)
                            "bool" -> viewModel.putBooleanExtra(newKey)
                            "long" -> viewModel.putLongExtra(newKey)
                            "double" -> viewModel.putDoubleExtra(newKey)
                            "float" -> viewModel.putFloatExtra(newKey)
                        }
                        newKey = ""
                    },
                    enabled = newKey.isNotBlank()
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                }
            }
        }
    }
}