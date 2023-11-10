package de.mm20.launcher2.ui.launcher.search.common.list

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.Contact
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.component.InnerCard
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.calendar.CalendarItem
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.contacts.ContactItem
import de.mm20.launcher2.ui.launcher.search.files.FileItem
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.launcher.search.location.LocationItem
import de.mm20.launcher2.ui.launcher.search.shortcut.AppShortcutItem
import de.mm20.launcher2.ui.locals.LocalGridSettings

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    item: SavableSearchable,
    highlight: Boolean = false,
    priorityCallback: ((key: String, priority: Int) -> Unit)? = null
) {
    var showDetails by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${item.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    LaunchedEffect(item, iconSize) {
        viewModel.init(item, iconSize.toInt())
    }

    var bounds by remember { mutableStateOf(Rect.Zero) }
    InnerCard(
        modifier = modifier
            .onGloballyPositioned {
                bounds = it.boundsInWindow()
            },
        highlight = highlight,
        raised = showDetails
    ) {
        when (item) {
            is Contact -> {
                ContactItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            enabled = !showDetails,
                            onClick = { showDetails = true },
                            onLongClick = { showDetails = true }
                        ),
                    calendar = item,
                    showDetails = showDetails,
                    onBack = { showDetails = false }
                )
            }

            is Location -> {
                LocationItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            enabled = !showDetails,
                            onClick = { showDetails = true },
                            onLongClick = { showDetails = true }),
                    location = item,
                    showDetails = showDetails,
                    onBack = { showDetails = false },
                    priorityCallback = priorityCallback
                )
            }

            is AppShortcut -> {
                AppShortcutItem(
                    shortcut = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
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