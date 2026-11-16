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
package com.vitorpamplona.amethyst.navigation

import com.vitorpamplona.amethyst.commons.ui.navigation.ShareIntentRouting
import com.vitorpamplona.amethyst.commons.ui.navigation.ShareTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the one link in the share-target chain the compiler cannot see: the `<activity-alias>`
 * names in AndroidManifest.xml against the `SHARE_AS_*_ALIAS_SIMPLE_NAME` constants that
 * [ShareIntentRouting] matches them by. Renaming an alias on one side only still builds and still
 * runs — the share just silently opens the wrong composer (a picture share falls through to the
 * default "New Post" target and becomes a text note).
 *
 * Checked in both directions, so neither a renamed constant nor a new alias without a constant
 * slips through.
 */
class ShareTargetManifestTest {
    private val aliasPattern = Regex("""<activity-alias[^>]*android:name="\.ui\.(\w+)"""", RegexOption.DOT_MATCHES_ALL)

    private fun manifest(): String {
        // Gradle runs unit tests with the module directory as the working directory; fall back to
        // the repo root so the test also passes when run from an IDE configured that way.
        val candidates =
            listOf(
                File("src/main/AndroidManifest.xml"),
                File("amethyst/src/main/AndroidManifest.xml"),
            )
        val found = candidates.firstOrNull { it.isFile }
        assertTrue(
            "Could not locate AndroidManifest.xml from ${File(".").absolutePath}",
            found != null,
        )
        return found!!.readText()
    }

    private fun declaredAliases(): Set<String> =
        aliasPattern
            .findAll(manifest())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun everyRoutedAliasIsDeclaredInTheManifest() {
        val declared = declaredAliases()

        val expected =
            listOf(
                ShareIntentRouting.SHARE_AS_DM_ALIAS_SIMPLE_NAME,
                ShareIntentRouting.SHARE_AS_HIGHLIGHT_ALIAS_SIMPLE_NAME,
                ShareIntentRouting.SHARE_AS_PICTURE_ALIAS_SIMPLE_NAME,
                ShareIntentRouting.SHARE_AS_SHORT_VIDEO_ALIAS_SIMPLE_NAME,
                ShareIntentRouting.SHARE_AS_VIDEO_ALIAS_SIMPLE_NAME,
                ShareIntentRouting.SCAN_QR_ALIAS_SIMPLE_NAME,
            )

        expected.forEach {
            assertTrue(
                "ShareIntentRouting routes \"$it\" but AndroidManifest.xml declares no such " +
                    "<activity-alias android:name=\".ui.$it\">. Declared: $declared",
                it in declared,
            )
        }
    }

    @Test
    fun everyDeclaredAliasRoutesToItsOwnTarget() {
        declaredAliases().forEach { alias ->
            val target = ShareIntentRouting.targetOf("com.vitorpamplona.amethyst.ui.$alias")
            assertTrue(
                "AndroidManifest.xml declares <activity-alias android:name=\".ui.$alias\"> but " +
                    "ShareIntentRouting has no constant for it, so shares to it fall through to " +
                    "the default New Post composer.",
                target != ShareTarget.NEW_POST,
            )
        }
    }

    @Test
    fun aliasesResolveToDistinctTargets() {
        val aliases = declaredAliases()
        val targets = aliases.map { ShareIntentRouting.targetOf("com.vitorpamplona.amethyst.ui.$it") }

        assertEquals(
            "Two aliases resolve to the same ShareTarget — one of them is shadowed by a " +
                "suffix match. Aliases: $aliases, targets: $targets",
            targets.size,
            targets.toSet().size,
        )
    }

    /** The media targets are the only ones that accept a multi-file selection. */
    @Test
    fun mediaAliasesAcceptSendMultiple() {
        val text = manifest()

        listOf(
            ShareIntentRouting.SHARE_AS_PICTURE_ALIAS_SIMPLE_NAME,
            ShareIntentRouting.SHARE_AS_SHORT_VIDEO_ALIAS_SIMPLE_NAME,
            ShareIntentRouting.SHARE_AS_VIDEO_ALIAS_SIMPLE_NAME,
        ).forEach { alias ->
            val block =
                text
                    .substringAfter("android:name=\".ui.$alias\"")
                    .substringBefore("</activity-alias>")

            assertTrue(
                "The \".ui.$alias\" share target has no SEND_MULTIPLE intent-filter, so sharing " +
                    "several files at once will not offer it.",
                block.contains("android.intent.action.SEND_MULTIPLE"),
            )
        }
    }
}
