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
package com.vitorpamplona.amethyst.service.connectivity

import android.content.Context
import com.vitorpamplona.amethyst.commons.service.connectivity.ConnectivityStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * There should be only one instance of the Tor binding per app.
 *
 * Tor will connect as soon as status is listened to.
 */
class ConnectivityManager(
    context: Context,
    scope: CoroutineScope,
) {
    val status: StateFlow<ConnectivityStatus> =
        ConnectivityFlow(context).status.distinctUntilChanged().stateIn(
            scope,
            SharingStarted.WhileSubscribed(30000),
            ConnectivityStatus.StartingService,
        )

    val isMobileOrNull: StateFlow<Boolean?> =
        status
            .map {
                (it as? ConnectivityStatus.Active)?.isMobile
            }.stateIn(
                scope,
                SharingStarted.WhileSubscribed(2000),
                (status.value as? ConnectivityStatus.Active)?.isMobile,
            )

    val isMobileOrFalse: StateFlow<Boolean> =
        status
            .map {
                (it as? ConnectivityStatus.Active)?.isMobile ?: false
            }.stateIn(
                scope,
                SharingStarted.WhileSubscribed(2000),
                (status.value as? ConnectivityStatus.Active)?.isMobile ?: false,
            )
}
