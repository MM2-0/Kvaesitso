package de.mm20.launcher2.ui.launcher.search.common.list

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.data.*
import de.mm20.launcher2.ui.component.InnerCard
import de.mm20.launcher2.ui.launcher.search.calendar.CalendarItem
import de.mm20.launcher2.ui.launcher.search.contacts.ContactItem
import de.mm20.launcher2.ui.launcher.search.files.FileItem
import de.mm20.launcher2.ui.launcher.search.shortcut.AppShortcutItem

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    item: SavableSearchable,
    highlight: Boolean = false
) {
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