package de.mm20.launcher2.ui.launcher.search.contacts

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.search.data.Contact
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.animation.animateTextStyleAsState
import de.mm20.launcher2.ui.component.*
import de.mm20.launcher2.ui.icons.Telegram
import de.mm20.launcher2.ui.icons.WhatsApp
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.customattrs.CustomizeSearchableSheet
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridIconSize
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
    val viewModel = remember(contact) { ContactItemVM(contact) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = LocalSnackbarHostState.current

    var edit by remember { mutableStateOf(false) }

    val transition = updateTransition(showDetails, label = "ContactItem")

    Column(
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconSize = 48.dp.toPixels().toInt()
            val icon by remember(contact) { viewModel.getIcon(iconSize) }.collectAsState(null)
            val padding by transition.animateDp(label = "iconPadding") {
                if (it) 16.dp else 8.dp
            }
            ShapedLauncherIcon(
                size = 48.dp,
                modifier = Modifier
                    .padding(start = padding, top = padding, bottom = padding),
                icon = icon,
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                val textStyle by animateTextStyleAsState(
                    if (showDetails) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.titleSmall
                )
                Text(
                    text = contact.label,
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
                                        viewModel.contact(context, it)
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
                                        viewModel.contact(context, it)
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
                                        viewModel.contact(context, it)
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
                                        viewModel.contact(context, it)
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
                                        viewModel.contact(context, it)
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
                                    message = context.getString(R.string.msg_item_hidden, contact.label),
                                    actionLabel = context.getString(R.string.action_undo),
                                    duration = SnackbarDuration.Short,
                                    )
                                if(result == SnackbarResult.ActionPerformed) {
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
                        }
                    )
                )

                toolbarActions.add(DefaultToolbarAction(
                    label = stringResource(R.string.menu_customize),
                    icon = Icons.Rounded.Edit,
                    action = { edit = true }
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

    if (edit) {
        CustomizeSearchableSheet(
            searchable = contact,
            onDismiss = { edit = false }
        )
    }
}

@Composable
fun ContactItemGridPopup(
    contact: Contact,
    show: Boolean,
    animationProgress: Float,
    origin: Rect,
    onDismiss: () -> Unit
) {
    AnimatedContent(
        targetState = show,
        transitionSpec = {
            fadeIn(snap()) with
                    fadeOut(snap(400)) using
                    SizeTransform { _, _ ->
                        tween(300)
                    }
        }
    ) { targetState ->
        if (targetState) {
            ContactItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(
                        1 - (1 - LocalGridIconSize.current / 48.dp) * (1 - animationProgress),
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
        } else {
            Box(
                modifier = Modifier
                    .requiredWidth(origin.width.toDp())
                    .requiredHeight(origin.height.toDp())
            )
        }
    }
}