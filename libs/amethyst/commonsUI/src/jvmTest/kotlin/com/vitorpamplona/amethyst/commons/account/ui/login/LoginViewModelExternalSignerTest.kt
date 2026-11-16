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
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Android's signer-app login (Amber, NIP-55): the button's activity result puts the signer's pubkey
 * in the key field and calls [LoginViewModel.loginWithExternalSigner], which must reach the session
 * manager as a signer-app login on Android, where remote signers are off.
 */
class LoginViewModelExternalSignerTest {
    private val pubkey = "a".repeat(64)

    private fun androidViewModel(session: AccountSessionManager) =
        LoginViewModel().apply {
            init(session)
            acceptedTerms = true
            remoteSignerLoginSupported = false
        }

    @Test
    fun theSignerAppsAnswerLogsInAsAnExternalSigner() {
        val session = mockk<AccountSessionManager>(relaxed = true)
        val model = androidViewModel(session)

        model.updateKey(TextFieldValue(pubkey), false)
        model.loginWithExternalSigner("com.greenart7c3.nostrsigner")

        verify(exactly = 1) {
            session.login(
                key = pubkey,
                transientAccount = false,
                loginWithExternalSigner = true,
                packageName = "com.greenart7c3.nostrsigner",
                onError = any<(String) -> Unit>(),
            )
        }
        assertTrue(model.processingLogin)
    }

    /** A rejected signer login must not leave the guard on, or the next Amber tap does nothing. */
    @Test
    fun aFailedSignerLoginCanBeTriedAgain() {
        val session = mockk<AccountSessionManager>()
        val onError = slot<(String) -> Unit>()
        every { session.login(any(), any(), any(), any(), capture(onError)) } answers { onError.captured("rejected") }
        val model = androidViewModel(session)

        model.updateKey(TextFieldValue(pubkey), false)
        model.loginWithExternalSigner("com.greenart7c3.nostrsigner")
        assertFalse(model.processingLogin)
        // Under the Amber button, not the key field.
        assertNotNull(model.signerErrorManager.error)
        assertNull(model.errorManager.error)

        // The next answer from the signer clears it before trying again.
        model.updateKey(TextFieldValue(pubkey), false)
        assertNull(model.signerErrorManager.error)
        model.loginWithExternalSigner("com.greenart7c3.nostrsigner")
        verify(exactly = 2) { session.login(any(), any(), true, "com.greenart7c3.nostrsigner", any<(String) -> Unit>()) }
    }

    /** While a typed key is logging in, a signer answer waits for it instead of racing it. */
    @Test
    fun aSignerAnswerDuringAnotherLoginIsIgnored() {
        val session = mockk<AccountSessionManager>(relaxed = true)
        val model = androidViewModel(session)
        model.processingLogin = true

        model.updateKey(TextFieldValue(pubkey), false)
        model.loginWithExternalSigner("com.greenart7c3.nostrsigner")

        verify(exactly = 0) { session.login(any(), any(), true, any(), any<(String) -> Unit>()) }
    }
}
