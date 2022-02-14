package de.mm20.launcher2.ui.launcher.search.common.list

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Contact
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.component.InnerCard
import de.mm20.launcher2.ui.launcher.search.calendar.CalendarItem
import de.mm20.launcher2.ui.launcher.search.contacts.ContactItem
import de.mm20.launcher2.ui.launcher.search.files.FileItem
import de.mm20.launcher2.ui.locals.LocalCardStyle
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled

@OptIn(ExperimentalMaterialApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ListItem(modifier: Modifier = Modifier, item: Searchable) {
    var showDetails by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val viewModel = remember(item.key) { ListItemVM(item) }
    val isPinned by viewModel.isPinned.collectAsState(false)
    val isHidden by viewModel.isHidden.collectAsState(false)

    val dismissState = rememberDismissState(
        confirmStateChange = {
            when (it) {
                DismissValue.Default -> {}
                DismissValue.DismissedToEnd -> {
                    if (isPinned) viewModel.unpin()
                    else viewModel.pin()
                }
                DismissValue.DismissedToStart -> {
                    if (isHidden) viewModel.unhide()
                    else viewModel.hide()
                }
            }
            it == DismissValue.DismissedToStart
        }
    )

    val swipeDirections = when {
        showDetails -> emptySet()
        LocalFavoritesEnabled.current -> setOf(
            DismissDirection.StartToEnd,
            DismissDirection.EndToStart
        )
        else -> setOf(DismissDirection.EndToStart)
    }

    SwipeToDismiss(
        modifier = modifier,
        state = dismissState,
        directions = swipeDirections,
        background = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(LocalCardStyle.current.radius.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = (dismissState.progress.fraction * 2f).coerceAtMost(
                                    1f
                                )
                            )
                        ),
                    contentAlignment = if (dismissState.dismissDirection == DismissDirection.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    val overThreshold =
                        dismissState.progress.fraction >= 0.5f && dismissState.dismissDirection != null
                    val iconScale by animateFloatAsState(
                        if (overThreshold) 1.25f else 1f
                    )
                    val iconColor by animateColorAsState(
                        if (overThreshold) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    val pinIcon = if (isPinned) Icons.Rounded.StarOutline else Icons.Rounded.Star
                    val hideIcon =
                        if (isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff
                    Icon(
                        modifier = Modifier
                            .padding(16.dp)
                            .scale(iconScale),
                        imageVector = if (dismissState.dismissDirection == DismissDirection.EndToStart) {
                            hideIcon
                        } else {
                            pinIcon
                        },
                        tint = iconColor,
                        contentDescription = null
                    )
                }
            }
        }
    ) {
        var bounds by remember { mutableStateOf(Rect.Zero) }
        InnerCard(
            modifier = Modifier
                .fillMaxWidth()
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
                                if (!viewModel.launch(context as AppCompatActivity, bounds)) {
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
            }
        }

    }
}