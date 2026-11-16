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
package com.vitorpamplona.amethyst.ui

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.uri.fragmentHashtagOrNull
import com.vitorpamplona.amethyst.commons.model.navigation.uri.uriToRoute
import com.vitorpamplona.cordn.appGroupRef.CordnGroupRef
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder

class UriToRouteTest {
    private val account = mockk<Account>()

    @Test
    fun fragmentHashtagRoutesToHashtagFeed() {
        assertEquals(Route.Hashtag("nostrmultiplayergames"), uriToRoute("#NostrMultiplayerGames", account))
    }

    @Test
    fun fragmentHashtagKeepsUnicodeTags() {
        assertEquals(Route.Hashtag("日本語"), uriToRoute("#日本語", account))
    }

    @Test
    fun invalidFragmentHashtagsAreNotRoutes() {
        assertNull(uriToRoute("#", account))
        assertNull(uriToRoute("# ", account))
        assertNull(uriToRoute("#two words", account))
    }

    @Test
    fun fullUrlsWithAnchorsAreNotHashtagRoutes() {
        assertNull(uriToRoute("https://example.com/page#anchor", account))
    }

    @Test
    fun hashtagQueryRoutesStillWork() {
        assertEquals(Route.Hashtag("foo"), uriToRoute("hashtag?id=foo", account))
        assertEquals(Route.Hashtag("foo"), uriToRoute("nostr:hashtag?id=foo", account))
    }

    // The QR scanner shows a "can't open this" sheet for anything uriToRoute returns null for, so
    // what does and does not route here decides what that sheet ever has to explain.

    @Test
    fun rawWalletConnectUrisRouteWithoutTheDlnwcWrapper() {
        // Not just the `dlnwc?value=` deep-link form: a wallet's QR code holds the bare URI, and
        // the fallback at the end of uriToRoute is what catches it.
        assertEquals(
            Route.WalletAddNwc(NWC_URI),
            uriToRoute(NWC_URI, account),
        )
    }

    @Test
    fun everyWalletConnectSchemeSpellingRoutes() {
        assertEquals(
            Route.WalletAddNwc(NWC_URI_NO_PLUS),
            uriToRoute(NWC_URI_NO_PLUS, account),
        )
    }

    @Test
    fun walletConnectDeepLinksStillUnwrapTheValueParameter() {
        // The value has to be percent-encoded, as a real deep link's would be: left raw, its own
        // `&secret=` reads as a parameter of the OUTER uri and the value comes back truncated.
        val encoded = URLEncoder.encode(NWC_URI, Charsets.UTF_8.name())

        assertEquals(
            Route.WalletAddNwc(NWC_URI),
            uriToRoute("dlnwc?value=$encoded", account),
        )
    }

    @Test
    fun walletConnectDeepLinksRouteInEverySpellingIsWalletConnectRouteAccepts() {
        // isWalletConnectRoute() accepts three spellings, so all three have to survive the parse
        // behind it. They did not: `java.net.URI` calls a scheme followed by anything but `/` an
        // *opaque* uri and reports it as having no query at all, so the one-colon form lost its
        // `value=` and came back to the user as "that uri was invalid" -- while the `//` spelling
        // of the very same link worked. Only the bare form was covered here before.
        val encoded = URLEncoder.encode(NWC_URI, Charsets.UTF_8.name())

        listOf(
            "dlnwc?value=$encoded",
            "amethyst+walletconnect:dlnwc?value=$encoded",
            "amethyst+walletconnect://dlnwc?value=$encoded",
        ).forEach { deepLink ->
            assertEquals(deepLink, Route.WalletAddNwc(NWC_URI), uriToRoute(deepLink, account))
        }
    }

    @Test
    fun theLauncherShortcutOpensTheScannerDirectly() {
        // res/xml/shortcuts.xml fires `amethyst:scanqr`. If this stops resolving, long-pressing the
        // app icon silently lands on the home feed instead of the camera.
        val signed = mockk<Account>()
        every { signed.signer } returns mockk { every { pubKey } returns PUBKEY_HEX }

        val expected = Route.QRDisplay(PUBKEY_HEX, startScanning = true)
        // The shortcut fires our own scheme; the other two stay accepted so a shortcut pinned by
        // an older build keeps working.
        assertEquals(expected, uriToRoute("amethyst:scanqr", signed))
        assertEquals(expected, uriToRoute("nostr:scanqr", signed))
        assertEquals(expected, uriToRoute("scanqr", signed))
    }

    @Test
    fun theShortcutRouteIsNotConfusedWithOtherNostrUris() {
        assertNull(uriToRoute("nostr:scanqrcode", account))
    }

    @Test
    fun bunkerUrisDoNotRouteAnywhere() {
        // Amethyst *publishes* bunker:// addresses (it is the remote signer); it has no screen that
        // consumes one. The NIP-46 signer screen pairs nostrconnect:// offers only. Until that
        // changes, a scanned bunker:// belongs in the scanner's explanation sheet, and the sheet
        // must not tell the user to take it somewhere that cannot accept it.
        assertNull(uriToRoute("bunker://$PUBKEY_HEX?relay=wss%3A%2F%2Frelay.example&secret=abc", account))
    }

    @Test
    fun cordnShareLinksOpenTheLinkScreenCarryingTheLink() {
        // What cordn.net's "Share group link" copies and puts in its QR code. The manifest sends
        // these to MainActivity; the link screen shows who runs the group before anyone asks to join.
        val ref = CordnGroupRef("6d1f0f6a-2a3e-4f2c", PUBKEY_HEX, listOf("wss://relay.example.com")).encode()
        listOf(
            "https://cordn.net/chat/$ref?m=eyJuYW1lIjoiVGVzdCJ9",
            "https://www.cordn.net/chat/$ref/info",
            ref,
        ).forEach { link ->
            assertEquals(link, Route.CordnLink(link), uriToRoute(link, account))
        }
    }

    @Test
    fun aBrokenCordnLinkStillOpensTheLinkScreen() {
        // The screen says which rule the ref broke; a "not a nostr link" toast would not.
        val link = "https://cordn.net/chat/cordn1qqqqqqq"
        assertEquals(Route.CordnLink(link), uriToRoute(link, account))
    }

    @Test
    fun otherCordnNetPagesAreNotCordnLinks() {
        assertNull(uriToRoute("https://cordn.net/chat/news", account))
        assertNull(uriToRoute("https://cordn.net/", account))
    }

    companion object {
        private const val PUBKEY_HEX = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
        private const val NWC_URI = "nostr+walletconnect://$PUBKEY_HEX?relay=wss%3A%2F%2Frelay.example&secret=$PUBKEY_HEX"
        private const val NWC_URI_NO_PLUS = "nostrwalletconnect://$PUBKEY_HEX?relay=wss%3A%2F%2Frelay.example&secret=$PUBKEY_HEX"
    }

    @Test
    fun nostrConnectOfferRoutesToTheSignerScreenCarryingTheUri() {
        val offer = "nostrconnect://" + "b".repeat(64) + "?relay=wss%3A%2F%2Frelay.example.com&secret=abc123"
        assertEquals(Route.Nip46Signer(connectUri = offer), uriToRoute(offer, account))
    }

    @Test
    fun fragmentHashtagOrNullExtractsTheTag() {
        assertEquals("NostrMultiplayerGames", fragmentHashtagOrNull("#NostrMultiplayerGames"))
        assertNull(fragmentHashtagOrNull("#"))
        assertNull(fragmentHashtagOrNull("##double"))
        assertNull(fragmentHashtagOrNull("#tag!"))
        assertNull(fragmentHashtagOrNull("https://example.com/page#anchor"))
        assertNull(fragmentHashtagOrNull("nostr:hashtag?id=foo"))
    }
}
