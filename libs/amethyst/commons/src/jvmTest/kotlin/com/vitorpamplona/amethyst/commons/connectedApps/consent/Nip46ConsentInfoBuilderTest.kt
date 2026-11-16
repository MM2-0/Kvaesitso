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
package com.vitorpamplona.amethyst.commons.connectedApps.consent

import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decrypt-consent contract: a user asked to expose a private message must be able to see WHOSE
 * conversation it is and WHAT it says. Before this, both were empty for every decrypt request, so the
 * dialog skipped its whole preview block and offered "Allow always" over a blank body.
 */
class Nip46ConsentInfoBuilderTest {
    private val alice = "1".repeat(64)
    private val coordinate = "nip46:${"a".repeat(64)}:${"c".repeat(64)}"
    private val account = SignerFace(name = "Me", picture = null, pubKey = "a".repeat(64))

    private val decryptFailedText = "Amethyst could not decrypt this message."

    private fun strings() =
        Nip46ConsentStrings(
            opLabel = { op ->
                when (op) {
                    is NostrSignerOp.DecryptFrom -> "read your private messages with ${op.counterparty.take(6)}"
                    NostrSignerOp.Decrypt -> "read your private messages"
                    else -> "do something"
                }
            },
            allowAlwaysFor = { "Always allow for $it" },
            decryptFailed = decryptFailedText,
        )

    private suspend fun build(
        request: BunkerRequest,
        op: NostrSignerOp = NostrSignerOp.Decrypt,
        faceOf: (String) -> SignerFace = { SignerFace(name = null, picture = null, pubKey = it) },
        decrypt: suspend (BunkerRequest) -> String? = { "the plaintext" },
    ): SignerConsentInfo =
        Nip46ConsentInfoBuilder.build(
            coordinate = coordinate,
            title = "Some App",
            iconUrl = null,
            op = op,
            request = request,
            account = account,
            faceOf = faceOf,
            strings = strings(),
            decrypt = decrypt,
        )

    @Test
    fun decryptConsentCarriesTheCounterpartyAndThePlaintext() =
        runTest {
            val info = build(BunkerRequestNip44Decrypt("1", alice, "ciphertext"))

            assertEquals(alice, info.counterpartyPubKey)
            assertTrue("counterparty label must not be empty", !info.counterpartyName.isNullOrBlank())
            assertEquals("the plaintext", info.contentPreview)
            // The dialog renders its preview block only when one of these is non-blank.
            assertTrue("the dialog must have content to show", info.contentPreview.isNotBlank() || info.rawData.isNotBlank())
        }

    @Test
    fun anUncachedCounterpartyFallsBackToAShortenedNpubNeverToNothing() =
        runTest {
            val info = build(BunkerRequestNip44Decrypt("1", alice, "ct"), faceOf = { SignerFace(null, null, it) })

            val name = info.counterpartyName
            assertNotNull(name)
            assertTrue("expected an npub fallback, got '$name'", name!!.startsWith("npub"))
        }

    @Test
    fun aBlankCachedNameStillFallsBackRatherThanShowingAnEmptyLabel() =
        runTest {
            val info = build(BunkerRequestNip44Decrypt("1", alice, "ct"), faceOf = { SignerFace("   ", null, it) })

            assertTrue(!info.counterpartyName.isNullOrBlank())
        }

    @Test
    fun anUndecryptableMessageStillProducesAPopulatedDialog() =
        runTest {
            val info = build(BunkerRequestNip04Decrypt("1", alice, "garbage"), decrypt = { error("bad ciphertext") })

            assertEquals(decryptFailedText, info.contentPreview)
            assertTrue("the counterparty is still shown", !info.counterpartyName.isNullOrBlank())
            assertTrue("the dialog must not be blank", info.contentPreview.isNotBlank())
        }

    @Test
    fun aSignerThatReturnsNothingIsTreatedAsAFailureNotAsAnEmptyDialog() =
        runTest {
            val blank = build(BunkerRequestNip44Decrypt("1", alice, "ct"), decrypt = { "   " })
            assertEquals(decryptFailedText, blank.contentPreview)

            val none = build(BunkerRequestNip44Decrypt("1", alice, "ct"), decrypt = { null })
            assertEquals(decryptFailedText, none.contentPreview)
        }

    /** A signer that never answers must not wedge the prompt — runTest fast-forwards the timeout. */
    @Test
    fun aHangingSignerTimesOutIntoTheFailureTextInsteadOfBlockingThePrompt() =
        runTest {
            val info = build(BunkerRequestNip44Decrypt("1", alice, "ct"), decrypt = { awaitCancellation() })

            assertEquals(decryptFailedText, info.contentPreview)
        }

    @Test
    fun aLongPlaintextIsTruncatedInlineAndOfferedInFullBehindTheToggle() =
        runTest {
            val long = "x".repeat(500)
            val info = build(BunkerRequestNip44Decrypt("1", alice, "ct"), decrypt = { long })

            assertEquals(Nip46ConsentInfoBuilder.PREVIEW_MAX_CHARS, info.contentPreview.length)
            assertEquals(long, info.rawData)
        }

    @Test
    fun decryptOffersANarrowerPerCounterpartyGrantAlongsideTheBroadOne() =
        runTest {
            val info = build(BunkerRequestNip44Decrypt("1", alice, "ct"))

            assertEquals(NostrSignerOp.DecryptFrom(alice), info.narrowOp)
            assertTrue("the narrow button needs a label", !info.narrowOpLabel.isNullOrBlank())
            // The broad op is still what the dialog's "Always allow" grants.
            assertEquals(NostrSignerOp.Decrypt, info.op)
            // The headline names the counterparty rather than saying "your private messages".
            assertTrue(info.operationSummary.contains("with"))
        }

    @Test
    fun signRequestsAreUnchangedAndCarryNoCounterparty() =
        runTest {
            val request = BunkerRequestSign("1", EventTemplate<Event>(createdAt = 1L, kind = 1, tags = emptyArray(), content = "hello"))
            val info = build(request, op = NostrSignerOp.SignKind(1))

            assertEquals("hello", info.contentPreview)
            assertTrue("sign still shows its JSON", info.rawData.contains("\"kind\""))
            assertNull(info.counterpartyName)
            assertNull(info.counterpartyPubKey)
            assertNull(info.narrowOp)
            assertNotNull(info.previewTemplate)
        }

    @Test
    fun aRelayLoginPreviewNamesTheRelayInsteadOfItsEmptyContent() =
        runTest {
            val auth = RelayAuthEvent.build(RelayUrlNormalizer.normalize("wss://relay.damus.io"), "chal-123")
            val template = EventTemplate<Event>(auth.createdAt, auth.kind, auth.tags, auth.content)
            val info = build(BunkerRequestSign(event = template), op = NostrSignerOp.SignKind(RelayAuthEvent.KIND))

            assertEquals("relay.damus.io", info.contentPreview)
            assertEquals(template, info.previewTemplate)
        }

    @Test
    fun aRegularPostPreviewIsStillItsContent() =
        runTest {
            val template = EventTemplate<Event>(1L, 1, emptyArray(), "  hello world  ")
            val info = build(BunkerRequestSign(event = template), op = NostrSignerOp.SignKind(1))

            assertEquals("hello world", info.contentPreview)
        }
}
