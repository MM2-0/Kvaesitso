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
package com.vitorpamplona.amethyst.commons.routstr

import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The purchase state machine with every network call faked: the money rules, one by one. */
class RoutstrPurchasesTest {
    private val account = "a".repeat(64)
    private val node = RoutstrTarget("https://node.example", "http://" + "a".repeat(56) + ".onion", "Example")

    /** A node that answers as the test says, and remembers what it was asked. */
    private class FakeNode : RoutstrApi {
        var invoiceCount = 0
        val bases = mutableListOf<String>()
        var invoiceExpiresAt: Long? = 2_000L
        val status = HashMap<String, () -> RoutstrInvoiceStatus>()
        var recover: (String) -> RoutstrInvoiceStatus = { throw RoutstrException(RoutstrError(404, RoutstrFailure.NOT_FOUND)) }
        var cashu: (String, String?) -> RoutstrCashuResult = { token, _ -> RoutstrCashuResult("sk-" + token.takeLast(4), 21_000, null, null, null) }
        val topupKeys = mutableListOf<String?>()
        val tokensSent = mutableListOf<String>()
        val balanceKeys = mutableListOf<String>()
        var balance: (String) -> RoutstrBalance = { throw RoutstrException(RoutstrError(401, RoutstrFailure.KEY_NOT_FOUND)) }

        /** What this device had kept when each Cashu create went out. */
        var keptAtCreate: () -> List<RoutstrPendingPurchase> = { emptyList() }
        val keptWhenSent = mutableListOf<List<RoutstrPendingPurchase>>()

        override suspend fun info(base: String) = error("not used")

        override suspend fun models(
            base: String,
            apiKey: String?,
        ) = error("not used")

        override suspend fun balance(
            base: String,
            apiKey: String,
        ): RoutstrBalance {
            balanceKeys += apiKey
            return balance(apiKey)
        }

        override suspend fun createInvoice(
            base: String,
            amountSats: Long,
            purpose: RoutstrPurpose,
            apiKey: String?,
        ): RoutstrInvoice {
            bases += base
            topupKeys += apiKey
            invoiceCount++
            return RoutstrInvoice("inv$invoiceCount", "lnbc${amountSats}fake$invoiceCount", amountSats, invoiceExpiresAt, "hash")
        }

        override suspend fun invoiceStatus(
            base: String,
            invoiceId: String,
        ): RoutstrInvoiceStatus {
            bases += base
            return status[invoiceId]?.invoke() ?: throw RoutstrException(RoutstrError(404, RoutstrFailure.NOT_FOUND))
        }

        override suspend fun recover(
            base: String,
            bolt11: String,
        ) = recover.invoke(bolt11)

        override suspend fun createWithCashu(
            base: String,
            token: String,
        ): RoutstrCashuResult {
            tokensSent += token
            keptWhenSent += keptAtCreate()
            return cashu(token, null)
        }

        override suspend fun topupWithCashu(
            base: String,
            apiKey: String,
            token: String,
        ): RoutstrCashuResult {
            tokensSent += token
            return cashu(token, apiKey)
        }
    }

    private class FakeWallet : RoutstrCashuWallet {
        val received = mutableListOf<String>()
        var accepts = true
        var minted = 0

        override fun balances(mints: List<String>) = mints.associateWith { 10_000L }

        override suspend fun mintToken(
            mint: String,
            amountSats: Long,
        ): String {
            minted++
            return "cashuBminted$amountSats"
        }

        override suspend fun receive(token: String): Boolean {
            if (accepts) received += token
            return accepts
        }
    }

    private fun pending(status: String = "pending") = RoutstrInvoiceStatus(status, null, 1000, null, 2_000L)

    private fun paid(key: String? = "sk-bought") = RoutstrInvoiceStatus("paid", key, 1000, 1_500L, 2_000L)

    private class Setup(
        val api: FakeNode = FakeNode(),
        val store: InMemoryRoutstrPurchaseStore = InMemoryRoutstrPurchaseStore(),
        val wallet: FakeWallet = FakeWallet(),
        var saves: Boolean = true,
        var now: Long = 1_000L,
        var tor: Boolean = false,
    ) {
        val saved = mutableListOf<AgentKeyTag>()
        private var ids = 0

        fun purchases(account: String) =
            RoutstrPurchases(
                account = account,
                api = api,
                store = store,
                saveKey = { tag ->
                    if (saves) saved += tag
                    saves
                },
                wallet = { wallet },
                torEnabled = { tor },
                now = { now },
                newId = { "id${++ids}" },
            )
    }

    @Test
    fun theInvoiceIsKeptBeforeItIsShownThenTheKeyIsSaved() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val purchase = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            // Kept on the device before anyone could pay it.
            assertEquals(listOf(purchase), s.store.saved[account])
            assertEquals(RoutstrStage.AWAITING_PAYMENT, purchase.stage)
            assertEquals("lnbc1000fake1", purchase.bolt11)

            s.api.status["inv1"] = { pending() }
            assertEquals(RoutstrPurchaseOutcome.Waiting, purchases.check(purchase.id))

            s.api.status["inv1"] = { paid() }
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), purchases.check(purchase.id))
            assertEquals(listOf(AgentKeyTag("sk-bought", "https://node.example/v1")), s.saved)
            assertEquals(emptyList(), s.store.saved[account])
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), purchases.outcomes.value[purchase.id])
        }

    @Test
    fun anInvoiceThisDeviceCantKeepIsNeverShown() =
        runTest {
            val s = Setup()
            s.store.failNextSave = IllegalStateException("disk full")
            val purchases = s.purchases(account)
            assertFailsWith<RoutstrNotPersistedException> { purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE) }
            assertEquals(emptyList(), purchases.pending.value)
        }

    @Test
    fun onLaunchAPendingPurchaseIsResumedAndRecoveredByBolt11() =
        runTest {
            val s = Setup()
            val bought = s.purchases(account).buyWithLightning(node, 1000, RoutstrPurpose.CREATE)

            // The app restarts; the node no longer knows the id, but the bolt11 still names the payment.
            s.api.recover = { bolt11 -> if (bolt11 == bought.bolt11) paid("sk-recovered") else throw RoutstrException(RoutstrError(404, RoutstrFailure.NOT_FOUND)) }
            val relaunched = s.purchases(account)
            relaunched.resume(backgroundScope)
            runCurrent()

            assertEquals(listOf(AgentKeyTag("sk-recovered", "https://node.example/v1")), s.saved)
            assertEquals(emptyList(), relaunched.pending.value)
            assertEquals(emptyList(), s.store.saved[account])
        }

    @Test
    fun aKeyThatCantBeSavedStaysPendingUntilItIs() =
        runTest {
            val s = Setup(saves = false)
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            s.api.status["inv1"] = { paid("sk-held") }

            assertEquals(RoutstrPurchaseOutcome.KeyNotSaved, purchases.check(bought.id))
            val held = s.store.saved[account]!!.single()
            assertEquals(RoutstrStage.KEY_UNSAVED, held.stage)
            assertEquals("sk-held", held.apiKey)
            // The key never shows in a log line.
            assertTrue("sk-held" !in held.toString())

            // The next launch tries again, and this time kind 12000 takes it.
            s.saves = true
            val relaunched = s.purchases(account)
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), relaunched.check(bought.id))
            assertEquals(emptyList(), s.store.saved[account])
            // Never bought twice.
            assertEquals(1, s.api.invoiceCount)
        }

    @Test
    fun anUnpaidInvoiceIsDroppedADayAfterItExpires() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            s.api.status["inv1"] = { pending("expired") }

            // Expired is not final: the node still settles it for 24 hours.
            s.now = 2_000L + 60
            assertEquals(RoutstrPurchaseOutcome.Expired, purchases.check(bought.id))
            assertEquals(1, purchases.pending.value.size)

            s.now = 2_000L + RoutstrPurchases.LATE_SETTLEMENT_SECONDS + 1
            assertEquals(RoutstrPurchaseOutcome.Expired, purchases.check(bought.id))
            assertEquals(emptyList(), purchases.pending.value)
            assertEquals(emptyList(), s.store.saved[account])
        }

    @Test
    fun anInvoiceOurWalletPaidIsNeverDropped() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            purchases.markWalletPaid(bought.id)
            s.api.status["inv1"] = { pending("expired") }
            s.now = 2_000L + RoutstrPurchases.LATE_SETTLEMENT_SECONDS * 3

            assertEquals(RoutstrPurchaseOutcome.PaidNoKeyYet, purchases.check(bought.id))
            assertEquals(RoutstrStage.PAID, purchases.get(bought.id)?.stage)
            assertEquals(false, purchases.abandon(bought.id))
            assertEquals(1, s.store.saved[account]!!.size)
        }

    @Test
    fun aSettlingOrKeylessPaidInvoiceIsRecoveredNotRebought() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)

            s.api.status["inv1"] = { pending("settlement_pending") }
            assertEquals(RoutstrPurchaseOutcome.PaidNoKeyYet, purchases.check(bought.id))
            s.api.status["inv1"] = { paid(key = null) }
            assertEquals(RoutstrPurchaseOutcome.PaidNoKeyYet, purchases.check(bought.id))
            // Once seen paid, an "expired" later doesn't drop it.
            s.api.status["inv1"] = { pending("expired") }
            s.now = 10_000_000L
            assertEquals(RoutstrPurchaseOutcome.PaidNoKeyYet, purchases.check(bought.id))

            s.api.recover = { paid("sk-late") }
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), purchases.check(bought.id))
            assertEquals(1, s.api.invoiceCount)
        }

    @Test
    fun watchPollsUntilPaidOnVirtualTime() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            var polls = 0
            s.api.status["inv1"] = { if (++polls < 4) pending() else paid() }
            purchases.setShown(bought.id, true)
            purchases.follow(backgroundScope, bought.id)
            advanceTimeBy(RoutstrPurchases.POLL_MS * 2 + 1)
            assertEquals(3, polls)
            assertEquals(emptyList(), s.saved)
            advanceTimeBy(RoutstrPurchases.POLL_MS)
            runCurrent()
            assertEquals(1, s.saved.size)
        }

    @Test
    fun aTopUpSendsItsKeyAndTorUsesTheOnion() =
        runTest {
            val s = Setup(tor = true)
            val purchases = s.purchases(account)
            val key = AgentKeyTag("sk-existing", "https://node.example/v1")
            val bought = purchases.buyWithLightning(node, 5000, RoutstrPurpose.TOPUP, key)
            assertEquals("sk-existing", s.api.topupKeys.single())
            assertTrue(
                s.api.bases
                    .single()
                    .endsWith(".onion"),
            )
            // Its record says which key, but never holds it.
            assertTrue("sk-existing" !in s.store.saved[account].toString())
            assertNull(bought.apiKey)

            s.api.status["inv1"] = { RoutstrInvoiceStatus("paid", null, 5000, 1_500, 2_000) }
            assertEquals(RoutstrPurchaseOutcome.ToppedUp, purchases.check(bought.id))
            assertEquals(emptyList(), s.saved)
        }

    @Test
    fun aCashuTokenIsNeverStoredAndItsKeyIsSaved() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val outcome = purchases.buyWithCashu(node, "cashuBpasted1234", RoutstrPurpose.CREATE)
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), outcome)
            assertEquals(listOf(AgentKeyTag("sk-1234", "https://node.example/v1")), s.saved)
            assertEquals(emptyList(), s.store.saved[account])
        }

    @Test
    fun aLostAnswerHandsTheTokenBackToSendAgain() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            s.api.cashu = { _, _ -> throw RoutstrException(RoutstrError(null, RoutstrFailure.UNREACHABLE)) }
            val lost = assertIs<RoutstrPurchaseOutcome.TokenNotTaken>(purchases.buyWithCashu(node, "cashuBpasted1234", RoutstrPurpose.CREATE))
            assertEquals("cashuBpasted1234", lost.token)
            // The token was never written anywhere.
            assertTrue(
                s.store.saved[account]
                    .orEmpty()
                    .none { "cashuBpasted1234" in (it.token ?: "") },
            )
            assertTrue("cashuBpasted1234" !in lost.toString())

            // Sent again, the node gives the same key: sk- + sha256(token).
            s.api.cashu = { token, _ -> RoutstrCashuResult("sk-" + token.takeLast(4), 21_000, null, null, null) }
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), purchases.buyWithCashu(node, lost.token!!, RoutstrPurpose.CREATE))
        }

    @Test
    fun aRefusedWalletTokenGoesBackToTheWallet() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            s.api.cashu = { _, _ -> throw RoutstrException(RoutstrError(400, RoutstrFailure.TOKEN_REJECTED)) }
            val refused = assertIs<RoutstrPurchaseOutcome.TokenNotTaken>(purchases.buyFromWallet(node, "https://mint.example", 2000, RoutstrPurpose.CREATE))
            assertNull(refused.token)
            assertEquals(listOf("cashuBminted2000"), s.wallet.received)
        }

    @Test
    fun changeIsHandedBackToTheWallet() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            s.api.cashu = { _, key -> RoutstrCashuResult(if (key == null) "sk-new" else null, 20_000, "cashuBchange", 3, "sat") }
            val key = AgentKeyTag("sk-existing", "https://node.example/v1")

            assertEquals(RoutstrPurchaseOutcome.ToppedUp, purchases.buyWithCashu(node, "cashuBforeign", RoutstrPurpose.TOPUP, key))
            assertEquals(listOf("cashuBchange"), s.wallet.received)
            assertEquals(emptyList(), purchases.pending.value)

            // Create returns change too, now and then: handled the same way.
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), purchases.buyWithCashu(node, "cashuBagain", RoutstrPurpose.CREATE))
            assertEquals(listOf("cashuBchange", "cashuBchange"), s.wallet.received)
        }

    @Test
    fun changeTheWalletWontTakeIsKeptUntilItDoes() =
        runTest {
            val s = Setup()
            s.wallet.accepts = false
            val purchases = s.purchases(account)
            s.api.cashu = { _, _ -> RoutstrCashuResult(null, 20_000, "cashuBchange", 3, "sat") }
            purchases.buyWithCashu(node, "cashuBforeign", RoutstrPurpose.TOPUP, AgentKeyTag("sk-existing", "https://node.example/v1"))

            val kept = s.store.saved[account]!!.single()
            assertEquals(RoutstrStage.CHANGE_UNCLAIMED, kept.stage)
            assertEquals("cashuBchange", kept.token)
            assertEquals(3L, kept.amountSats)

            s.wallet.accepts = true
            assertEquals(RoutstrPurchaseOutcome.ChangeReturned, purchases.check(kept.id))
            assertEquals(emptyList(), s.store.saved[account])
        }

    @Test
    fun pendingPurchasesArePerAccount() =
        runTest {
            val s = Setup()
            s.purchases(account).buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            assertEquals(emptyList(), s.purchases("b".repeat(64)).load())
            assertEquals(1, s.purchases(account).load().size)
        }

    @Test
    fun aCancelledInvoiceIsHiddenButKeptUntilItCantSettle() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            s.api.status["inv1"] = { pending("expired") }
            assertTrue(purchases.abandon(bought.id))
            // Kept: it may have been paid from another wallet, and nodes settle late ones for a day.
            assertTrue(purchases.get(bought.id)!!.hiddenByUser)
            s.now = 2_000L + 3_600
            assertEquals(RoutstrPurchaseOutcome.Expired, purchases.check(bought.id))
            assertEquals(1, purchases.pending.value.size)
            // Paid late after all: the key is saved.
            s.api.status["inv1"] = { paid("sk-late") }
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), purchases.check(bought.id))

            // Never paid: dropped a day past its expiry.
            val second = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            s.api.status["inv2"] = { pending("expired") }
            assertTrue(purchases.abandon(second.id))
            s.now = (second.expiresAt ?: 0) + RoutstrPurchases.LATE_SETTLEMENT_SECONDS + 1
            assertEquals(RoutstrPurchaseOutcome.Expired, purchases.check(second.id))
            assertNull(purchases.get(second.id))

            // One paid meanwhile is kept, and shown again as paid.
            val third = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            s.api.status["inv3"] = { paid(key = null) }
            assertTrue(purchases.abandon(third.id))
            assertEquals(RoutstrPurchaseOutcome.PaidNoKeyYet, purchases.check(third.id))
            assertEquals(RoutstrStage.PAID, purchases.get(third.id)!!.stage)
            assertEquals(false, purchases.abandon(third.id))
            // One the app's wallet was asked to pay can't be cancelled.
            val fourth = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            purchases.markPayRequested(fourth.id)
            assertEquals(false, purchases.abandon(fourth.id))
            assertTrue(
                s.store.saved[account]!!
                    .first { it.id == fourth.id }
                    .walletPayRequested,
            )
        }

    @Test
    fun anInvoiceTheWalletWasAskedToPayIsNeverOfferedAgain() {
        val invoice = RoutstrPendingPurchase("inv1", RoutstrPayment.LIGHTNING, RoutstrPurpose.CREATE, RoutstrStage.AWAITING_PAYMENT, "https://node.example", amountSats = 1000, createdAt = 1, bolt11 = "lnbc")
        assertTrue(RoutstrBuyUi.Invoice(invoice).canPay)
        assertFalse(RoutstrBuyUi.Invoice(invoice.copy(walletPayRequested = true)).canPay)
        assertFalse(RoutstrBuyUi.Invoice(invoice.copy(walletPaid = true)).canPay)
        assertFalse(RoutstrBuyUi.Invoice(invoice, paying = true).canPay)
    }

    @Test
    fun aCashuCreateIsKeptByItsKeyBeforeTheTokenGoes() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val token = "cashuBpasted1234"
            s.api.keptAtCreate = { s.store.saved[account].orEmpty() }
            purchases.buyWithCashu(node, token, RoutstrPurpose.CREATE)
            val kept =
                s.api.keptWhenSent
                    .single()
                    .single()
            assertEquals(RoutstrStage.CASHU_SENT, kept.stage)
            assertEquals(RoutstrPurchases.cashuKey(token), kept.apiKey)
            assertTrue(kept.apiKey!!.startsWith("sk-") && kept.apiKey!!.length == 3 + 64)
            assertNull(kept.token)
            assertTrue(token !in s.store.saved.toString())
        }

    @Test
    fun aLostCashuAnswerIsSettledByTheKeysBalance() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val token = "cashuBpasted5678"
            s.api.cashu = { _, _ -> throw RoutstrException(RoutstrError(null, RoutstrFailure.UNREACHABLE)) }
            s.api.balance = { throw RoutstrException(RoutstrError(null, RoutstrFailure.UNREACHABLE)) }
            assertIs<RoutstrPurchaseOutcome.TokenNotTaken>(purchases.buyWithCashu(node, token, RoutstrPurpose.CREATE))
            val held = s.store.saved[account]!!.single()
            assertEquals(RoutstrStage.CASHU_SENT, held.stage)

            // The app restarts; the node took the token after all: its key has a balance.
            s.api.balance = { RoutstrBalance(21_000, null, null, null, null) }
            val again = s.purchases(account)
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), again.check(held.id))
            assertEquals(listOf(AgentKeyTag(RoutstrPurchases.cashuKey(token), "https://node.example/v1")), s.saved)
            assertEquals(emptyList(), s.store.saved[account])
        }

    @Test
    fun aCashuKeyTheNodeNeverMadeIsDroppedAfterTheWindow() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            s.api.cashu = { _, _ -> throw RoutstrException(RoutstrError(null, RoutstrFailure.UNREACHABLE)) }
            purchases.buyWithCashu(node, "cashuBpasted9999", RoutstrPurpose.CREATE)
            val held = s.store.saved[account]!!.single()
            // Unknown to the node, but it may still be making it: kept for now.
            assertIs<RoutstrPurchaseOutcome.Failed>(purchases.check(held.id))
            s.now += RoutstrPurchases.CASHU_PROBE_WINDOW_SECONDS
            assertEquals(RoutstrPurchaseOutcome.Gone, purchases.check(held.id))
            assertEquals(emptyList(), s.store.saved[account])
        }

    @Test
    fun aWalletTokenTheNodeNeverAnsweredGoesBackToTheWallet() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            s.api.cashu = { _, _ -> throw RoutstrException(RoutstrError(null, RoutstrFailure.UNREACHABLE)) }
            val lost = assertIs<RoutstrPurchaseOutcome.TokenNotTaken>(purchases.buyFromWallet(node, "https://mint.example", 2000, RoutstrPurpose.CREATE))
            // The mint took the proofs back, so the node can't have them: nothing bought, nothing kept.
            assertNull(lost.token)
            assertEquals(listOf("cashuBminted2000"), s.wallet.received)
            assertEquals(emptyList(), s.store.saved[account])

            // The mint refuses them (the node spent them): kept, and the key is asked for later.
            s.wallet.accepts = false
            val spent = assertIs<RoutstrPurchaseOutcome.TokenNotTaken>(purchases.buyFromWallet(node, "https://mint.example", 3000, RoutstrPurpose.CREATE))
            assertEquals("cashuBminted3000", spent.token)
            assertEquals(
                RoutstrStage.CASHU_SENT,
                s.store.saved[account]!!
                    .single()
                    .stage,
            )
        }

    @Test
    fun aListThatCantBeReadIsNeverOverwritten() =
        runTest {
            val s = Setup()
            s.purchases(account).buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            val before = s.store.saved[account]
            s.store.unreadable = true
            val purchases = s.purchases(account)
            assertEquals(emptyList(), purchases.load())
            // Nothing is written over it: no invoice is shown that couldn't be kept.
            assertFailsWith<RoutstrNotPersistedException> { purchases.buyWithLightning(node, 2000, RoutstrPurpose.CREATE) }
            assertEquals(before, s.store.saved[account])
            // Readable again: the list comes back.
            s.store.unreadable = false
            assertEquals(before, purchases.load())
        }

    @Test
    fun aPaidInvoiceWithoutAKeyIsNotWrittenForEachCheck() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            s.api.status["inv1"] = { paid(key = null) }
            assertEquals(RoutstrPurchaseOutcome.PaidNoKeyYet, purchases.check(bought.id))
            val written = s.store.saved[account]
            assertEquals(RoutstrStage.PAID, written!!.single().stage)
            val writes = s.store.saves
            repeat(3) { assertEquals(RoutstrPurchaseOutcome.PaidNoKeyYet, purchases.check(bought.id)) }
            // Only the counter changed: nothing was written.
            assertEquals(writes, s.store.saves)
            assertEquals(written, s.store.saved[account])
            assertEquals(4, purchases.get(bought.id)!!.recoverAttempts)
        }

    @Test
    fun aPaidInvoiceWithoutAKeyBacksOff() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            var polls = 0
            s.api.status["inv1"] = {
                polls++
                paid(key = null)
            }
            purchases.follow(backgroundScope, bought.id)
            runCurrent()
            assertEquals(1, polls)
            // Then after 30 s, 60 s, 120 s, 240 s: 450 s in all for five polls.
            advanceTimeBy(RoutstrPurchases.RECOVER_MS * 15 - 1)
            assertEquals(4, polls)
            advanceTimeBy(2)
            assertEquals(5, polls)
            // Never more than an hour apart.
            advanceTimeBy(RoutstrPurchases.MAX_BACKOFF_MS * 10)
            assertTrue(polls in 15..18, "$polls")
        }

    @Test
    fun anInvoiceNoScreenShowsIsCheckedEveryMinute() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            var polls = 0
            s.api.status["inv1"] = {
                polls++
                pending()
            }
            purchases.follow(backgroundScope, bought.id)
            runCurrent()
            advanceTimeBy(RoutstrPurchases.IDLE_MS - 1)
            assertEquals(1, polls)
            advanceTimeBy(2)
            assertEquals(2, polls)
            // A screen opens it: checked at once, then every few seconds.
            purchases.setShown(bought.id, true)
            runCurrent()
            assertEquals(3, polls)
            advanceTimeBy(RoutstrPurchases.POLL_MS + 1)
            assertEquals(4, polls)
        }
}
