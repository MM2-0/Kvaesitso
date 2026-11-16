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
package com.vitorpamplona.amethyst.service.playback

/**
 * Shared logcat tag for the playback diagnostic trace (source routing, player lifecycle, error
 * recovery, HLS liveness learning).
 *
 * Emitted with `Log.d`, which fires only while `Log.minLevel <= DEBUG`. No shipped variant is
 * there by default: [com.vitorpamplona.amethyst.Amethyst.DEFAULT_LOG_LEVEL] gives debug AND
 * benchmark builds `INFO` (the `benchmark` build type counts as `isDebug`) and release `WARN`, so
 * this trace is silent everywhere until `Amethyst.VERBOSE_LOGS` is flipped to true. To capture a
 * playback investigation, set that flag, install a debug build and run:
 *
 * ```
 * adb logcat -s PlaybackDiag
 * ```
 */
const val PLAYBACK_DIAG_TAG = "PlaybackDiag"
