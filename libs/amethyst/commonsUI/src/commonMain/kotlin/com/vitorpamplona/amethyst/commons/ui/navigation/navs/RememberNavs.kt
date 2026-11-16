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
package com.vitorpamplona.amethyst.commons.ui.navigation.navs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.vitorpamplona.amethyst.commons.model.navigation.NavBackStacks

/** Keeps the back stack across configuration changes and process death, as the old NavController did. */
private val NavBackStacksSaver =
    Saver<NavBackStacks, String>(
        save = { it.encode() },
        restore = { NavBackStacks.decode(it) },
    )

/**
 * The window's [Nav]. A fresh one starts from [initial] when it gives one (the tabs or windows a
 * previous run left open); state saved across a configuration change or process death wins.
 */
@Composable
fun rememberNav(initial: () -> NavBackStacks? = { null }): Nav {
    val stacks = rememberSaveable(saver = NavBackStacksSaver) { initial() ?: NavBackStacks() }
    val scope = rememberCoroutineScope()
    // The settler is rebuilt whenever its inputs change (density, the keyboard controller), and the
    // app handles density and size changes without recreating the activity. Nav must survive that:
    // a new instance would reset the drawer state and the docked flag and recompose every screen that
    // takes it. So Nav keeps one settler that always defers to the latest.
    val ime by rememberUpdatedState(rememberImeSettler())

    return remember(stacks, scope) {
        Nav(stacks, scope) { ime.settle() }
    }
}
