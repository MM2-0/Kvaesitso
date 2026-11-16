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
package com.vitorpamplona.amethyst.commons.nip52Calendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_fab_new_collection
import com.vitorpamplona.amethyst.commons.resources.calendar_fab_new_event
import com.vitorpamplona.amethyst.commons.resources.calendar_fab_toggle
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size26Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size55Modifier

@Composable
fun NewCalendarButton(nav: INav) {
    var isOpen by remember { mutableStateOf(false) }

    Column {
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        ) {
            Column {
                FloatingActionButton(
                    onClick = {
                        isOpen = false
                        nav.nav(Route.NewCalendarCollection())
                    },
                    modifier = Size55Modifier,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        symbol = MaterialSymbols.CalendarMonth,
                        // Spell out the full intent for screen readers — the bare "New collection"
                        // string is ambiguous out of context.
                        contentDescription = stringRes(Res.string.calendar_fab_new_collection),
                        modifier = Size26Modifier,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                FloatingActionButton(
                    onClick = {
                        isOpen = false
                        nav.nav(Route.NewCalendarEvent())
                    },
                    modifier = Size55Modifier,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Add,
                        contentDescription = stringRes(Res.string.calendar_fab_new_event),
                        modifier = Size26Modifier,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        FloatingActionButton(
            onClick = { isOpen = !isOpen },
            modifier = Size55Modifier,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            AnimatedVisibility(
                visible = isOpen,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.calendar_fab_toggle),
                    modifier = Size26Modifier,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }

            AnimatedVisibility(
                visible = !isOpen,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Icon(
                    symbol = MaterialSymbols.Add,
                    // Top FAB toggles the sub-FABs in/out — describe that, not the sub-action.
                    contentDescription = stringRes(Res.string.calendar_fab_toggle),
                    modifier = Size26Modifier,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
