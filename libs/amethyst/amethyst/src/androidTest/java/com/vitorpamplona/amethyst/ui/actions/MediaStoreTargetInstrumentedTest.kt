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
package com.vitorpamplona.amethyst.ui.actions

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk.MediaStoreTarget
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [MediaStoreTarget] spells its directories out as literals because Environment's
 * DIRECTORY_* fields are plain statics that the unit-test android.jar leaves null.
 * This is the other half of that trade: on a real device the literals are checked
 * against the platform constants they stand in for.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreTargetInstrumentedTest {
    @Test
    fun directoriesMatchThePlatformConstants() {
        assertEquals(Environment.DIRECTORY_PICTURES, MediaStoreTarget.IMAGES.relativeDirectory)
        assertEquals(Environment.DIRECTORY_MUSIC, MediaStoreTarget.AUDIO.relativeDirectory)
        assertEquals(Environment.DIRECTORY_MOVIES, MediaStoreTarget.VIDEO.relativeDirectory)
        assertEquals(Environment.DIRECTORY_DOWNLOADS, MediaStoreTarget.DOWNLOADS.relativeDirectory)
    }
}
