package de.mm20.launcher2.ui.launcher.search.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.data.Contact
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.animation.animateTextStyleAsState
import de.mm20.launcher2.ui.component.Chip
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.icons.Signal
import de.mm20.launcher2.ui.icons.Telegram
import de.mm20.launcher2.ui.icons.WhatsApp
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.launch

@Composable
fun ContactItem(
    modifier: Modifier = Modifier,
    contact: Contact,
    showDetails: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${contact.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    LaunchedEffect(contact, iconSize) {
        viewModel.init(contact, iconSize.toInt())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = LocalSnackbarHostState.current

    val transition = updateTransition(showDetails, label = "ContactItem")

    Column(
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon by viewModel.icon.collectAsStateWithLifecycle()
            val padding by transition.animateDp(label = "iconPadding") {
                if (it) 16.dp else 8.dp
            }
            ShapedLauncherIcon(
                size = 48.dp,
                modifier = Modifier
                    .padding(start = padding, top = padding, bottom = padding),
                icon = { icon },
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                val textStyle by animateTextStyleAsState(
                    if (showDetails) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.titleSmall
                )
                Text(
                    text = contact.labelOverride ?: contact.label,
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(!showDetails) {
                    Text(
                        contact.summary,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AnimatedVisibility(showDetails) {
                    val tags by viewModel.tags.collectAsState(emptyList())
                    if (tags.isNotEmpty()) {
                        Text(
                            modifier = Modifier.padding(top = 1.dp),
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

                if (contact.phones.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null)
                        LazyRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(contact.phones.toList()) {
                                Chip(
                                    modifier = Modifier.padding(end = 16.dp),
                                    text = it.label,
                                    onClick = {
                                        context.tryStartActivity(
                                            Intent(Intent.ACTION_VIEW).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                .setData(Uri.parse(it.data))
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                if (contact.emails.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Email, contentDescription = null)
                        LazyRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(contact.emails.toList()) {
                                Chip(
                                    modifier = Modifier.padding(end = 16.dp),
                                    text = it.label,
                                    onClick = {
                                        context.tryStartActivity(
                                            Intent(Intent.ACTION_VIEW).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                .setData(Uri.parse(it.data))
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                if (contact.signal.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Signal, contentDescription = null)
                        LazyRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(contact.signal.toList()) {
                                Chip(
                                    modifier = Modifier.padding(end = 16.dp),
                                    text = it.label,
                                    onClick = {
                                        context.tryStartActivity(
                                            Intent(Intent.ACTION_VIEW).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                .setData(Uri.parse(it.data))
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                if (contact.telegram.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Telegram, contentDescription = null)
                        LazyRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(contact.telegram.toList()) {
                                Chip(
                                    modifier = Modifier.padding(end = 16.dp),
                                    text = it.label,
                                    onClick = {
                                        context.tryStartActivity(
                                            Intent(Intent.ACTION_VIEW).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                .setData(Uri.parse(it.data))
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                if (contact.whatsapp.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.WhatsApp, contentDescription = null)
                        LazyRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(contact.whatsapp.toList()) {
                                Chip(
                                    modifier = Modifier.padding(end = 16.dp),
                                    text = it.label,
                                    onClick = {
                                        context.tryStartActivity(
                                            Intent(Intent.ACTION_VIEW).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                .setData(Uri.parse(it.data))
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                if (contact.postals.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Place, contentDescription = null)
                        LazyRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(contact.postals.toList()) {
                                Chip(
                                    modifier = Modifier.padding(end = 16.dp),
                                    text = it.label,
                                    onClick = {
                                        context.tryStartActivity(
                                            Intent(Intent.ACTION_VIEW).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                .setData(Uri.parse(it.data))
                                        )
                                    }
                                )
                            }
                        }
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
                                        contact.label
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
                        label = stringResource(R.string.menu_contacts_open_externally),
                        icon = Icons.Rounded.OpenInNew,
                        action = {
                            viewModel.launch(context)
                        }
                    )
                )

                val sheetManager = LocalBottomSheetManager.current
                toolbarActions.add(DefaultToolbarAction(
                    label = stringResource(R.string.menu_customize),
                    icon = Icons.Rounded.Edit,
                    action = { sheetManager.showCustomizeSearchableModal(contact) }
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
            }
        }
    }
}

@Composable
fun ContactItemGridPopup(
    contact: Contact,
    show: MutableTransitionState<Boolean>,
    animationProgress: Float,
    origin: Rect,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.TopStart,
        ) { origin.roundToIntRect().size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.TopStart,
        ) { origin.roundToIntRect().size },
    ) {
        ContactItem(
            modifier = Modifier
                .fillMaxWidth()
                .scale(
                    1 - (1 - LocalGridSettings.current.iconSize / 48f) * (1 - animationProgress),
                    transformOrigin = TransformOrigin(0f, 0f)
                )
                .offset(
                    x = -16.dp * (1 - animationProgress),
                    y = -16.dp * (1 - animationProgress)
                ),
            contact = contact,
            showDetails = true,
            onBack = onDismiss
        )
    }
}