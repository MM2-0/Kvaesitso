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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** How a purchase is paid. */
@Serializable
enum class RoutstrPayment {
    @SerialName("lightning")
    LIGHTNING,

    @SerialName("cashu")
    CASHU,
}

/** Where a purchase stands. Every stage but [AWAITING_PAYMENT] holds money the user has already spent. */
@Serializable
enum class RoutstrStage {
    /** An invoice is out and not paid yet. Dropped a day after it expires unpaid: nodes settle late ones for 24 hours. */
    @SerialName("awaiting_payment")
    AWAITING_PAYMENT,

    /** Paid (or settling), and the node hasn't handed the key over yet: recovered by bolt11, never bought again. */
    @SerialName("paid")
    PAID,

    /** The key arrived and isn't in the user's kind 12000 yet: it is held here until it is. */
    @SerialName("key_unsaved")
    KEY_UNSAVED,

    /** Change the node gave back that the user's wallet hasn't taken: shown until it does, or they copy it. */
    @SerialName("change_unclaimed")
    CHANGE_UNCLAIMED,

    /**
     * A Cashu token went to a node for a new key, and no clear answer came back. The token itself
     * is never stored: the record holds the key the node would have made from it (`sk-` +
     * sha256 of the token), and `/v1/balance/info` with that key tells whether it did.
     */
    @SerialName("cashu_sent")
    CASHU_SENT,
}

/**
 * A Routstr purchase on its way, kept on this device only (never in an event) from before the
 * invoice is shown until the key is in the user's kind 12000. The `toString` never shows the key
 * or a token.
 */
@Immutable
@Serializable
data class RoutstrPendingPurchase(
    val id: String,
    val payment: RoutstrPayment,
    val purpose: RoutstrPurpose,
    val stage: RoutstrStage,
    /** The node's https root: where the key is saved (`…/v1`). */
    val nodeRoot: String,
    /** The node's onion root, used for the calls when Tor is on. */
    val onionRoot: String? = null,
    val nodeName: String? = null,
    val amountSats: Long,
    val createdAt: Long,
    val invoiceId: String? = null,
    val bolt11: String? = null,
    val expiresAt: Long? = null,
    /** For a top-up: the masked key it adds to, to tell the user which one. */
    val topupKeyMask: String? = null,
    /** [RoutstrStage.KEY_UNSAVED]: the key bought. */
    val apiKey: String? = null,
    /**
     * [RoutstrStage.CHANGE_UNCLAIMED] only: change the node handed back that the wallet didn't
     * take. A token paid to a node is never stored: resending it returns the same key.
     */
    val token: String? = null,
    /** This app's wallet paid the invoice: it is never dropped, whatever the node says. */
    val walletPaid: Boolean = false,
    /** How many times the status was checked after payment without a key. Counted in memory: a change of it alone isn't written. */
    val recoverAttempts: Int = 0,
    /** This app asked its wallet to pay the invoice: it may have paid, so Pay is never offered again. */
    val walletPayRequested: Boolean = false,
    /** The user cancelled the invoice: hidden, but kept (and checked slowly) until it can't settle any more. */
    val hiddenByUser: Boolean = false,
) {
    val keyUrl: String get() = RoutstrNodes.keyUrl(nodeRoot)

    fun callBase(torEnabled: Boolean): String = RoutstrNodes.onionRoot(onionRoot)?.takeIf { torEnabled } ?: nodeRoot

    /** Whether the user's money is in this record (anything past an unpaid invoice). */
    val holdsMoney: Boolean get() = stage != RoutstrStage.AWAITING_PAYMENT

    override fun toString() = "RoutstrPendingPurchase(id=$id, $payment, $purpose, $stage, node=$nodeRoot, $amountSats sats)"
}

/**
 * The purchases kept on this device couldn't be read. They are not overwritten: the raw list is
 * set aside under a backup key, and nothing is written until it can be read again.
 */
class RoutstrStoreUnreadableException(
    cause: Throwable? = null,
) : Exception("The pending Routstr purchases on this device can't be read", cause)

/** Each account's pending purchases on this device. */
interface RoutstrPurchaseStore {
    /** @throws RoutstrStoreUnreadableException when what is stored can't be read (it is set aside first) */
    suspend fun load(account: HexKey): List<RoutstrPendingPurchase>

    suspend fun save(
        account: HexKey,
        purchases: List<RoutstrPendingPurchase>,
    )
}

class InMemoryRoutstrPurchaseStore : RoutstrPurchaseStore {
    val saved = HashMap<HexKey, List<RoutstrPendingPurchase>>()

    /** When set, the next [save] throws it: a disk that is full or gone. */
    var failNextSave: Exception? = null

    /** How many times [save] wrote. */
    var saves: Int = 0

    /** While set, [load] finds an unreadable list: a corrupt file. */
    var unreadable: Boolean = false

    override suspend fun load(account: HexKey): List<RoutstrPendingPurchase> {
        if (unreadable) throw RoutstrStoreUnreadableException()
        return saved[account].orEmpty()
    }

    override suspend fun save(
        account: HexKey,
        purchases: List<RoutstrPendingPurchase>,
    ) {
        failNextSave?.let {
            failNextSave = null
            throw it
        }
        saves++
        saved[account] = purchases
    }
}

/**
 * In its own preferences file, `routstr_purchases`, one JSON list per account: DataStore on
 * Android and on the desktop alike ([AppPreferenceStores]), in the app's private files.
 */
class DataStoreRoutstrPurchaseStore(
    private val stores: AppPreferenceStores,
) : RoutstrPurchaseStore {
    private val store get() = stores.getDataStore(FILE_NAME)

    private fun keyFor(account: HexKey) = stringPreferencesKey("pending." + account.lowercase())

    /** Where an unreadable list is set aside, so no later write loses what it held. */
    private fun backupKeyFor(account: HexKey) = stringPreferencesKey("unreadable." + account.lowercase())

    override suspend fun load(account: HexKey): List<RoutstrPendingPurchase> {
        val raw = store.data.first()[keyFor(account)] ?: return emptyList()
        val problem =
            try {
                return JSON.decodeFromString(LIST, raw)
            } catch (e: SerializationException) {
                e
            } catch (e: IllegalArgumentException) {
                e
            }
        // Never overwritten: kept aside, and the caller writes nothing until it reads again.
        Log.w(TAG, "Unreadable pending Routstr purchases; set aside under the backup key", problem)
        store.edit { it[backupKeyFor(account)] = raw }
        throw RoutstrStoreUnreadableException(problem)
    }

    override suspend fun save(
        account: HexKey,
        purchases: List<RoutstrPendingPurchase>,
    ) {
        store.edit {
            if (purchases.isEmpty()) it.remove(keyFor(account)) else it[keyFor(account)] = JSON.encodeToString(LIST, purchases)
        }
    }

    companion object {
        const val FILE_NAME = "routstr_purchases"
        private const val TAG = "RoutstrPurchaseStore"
        private val JSON = Json { ignoreUnknownKeys = true }
        private val LIST = ListSerializer(RoutstrPendingPurchase.serializer())
    }
}
