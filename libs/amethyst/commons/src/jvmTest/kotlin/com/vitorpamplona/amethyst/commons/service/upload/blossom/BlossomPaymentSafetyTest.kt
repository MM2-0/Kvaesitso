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
package com.vitorpamplona.amethyst.commons.service.upload.blossom

import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentRequired
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * BUD-07 is "a server tells the client to pay an invoice it chose". Everything
 * about the amount, the retry loop and the on-screen justification is therefore
 * attacker-influenced. These tests pin the four limits that keep a hostile paid
 * Blossom server from draining the user's NWC wallet.
 */
class BlossomPaymentSafetyTest {
    // Real BOLT-11 invoices: getAmountInSats verifies the bech32 checksum, so
    // these cannot be fabricated.

    /** 1,000 sats — a plausible per-blob storage fee, under the cap. */
    private val invoice1000Sats =
        "lnbc10u1p3l0wg0pp5y5y3vxt3429m28uuq56uqhwxadftn67yaarq06h3y9nqapz72n6sdqqxqyjw5q9q7sqqqqqqqqqqqqqqqqqqqqqqqqq9qsqsp5y2tazp42xde3c0tdsz30zqcekrt0lzrneszdtagy2qn7vs0d3p5qrzjqwryaup9lh50kkranzgcdnn2fgvx390wgj5jd07rwr3vxeje0glcll7jdvcln4lhw5qqqqlgqqqqqeqqjqdau9jzseecmvmh03h88xyf5f980xx45fmn0cej654v5jr79ye36pww90jwdda38damlmgt54v8rn6q9kywtw057rh4v3wwrmn8fajagqnssr7v"

    /** 42 sats. */
    private val invoice42Sats =
        "lnbc420n1pns600jdqjfah8wctjvss0p8at5ynp4qtfc238rdkzsj26waa3l8zgag9damzltzsqcrlscj9gvpc7ch2qs2pp5ks03qwm8laa78hnh0xy0p78l6wmyuj3zfqas3gzc2a52lj2zrmtqsp539528j3tvvfzpk8n5v966zccvj3pq2l3etxqxh5qsp8emh29yw3q9qyysgqcqpcxqyz5vqrzjqvdnqyc82a9maxu6c7mee0shqr33u4z9z04wpdwhf96gxzpln8jcrapyqqqqqqp2rcqqqqlgqqqqqzsq2qrzjqw9fu4j39mycmg440ztkraa03u5qhtuc5zfgydsv6ml38qd4azymlapyqqqqqqqp9sqqqqlgqqqq86qqjqrzjq26922n6s5n5undqrf78rjjhgpcczafws45tx8237y7pzx3fg8wwxrgayyqq2mgqqqqqqqqqqqqqqqqq2quc90y7tgfxuauh0vjfvhxjgektaycfesne76jcuk4u9mt6a9l39pddzk3muwy03sjvfk0w8390xxnpzu2656jf2l73ya59ye2yx9aaqpdgxmaq"

    /** 100,000 sats — an order of magnitude past any real storage fee. */
    private val invoice100kSats =
        "lnbc1m1pjt9u0qsp553q90pj5mafzv20w45eqavned9tgwhl4q99n9s5ppcw24nzw3zeqpp5002kd3ktym67du86kj665fgaev7ka8ys7j5yz5fg686lr5e2gfkshp5dkk27nnuax05az3pk2r6ytxtvwn5j4xzsq9ajprhc7crjkmgvr3qxqyjw5qcqpjrzjqtzxvfsuxe4l92pf97tt4rcgpy2xalkmlwexh899wqxf83l8nwv4xzh0gvqq89qqqqqqqqlgqqqqq0gqvs9qxpqysgqx5mz04wd7kqu5zhhel9enr036hjrp4gga0nz084p2asjl36a0zmrk6mhqa249zsgqref2rlvhffm73u7rxgr47gden6rugup4ksvpzsqvds4pz"

    /** No amount in the HRP: the payee decides how much to take. */
    private val invoiceAmountless =
        "lnbc1pvjluezsp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygspp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdpl2pkx2ctnv5sxxmmwwd5kgetjypeh2ursdae8g6twvus8g6rfwvs8qun0dfjkxaq9qrsgq357wnc5r2ueh7ck6q93dj32dlqnls087fxdwk8qakdyafkq3yap9us6v52vjjsrvywa6rt52cm9r9zqt8r2t7mlcwspyetp5h2tztugp9lfyql"

    private fun challenge(
        invoice: String,
        reason: String? = null,
    ) = BlossomPaymentRequired(lightning = invoice, reason = reason)

    @Before
    fun reset() {
        InFlightInvoices.clear()
    }

    // ------------------------------------------------------------------
    // (a) amount cap + "what you saw is what you pay"
    // ------------------------------------------------------------------

    @Test
    fun amountUnderCapAndMatchingTheDialogIsAccepted() {
        assertEquals(
            BlossomPaymentHandler.AmountCheck.Ok(1_000L),
            BlossomPaymentHandler.checkAmount(challenge(invoice1000Sats), shownSats = 1_000L),
        )
        assertEquals(
            BlossomPaymentHandler.AmountCheck.Ok(42L),
            BlossomPaymentHandler.checkAmount(challenge(invoice42Sats), shownSats = 42L),
        )
    }

    @Test
    fun overCapInvoiceIsRefused() {
        val check = BlossomPaymentHandler.checkAmount(challenge(invoice100kSats), shownSats = 100_000L)
        assertTrue("100k sats must be over the cap, got $check", check is BlossomPaymentHandler.AmountCheck.OverCap)
        assertEquals(100_000L, (check as BlossomPaymentHandler.AmountCheck.OverCap).sats)
        // The cap must actually bite below this invoice, otherwise the test is vacuous.
        assertTrue(BlossomPaymentHandler.MAX_PAYMENT_SATS < 100_000L)
    }

    @Test
    fun amountThatDoesNotMatchTheDialogIsRefused() {
        // The dialog said 1 sat; the invoice wants 1,000.
        val check = BlossomPaymentHandler.checkAmount(challenge(invoice1000Sats), shownSats = 1L)
        assertEquals(BlossomPaymentHandler.AmountCheck.Mismatch(1L, 1_000L), check)
    }

    @Test
    fun amountlessInvoiceIsRefused() {
        assertEquals(
            BlossomPaymentHandler.AmountCheck.Amountless,
            BlossomPaymentHandler.checkAmount(challenge(invoiceAmountless), shownSats = null),
        )
        // ...and it cannot be smuggled through by claiming a shown amount either.
        assertEquals(
            BlossomPaymentHandler.AmountCheck.Amountless,
            BlossomPaymentHandler.checkAmount(challenge(invoiceAmountless), shownSats = 1L),
        )
    }

    @Test
    fun everyRefusalCarriesAUserFacingReason() {
        listOf(
            BlossomPaymentHandler.checkAmount(challenge(invoice100kSats), 100_000L),
            BlossomPaymentHandler.checkAmount(challenge(invoice1000Sats), 1L),
            BlossomPaymentHandler.checkAmount(challenge(invoiceAmountless), null),
        ).forEach {
            assertTrue("refusal must explain itself: $it", BlossomPaymentHandler.refusalReason(it).isNotBlank())
        }
    }

    // ------------------------------------------------------------------
    // (b) the re-prompt loop is bounded
    // ------------------------------------------------------------------

    @Test
    fun aTargetPromptsAtMostOncePerUserAction() {
        val ledger = PaymentPromptLedger()
        ledger.beginUserAction()

        assertTrue("first 402 must prompt", ledger.shouldPrompt("hash1", "https://paid.example.com"))
        // The server pockets the preimage and answers 402 again, forever.
        repeat(50) {
            assertFalse(
                "a server replying 402 after payment must not re-prompt",
                ledger.shouldPrompt("hash1", "https://paid.example.com"),
            )
        }
    }

    @Test
    fun otherTargetsInTheSameActionStillGetTheirOnePrompt() {
        val ledger = PaymentPromptLedger()
        ledger.beginUserAction()

        assertTrue(ledger.shouldPrompt("hash1", "https://a.example.com"))
        assertTrue(ledger.shouldPrompt("hash1", "https://b.example.com"))
        assertTrue(ledger.shouldPrompt("hash2", "https://a.example.com"))
        assertFalse(ledger.shouldPrompt("hash1", "https://a.example.com"))
    }

    @Test
    fun aFreshUserActionRestoresTheBudget() {
        val ledger = PaymentPromptLedger()
        ledger.beginUserAction()
        assertTrue(ledger.shouldPrompt("hash1", "https://a.example.com"))
        assertFalse(ledger.shouldPrompt("hash1", "https://a.example.com"))

        ledger.beginUserAction() // the user tapped mirror again, deliberately
        assertTrue(ledger.shouldPrompt("hash1", "https://a.example.com"))
    }

    // ------------------------------------------------------------------
    // (c) a timed-out payment cannot be paid a second time
    // ------------------------------------------------------------------

    @Test
    fun aTimedOutInvoiceCannotBeSentAgain() {
        // pay() claims the invoice before handing it to the wallet...
        assertTrue(InFlightInvoices.tryClaim(invoice1000Sats))
        // ...and on timeout deliberately does NOT release it, because NIP-47 has
        // no cancel and the payment may still settle.
        assertTrue(InFlightInvoices.isAwaiting(invoice1000Sats))
        assertFalse(
            "an unresolved invoice must never be sent to the wallet twice",
            InFlightInvoices.tryClaim(invoice1000Sats),
        )
    }

    @Test
    fun aResolvedInvoiceReleasesTheClaim() {
        assertTrue(InFlightInvoices.tryClaim(invoice1000Sats))
        InFlightInvoices.release(invoice1000Sats) // wallet answered: paid, or explicitly failed
        assertFalse(InFlightInvoices.isAwaiting(invoice1000Sats))
        assertTrue(InFlightInvoices.tryClaim(invoice1000Sats))
    }

    @Test
    fun claimsAreScopedToTheInvoice() {
        assertTrue(InFlightInvoices.tryClaim(invoice1000Sats))
        assertTrue("a different invoice is unaffected", InFlightInvoices.tryClaim(invoice42Sats))
    }

    // ------------------------------------------------------------------
    // (d) the server's X-Reason cannot impersonate our own wording
    // ------------------------------------------------------------------

    @Test
    fun reasonIsStrippedOfControlCharactersAndNewlines() {
        val hostile = challenge(invoice1000Sats, reason = "Storage fee\n\n\n\n\n\n\n\n\n\n\n\n\nPay 1 sat ")
        val clean = hostile.sanitizedReason()!!

        assertFalse("no newlines", clean.contains('\n'))
        assertFalse("no carriage returns", clean.contains('\r'))
        assertFalse("no control characters", clean.any { it.isISOControl() })
        assertEquals("Storage fee Pay 1 sat", clean)
    }

    @Test
    fun reasonIsClampedInLength() {
        val long = challenge(invoice1000Sats, reason = "A".repeat(5_000))
        val clean = long.sanitizedReason()!!
        assertTrue("clamped, got ${clean.length}", clean.length <= BlossomPaymentRequired.MAX_REASON_LENGTH + 1)
    }

    @Test
    fun reasonBidiOverridesAreRemoved() {
        val clean = challenge(invoice1000Sats, reason = "fee \u202Ereversed\u202C text").sanitizedReason()!!
        assertFalse(clean.contains('\u202E'))
        assertFalse(clean.contains('\u202C'))
    }

    @Test
    fun blankOrAbsentReasonBecomesNull() {
        assertEquals(null, challenge(invoice1000Sats, reason = null).sanitizedReason())
        assertEquals(null, challenge(invoice1000Sats, reason = "\n\t    ").sanitizedReason())
    }
}
