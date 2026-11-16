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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKey
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import kotlinx.coroutines.CancellationException

@Stable
class UpdateZapAmountViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel

    var nextAmount by mutableStateOf(TextFieldValue(""))
    var amountSet by mutableStateOf(listOf<Long>())
    var walletConnectRelay by mutableStateOf(TextFieldValue(""))
    var walletConnectPubkey by mutableStateOf(TextFieldValue(""))
    var walletConnectSecret by mutableStateOf(TextFieldValue(""))
    var selectedZapType by mutableStateOf(ZapReceiptEvent.ZapType.PRIVATE)

    // A local UI preference rather than synced account state, but it is edited on
    // this screen, so it follows this screen's Save/Cancel contract instead of
    // applying instantly — a toggle that ignored Cancel would read as a bug.
    var showPayToChip by mutableStateOf(false)

    fun copyFromClipboard(text: String) {
        if (text.isBlank()) {
            return
        }
        updateNIP47(text)
    }

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
    }

    fun load() {
        this.amountSet = accountViewModel.account.settings.syncedSettings.zaps.zapAmountChoices.value
        this.selectedZapType = accountViewModel.account.settings.syncedSettings.zaps.defaultZapType.value

        this.showPayToChip = uiSettings().showPayToZapChip.value

        val nip47 = accountViewModel.account.settings.defaultZapPaymentRequest()

        this.walletConnectPubkey = nip47?.pubKeyHex?.let { TextFieldValue(it) } ?: TextFieldValue("")
        this.walletConnectRelay = nip47?.relayUri?.url?.let { TextFieldValue(it) } ?: TextFieldValue("")
        this.walletConnectSecret = nip47?.secret?.let { TextFieldValue(it) } ?: TextFieldValue("")
    }

    fun toListOfAmounts(commaSeparatedAmounts: String): List<Long> = commaSeparatedAmounts.split(",").map { it.trim().toLongOrNull() ?: 0 }

    fun addAmount() {
        val newValue = nextAmount.text.trim().toLongOrNull()
        // De-dupe: a repeated amount would give two preset chips the same key (a
        // Compose duplicate-key hazard) and make the drag-reorder's indexOf()
        // resolve to the wrong chip.
        if (newValue != null && newValue !in amountSet) {
            amountSet = amountSet + newValue
        }

        nextAmount = TextFieldValue("")
    }

    fun removeAmount(amount: Long) {
        amountSet = amountSet - amount
    }

    /** Move the preset at [fromIndex] to [toIndex] (drag-and-drop reorder). */
    fun moveAmount(
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (fromIndex == toIndex) return
        val list = amountSet.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        list.add(toIndex, list.removeAt(fromIndex))
        amountSet = list
    }

    fun sendPost() {
        accountViewModel.launchSigner {
            sendPostSuspend()
        }
    }

    suspend fun sendPostSuspend() {
        val nip47Update =
            if (walletConnectRelay.text.isNotBlank() && walletConnectPubkey.text.isNotBlank()) {
                val pubkeyHex =
                    try {
                        decodePublicKey(walletConnectPubkey.text.trim()).toHexKey()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }

                val relayUrl = walletConnectRelay.text.ifBlank { null }?.let { RelayUrlNormalizer.normalizeOrNull(it) }
                val privKeyHex = walletConnectSecret.text.ifBlank { null }?.let { decodePrivateKeyAsHexOrNull(it) }

                if (pubkeyHex != null && relayUrl != null) {
                    Nip47WalletConnect.Nip47URINorm(
                        pubkeyHex,
                        relayUrl,
                        privKeyHex,
                    )
                } else {
                    null
                }
            } else {
                null
            }

        accountViewModel.account.updateZapAmounts(amountSet, selectedZapType, nip47Update)
        uiSettings().showPayToZapChip.tryEmit(showPayToChip)

        nextAmount = TextFieldValue("")
    }

    fun cancel() {
        nextAmount = TextFieldValue("")
        showPayToChip = uiSettings().showPayToZapChip.value
    }

    private fun uiSettings() = accountViewModel.settings.uiSettingsFlow

    fun hasChanged(): Boolean {
        val defaultUri = accountViewModel.account.settings.defaultZapPaymentRequest()
        return (
            selectedZapType != accountViewModel.account.settings.syncedSettings.zaps.defaultZapType.value ||
                amountSet != accountViewModel.account.settings.syncedSettings.zaps.zapAmountChoices.value ||
                walletConnectPubkey.text != (defaultUri?.pubKeyHex ?: "") ||
                walletConnectRelay.text != (defaultUri?.relayUri?.url ?: "") ||
                walletConnectSecret.text != (defaultUri?.secret ?: "") ||
                showPayToChip != uiSettings().showPayToZapChip.value
        )
    }

    fun updateNIP47(uri: String) {
        val contact = Nip47WalletConnect.parse(uri)
        walletConnectPubkey = TextFieldValue(contact.pubKeyHex)
        walletConnectRelay = TextFieldValue(contact.relayUri.url)
        walletConnectSecret = TextFieldValue(contact.secret ?: "")
    }
}
