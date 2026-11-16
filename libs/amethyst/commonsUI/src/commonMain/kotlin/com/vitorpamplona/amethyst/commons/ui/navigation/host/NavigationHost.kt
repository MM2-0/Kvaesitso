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
package com.vitorpamplona.amethyst.commons.ui.navigation.host

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.vitorpamplona.amethyst.commons.model.navigation.NavStackEntry
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav

/**
 * Renders [nav]'s back stack with the screens [destinations] declares: the one navigation host
 * every front end shows.
 *
 * Every entry whose state must survive is decorated — the visible stack AND the tab roots the user
 * left — so a tab keeps its ViewModels and scroll position while it is out of sight. Only the
 * visible stack is displayed; an entry's state is dropped once it leaves both, and its ViewModels
 * also once the entry is handed new arguments in place (see [rememberRouteViewModelStoreNavEntryDecorator]).
 *
 * The host paints the theme background under the screens: a push or pop fades them (and scales the
 * one behind), so mid-transition what is under the host shows through. That is the window's own
 * background, which does not follow the theme the user picked, so a dark theme flashed light on
 * every back.
 */
@Composable
fun NavigationHost(
    nav: Nav,
    destinations: NavDestinations,
    modifier: Modifier = Modifier,
    navFor: (NavStackEntry) -> INav = nav::forEntry,
) {
    NavigationHost(
        visible = nav.shownEntries(),
        retained = nav.stacks.retained(),
        destinations = destinations,
        navFor = navFor,
        // Switching tabs or sections swaps the screen at once; only a stack slides and fades.
        animated = !nav.stacks.isSingleScreen || nav.shownEntries().size > 1,
        onBack = { nav.stacks.back() },
        modifier = modifier,
    )
}

/**
 * Renders [visible] (bottom to top) and keeps the state of every [retained] entry: the host behind
 * the main window's [Nav], each deck column and each screen window, which shows its one entry. Each
 * screen draws with the navigation [navFor] gives its entry.
 */
@Composable
fun NavigationHost(
    visible: List<NavStackEntry>,
    retained: List<NavStackEntry>,
    destinations: NavDestinations,
    navFor: (NavStackEntry) -> INav,
    animated: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentNavFor = rememberUpdatedState(navFor)
    // The shown entries first: the display takes the head of the decorated list.
    val all = visible + retained.filterNot { it in visible }
    val decorated =
        rememberDecoratedNavEntries(
            backStack = all,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberRouteViewModelStoreNavEntryDecorator(all),
                ),
            entryProvider = { entry -> destinations.entryFor(entry, currentNavFor.value) },
        )

    Display(decorated.subList(0, visible.size), destinations, animated, onBack, modifier)
}

@Composable
private fun Display(
    entries: List<NavEntry<NavStackEntry>>,
    destinations: NavDestinations,
    animated: Boolean,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    NavDisplay(
        entries = entries,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        transitionSpec = { if (animated) destinations.pushTransition(this) else NoTransition },
        popTransitionSpec = { if (animated) destinations.popTransition(this) else NoTransition },
        // A back gesture runs the same motion as a back press, scrubbed by the finger.
        predictivePopTransitionSpec = { if (animated) destinations.popTransition(this) else NoTransition },
        onBack = onBack,
    )
}

private val NoTransition: ContentTransform = EnterTransition.None togetherWith ExitTransition.None
