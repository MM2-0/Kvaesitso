package de.mm20.launcher2.ui.settings.filterbar

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.preferences.KeyboardFilterBarItem
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.dragndrop.DraggableItem
import de.mm20.launcher2.ui.component.dragndrop.LazyDragAndDropColumn
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropListState
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.launcher.search.filters.getLabel
import de.mm20.launcher2.ui.launcher.search.filters.iconMedium
import de.mm20.launcher2.ui.launcher.search.filters.isCategory
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun FilterBarSettingsScreen() {
    val viewModel: FilterBarSettingsScreenVM = viewModel()
    val navController = LocalNavController.current
    val systemUiController = rememberSystemUiController()
    systemUiController.setStatusBarColor(MaterialTheme.colorScheme.surface)
    systemUiController.setNavigationBarColor(Color.Black)

    val context = LocalContext.current
    val activity = LocalContext.current as? AppCompatActivity

    val listState = rememberLazyDragAndDropListState(
        onDragStart = {
            it.key is KeyboardFilterBarItem
        },
        onItemMove = { from, to ->
            val item =
                (from.key as? KeyboardFilterBarItem) ?: return@rememberLazyDragAndDropListState
            val toItem =
                (to.key as? KeyboardFilterBarItem) ?: return@rememberLazyDragAndDropListState
            viewModel.moveItem(item, toItem)
        }
    )

    val enabledItems by viewModel.filterBarItems.collectAsState()

    val disabledItems by remember {
        derivedStateOf {
            KeyboardFilterBarItem.entries.filter { enabledItems?.contains(it) == false }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.preference_customize_filter_bar),
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (navController?.navigateUp() != true) {
                            activity?.onBackPressed()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
            )
        }) {

        if (enabledItems == null) {
            return@Scaffold
        }

        LazyDragAndDropColumn(
            state = listState,
            bidirectionalDrag = false,
            contentPadding = it,
            modifier = Modifier
                .fillMaxSize()
        ) {
            item(
                key = "disabled-info"
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 28.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.info_20px), null,
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        modifier = Modifier,
                        text = stringResource(R.string.hint_drag_and_drop_reorder),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            for (i in 0 until KeyboardFilterBarItem.entries.size) {
                val item = enabledItems!!.getOrNull(i) ?: disabledItems[i - enabledItems!!.size]
                val prevItem = enabledItems!!.getOrNull(i - 1)
                    ?: disabledItems.getOrNull(i - enabledItems!!.size - 1)
                if (prevItem != null && prevItem.isCategory != item.isCategory) {
                    item(key = "divider-$i") {
                        HorizontalDivider()
                    }
                }
                item(key = item) {
                    DraggableItem(state = listState, key = item) {
                        val elevation by animateDpAsState(if (it) 4.dp else 0.dp)
                        Surface(
                            shadowElevation = elevation,
                            tonalElevation = elevation,
                            modifier = Modifier.zIndex(if (it) 1f else 0f)
                        ) {
                            SwitchPreference(
                                title = item.getLabel(context),
                                icon = item.iconMedium,
                                value = enabledItems!!.contains(item), onValueChanged = {
                                    if (it) {
                                        viewModel.addAction(item)
                                    } else {
                                        viewModel.removeAction(item)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}