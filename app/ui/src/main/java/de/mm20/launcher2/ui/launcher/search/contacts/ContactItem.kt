package de.mm20.launcher2.ui.launcher.search.contacts

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.Contact
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.modifier.scale
import androidx.core.net.toUri
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.ktx.getApplicationIconOrNull
import de.mm20.launcher2.ktx.getApplicationInfoOrNull
import de.mm20.launcher2.search.contact.ContactInfoType

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

    val icon by viewModel.icon.collectAsStateWithLifecycle()
    val callOnTap by viewModel.callOnTap.collectAsStateWithLifecycle(false)
    val badge by viewModel.badge.collectAsState(null)

    SharedTransitionLayout {
        AnimatedContent(showDetails) { showDetails ->
            if (showDetails) {
                Column {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShapedLauncherIcon(
                            size = 48.dp,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .sharedElement(
                                    rememberSharedContentState("icon"),
                                    this@AnimatedContent,
                                ),
                            icon = { icon },
                            badge = { badge }
                        )
                        Text(
                            text = contact.labelOverride ?: contact.label,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.sharedBounds(
                                rememberSharedContentState("label"),
                                this@AnimatedContent,
                            ),
                        )
                    }

                    val canNavigate = remember {
                        context.packageManager.queryIntentActivities(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("google.navigation:q=")
                            ),
                            0
                        ).isNotEmpty()
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        var expandedSection by remember { mutableStateOf(-1) }
                        if (contact.phoneNumbers.isNotEmpty()) {
                            ContactInfo(
                                icon = R.drawable.call_24px,
                                label = pluralStringResource(
                                    R.plurals.contact_phone_numbers,
                                    contact.phoneNumbers.size,
                                    contact.phoneNumbers.size
                                ),
                                items = contact.phoneNumbers,
                                itemLabel = { it.number },
                                itemSubLabel = { it.type.toString(context) },
                                expanded = expandedSection == 0,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .fillMaxWidth(),
                                secondaryAction = {
                                    IconButton(onClick = {
                                        viewModel.reportUsage(contact)
                                        context.tryStartActivity(
                                            Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("smsto:${it.number}")
                                            }
                                        )
                                    }) {
                                        Icon(
                                            painterResource(R.drawable.sms_24px),
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onExpand = {
                                    expandedSection = if (it) 0 else -1
                                },
                                onContact = {
                                    viewModel.reportUsage(contact)
                                    context.tryStartActivity(
                                        Intent(
                                            if (callOnTap)
                                                Intent.ACTION_CALL
                                            else
                                                Intent.ACTION_DIAL
                                        ).setData("tel:${it.number}".toUri())
                                    )
                                },
                                copyText = { it.number },
                            )
                        }
                        if (contact.emailAddresses.isNotEmpty()) {
                            ContactInfo(
                                icon = R.drawable.mail_24px,
                                label = pluralStringResource(
                                    R.plurals.contact_email_addresses,
                                    contact.emailAddresses.size,
                                    contact.emailAddresses.size
                                ),
                                items = contact.emailAddresses,
                                itemLabel = { it.address },
                                itemSubLabel = { it.type.toString(context) },
                                expanded = expandedSection == 1,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .fillMaxWidth(),
                                onExpand = {
                                    expandedSection = if (it) 1 else -1
                                },
                                onContact = {
                                    viewModel.reportUsage(contact)
                                    context.tryStartActivity(
                                        Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:${it.address}")
                                        }
                                    )
                                },
                                copyText = { it.address },
                            )
                        }
                        if (contact.postalAddresses.isNotEmpty()) {
                            ContactInfo(
                                icon = R.drawable.location_on_24px,
                                label = pluralStringResource(
                                    R.plurals.contact_postal_addresses,
                                    contact.postalAddresses.size,
                                    contact.postalAddresses.size
                                ),
                                items = contact.postalAddresses,
                                itemLabel = { it.address },
                                itemSubLabel = { it.type.toString(context) },
                                secondaryAction = if (canNavigate) {
                                    {
                                        IconButton(onClick = {
                                            viewModel.reportUsage(contact)
                                            context.tryStartActivity(
                                                Intent(Intent.ACTION_VIEW).apply {
                                                    data =
                                                        Uri.parse("google.navigation:q=${it.address}")
                                                }
                                            )
                                        }) {
                                            Icon(
                                                painterResource(R.drawable.directions_24px),
                                                null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                } else null,
                                expanded = expandedSection == 2,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .fillMaxWidth(),
                                onExpand = {
                                    expandedSection = if (it) 2 else -1
                                },
                                onContact = {
                                    viewModel.reportUsage(contact)
                                    context.tryStartActivity(
                                        Intent(Intent.ACTION_VIEW).apply {
                                            data = Uri.parse("geo:0,0?q=${it.address}")
                                        }
                                    )
                                },
                                copyText = { it.address },
                            )
                        }
                        val apps = remember(contact) {
                            contact.customActions.groupBy { it.packageName }
                        }
                        for ((i, app) in apps.entries.withIndex()) {
                            val packageName = app.key
                            val packageInfo = remember(packageName) {
                                context.packageManager.getApplicationInfoOrNull(packageName)
                            } ?: continue

                            val appIcon = remember(packageName) {
                                context.packageManager.getApplicationIconOrNull(packageName)
                            }
                            val label = remember(app) {
                                packageInfo.loadLabel(context.packageManager).toString()
                            }
                            val itemsWithPermission = remember(app) {
                                app.value.filter {
                                    // exclude activities we have no permission for
                                    val resolvedActivityInfo = context.packageManager.resolveActivity(
                                        Intent(Intent.ACTION_VIEW).setPackage(it.packageName).setDataAndType(it.uri, it.mimeType),
                                        0
                                    )?.activityInfo ?: return@filter false

                                    resolvedActivityInfo.permission == null || context.checkPermission(resolvedActivityInfo.permission)
                                }
                            }

                            if (itemsWithPermission.isEmpty()) continue

                            ContactInfo(
                                icon = R.drawable.open_in_new_24px,
                                customIcon = appIcon,
                                label = label,
                                items = itemsWithPermission,
                                itemLabel = { it.label },
                                expanded = expandedSection == 3 + i,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .fillMaxWidth(),
                                onExpand = {
                                    expandedSection = if (it) 3 + i else -1
                                },
                                onContact = {
                                    viewModel.reportUsage(contact)
                                    context.tryStartActivity(
                                        Intent(Intent.ACTION_VIEW).apply {
                                            setPackage(packageName)
                                            setDataAndType(
                                                it.uri,
                                                it.mimeType
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    }

                    val toolbarActions = mutableListOf<ToolbarAction>()

                    if (LocalFavoritesEnabled.current) {
                        val isPinned by viewModel.isPinned.collectAsState(false)
                        val favAction = if (isPinned) {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_unpin),
                                icon = R.drawable.star_24px_filled,
                                action = {
                                    viewModel.unpin()
                                }
                            )
                        } else {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_pin),
                                icon = R.drawable.star_24px,
                                action = {
                                    viewModel.pin()
                                })
                        }
                        toolbarActions.add(favAction)
                    }

                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_contacts_open_externally),
                            icon = R.drawable.open_in_new_24px,
                            action = {
                                viewModel.launch(context)
                            }
                        )
                    )

                    val sheetManager = LocalBottomSheetManager.current
                    toolbarActions.add(DefaultToolbarAction(
                        label = stringResource(R.string.menu_customize),
                        icon = R.drawable.tune_24px,
                        action = { sheetManager.showCustomizeSearchableModal(contact) }
                    ))


                    Toolbar(
                        leftActions = listOf(
                            DefaultToolbarAction(
                                label = stringResource(id = R.string.menu_back),
                                icon = R.drawable.arrow_back_24px,
                            ) {
                                onBack()
                            }
                        ),
                        rightActions = toolbarActions
                    )
                }
            } else {
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(
                            start = 8.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                            end = 16.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShapedLauncherIcon(
                        size = 48.dp,
                        modifier = Modifier
                            .padding(8.dp)
                            .sharedElement(
                                rememberSharedContentState("icon"),
                                this@AnimatedContent,
                            ),
                        icon = { icon },
                        badge = { badge }
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        Text(
                            modifier = Modifier.sharedBounds(
                                rememberSharedContentState("label"),
                                this@AnimatedContent,
                            ),
                            text = contact.labelOverride ?: contact.label,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            contact.summary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ContactInfo(
    label: String,
    modifier: Modifier = Modifier,
    items: List<T>,
    itemLabel: (T) -> String,
    itemSubLabel: (T) -> String? = { null },
    itemIcon: (T) -> Int? = { null },
    secondaryAction: (@Composable (T) -> Unit)? = null,
    @DrawableRes icon: Int,
    customIcon: Drawable? = null,
    copyText: ((T) -> String)? = null,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    onContact: (T) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(
                enabled = !expanded && items.size != 1
            ) {
                if (items.size > 1) {
                    onExpand(true)
                } else {
                    onContact(items.first())
                }
            }
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.small
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(expanded || items.size == 1) { exp ->
            if (exp) {
                Column {
                    for (item in items) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = if (secondaryAction != null) 8.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier =
                                Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onLongClick = if (copyText != null) {
                                            {
                                                clipboardManager.setText(
                                                    AnnotatedString(
                                                        copyText(
                                                            item
                                                        )
                                                    )
                                                )
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            onContact(item)
                                        })
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (customIcon != null) {
                                    AsyncImage(
                                        model = customIcon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        painterResource(itemIcon(item) ?: icon),
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 16.dp),
                                ) {
                                    itemSubLabel(item)?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }

                                    Text(
                                        text = itemLabel(item),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                            }
                            if (secondaryAction != null) {
                                VerticalDivider(
                                    modifier = Modifier
                                        .height(24.dp)
                                        .padding(end = 4.dp)
                                )
                                secondaryAction(item)
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (customIcon != null) {
                        AsyncImage(
                            model = customIcon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(24.dp)
                        )
                    } else {
                        Icon(
                            painterResource(icon),
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                    )
                    Icon(
                        painterResource(R.drawable.chevron_forward_24px),
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        }

    }
}

private fun ContactInfoType.toString(context: Context): String? {
    return when (this) {
        ContactInfoType.Home -> context.getString(R.string.contact_info_home)
        ContactInfoType.Mobile -> context.getString(R.string.contact_info_mobile)
        ContactInfoType.Work -> context.getString(R.string.contact_info_work)
        ContactInfoType.Other -> null
    }
}


@Composable
fun ContactItemGridPopup(
    contact: Contact,
    show: MutableTransitionState<Boolean>,
    animationProgress: Float,
    origin: IntRect,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.TopStart,
        ) { origin.size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.TopStart,
        ) { origin.size },
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