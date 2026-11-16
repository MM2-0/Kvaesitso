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
package com.vitorpamplona.amethyst.commons.model.payments

import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentTargetTypesTest {
    @Test
    fun canonicalTrimsAndLowercases() {
        assertEquals("venmo", PaymentTargetTypes.canonical("  VenMo "))
        assertEquals("iban", PaymentTargetTypes.canonical("IBAN"))
    }

    @Test
    fun canonicalCollapsesAliasesWithinARailOnly() {
        assertEquals("bitcoin", PaymentTargetTypes.canonical("btc"))
        assertEquals("bitcoin", PaymentTargetTypes.canonical("onchain"))
        assertEquals("lightning", PaymentTargetTypes.canonical("ln"))
        assertEquals("lightning", PaymentTargetTypes.canonical("lnurl"))
        assertEquals("monero", PaymentTargetTypes.canonical("xmr"))
        // Different rails must never collapse together.
        assertTrue(PaymentTargetTypes.canonical("monero") != PaymentTargetTypes.canonical("bitcoin"))
    }

    @Test
    fun unknownTypesPassThroughUntouched() {
        assertEquals("pix", PaymentTargetTypes.canonical("pix"))
        assertEquals("upi", PaymentTargetTypes.canonical("upi"))
    }

    @Test
    fun walletCoveredIsExactlyTheLightningAndBitcoinFamilies() {
        listOf("lightning", "ln", "LNURL", "bitcoin", "btc", " onchain ").forEach {
            assertTrue(PaymentTargetTypes.isWalletCovered(it), "$it should be wallet covered")
        }
        listOf("venmo", "monero", "iban", "liquid", "ethereum").forEach {
            assertFalse(PaymentTargetTypes.isWalletCovered(it), "$it should not be wallet covered")
        }
    }

    @Test
    fun uriUsesTheDedicatedSchemeWhenThereIsOne() {
        assertEquals("bitcoin:bc1qxyz", PaymentTargetTypes.uriFor("btc", "bc1qxyz"))
        assertEquals("lightning:me@ln.tips", PaymentTargetTypes.uriFor("lnurl", "me@ln.tips"))
        assertEquals("liquidnetwork:lq1abc", PaymentTargetTypes.uriFor("liquid", "lq1abc"))
        assertEquals("monero:4Aaddr", PaymentTargetTypes.uriFor("XMR", "4Aaddr"))
    }

    @Test
    fun webTypesBecomeHttpsPages() {
        assertEquals("https://venmo.com/vitor", PaymentTargetTypes.uriFor("venmo", "vitor"))
        assertEquals("https://cash.app/\$vitor", PaymentTargetTypes.uriFor("cashapp", "\$vitor"))
        assertTrue(PaymentTargetTypes.isWebTarget("PayPal"))
        assertFalse(PaymentTargetTypes.isWebTarget("iban"))
    }

    @Test
    fun specCashmeIsCashAppAndCashappStaysAnAlias() {
        assertEquals("cashme", PaymentTargetTypes.canonical("cashapp"))
        assertEquals("cashme", PaymentTargetTypes.canonical("CashMe"))
        assertEquals("https://cash.app/\$vitor", PaymentTargetTypes.uriFor("cashme", "\$vitor"))
        // NIP-A3 describes a $-prefixed cashtag; accept the bare name too.
        assertEquals("https://cash.app/\$vitor", PaymentTargetTypes.uriFor("cashme", "vitor"))
        assertTrue(PaymentTargetTypes.isWebTarget("cashme"))
        assertEquals(PaymentTargetTypes.probeKeyFor("cashapp"), PaymentTargetTypes.probeKeyFor("cashme"))
    }

    @Test
    fun specListedTypesUseTheirNativeScheme() {
        assertEquals("bitcoin:bc1q", PaymentTargetTypes.uriFor("bitcoin", "bc1q"))
        assertEquals("bitcoincash:qq1", PaymentTargetTypes.uriFor("bitcoincash", "qq1"))
        assertEquals("ethereum:0xabc", PaymentTargetTypes.uriFor("ethereum", "0xabc"))
        assertEquals("lightning:me@ln.tips", PaymentTargetTypes.uriFor("lightning", "me@ln.tips"))
        assertEquals("litecoin:ltc1", PaymentTargetTypes.uriFor("litecoin", "ltc1"))
        assertEquals("monero:4A", PaymentTargetTypes.uriFor("monero", "4A"))
        assertEquals("nano:nano_1dctq", PaymentTargetTypes.uriFor("nano", "nano_1dctq"))
        assertEquals("solana:So1", PaymentTargetTypes.uriFor("solana", "So1"))
        assertEquals("tron:T9", PaymentTargetTypes.uriFor("tron", "T9"))
        assertEquals("zcash:zs1", PaymentTargetTypes.uriFor("zcash", "zs1"))
        // BIP-352 silent payment addresses travel in a BIP-321 bitcoin: URI's sp parameter.
        assertEquals("bitcoin:?sp=sp1qq", PaymentTargetTypes.uriFor("bip352", "sp1qq"))
        assertEquals("https://paypal.me/vitor", PaymentTargetTypes.uriFor("paypal", "vitor"))
        assertEquals("https://venmo.com/vitor", PaymentTargetTypes.uriFor("venmo", "vitor"))
        assertEquals("https://revolut.me/vitor", PaymentTargetTypes.uriFor("revolut", "vitor"))
    }

    @Test
    fun specListedTypesWithoutASchemeUsePayto() {
        // A BIP-353 name has to be resolved over DNS first; there is no URI scheme for it.
        assertEquals("payto://bip353/vitor@example.com", PaymentTargetTypes.uriFor("bip353", "vitor@example.com"))
    }

    @Test
    fun unknownTypesFallBackToPayto() {
        assertEquals("payto://iban/DE75512108001245126199", PaymentTargetTypes.uriFor("IBAN", "DE75512108001245126199"))
        assertEquals("payto://upi/vitor@bank", PaymentTargetTypes.uriFor("upi", " vitor@bank "))
    }

    @Test
    fun probeKeyKeepsHostSoPaytoTypesDoNotShareOneAnswer() {
        // An app may declare scheme="payto" host="iban"; a scheme-only key would
        // then wrongly report that payto://upi is handled too.
        assertEquals("payto://iban/", PaymentTargetTypes.probeKeyFor("iban"))
        assertEquals("payto://upi/", PaymentTargetTypes.probeKeyFor("upi"))
        assertTrue(PaymentTargetTypes.probeKeyFor("iban") != PaymentTargetTypes.probeKeyFor("upi"))
        // Aliases of one rail share a key, as they share a scheme.
        assertEquals(PaymentTargetTypes.probeKeyFor("btc"), PaymentTargetTypes.probeKeyFor("bitcoin"))
    }
}

class PayToRailMatcherTest {
    private fun t(
        type: String,
        authority: String = "handle",
    ) = PaymentTarget(type, authority)

    @Test
    fun noRecipientTargetsMeansNoChips() {
        assertEquals(emptyList(), PayToRailMatcher.match(emptyList()))
    }

    @Test
    fun aTargetIsOfferedRegardlessOfWhatTheSenderPublishes() {
        // Capability, not symmetry: paying a Monero address needs a wallet, not a
        // published address of one, so the sender's own list is never consulted.
        val out = PayToRailMatcher.match(listOf(t("monero", "theirs")))
        assertEquals(listOf(t("monero", "theirs")), out)
    }

    @Test
    fun walletCoveredTypesNeverProduceAChip() {
        // Those ARE the existing rails — matching them would draw a second bolt
        // beside the first.
        val wallets = listOf(t("lightning", "a@b.c"), t("btc", "bc1q"), t("ln", "x@y.z"))
        assertEquals(emptyList(), PayToRailMatcher.match(wallets))
    }

    @Test
    fun aliasesCollapseToOneChip() {
        val out = PayToRailMatcher.match(listOf(t("xmr", "first"), t("monero", "second")))
        assertEquals(listOf(t("xmr", "first")), out)
    }

    @Test
    fun oneChipPerProtocolKeepingTheFirst() {
        val recipient = listOf(t("venmo", "first"), t("venmo", "second"), t("monero", "xmr1"))
        assertEquals(listOf(t("venmo", "first"), t("monero", "xmr1")), PayToRailMatcher.match(recipient))
    }

    @Test
    fun blankAuthoritiesAreSkipped() {
        assertEquals(emptyList(), PayToRailMatcher.match(listOf(t("venmo", "   "))))
    }

    @Test
    fun recipientOrderIsPreserved() {
        val recipient = listOf(t("monero", "m"), t("venmo", "v"))
        assertEquals(listOf("monero", "venmo"), PayToRailMatcher.match(recipient).map { it.type })
    }

    @Test
    fun typesAreNormalisedBeforeDeduping() {
        val out = PayToRailMatcher.match(listOf(t(" VENMO ", "first"), t("venmo", "second")))
        assertEquals(listOf(t(" VENMO ", "first")), out)
    }
}

/** The gates on the hand-off chip, exercised without a Note or a PackageManager. */
class PayToRailGateTest {
    private fun t(
        type: String,
        authority: String = "handle",
    ) = PaymentTarget(type, authority)

    private val theirs = listOf(t("venmo", "them"), t("monero", "theirxmr"))
    private val anyAppOpens: (PaymentTarget) -> Boolean = { true }

    private fun select(
        enabled: Boolean = true,
        hasAuthor: Boolean = true,
        hasZapSplit: Boolean = false,
        recipient: List<PaymentTarget> = theirs,
        canOpen: (PaymentTarget) -> Boolean = anyAppOpens,
    ) = PayToRailMatcher.selectFor(enabled, hasAuthor, hasZapSplit, canOpen) { recipient }

    @Test
    fun offeredWhenEveryGatePasses() {
        assertEquals(listOf("venmo", "monero"), select().map { it.type })
    }

    @Test
    fun settingOffHidesIt() {
        assertEquals(emptyList(), select(enabled = false))
    }

    @Test
    fun aZapSplitHidesIt() {
        // payto leaves with one authority and returns no receipt, so it cannot
        // honour a note that asks for the zap to be divided.
        assertEquals(emptyList(), select(hasZapSplit = true))
    }

    @Test
    fun noAuthorHidesIt() {
        assertEquals(emptyList(), select(hasAuthor = false))
    }

    @Test
    fun aTargetNoInstalledAppCanOpenIsDropped() {
        val out = select(canOpen = { it.type == "venmo" })
        assertEquals(listOf("venmo"), out.map { it.type })
    }

    @Test
    fun noInstalledAppAtAllHidesIt() {
        assertEquals(emptyList(), select(canOpen = { false }))
    }

    @Test
    fun everyOpenableTargetIsOfferedWithNoCap() {
        val many = listOf(t("venmo"), t("monero"), t("pix"), t("upi"), t("iban"))
        assertEquals(many.size, select(recipient = many).size)
    }

    @Test
    fun recipientTargetsAreNotReadWhenACheapGateAlreadyFailed() {
        // peek() runs on the one-tap zap path too, and reading the recipient's
        // kind:10133 walks its tag array. Nothing should touch it once a gate fails.
        var reads = 0
        val counted = {
            reads++
            theirs
        }

        PayToRailMatcher.selectFor(false, true, false, anyAppOpens, counted)
        PayToRailMatcher.selectFor(true, false, false, anyAppOpens, counted)
        PayToRailMatcher.selectFor(true, true, true, anyAppOpens, counted)
        assertEquals(0, reads)

        PayToRailMatcher.selectFor(true, true, false, anyAppOpens, counted)
        assertEquals(1, reads)
    }

    @Test
    fun lightningAndBitcoinStayWithTheirOwnRails() {
        assertEquals(emptyList(), select(recipient = listOf(t("lightning", "a@b.c"), t("btc", "bc1q"))))
    }
}
