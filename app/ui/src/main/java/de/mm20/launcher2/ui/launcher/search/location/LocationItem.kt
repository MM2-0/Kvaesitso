package de.mm20.launcher2.ui.launcher.search.location

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.animation.animateTextStyleAsState
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import java.text.DecimalFormat
import kotlin.math.roundToInt

@Composable
fun LocationItem(
    modifier: Modifier = Modifier,
    location: Location,
    showDetails: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${location.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    LaunchedEffect(location) {
        viewModel.init(location, iconSize.toInt())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = LocalSnackbarHostState.current

    val darkMode = LocalDarkTheme.current
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Row(modifier = modifier) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row {
                val padding by animateDpAsState(if (showDetails) 16.dp else 12.dp)
                Column(
                    modifier = Modifier.padding(
                        top = padding,
                        start = padding,
                        bottom = 12.dp,
                        end = padding
                    )
                ) {
                    val textStyle by animateTextStyleAsState(
                        if (showDetails) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleSmall
                    )
                    Text(text = location.labelOverride ?: location.label, style = textStyle)
                    AnimatedVisibility(!showDetails) {
                        Text(
                            modifier = Modifier.padding(top = 2.dp),
                            text = location.getSummary(context),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    AnimatedVisibility(showDetails) {
                        val tags by viewModel.tags.collectAsState(emptyList())
                        if (tags.isNotEmpty()) {
                            Text(
                                modifier = Modifier.padding(top = 1.dp, bottom = 4.dp),
                                text = tags.joinToString(separator = " #", prefix = "#"),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(showDetails) {
                Column {
                    /*
                    Row(
                        Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null
                        )
                        Text(
                            text = calendar.formatTime(context),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (calendar.description != null) {
                        Row(
                            Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                                imageVector = Icons.Rounded.Notes,
                                contentDescription = null
                            )
                            Text(
                                text = calendar.description!!,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (calendar.attendees.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                                imageVector = Icons.Rounded.People,
                                contentDescription = null
                            )
                            Text(
                                text = calendar.attendees.joinToString(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (calendar.location != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    calendar.openLocation(context)
                                }
                        ) {
                            Icon(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                                imageVector = Icons.Rounded.Place,
                                contentDescription = null
                            )
                            Text(
                                text = calendar.location!!,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    val toolbarActions = mutableListOf<ToolbarAction>()

                    if (LocalFavoritesEnabled.current) {
                        val isPinned by viewModel.isPinned.collectAsState(false)
                        val favAction = if (isPinned) {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_unpin),
                                icon = Icons.Rounded.Star,
                                action = {
                                    viewModel.unpin()
                                }
                            )
                        } else {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_pin),
                                icon = Icons.Rounded.StarOutline,
                                action = {
                                    viewModel.pin()
                                })
                        }
                        toolbarActions.add(favAction)
                    }

                    val isHidden by viewModel.isHidden.collectAsState(false)
                    val hideAction = if (isHidden) {
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_unhide),
                            icon = Icons.Rounded.Visibility,
                            action = {
                                viewModel.unhide()
                                onBack()
                            }
                        )
                    } else {
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_hide),
                            icon = Icons.Rounded.VisibilityOff,
                            action = {
                                viewModel.hide()
                                onBack()
                                lifecycleOwner.lifecycleScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(
                                            R.string.msg_item_hidden,
                                            calendar.label
                                        ),
                                        actionLabel = context.getString(R.string.action_undo),
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.unhide()
                                    }
                                }
                            })
                    }

                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_calendar_open_externally),
                            icon = Icons.Rounded.OpenInNew,
                            action = {
                                viewModel.launch(context)
                                onBack()
                            }
                        )
                    )

                    val sheetManager = LocalBottomSheetManager.current
                    toolbarActions.add(DefaultToolbarAction(
                        label = stringResource(R.string.menu_customize),
                        icon = Icons.Rounded.Edit,
                        action = { sheetManager.showCustomizeSearchableModal(calendar) }
                    ))

                    toolbarActions.add(hideAction)

                    Toolbar(
                        leftActions = listOf(
                            DefaultToolbarAction(
                                label = stringResource(id = R.string.menu_back),
                                icon = Icons.Rounded.ArrowBack
                            ) {
                                onBack()
                            }
                        ),
                        rightActions = toolbarActions
                    )
                    */
                }
            }
        }
    }
}

private fun Location.getSummary(context: Context): String {
    val summary = StringBuilder()
    if (this.category != null) {
        summary.append(this.category) // TODO Localize
        summary.append(' ')
    }
    if (this.distanceMeters != null) {
        val isKm = this.distanceMeters!! > 1000
        val value =
            if (isKm) DecimalFormat().apply { maximumFractionDigits = 1; minimumFractionDigits = 0 }
                .format(this.distanceMeters!! / 1000.0)
            else this.distanceMeters!!.roundToInt().toString()
        val unit =
            context.getString(if (isKm) R.string.unit_kilometer_symbol else R.string.unit_meter_symbol)
        summary.append(value, ' ', unit, ' ')
    }
    if (this.street != null) {
        summary.append(this.street)
        summary.append(" ")
        if (this.houseNumber != null) {
            summary.append(this.houseNumber)
            summary.append(' ')
        }
    }
    if (!this.openingHours.isNullOrEmpty()) {
        val isOpen = this.openingHours!!.any { it.isOpen }
        summary.append(context.getString(if (isOpen) R.string.location_open else R.string.location_closed))
    }
    return summary.toString()
}
