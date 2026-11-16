/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.ui.navigation.bottombars

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomAppBarDefaults.windowInsets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.insets.KeyboardState
import com.vitorpamplona.amethyst.commons.ui.insets.keyboardAsState
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.LocalTabReselectCoordinator
import com.vitorpamplona.amethyst.commons.ui.navigation.host.canOpen
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.platform.rememberNappletIconModel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.HorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size0dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size10Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size25Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size27Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.onSurface65
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/** Content height of the [AppBottomBar] (the 50.dp Column inside [RenderBottomMenu]),
 * exclusive of the system navigation-bar inset. Used by FAB callers that want to
 * reserve the same vertical space when the bar hides itself on in-app pushes. */
val AppBottomBarHeight = 50.dp

@Composable
fun AppBottomBar(
    selectedRoute: Route?,
    nav: INav,
    accountViewModel: AccountViewModel,
    onClick: (Route) -> Unit,
) {
    // Publish this screen's re-tap behavior even when the bar renders nothing: on large
    // screens the navigation rail routes reselect taps back through the coordinator so the
    // same per-screen scroll-to-top/refresh logic runs.
    val coordinator = LocalTabReselectCoordinator.current
    val latestRoute by rememberUpdatedState(selectedRoute)
    val latestOnClick by rememberUpdatedState(onClick)
    DisposableEffect(coordinator) {
        val handler: (Route) -> Unit = { latestOnClick(it) }
        coordinator.register({ latestRoute }, handler)
        onDispose { coordinator.unregister(handler) }
    }

    // Large screens replace the bottom bar with the navigation rail (Medium) or the
    // permanently docked drawer (Expanded).
    if (LocalScreenLayout.current.isLargeScreen) return

    // Hide the bar on in-app pushes. Tab roots, Home and screens opened from
    // the drawer keep it, even though the drawer ones still show a back arrow.
    if (!nav.showsBottomBar()) return

    val items by accountViewModel.account.settings.syncedSettings.navigation.bottomBarItems
        .collectAsStateWithLifecycle()
    if (items.isEmpty()) {
        Spacer(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets)
                    .consumeWindowInsets(windowInsets),
        )
        return
    }

    // Favorite entries in the unified list resolve to a live favorite for their icon/label and to an
    // embedded-tab route. Both kinds embed in-process (WebApp → browser surface, NostrApp → napplet
    // surface), so such a tab swaps in place rather than launching an activity from the bottom row.
    val favorites by LocalAppServices.current.favoriteApps.favorites
        .collectAsStateWithLifecycle()

    val isKeyboardState by keyboardAsState()
    if (isKeyboardState == KeyboardState.Closed) {
        RenderBottomMenu(items, favorites, selectedRoute, accountViewModel, onClick)
    }
}

/**
 * Resolves the icon model for a pinned favorite: a web favorite's captured favicon (else the
 * generic globe), an nsite/napplet's verified manifest icon bundled in its own content (the
 * iframe sandbox rules out live capture; else the grid glyph). Shared by the bottom bar and
 * the navigation rail.
 */
@Composable
internal fun rememberFavoriteIconModel(fav: FavoriteApp): Any? =
    when (fav) {
        is FavoriteApp.WebApp -> {
            // Captured favicons, keyed so the icon appears once the site's capture lands.
            val browserIcons = LocalAppServices.current.browserIcons
            val iconKeys by browserIcons.keys
                .collectAsStateWithLifecycle()
            remember(fav, iconKeys) {
                OmniboxInput.hostOf(fav.url)?.let(browserIcons::iconModelFor)
            }
        }

        is FavoriteApp.NostrApp -> rememberNappletIconModel(fav.coordinate)
    }

private val GrayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** The icon block for a pinned favorite entry, shared by the bottom bar and the rail. */
@Composable
internal fun FavoriteEntryIcon(
    fav: FavoriteApp,
    selected: Boolean,
    iconModel: Any?,
) {
    Box(Size27Modifier, contentAlignment = Alignment.Center) {
        FavoriteAppIcon(
            app = fav,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface65,
            modifier = Size25Modifier,
            iconModel = iconModel,
            // A favicon is a full-color brand mark sitting among monochrome glyphs; unselected it
            // goes grayscale like its neighbours and gets its color back once it is the open tab.
            colorFilter = if (selected) null else GrayscaleFilter,
        )
    }
}

@Composable
private fun RenderBottomMenu(
    items: List<BottomBarEntry>,
    favorites: List<FavoriteApp>,
    selectedRoute: Route?,
    accountViewModel: AccountViewModel,
    nav: (Route) -> Unit,
) {
    // Index favorites by id so resolving each Favorite entry is a map lookup, not a per-entry scan.
    val favoritesById = remember(favorites) { favorites.associateBy { it.id } }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(windowInsets)
                .consumeWindowInsets(windowInsets)
                .height(AppBottomBarHeight),
    ) {
        HorizontalDivider(
            thickness = DividerThickness,
        )
        NavigationBar(
            modifier = HorzPadding,
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = Size0dp,
        ) {
            // Render in the user's saved order — built-ins, favorites and pinned groups interleaved.
            // Each entry resolves to a shared BottomBarSlot (route + icon), the same one the rail uses.
            // The selected pill is a neutral wash rather than the accent container: the selected
            // icon is already accent-tinted, and an accent pill behind it made the bar one of the
            // loudest things on every screen.
            val itemColors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            items.forEach { entry ->
                val slot = rememberBottomBarSlot(entry, favoritesById, accountViewModel) ?: return@forEach
                val selected = slot.route == selectedRoute
                NavigationBarItem(
                    alwaysShowLabel = false,
                    icon = { slot.icon(selected) },
                    selected = selected,
                    onClick = { nav(slot.route) },
                    colors = itemColors,
                )
            }
        }
    }
}

/**
 * A resolved bottom-bar/rail slot: the [route] to navigate to and the [icon] to draw for a given
 * selected state. Built from a [BottomBarEntry] by [rememberBottomBarSlot], so the phone bar and the
 * large-screen rail render every entry kind (built-in, favorite, pinned group) through one code path.
 */
class BottomBarSlot(
    val route: Route,
    val icon: @Composable (selected: Boolean) -> Unit,
)

/**
 * Resolves an entry to its live [BottomBarSlot], or null if it no longer resolves (deleted favorite,
 * etc.) or leads to a screen this front end doesn't have: the bar items sync from every device the
 * account uses, and the desktop has no Health Connect or app runtime.
 */
@Composable
fun rememberBottomBarSlot(
    entry: BottomBarEntry,
    favoritesById: Map<String, FavoriteApp>,
    accountViewModel: AccountViewModel,
): BottomBarSlot? {
    val slot = resolveBottomBarSlot(entry, favoritesById, accountViewModel) ?: return null
    return if (canOpen(slot.route)) slot else null
}

@Composable
private fun resolveBottomBarSlot(
    entry: BottomBarEntry,
    favoritesById: Map<String, FavoriteApp>,
    accountViewModel: AccountViewModel,
): BottomBarSlot? {
    return when (entry) {
        is BottomBarEntry.BuiltIn -> {
            val def = NavBarCatalog[entry.item] ?: return null
            val route = remember(def, accountViewModel) { def.resolveRoute(accountViewModel) }
            BottomBarSlot(route) { selected -> NotifiableIcon(selected, def, route, accountViewModel) }
        }
        is BottomBarEntry.Favorite -> {
            val fav = favoritesById[entry.favoriteId] ?: return null
            val route =
                when (fav) {
                    is FavoriteApp.WebApp -> Route.WebApp(fav.url)
                    is FavoriteApp.NostrApp -> Route.NostrApp(fav.coordinate)
                }
            val iconModel = rememberFavoriteIconModel(fav)
            BottomBarSlot(route) { selected -> FavoriteEntryIcon(fav, selected, iconModel) }
        }
        is BottomBarEntry.PublicChat,
        is BottomBarEntry.RelayGroup,
        is BottomBarEntry.RelayServer,
        is BottomBarEntry.Concord,
        is BottomBarEntry.ConcordChannel,
        is BottomBarEntry.Geohash,
        -> {
            val display = rememberGroupEntryDisplay(entry, accountViewModel) ?: return null
            // A pinned chat/group shows its avatar, like the favorite-app tabs — icon only.
            BottomBarSlot(display.route) { Box(Size27Modifier, contentAlignment = Alignment.Center) { GroupEntryAvatar(display, 25.dp, accountViewModel) } }
        }
    }
}

/** The icon block for a built-in entry (catalog icon + new-items dot), shared by the bottom bar and the rail. */
@Composable
internal fun NotifiableIcon(
    selected: Boolean,
    def: NavBarItemDef,
    destination: Route,
    accountViewModel: AccountViewModel,
) {
    Box(Size27Modifier, contentAlignment = Alignment.Center) {
        NavItemIcon(
            symbol = def.icon,
            imageVector = def.imageVector,
            contentDescription = stringRes(def.labelRes),
            modifier = Size25Modifier,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface65,
        )

        AddNotifIconIfNeeded(destination, accountViewModel, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
fun AddNotifIconIfNeeded(
    route: Route,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val flow = accountViewModel.hasNewItems[route] ?: return
    val hasNewItems by flow.collectAsStateWithLifecycle()
    if (hasNewItems) {
        NotificationDotIcon(modifier)
    }
}

@Composable
private fun NotificationDotIcon(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Size10Modifier.then(modifier), onDraw = {
        drawCircle(color = color)
    })
}
