package de.mm20.launcher2.ui.launcher.search.common.list

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.search.data.*
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.InnerCard
import de.mm20.launcher2.ui.launcher.search.calendar.CalendarItem
import de.mm20.launcher2.ui.launcher.search.contacts.ContactItem
import de.mm20.launcher2.ui.launcher.search.files.FileItem
import de.mm20.launcher2.ui.launcher.search.shortcut.AppShortcutItem
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ListItem(modifier: Modifier = Modifier, item: Searchable) {
    var showDetails by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val viewModel = remember(item.key) { ListItemVM(item) }

    var bounds by remember { mutableStateOf(Rect.Zero) }
    InnerCard(
        modifier = modifier
            .onGloballyPositioned {
                bounds = it.boundsInWindow()
            },
        raised = showDetails
    ) {
        when (item) {
            is Contact -> {
                ContactItem(
                    modifier = Modifier.combinedClickable(
                        enabled = !showDetails,
                        onClick = { showDetails = true },
                        onLongClick = { showDetails = true }
                    ),
                    contact = item,
                    showDetails = showDetails,
                    onBack = { showDetails = false }
                )
            }
            is File -> {
                FileItem(
                    modifier = Modifier.combinedClickable(
                        enabled = !showDetails,
                        onClick = {
                            if (!viewModel.launch(context, bounds)) {
                                showDetails = true
                            }
                        },
                        onLongClick = { showDetails = true }
                    ),
                    file = item,
                    showDetails = showDetails,
                    onBack = { showDetails = false }
                )
            }
            is CalendarEvent -> {
                CalendarItem(
                    modifier = Modifier.combinedClickable(
                        enabled = !showDetails,
                        onClick = { showDetails = true },
                        onLongClick = { showDetails = true }
                    ),
                    calendar = item,
                    showDetails = showDetails,
                    onBack = { showDetails = false }
                )
            }
            is AppShortcut -> {
                AppShortcutItem(
                    shortcut = item,
                    modifier = Modifier.combinedClickable(
                        enabled = !showDetails,
                        onClick = {
                            if (!viewModel.launch(context, bounds)) {
                                showDetails = true
                            }
                        },
                        onLongClick = { showDetails = true }
                    ),
                    showDetails = showDetails,
                    onBack = { showDetails = false }
                )
            }
        }
    }
}