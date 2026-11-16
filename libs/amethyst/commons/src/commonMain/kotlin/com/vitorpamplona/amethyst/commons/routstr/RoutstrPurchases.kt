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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The user's Cashu wallet, as a Routstr purchase uses it: spend from a mint the node takes, and take change back. */
interface RoutstrCashuWallet {
    /** What the wallet holds, in sats, at each of [mints] it has proofs at. */
    fun balances(mints: List<String>): Map<String, Long>

    /** A `cashuB…` token of [amountSats] from the proofs at [mint]. Throws when it can't. */
    suspend fun mintToken(
        mint: String,
        amountSats: Long,
    ): String

    /** Takes [token] into the wallet; true when it did. */
    suspend fun receive(token: String): Boolean
}

/** Where a node is: its https root (where keys are saved), its onion, and the name to show. */
@Immutable
data class RoutstrTarget(
    val root: String,
    val onionRoot: String? = null,
    val name: String? = null,
) {
    /** Where calls go: the onion when Tor is on and [onionRoot] is a real v3 onion, else [root]. */
    fun callBase(torEnabled: Boolean): String = RoutstrNodes.onionRoot(onionRoot)?.takeIf { torEnabled } ?: root

    companion object {
        /**
         * A target from an announcement alone: its https root, never its onion (anyone can announce
         * someone else's https host with their own onion). The onion comes from that root's own
         * https `/v1/info` ([RoutstrRouting.target], [RoutstrNodeDetail.target]).
         */
        fun of(node: RoutstrNode): RoutstrTarget? = node.httpsUrl?.let { RoutstrTarget(it, null, node.displayName) }
    }
}

/** How one step of a purchase ended. */
@Immutable
sealed interface RoutstrPurchaseOutcome {
    /** The invoice isn't paid yet. */
    data object Waiting : RoutstrPurchaseOutcome

    /** Paid, and the node hasn't handed the key over yet: it is asked again, never paid again. */
    data object PaidNoKeyYet : RoutstrPurchaseOutcome

    /** The key is in the user's kind 12000, under [keyUrl]. */
    data class KeySaved(
        val keyUrl: String,
    ) : RoutstrPurchaseOutcome

    /** "Your key was bought but not saved yet": it is held on this device until a save goes through. */
    data object KeyNotSaved : RoutstrPurchaseOutcome

    data object ToppedUp : RoutstrPurchaseOutcome

    /**
     * The invoice expired unpaid, as far as the node says now. Not final: nodes settle a late
     * payment for a day, so it is kept and checked until then, then dropped.
     */
    data object Expired : RoutstrPurchaseOutcome

    /**
     * The node didn't take the Cashu token, or its answer was lost. [token] is the user's to send
     * again (the same token returns the same key) or copy; null when it went back to the wallet.
     * It is held only here, in memory, never stored: a lost answer leaves a record holding the
     * key the token would make instead ([RoutstrStage.CASHU_SENT]), which is checked until the
     * node says.
     */
    class TokenNotTaken(
        val error: RoutstrError,
        val token: String?,
    ) : RoutstrPurchaseOutcome {
        override fun toString() = "TokenNotTaken(${error.failure}, token=${if (token != null) "<held>" else "<none>"})"
    }

    /** The change is back in the user's wallet. */
    data object ChangeReturned : RoutstrPurchaseOutcome

    /** The wallet didn't take the change: kept here for the user to copy. */
    data object ChangeUnclaimed : RoutstrPurchaseOutcome

    /** A call failed (offline, the node down); the purchase is kept and tried again. */
    data class Failed(
        val error: RoutstrError,
    ) : RoutstrPurchaseOutcome

    /** No such purchase any more. */
    data object Gone : RoutstrPurchaseOutcome
}

/** The purchase couldn't be written to this device, so the invoice was never shown (nothing to pay). */
class RoutstrNotPersistedException(
    cause: Throwable,
) : Exception("The purchase couldn't be kept on this device", cause)

/**
 * One account's Routstr purchases on this device, with the money rules:
 *
 * - An invoice is kept ([RoutstrPurchaseStore]) before it is shown, so a payment is never made
 *   for something this device forgot.
 * - On launch ([resume]) every pending purchase is checked again: by its invoice id, else (the id
 *   lost) by its bolt11 through `/v2/lightning/recover`.
 * - A key is saved to kind 12000 ([saveKey], the AI-key editor) the moment it arrives; until a
 *   save goes through it stays held here.
 * - An invoice that expires unpaid is dropped; a paid one whose key never comes is kept and
 *   recovered, never bought again.
 * - A Cashu token is kept until a node takes it, and change the node returns goes to the wallet,
 *   or stays here until it does.
 */
@OptIn(ExperimentalUuidApi::class)
class RoutstrPurchases(
    private val account: HexKey,
    private val api: RoutstrApi,
    private val store: RoutstrPurchaseStore,
    /** Saves the key into the user's AI keys (kind 12000); true once it is saved. */
    private val saveKey: suspend (AgentKeyTag) -> Boolean,
    private val wallet: () -> RoutstrCashuWallet? = { null },
    private val torEnabled: () -> Boolean = { false },
    private val now: () -> Long = { TimeUtils.now() },
    private val newId: () -> String = { Uuid.random().toString() },
    /** How often an invoice on screen is checked. */
    private val pollMs: Long = POLL_MS,
    /** How often an invoice no screen shows is checked. */
    private val idleMs: Long = IDLE_MS,
    /** The first wait after a paid invoice without a key, or a node out of reach; doubled each time up to [maxBackoffMs]. */
    private val recoverMs: Long = RECOVER_MS,
    private val maxBackoffMs: Long = MAX_BACKOFF_MS,
) {
    private val mutex = Mutex()

    /** One step at a time, so the launch's follow and the screen's tap never save the same key twice. */
    private val steps = Mutex()
    private var loaded = false

    /** The ids of the invoices a screen shows now: those are checked every [pollMs], the rest every [idleMs]. */
    private val shown = MutableStateFlow<Set<String>>(emptySet())

    /** A screen shows invoice [id] ([on]), or stopped showing it. */
    fun setShown(
        id: String,
        on: Boolean,
    ) {
        shown.update { if (on) it + id else it - id }
    }

    private val _pending = MutableStateFlow<List<RoutstrPendingPurchase>>(emptyList())

    /** What is on its way, as this device holds it. */
    val pending: StateFlow<List<RoutstrPendingPurchase>> = _pending.asStateFlow()

    private val watching = MutableStateFlow<Map<String, Job>>(emptyMap())

    private val _outcomes = MutableStateFlow<Map<String, RoutstrPurchaseOutcome>>(emptyMap())

    /** How the last step of each purchase ended, for a screen that comes back to one. */
    val outcomes: StateFlow<Map<String, RoutstrPurchaseOutcome>> = _outcomes.asStateFlow()

    private fun record(
        id: String,
        outcome: RoutstrPurchaseOutcome,
    ): RoutstrPurchaseOutcome {
        _outcomes.update { it + (id to outcome) }
        return outcome
    }

    fun get(id: String): RoutstrPendingPurchase? = _pending.value.firstOrNull { it.id == id }

    suspend fun load(): List<RoutstrPendingPurchase> =
        mutex.withLock {
            ensureLoaded()
            _pending.value
        }

    /**
     * Reads the list once. When it can't be read it stays unloaded: nothing is written over it
     * (the store set it aside), and it is read again next time. What this run added in the
     * meantime is kept in memory and joined to the list once it reads.
     */
    private suspend fun ensureLoaded(): Boolean {
        if (loaded) return true
        val read =
            try {
                store.load(account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read the pending Routstr purchases; nothing is written until they read", e)
                return false
            }
        val held = _pending.value.filter { mine -> read.none { it.id == mine.id } }
        _pending.value = read + held
        loaded = true
        return true
    }

    /**
     * Changes the list here and on disk; the change stays here even if the disk refuses it, and
     * that is rethrown. A list that couldn't be read is never written over
     * ([RoutstrStoreUnreadableException]).
     */
    private suspend fun write(change: (List<RoutstrPendingPurchase>) -> List<RoutstrPendingPurchase>) {
        mutex.withLock {
            val readable = ensureLoaded()
            val next = change(_pending.value)
            _pending.value = next
            if (!readable) throw RoutstrStoreUnreadableException()
            store.save(account, next)
        }
    }

    /** Changes the list in memory only: for a counter, which isn't worth a write. */
    private suspend fun touch(change: (List<RoutstrPendingPurchase>) -> List<RoutstrPendingPurchase>) {
        mutex.withLock { _pending.update(change) }
    }

    /** A paid invoice without a key yet: written when it becomes paid, counted in memory after that. */
    private suspend fun stillPaid(p: RoutstrPendingPurchase) {
        if (p.stage == RoutstrStage.PAID) {
            touch { list -> list.map { if (it.id == p.id) it.copy(recoverAttempts = it.recoverAttempts + 1) else it } }
        } else {
            replace(p.copy(stage = RoutstrStage.PAID, recoverAttempts = p.recoverAttempts + 1))
        }
    }

    /** [write], for a record that holds money: a disk that refuses it is logged, and the record stays here. */
    private suspend fun keep(change: (List<RoutstrPendingPurchase>) -> List<RoutstrPendingPurchase>) {
        try {
            write(change)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't write a Routstr purchase to this device; it is held in memory", e)
        }
    }

    private suspend fun replace(record: RoutstrPendingPurchase) = keep { list -> list.map { if (it.id == record.id) record else it } }

    private suspend fun remove(id: String) = keep { list -> list.filterNot { it.id == id } }

    // --- Lightning ---------------------------------------------------------------

    /**
     * Asks the node at [target] for an invoice of [amountSats] (a new key, or a top-up of
     * [topupKey]) and keeps it on this device before returning it: only then may it be shown.
     * @throws RoutstrException when the node won't make one
     * @throws RoutstrNotPersistedException when this device can't keep it
     */
    suspend fun buyWithLightning(
        target: RoutstrTarget,
        amountSats: Long,
        purpose: RoutstrPurpose,
        topupKey: AgentKeyTag? = null,
    ): RoutstrPendingPurchase {
        require(amountSats > 0) { "amount must be positive" }
        require(purpose == RoutstrPurpose.CREATE || topupKey != null) { "a top-up needs its key" }
        val base = target.callBase(torEnabled())
        val invoice = api.createInvoice(base, amountSats, purpose, topupKey?.key)
        val record =
            RoutstrPendingPurchase(
                id = invoice.invoiceId,
                payment = RoutstrPayment.LIGHTNING,
                purpose = purpose,
                stage = RoutstrStage.AWAITING_PAYMENT,
                nodeRoot = RoutstrNodes.root(target.root),
                onionRoot = RoutstrNodes.onionRoot(target.onionRoot),
                nodeName = target.name,
                amountSats = invoice.amountSats.takeIf { it > 0 } ?: amountSats,
                createdAt = now(),
                invoiceId = invoice.invoiceId,
                bolt11 = invoice.bolt11,
                expiresAt = invoice.expiresAt,
                topupKeyMask = topupKey?.let { AgentProviderPresets.maskKey(it.key) },
            )
        try {
            write { list -> list.filterNot { it.id == record.id } + record }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not kept, so not shown: an unpaid invoice nobody sees costs nothing.
            _pending.update { list -> list.filterNot { it.id == record.id } }
            throw RoutstrNotPersistedException(e)
        }
        return record
    }

    /**
     * Whether an unpaid invoice may be dropped: a day past its expiry (nodes keep settling an
     * expired invoice for 24 hours), and never one this app's wallet paid.
     */
    private fun droppable(p: RoutstrPendingPurchase): Boolean {
        if (p.walletPaid || p.stage != RoutstrStage.AWAITING_PAYMENT) return false
        val expiry = p.expiresAt ?: (p.createdAt + DEFAULT_INVOICE_LIFETIME_SECONDS)
        return now() > expiry + LATE_SETTLEMENT_SECONDS
    }

    /** This app is about to ask its wallet to pay [id]'s invoice: kept before the request goes, so Pay is never offered twice. */
    suspend fun markPayRequested(id: String) {
        val p = get(id) ?: return
        if (!p.walletPayRequested) replace(p.copy(walletPayRequested = true))
    }

    /** This app's wallet paid [id]'s invoice: from now on it is never dropped, only recovered. */
    suspend fun markWalletPaid(id: String) {
        val p = get(id) ?: return
        if (!p.walletPaid) replace(p.copy(walletPaid = true))
    }

    private suspend fun checkInvoice(p: RoutstrPendingPurchase): RoutstrPurchaseOutcome {
        val base = p.callBase(torEnabled())
        val status: RoutstrInvoiceStatus? =
            try {
                val id = p.invoiceId ?: throw RoutstrException(RoutstrError(404, RoutstrFailure.NOT_FOUND))
                api.invoiceStatus(base, id)
            } catch (e: RoutstrException) {
                if (e.failure != RoutstrFailure.NOT_FOUND) return RoutstrPurchaseOutcome.Failed(e.error)
                // The id is lost (or the node forgot it): the bolt11 still names the payment.
                val bolt11 = p.bolt11 ?: return RoutstrPurchaseOutcome.Failed(e.error)
                try {
                    api.recover(base, bolt11)
                } catch (e2: RoutstrException) {
                    if (e2.failure != RoutstrFailure.NOT_FOUND) return RoutstrPurchaseOutcome.Failed(e2.error)
                    null
                }
            }

        if (status != null && status.isPaid) return paid(p, status)
        if (status != null && status.isSettling) {
            // The money reached the node: kept as paid until the key comes.
            stillPaid(p)
            return RoutstrPurchaseOutcome.PaidNoKeyYet
        }
        if (p.stage == RoutstrStage.PAID || p.walletPaid) {
            // Seen paid once, or paid by this app's wallet: it stays paid, whatever a later answer
            // says, and the key is asked for by its bolt11.
            val recovered =
                p.bolt11?.let { bolt11 ->
                    try {
                        api.recover(base, bolt11).takeIf { it.apiKey != null }
                    } catch (e: RoutstrException) {
                        null
                    }
                }
            if (recovered != null) return paid(p, recovered)
            stillPaid(p)
            return RoutstrPurchaseOutcome.PaidNoKeyYet
        }
        if (droppable(p)) {
            remove(p.id)
            return RoutstrPurchaseOutcome.Expired
        }
        // `expired` isn't final: the node still settles it for a day.
        return if (status?.isExpired == true) RoutstrPurchaseOutcome.Expired else RoutstrPurchaseOutcome.Waiting
    }

    private suspend fun paid(
        p: RoutstrPendingPurchase,
        status: RoutstrInvoiceStatus,
    ): RoutstrPurchaseOutcome {
        if (p.purpose == RoutstrPurpose.TOPUP) {
            remove(p.id)
            return RoutstrPurchaseOutcome.ToppedUp
        }
        var key = status.apiKey
        if (key == null && p.bolt11 != null) {
            key =
                try {
                    api.recover(p.callBase(torEnabled()), p.bolt11).apiKey
                } catch (e: RoutstrException) {
                    null
                }
        }
        if (key == null) {
            stillPaid(p)
            return RoutstrPurchaseOutcome.PaidNoKeyYet
        }
        val held = p.copy(stage = RoutstrStage.KEY_UNSAVED, apiKey = key)
        replace(held)
        return saveHeldKey(held)
    }

    private suspend fun saveHeldKey(p: RoutstrPendingPurchase): RoutstrPurchaseOutcome {
        val key = p.apiKey ?: return RoutstrPurchaseOutcome.Gone
        val saved =
            try {
                saveKey(AgentKeyTag(key, p.keyUrl))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save the Routstr key to the AI keys", e)
                false
            }
        if (!saved) return RoutstrPurchaseOutcome.KeyNotSaved
        remove(p.id)
        return RoutstrPurchaseOutcome.KeySaved(p.keyUrl)
    }

    // --- Cashu -------------------------------------------------------------------

    /**
     * Pays the node at [target] with a Cashu [token]: a new key, or a top-up of [topupKey]. The
     * token is never stored. A new key is `sk-` + sha256(token), so before the token goes, a
     * record holding that key is kept ([RoutstrStage.CASHU_SENT]); after a lost answer
     * `/v1/balance/info` with it tells whether the node made the key, now or on a later launch.
     * The token is also handed back ([RoutstrPurchaseOutcome.TokenNotTaken]) to send again (the
     * same token returns the same key) or copy. Change, on either call, goes to the wallet.
     */
    suspend fun buyWithCashu(
        target: RoutstrTarget,
        token: String,
        purpose: RoutstrPurpose,
        topupKey: AgentKeyTag? = null,
    ): RoutstrPurchaseOutcome {
        val trimmed = token.trim()
        require(isCashuToken(trimmed)) { "not a Cashu token" }
        require(purpose == RoutstrPurpose.CREATE || topupKey != null) { "a top-up needs its key" }
        val id = cashuId(trimmed, purpose)
        return steps.withLock { record(id, spendToken(id, target, trimmed, purpose, topupKey)) }
    }

    /**
     * [buyWithCashu] with a token of [amountSats] taken from the wallet's proofs at [mint]. A
     * token the node refused, or never got, goes back to the wallet: the mint decides, since it
     * takes the proofs back only if the node didn't spend them. If the wallet won't take it
     * either, it is in the [RoutstrPurchaseOutcome.TokenNotTaken] to send again or copy.
     */
    suspend fun buyFromWallet(
        target: RoutstrTarget,
        mint: String,
        amountSats: Long,
        purpose: RoutstrPurpose,
        topupKey: AgentKeyTag? = null,
    ): RoutstrPurchaseOutcome {
        val cashu = wallet() ?: throw IllegalStateException("No Cashu wallet")
        val token = cashu.mintToken(mint, amountSats)
        val id = cashuId(token, purpose)
        val outcome = buyWithCashu(target, token, purpose, topupKey)
        if (outcome is RoutstrPurchaseOutcome.TokenNotTaken && outcome.token != null && giveToWallet(token)) {
            // The mint took the proofs back, so the node can't have spent them: nothing was bought.
            if (get(id)?.stage == RoutstrStage.CASHU_SENT) remove(id)
            return record(id, RoutstrPurchaseOutcome.TokenNotTaken(outcome.error, token = null))
        }
        return outcome
    }

    private suspend fun spendToken(
        id: String,
        target: RoutstrTarget,
        token: String,
        purpose: RoutstrPurpose,
        topupKey: AgentKeyTag?,
    ): RoutstrPurchaseOutcome {
        val base = target.callBase(torEnabled())
        if (purpose == RoutstrPurpose.TOPUP) {
            val key = topupKey?.key ?: return RoutstrPurchaseOutcome.Gone
            val result =
                try {
                    api.topupWithCashu(base, key, token)
                } catch (e: RoutstrException) {
                    return RoutstrPurchaseOutcome.TokenNotTaken(e.error, token)
                }
            handleChange(target, purpose, result)
            return RoutstrPurchaseOutcome.ToppedUp
        }

        val sent =
            RoutstrPendingPurchase(
                id = id,
                payment = RoutstrPayment.CASHU,
                purpose = purpose,
                stage = RoutstrStage.CASHU_SENT,
                nodeRoot = RoutstrNodes.root(target.root),
                onionRoot = RoutstrNodes.onionRoot(target.onionRoot),
                nodeName = target.name,
                amountSats = 0,
                createdAt = now(),
                apiKey = cashuKey(token),
            )
        try {
            write { list -> list.filterNot { it.id == id } + sent }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not kept, so not sent: a token nobody sent costs nothing.
            Log.w(TAG, "Couldn't keep the Cashu purchase on this device; the token wasn't sent", e)
            touch { list -> list.filterNot { it.id == id } }
            return RoutstrPurchaseOutcome.TokenNotTaken(RoutstrError(null, RoutstrFailure.UNEXPECTED), token)
        }

        val result =
            try {
                api.createWithCashu(base, token)
            } catch (e: RoutstrException) {
                if (!isAmbiguous(e)) {
                    // The node answered that it didn't take it.
                    remove(id)
                    return RoutstrPurchaseOutcome.TokenNotTaken(e.error, token)
                }
                // The answer was lost: the key the token makes says whether the node took it.
                return when (val probed = probeCashu(sent)) {
                    is RoutstrPurchaseOutcome.KeySaved, RoutstrPurchaseOutcome.KeyNotSaved -> probed
                    else -> RoutstrPurchaseOutcome.TokenNotTaken(e.error, token)
                }
            }
        // The token is spent now: whatever comes next, it is not offered again.
        handleChange(target, purpose, result)
        val key = result.apiKey ?: sent.apiKey
        val held = sent.copy(stage = RoutstrStage.KEY_UNSAVED, apiKey = key, amountSats = result.balanceMsats?.let { it / 1000 } ?: 0)
        replace(held)
        return saveHeldKey(held)
    }

    /** No answer, or the node's side failed (5xx): the token may or may not have been taken. */
    private fun isAmbiguous(e: RoutstrException): Boolean = e.failure == RoutstrFailure.UNREACHABLE || (e.error.status ?: 0) >= 500

    /**
     * Asks the node whether a [RoutstrStage.CASHU_SENT] token made its key: a balance means it
     * did, and the key is saved. A key the node doesn't know means it didn't: the record goes once
     * it is [CASHU_PROBE_WINDOW_SECONDS] old (a slow node may still be making it). No answer: kept.
     */
    private suspend fun probeCashu(p: RoutstrPendingPurchase): RoutstrPurchaseOutcome {
        val key = p.apiKey ?: return RoutstrPurchaseOutcome.Gone
        val balance =
            try {
                api.balance(p.callBase(torEnabled()), key)
            } catch (e: RoutstrException) {
                val unknownKey = e.failure == RoutstrFailure.KEY_NOT_FOUND || e.failure == RoutstrFailure.INVALID_KEY
                if (unknownKey && now() - p.createdAt >= CASHU_PROBE_WINDOW_SECONDS) {
                    remove(p.id)
                    return RoutstrPurchaseOutcome.Gone
                }
                return RoutstrPurchaseOutcome.Failed(e.error)
            }
        val held = p.copy(stage = RoutstrStage.KEY_UNSAVED, amountSats = balance.balanceSats)
        replace(held)
        return saveHeldKey(held)
    }

    private suspend fun handleChange(
        target: RoutstrTarget,
        purpose: RoutstrPurpose,
        result: RoutstrCashuResult,
    ) {
        val change = result.changeToken?.takeIf { it.isNotBlank() } ?: return
        if (giveToWallet(change)) return
        keep {
            it +
                RoutstrPendingPurchase(
                    id = "change-" + newId(),
                    payment = RoutstrPayment.CASHU,
                    purpose = purpose,
                    stage = RoutstrStage.CHANGE_UNCLAIMED,
                    nodeRoot = RoutstrNodes.root(target.root),
                    onionRoot = RoutstrNodes.onionRoot(target.onionRoot),
                    nodeName = target.name,
                    amountSats = result.changeAmount ?: 0,
                    createdAt = now(),
                    token = change,
                )
        }
    }

    private suspend fun giveToWallet(token: String): Boolean =
        try {
            wallet()?.receive(token) == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The wallet didn't take the Routstr change", e)
            false
        }

    private suspend fun claimChange(p: RoutstrPendingPurchase): RoutstrPurchaseOutcome {
        val token = p.token ?: return RoutstrPurchaseOutcome.Gone
        if (!giveToWallet(token)) return RoutstrPurchaseOutcome.ChangeUnclaimed
        remove(p.id)
        return RoutstrPurchaseOutcome.ChangeReturned
    }

    // --- Driving them --------------------------------------------------------------

    /** One step for purchase [id]: check the invoice, save the key, spend the token or return the change. */
    suspend fun check(id: String): RoutstrPurchaseOutcome = steps.withLock { record(id, step(id)) }

    private suspend fun step(id: String): RoutstrPurchaseOutcome {
        val p =
            mutex.withLock {
                ensureLoaded()
                get(id)
            } ?: return RoutstrPurchaseOutcome.Gone
        return when (p.stage) {
            RoutstrStage.AWAITING_PAYMENT, RoutstrStage.PAID -> checkInvoice(p)
            RoutstrStage.KEY_UNSAVED -> saveHeldKey(p)
            RoutstrStage.CHANGE_UNCLAIMED -> claimChange(p)
            RoutstrStage.CASHU_SENT -> probeCashu(p)
        }
    }

    /**
     * [check]s purchase [id] until it settles: every [pollMs] while a screen shows the invoice
     * and every [idleMs] while none does; a paid one without a key, a node out of reach, an
     * expired or cancelled invoice that may still settle, and a Cashu answer that was lost back
     * off from [recoverMs], doubling up to [maxBackoffMs]. Stops when it is done, needs the user
     * (a key not saved, a token not taken, change unclaimed), or an unpaid invoice is past its
     * late-settlement day with the node out of reach: it stays kept, and the next launch checks
     * it again.
     */
    suspend fun watch(id: String): RoutstrPurchaseOutcome {
        var quiet = 0
        while (true) {
            val outcome = check(id)
            when (outcome) {
                RoutstrPurchaseOutcome.Waiting -> {
                    quiet = 0
                    if (get(id)?.hiddenByUser == true) delay(backoff(quiet++)) else pause(id)
                }
                RoutstrPurchaseOutcome.PaidNoKeyYet -> delay(backoff((get(id)?.recoverAttempts ?: 1) - 1))
                // Past its expiry and still kept: a late settlement may still come, for a day.
                RoutstrPurchaseOutcome.Expired -> if (get(id) == null) return outcome else delay(backoff(quiet++))
                is RoutstrPurchaseOutcome.Failed -> {
                    val p = get(id) ?: return outcome
                    if (p.stage == RoutstrStage.AWAITING_PAYMENT && !p.walletPaid && p.expiresAt != null && now() > p.expiresAt + LATE_SETTLEMENT_SECONDS) return outcome
                    if (p.stage != RoutstrStage.AWAITING_PAYMENT && p.stage != RoutstrStage.PAID && p.stage != RoutstrStage.CASHU_SENT) return outcome
                    delay(backoff(quiet++))
                }
                else -> return outcome
            }
        }
    }

    /** The wait after [attempt] tries that changed nothing: [recoverMs], doubled each time, at most [maxBackoffMs]. */
    private fun backoff(attempt: Int): Long {
        var ms = recoverMs
        repeat(attempt.coerceIn(0, 30)) { ms = (ms * 2).coerceAtMost(maxBackoffMs) }
        return ms.coerceAtMost(maxBackoffMs)
    }

    /** Waits [pollMs] while a screen shows [id], else up to [idleMs]: cut short when a screen opens it. */
    private suspend fun pause(id: String) {
        if (id in shown.value) {
            delay(pollMs)
        } else {
            withTimeoutOrNull(idleMs) { shown.first { id in it } }
        }
    }

    /** [watch] on [scope], once per purchase however often it is asked. */
    fun follow(
        scope: CoroutineScope,
        id: String,
    ): Job {
        watching.value[id]?.takeIf { it.isActive }?.let { return it }
        val job = scope.launch { watch(id) }
        watching.update { it + (id to job) }
        job.invokeOnCompletion { watching.update { map -> if (map[id] === job) map - id else map } }
        return job
    }

    /** On launch: every purchase this device kept is followed again. */
    suspend fun resume(scope: CoroutineScope) {
        load().forEach { follow(scope, it.id) }
    }

    /**
     * Hides an unpaid invoice the user gave up on. It isn't dropped: it may still be paid from
     * another wallet, and nodes settle late payments for a day, so it is kept and checked slowly
     * until a day past its expiry, like any unpaid invoice. One this app's wallet was asked to pay
     * is never hidden. True when it was hidden.
     */
    suspend fun abandon(id: String): Boolean {
        val p = get(id) ?: return true
        if (p.stage != RoutstrStage.AWAITING_PAYMENT || p.walletPaid || p.walletPayRequested) return false
        if (!p.hiddenByUser) replace(p.copy(hiddenByUser = true))
        return true
    }

    /** Forgets unclaimed change the user copied out themselves. Never a key or a paid invoice. */
    suspend fun forgetToken(id: String): Boolean {
        val p = get(id) ?: return true
        if (p.stage != RoutstrStage.CHANGE_UNCLAIMED) return false
        remove(id)
        return true
    }

    companion object {
        private const val TAG = "RoutstrPurchases"
        const val POLL_MS = 3_000L
        const val IDLE_MS = 60_000L
        const val RECOVER_MS = 30_000L
        const val MAX_BACKOFF_MS = 60 * 60_000L

        /** A key a sent Cashu token would make, unknown to the node for this long, was never made. */
        const val CASHU_PROBE_WINDOW_SECONDS = 60 * 60L

        /** Nodes keep settling an expired invoice for this long (routstr-core): only then is an unpaid one dropped. */
        const val LATE_SETTLEMENT_SECONDS = 24 * 60 * 60L

        /** An invoice that names no expiry is taken to last an hour. */
        const val DEFAULT_INVOICE_LIFETIME_SECONDS = 60 * 60L

        /** The key a node makes from a Cashu [token]: `sk-` + its sha256, as routstr-core does. */
        fun cashuKey(token: String): String = "sk-" + sha256(token.trim().encodeToByteArray()).toHexKey()

        /** One id per token, so sending the same token again finds the same record; never the key itself. */
        private fun cashuId(
            token: String,
            purpose: RoutstrPurpose,
        ): String = "cashu-" + purpose.wire + "-" + sha256(("id:" + cashuKey(token)).encodeToByteArray()).toHexKey().take(16)

        fun isCashuToken(text: String): Boolean {
            val t = text.trim()
            return t.startsWith("cashuA") || t.startsWith("cashuB")
        }
    }
}
