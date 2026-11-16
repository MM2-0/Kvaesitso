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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.metadata

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadToDefaultServer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class ChannelMetadataViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    private var originalChannel: PublicChatChannel? = null

    val channelName = mutableStateOf(TextFieldValue())
    val channelPicture = mutableStateOf(TextFieldValue())
    val channelDescription = mutableStateOf(TextFieldValue())

    private val _channelRelays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val channelRelays = _channelRelays.asStateFlow()

    var isUploadingImageForPicture by mutableStateOf(false)

    val canPost by derivedStateOf {
        channelName.value.text.isNotBlank()
    }

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun new() {
        originalChannel = null
        clear()
    }

    fun load(channel: PublicChatChannel) {
        originalChannel = channel
        channelName.value = TextFieldValue(channel.info.name ?: "")
        channelPicture.value = TextFieldValue(channel.info.picture ?: "")
        channelDescription.value = TextFieldValue(channel.info.about ?: "")

        val relays =
            channel.info.relays
                ?.map { relaySetupInfoBuilder(it) }
                ?.distinctBy { it.relay }

        _channelRelays.update { relays ?: emptyList() }
    }

    fun isNewChannel() = originalChannel == null && _channelRelays.value.isNotEmpty()

    fun createOrUpdate(onDone: (PublicChatChannel) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            accountViewModel.reportSignerErrors {
                account.let { account ->
                    val channel = originalChannel
                    if (channel == null) {
                        val template =
                            ChannelCreateEvent.build(
                                channelName.value.text,
                                channelDescription.value.text,
                                channelPicture.value.text,
                                channelRelays.value.map { it.relay },
                            )

                        val signedResult =
                            account.signAndSendPrivatelyOrBroadcast(
                                template,
                                relayList = { it.channelInfo().relays },
                            )
                        val channel = LocalCache.getOrCreatePublicChatChannel(signedResult.id)
                        // follows the channel
                        account.follow(channel)
                        onDone(channel)
                    } else {
                        val event = channel.event

                        val template =
                            if (event != null) {
                                val hint = EventHintBundle(event, channel.relays().firstOrNull())

                                ChannelMetadataEvent.build(
                                    channelName.value.text,
                                    channelDescription.value.text,
                                    channelPicture.value.text,
                                    channelRelays.value.map { it.relay },
                                    hint,
                                )
                            } else {
                                val eTag = ETag(channel.idHex, channel.relays().firstOrNull())

                                ChannelMetadataEvent.build(
                                    channelName.value.text,
                                    channelDescription.value.text,
                                    channelPicture.value.text,
                                    channelRelays.value.map { it.relay },
                                    eTag,
                                )
                            }

                        val signedResult =
                            account.signAndSendPrivatelyOrBroadcast(
                                template,
                                relayList = { it.channelInfo().relays },
                            )
                        val channel = LocalCache.getOrCreatePublicChatChannel(signedResult.id)
                        onDone(channel)
                    }
                }

                clear()
            }
        }
    }

    fun addHomeRelay(relay: BasicRelaySetupInfo) {
        if (_channelRelays.value.any { it.relay == relay.relay }) return

        _channelRelays.update { it.plus(relay) }
    }

    fun deleteHomeRelay(relay: BasicRelaySetupInfo) {
        _channelRelays.update { it.minus(relay) }
    }

    fun clear() {
        channelName.value = TextFieldValue()
        channelPicture.value = TextFieldValue()
        channelDescription.value = TextFieldValue()
        _channelRelays.update { emptyList() }
    }

    fun uploadForPicture(
        uri: SelectedMedia,
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            upload(
                uri,
                onUploading = { isUploadingImageForPicture = it },
                onUploaded = { channelPicture.value = TextFieldValue(it) },
                onError = onError,
            )
        }
    }

    private suspend fun upload(
        galleryUri: SelectedMedia,
        onUploading: (Boolean) -> Unit,
        onUploaded: (String) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        onUploading(true)
        val url = uploadToDefaultServer(galleryUri, account, accountViewModel.mediaUploader, onError)
        onUploading(false)
        if (url != null) onUploaded(url)
    }
}
