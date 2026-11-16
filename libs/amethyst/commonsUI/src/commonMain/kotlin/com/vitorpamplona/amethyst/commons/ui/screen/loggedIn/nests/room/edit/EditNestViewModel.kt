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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.edit

import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nest_error_publish_failed
import com.vitorpamplona.amethyst.commons.resources.nest_error_room_fields_required
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.edit.EditNestSheet
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.endpoint
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.image
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.participants
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.summary
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile

/**
 * Backing ViewModel for [EditNestSheet]. Loads the existing
 * [MeetingSpaceEvent], lets the host edit room name / summary /
 * image / endpoint, then republishes kind-30312 with the SAME `d`
 * tag so the relay treats it as a replacement of the original.
 *
 * Closing the room ([closeRoom]) is the same path with
 * [StatusTag.STATUS.ENDED] forced regardless of the form fields,
 * so a host can close without editing anything else.
 *
 * Participants are preserved verbatim — re-publishing must NOT lose
 * speakers or admins who were promoted in a previous version of
 * the event (audit risk note in the Tier-1 plan).
 */
class EditNestViewModel : ViewModel() {
    @Volatile private var account: AccountViewModel? = null

    @Volatile private var original: MeetingSpaceEvent? = null

    private val _state = MutableStateFlow(FormState.empty())
    val state: StateFlow<FormState> = _state.asStateFlow()

    fun bind(
        accountViewModel: AccountViewModel,
        existing: MeetingSpaceEvent,
    ) {
        account = accountViewModel
        original = existing
        // Only seed the form once per (sheet open, event) pair so a
        // recomposition of the sheet doesn't blow away the user's
        // unsaved edits.
        if (_state.value.dTag.isEmpty()) {
            _state.value =
                FormState(
                    dTag = existing.dTag(),
                    roomName = existing.room().orEmpty(),
                    summary = existing.summary().orEmpty(),
                    imageUrl = existing.image().orEmpty(),
                    endpointUrl = existing.endpoint().orEmpty(),
                    serviceUrl = existing.service().orEmpty(),
                    isPublishing = false,
                    error = null,
                )
        }
    }

    fun setRoomName(value: String) = _state.update { it.copy(roomName = value) }

    fun setSummary(value: String) = _state.update { it.copy(summary = value) }

    fun setImageUrl(value: String) = _state.update { it.copy(imageUrl = value) }

    fun setEndpointUrl(value: String) = _state.update { it.copy(endpointUrl = value) }

    fun setServiceUrl(value: String) = _state.update { it.copy(serviceUrl = value) }

    /** Publish the edited room. Returns `true` on success. */
    suspend fun save(): Boolean = republish(StatusTag.STATUS.LIVE)

    /** Close the room (host only). Republishes with status=CLOSED. */
    suspend fun closeRoom(): Boolean = republish(StatusTag.STATUS.ENDED)

    private suspend fun republish(targetStatus: StatusTag.STATUS): Boolean {
        val avm = account ?: return false
        val originalEvent = original ?: return false
        val current = _state.value

        if (current.roomName.isBlank() || current.serviceUrl.isBlank() || current.endpointUrl.isBlank()) {
            _state.update { it.copy(error = loadStringRes(Res.string.nest_error_room_fields_required)) }
            return false
        }

        _state.update { it.copy(isPublishing = true, error = null) }

        val template = buildEditTemplate(originalEvent, current, targetStatus)

        return try {
            avm.account.signAndComputeBroadcast(template)
            _state.update { it.copy(isPublishing = false) }
            true
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    isPublishing = false,
                    error = loadStringRes(Res.string.nest_error_publish_failed, t.message ?: t::class.simpleName.toString()),
                )
            }
            false
        }
    }

    companion object {
        /**
         * Pure template builder — extracted so it can be unit-tested
         * without an AccountViewModel / signer. The republished
         * template MUST reuse the original `dTag` and preserve every
         * participant tag verbatim, including the host.
         */
        fun buildEditTemplate(
            original: MeetingSpaceEvent,
            form: FormState,
            status: StatusTag.STATUS,
        ): com.vitorpamplona.quartz.nip01Core.signers.EventTemplate<MeetingSpaceEvent> {
            val allParticipants = original.participants()
            val host =
                allParticipants.firstOrNull { it.role.equals(ROLE.HOST.code, true) }
                    ?: ParticipantTag(original.pubKey, null, ROLE.HOST.code, null)
            val others = allParticipants.filterNot { it.pubKey == host.pubKey }

            return MeetingSpaceEvent.build(
                room = form.roomName.trim(),
                status = status,
                service = form.serviceUrl.trim(),
                host = host,
                dTag = form.dTag,
            ) {
                endpoint(form.endpointUrl.trim())
                form.summary
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { summary(it) }
                form.imageUrl
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { image(it) }
                if (others.isNotEmpty()) participants(others)
            }
        }
    }

    data class FormState(
        val dTag: String,
        val roomName: String,
        val summary: String,
        val imageUrl: String,
        val endpointUrl: String,
        val serviceUrl: String,
        val isPublishing: Boolean,
        val error: String?,
    ) {
        val canSubmit: Boolean
            get() = roomName.isNotBlank() && serviceUrl.isNotBlank() && endpointUrl.isNotBlank() && !isPublishing

        companion object {
            fun empty() =
                FormState(
                    dTag = "",
                    roomName = "",
                    summary = "",
                    imageUrl = "",
                    endpointUrl = "",
                    serviceUrl = "",
                    isPublishing = false,
                    error = null,
                )
        }
    }
}
