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
package com.vitorpamplona.amethyst.commons.account.ui.login

import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.login_bunker_not_supported
import com.vitorpamplona.amethyst.commons.resources.login_nostrconnect_not_supported
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Amethyst for Android is the bunker, never the bunker's client.
 *
 * `AccountSettings.isWriteable()` is `privKey != null || externalSignerPackageName != null`: the
 * only remote signing it can persist is an external signer app. A `bunker://` address pasted into
 * the key field is therefore not a key it failed to read, it is a sign-in method it does not have
 * — but it used to fall through to the hex branch and come back as
 * "Invalid key: Could not sign in: Invalid hex bunker://…", which reads as "you mistyped it" for a
 * string the user pasted correctly.
 */
class LoginViewModelKeyKindTest {
    private fun viewModelWithKey(text: String) =
        LoginViewModel().apply {
            acceptedTerms = true
            key = TextFieldValue(text)
        }

    private fun errorResIdOf(model: LoginViewModel) = (model.errorManager.error as? LoginErrorManager.SingleErrorMsg)?.errorResId

    @Test
    fun aBunkerAddressIsRefusedAsAnUnsupportedMethodNotAsABadKey() {
        val model = viewModelWithKey("bunker://${"a".repeat(64)}?relay=wss%3A%2F%2Fr.example&secret=s1")

        assertFalse(model.checkCanLogin())
        assertEquals(Res.string.login_bunker_not_supported, errorResIdOf(model))
    }

    /** Same defect, same field: a connect offer is not a sign-in method either. */
    @Test
    fun aNostrConnectOfferIsRefusedWithItsOwnExplanation() {
        val model = viewModelWithKey("nostrconnect://${"b".repeat(64)}?relay=wss%3A%2F%2Fr.example&secret=s1")

        assertFalse(model.checkCanLogin())
        assertEquals(Res.string.login_nostrconnect_not_supported, errorResIdOf(model))
    }

    /** Pasted from a chat app, which loves to add a trailing space. */
    @Test
    fun surroundingWhitespaceDoesNotHideTheExplanation() {
        val model = viewModelWithKey("  bunker://${"a".repeat(64)}?secret=s1  ")

        assertFalse(model.checkCanLogin())
        assertEquals(Res.string.login_bunker_not_supported, errorResIdOf(model))
    }

    /** An npub is still a key, so the guard must not swallow the ordinary path. */
    @Test
    fun anOrdinaryKeyStillPassesTheCheck() {
        val model = viewModelWithKey("npub1${"q".repeat(58)}")

        assertEquals(true, model.checkCanLogin())
    }

    /** "Remember my key" is offered only where a private key could be typed. */
    @Test
    fun onlyAPrivateKeyIsOfferedToBeRemembered() {
        listOf("", "nsec1abc", "ncryptsec1abc", "a".repeat(64), "word ".repeat(12).trim()).forEach {
            assertTrue(viewModelWithKey(it).keyMayBePrivate, it)
        }
        listOf("npub1${"q".repeat(58)}", "NPUB1QQQQ", "nprofile1abc", "bunker://${"a".repeat(64)}", "nostrconnect://x", "alice@example.com").forEach {
            assertFalse(viewModelWithKey(it).keyMayBePrivate, it)
        }
    }
}
