package de.mm20.launcher2.ui.media

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.rememberVirtualAppBlur
import de.mm20.launcher2.ui.component.virtualAppElevated
import de.mm20.launcher2.ui.component.virtualAppGlass
import de.mm20.launcher2.ui.component.virtualAppSurface
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.theme.transparency.transparency

/**
 * True inside Telos Media (the hub): the hub shows the top bar (space pill and back button) and
 * handles the system bars, so [MediaFrame] only shows the actions of the screen and the content.
 */
val LocalInMediaHub = androidx.compose.runtime.staticCompositionLocalOf { false }

/** The hub composes each screen's actions in its own toolbar, without registering mutable UI state. */
val LocalMediaHubToolbar = androidx.compose.runtime.staticCompositionLocalOf<
    (@Composable (@Composable RowScope.() -> Unit) -> Unit)?
> { null }

/**
 * Frame for the Telos media screens (music, video, photos, radio): a top bar with back button and
 * a surface that sets the text color, and keeps the content clear of the status and navigation bars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaFrame(
    title: String,
    askNotifications: Boolean = false,
    guardKey: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val backStack = LocalBackStack.current
    if (guardKey != null) VirtualAppGuardEffect(guardKey)
    if (askNotifications) RequestNotificationPermission()
    if (LocalInMediaHub.current) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            val toolbar = LocalMediaHubToolbar.current
            if (toolbar != null) toolbar(actions)
            else androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                content = actions,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
        return
    }
    val blur = rememberVirtualAppBlur()
    val bar = virtualAppElevated()
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.virtualAppGlass(blur, bar),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { TelosCyberHeading {  Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)  } },
                navigationIcon = {
                    if (backStack.size > 1) {
                        IconButton(onClick = { backStack.removeLastOrNull() }) {
                            Icon(painterResource(BaseR.drawable.arrow_back_24px), contentDescription = stringResource(I18nR.string.hc_back))
                        }
                    }
                },
                actions = actions,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = 8.dp).background(virtualAppSurface())) {
            content()
        }
    }
}

/** Search occupies a touch-sized icon until explicitly expanded; existing query owners are unchanged. */
@Composable
fun MediaSearchAction(expanded: Boolean, hasQuery: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { selected = expanded || hasQuery }) {
        Icon(painterResource(BaseR.drawable.search_24px),
            contentDescription = stringResource(I18nR.string.hc_search),
            tint = if (expanded || hasQuery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/** Null [expanded] provides a self-contained icon for nested media filter panels. */
@Composable
fun MediaSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    onClose: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    searchEnabled: Boolean = true,
) {
    var localExpanded by rememberSaveable { mutableStateOf(false) }
    val open = expanded ?: localExpanded
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val close: () -> Unit = {
        keyboard?.hide()
        focus.clearFocus()
        onClose?.invoke() ?: run { onValueChange(""); localExpanded = false }
    }
    androidx.activity.compose.BackHandler(enabled = open) { close() }
    Column(modifier.fillMaxWidth()) {
        if (expanded == null && !open) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            MediaSearchAction(false, value.isNotBlank()) { localExpanded = true }
        }
        if (open) {
            de.mm20.launcher2.ui.component.TelosSearchBar(value, onValueChange, placeholder, autoFocus = true,
                onSearch = onSearch,
                leading = {
                    IconButton(onClick = close, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.hc_close))
                    }
                }, trailing = {
                    if (onSearch != null) IconButton(onClick = { keyboard?.hide(); focus.clearFocus(); onSearch() }, enabled = value.isNotBlank() && searchEnabled) {
                        Icon(painterResource(BaseR.drawable.search_24px), stringResource(I18nR.string.hc_search))
                    }
                })
        }
    }
}

/** Playback notifications need the notification permission on Android 13 and newer. */
@Composable
private fun RequestNotificationPermission() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Marks [key] as the app on screen while the activity is started, for the crash guard */
@Composable
fun VirtualAppGuardEffect(guardKey: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(guardKey, lifecycle) {
        // on screen only while the activity is started, so a launcher that was sent to the
        // background is not blamed for a later crash
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) de.mm20.launcher2.base.VirtualAppGuard.enter(context, guardKey)
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) de.mm20.launcher2.base.VirtualAppGuard.leave(context, guardKey)
        }
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            de.mm20.launcher2.base.VirtualAppGuard.enter(context, guardKey)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            de.mm20.launcher2.base.VirtualAppGuard.leave(context, guardKey)
        }
    }
}

/** Compact filled media filters inspired by music players; selection is also exposed to accessibility. */
@Composable
fun MediaFilterButton(selected: Boolean, onClick: () -> Unit, label: String, icon: Int? = null) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 4.dp).semantics { this.selected = selected },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(painterResource(icon), null, Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
