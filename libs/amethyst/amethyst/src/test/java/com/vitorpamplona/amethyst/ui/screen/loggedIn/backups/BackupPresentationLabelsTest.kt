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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.backups

import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.backups.labelsWrittenBy
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.backups.presentationOf
import com.vitorpamplona.quartz.nip01Core.diff.EventDiff
import com.vitorpamplona.quartz.nip01Core.diff.ListDiff
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataDiff
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListDiff
import org.jetbrains.compose.resources.StringResource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * rememberPresentation resolves labels in composition and hands presentationOf a lookup
 * over exactly [labelsWrittenBy]; a label presentationOf asks for that is not in that list
 * crashes the review screen. presentationOf asks for every label of a diff type
 * regardless of which fields changed, so an empty diff of each type exercises them all.
 */
class BackupPresentationLabelsTest {
    private fun <T> empty() = ListDiff<T>(emptyList(), emptyList(), emptyList())

    private fun asked(diff: EventDiff): Set<StringResource> {
        val asked = mutableSetOf<StringResource>()
        presentationOf(diff) {
            asked.add(it)
            ""
        }
        return asked
    }

    @Test
    fun metadataLabelsAreAllProvided() {
        val diff = MetadataDiff(null, null, null, null, null, null, null, null, null, null, null, null, null, empty(), empty())
        assertEquals(labelsWrittenBy(diff).toSet(), asked(diff))
    }

    @Test
    fun relayListLabelsAreAllProvided() {
        val diff = AdvertisedRelayListDiff(empty())
        assertEquals(labelsWrittenBy(diff).toSet(), asked(diff))
    }
}
