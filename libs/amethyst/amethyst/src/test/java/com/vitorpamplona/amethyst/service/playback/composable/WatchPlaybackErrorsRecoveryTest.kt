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
package com.vitorpamplona.amethyst.service.playback.composable

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recovery predicate deliberately covers only BEHIND_LIVE_WINDOW. A prior version recovered any
 * live I/O error, which thrashed on a stream whose segments fail to parse (it re-prepared, briefly
 * reached READY, hit the same bad segment, and — because the cap reset on READY — never gave up).
 * Everything except BEHIND_LIVE_WINDOW is now left terminal.
 */
class WatchPlaybackErrorsRecoveryTest {
    @Test
    fun onlyBehindLiveWindowIsRecoverable() {
        assertTrue(isRecoverableLiveError(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW))
    }

    @Test
    fun ioErrorsAreNotRecoverable() {
        // The nogoodradio thrash: UnexpectedLoaderException surfaced as ERROR_CODE_IO_UNSPECIFIED.
        assertFalse(isRecoverableLiveError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        assertFalse(isRecoverableLiveError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertFalse(isRecoverableLiveError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(isRecoverableLiveError(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
    }

    @Test
    fun decodeAndFormatErrorsAreNotRecoverable() {
        assertFalse(isRecoverableLiveError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertFalse(isRecoverableLiveError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertFalse(isRecoverableLiveError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
    }

    @Test
    fun unspecifiedRuntimeErrorIsNotRecoverable() {
        assertFalse(isRecoverableLiveError(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }
}
