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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.ephemChat.metadata

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

class NewEphemeralChatMetaViewModel : ViewModel() {
    private var account: Account? = null

    val relayUrl = mutableStateOf(TextFieldValue())
    val channelName = mutableStateOf(TextFieldValue())

    val canPost by derivedStateOf {
        channelName.value.text.isNotBlank() && relayUrl.value.text.isNotBlank()
    }

    fun load(account: Account) {
        this.account = account
    }

    fun buildRoom() =
        RelayUrlNormalizer
            .normalizeOrNull(relayUrl.value.text)
            ?.let { RoomId(channelName.value.text, it) }

    /*
    fun createOrUpdate(onDone: (RoomId) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            account?.follow(, onDone)
            clear()
        }
    }*/

    fun clear() {
        channelName.value = TextFieldValue()
        relayUrl.value = TextFieldValue()
    }
}
