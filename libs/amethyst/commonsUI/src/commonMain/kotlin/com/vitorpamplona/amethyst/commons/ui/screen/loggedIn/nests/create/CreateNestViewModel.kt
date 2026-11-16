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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.create

import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.resources.metadata_strip_failed_title
import com.vitorpamplona.amethyst.commons.resources.metadata_strip_failed_upload_cancelled
import com.vitorpamplona.amethyst.commons.resources.nest_error_account_not_bound
import com.vitorpamplona.amethyst.commons.resources.nest_error_endpoint_url_scheme
import com.vitorpamplona.amethyst.commons.resources.nest_error_pick_future_start
import com.vitorpamplona.amethyst.commons.resources.nest_error_pick_start_time
import com.vitorpamplona.amethyst.commons.resources.nest_error_publish_failed
import com.vitorpamplona.amethyst.commons.resources.nest_error_room_name_required
import com.vitorpamplona.amethyst.commons.resources.nest_error_service_url_scheme
import com.vitorpamplona.amethyst.commons.resources.server_did_not_provide_a_url_after_uploading
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.UploadError
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.create.CreateNestSheet
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.endpoint
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.image
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.relays
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.starts
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.summary
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile

/**
 * Backing ViewModel for [CreateNestSheet]. Holds form state, runs
 * [publishAndBuildLaunchInfo] which signs and broadcasts a NIP-53 kind
 * 30312 [MeetingSpaceEvent] tagging the user as `host`, then returns
 * the launch parameters for the room screen.
 *
 * The defaults point at `nostrnests.com`'s public moq-rs deployment so a
 * blank form produces a working room. The user can edit them to point at
 * their own moq-rs / moq-auth pair.
 */
class CreateNestViewModel : ViewModel() {
    /** Lazily bound on first composition; the sheet calls [bindAccountIfMissing]. */
    @Volatile private var account: AccountViewModel? = null

    private val _state = MutableStateFlow(FormState.defaults())
    val state: StateFlow<FormState> = _state.asStateFlow()

    fun bindAccountIfMissing(accountViewModel: AccountViewModel) {
        if (account != null) return
        account = accountViewModel
        seedDefaultsFromAccount()
    }

    /**
     * Reset the form to fresh defaults and re-seed the service / endpoint
     * URLs from the user's saved kind-10112 list. Called after a
     * successful publish so the next open of the sheet doesn't show
     * stale fields (room name, summary, image URL) or — worse — a stuck
     * `isPublishing=true` Submit button. The ViewModel is keyed by the
     * user's pubkey, so without this reset the same instance survives
     * across sheet open/close cycles.
     */
    fun resetForm() {
        _state.value = FormState.defaults()
        seedDefaultsFromAccount()
    }

    /**
     * Seed the URL fields from the user's saved kind-10112 list so
     * first-time use flows naturally from the Settings screen. If the
     * list is empty (or the user hasn't published one yet), keep the
     * nostrnests.com defaults already in [FormState.defaults].
     */
    private fun seedDefaultsFromAccount() {
        val first =
            account
                ?.account
                ?.nestsServers
                ?.flow
                ?.value
                ?.firstOrNull()
                ?: return
        if (first.relay.startsWith("http") && first.auth.startsWith("http")) {
            _state.update { it.copy(serviceUrl = first.auth, endpointUrl = first.relay) }
        }
    }

    fun onRoomNameChange(value: String) = _state.update { it.copy(roomName = value, error = null) }

    fun onSummaryChange(value: String) = _state.update { it.copy(summary = value, error = null) }

    fun onServiceUrlChange(value: String) = _state.update { it.copy(serviceUrl = value.trim(), error = null) }

    fun onEndpointUrlChange(value: String) = _state.update { it.copy(endpointUrl = value.trim(), error = null) }

    fun onImageUrlChange(value: String) = _state.update { it.copy(imageUrl = value.trim(), error = null) }

    fun uploadForImage(
        uri: SelectedMedia,
        uploader: MediaUploader,
        onError: (String, String) -> Unit,
    ) {
        val avm = account ?: return
        avm.launchSigner {
            upload(
                galleryUri = uri,
                account = avm.account,
                uploader = uploader,
                onError = onError,
            )?.let { url ->
                _state.update { it.copy(imageUrl = url, error = null) }
            }
        }
    }

    private suspend fun upload(
        galleryUri: SelectedMedia,
        account: Account,
        uploader: MediaUploader,
        onError: (String, String) -> Unit,
    ): String? {
        _state.update { it.copy(isUploadingImage = true) }
        return try {
            val result =
                UploadOrchestrator().upload(
                    uri = galleryUri.uri,
                    mimeType = galleryUri.mimeType,
                    alt = null,
                    contentWarningReason = null,
                    compressionQuality = CompressorQuality.MEDIUM,
                    server = account.settings.defaultFileServer,
                    account = account,
                    uploader = uploader,
                    stripMetadata = account.settings.stripLocationOnUpload,
                    // A cover whose location could not be removed is not uploaded.
                    onStrippingFailed = { false },
                )
            when (result) {
                is UploadingState.Finished -> {
                    val url = (result.result as? UploadOrchestrator.OrchestratorResult.ServerResult)?.url
                    if (url == null) {
                        onError(
                            loadStringRes(Res.string.failed_to_upload_media_no_details),
                            loadStringRes(Res.string.server_did_not_provide_a_url_after_uploading),
                        )
                    }
                    url
                }

                is UploadingState.Error -> {
                    val title =
                        when (result.error) {
                            UploadError.UPLOAD_CANCELLED, UploadError.AVIF_METADATA_STRIP_FAILED -> Res.string.metadata_strip_failed_title
                            else -> Res.string.failed_to_upload_media_no_details
                        }
                    val message =
                        if (result.error == UploadError.UPLOAD_CANCELLED) {
                            loadStringRes(Res.string.metadata_strip_failed_upload_cancelled)
                        } else {
                            loadStringRes(result.errorResource, *result.params)
                        }
                    onError(loadStringRes(title), message)
                    null
                }
            }
        } finally {
            _state.update { it.copy(isUploadingImage = false) }
        }
    }

    /**
     * Toggle scheduled-vs-now mode. When [scheduled] is true the
     * sheet shows a date / time picker and the published event uses
     * `status=PLANNED` + `["starts", <unix>]`. When false, the
     * existing `status=OPEN` "live now" path runs.
     */
    fun onScheduledToggle(scheduled: Boolean) = _state.update { it.copy(scheduled = scheduled, error = null) }

    fun onScheduledStartChange(unixSeconds: Long) = _state.update { it.copy(scheduledStartUnix = unixSeconds, error = null) }

    /**
     * Build the kind-30312 event, sign + broadcast it, and return the
     * launch info the sheet needs to start the room screen. Returns
     * null on validation or network failure (with [FormState.error]
     * set so the UI can render it).
     */
    suspend fun publishAndBuildLaunchInfo(): RoomLaunchInfo? {
        val current = _state.value
        val account =
            account ?: run {
                _state.update { it.copy(error = loadStringRes(Res.string.nest_error_account_not_bound)) }
                return null
            }
        if (current.roomName.isBlank()) {
            _state.update { it.copy(error = loadStringRes(Res.string.nest_error_room_name_required)) }
            return null
        }
        val service = current.serviceUrl.trim().trimEnd('/')
        val endpoint = current.endpointUrl.trim().trimEnd('/')
        if (service.isBlank() || !service.startsWith("http")) {
            _state.update { it.copy(error = loadStringRes(Res.string.nest_error_service_url_scheme)) }
            return null
        }
        if (endpoint.isBlank() || !endpoint.startsWith("http")) {
            _state.update { it.copy(error = loadStringRes(Res.string.nest_error_endpoint_url_scheme)) }
            return null
        }

        if (current.scheduled && current.scheduledStartUnix <= 0L) {
            _state.update { it.copy(error = loadStringRes(Res.string.nest_error_pick_start_time)) }
            return null
        }
        // The host may want to backdate a "scheduled" announcement
        // for a room that already started elsewhere — but more often
        // a past timestamp means the picker landed on the wrong day
        // and the host didn't notice. Guard against the misfire.
        // (Audience-side render is already past-aware: SCHEDULED
        // chips suppress the "Starts <date>" subline once `now` >
        // starts.)
        val nowSec = TimeUtils.now()
        if (current.scheduled && current.scheduledStartUnix < nowSec) {
            _state.update { it.copy(error = loadStringRes(Res.string.nest_error_pick_future_start)) }
            return null
        }

        _state.update { it.copy(isPublishing = true, error = null) }
        val accountModel = account.account
        val hostPubkey = accountModel.userProfile().pubkeyHex
        val targetStatus = if (current.scheduled) StatusTag.STATUS.PLANNED else StatusTag.STATUS.LIVE
        val template =
            MeetingSpaceEvent.build(
                room = current.roomName.trim(),
                status = targetStatus,
                service = service,
                host = ParticipantTag(hostPubkey, null, ROLE.HOST.code, null),
            ) {
                endpoint(endpoint)
                if (current.scheduled) {
                    starts(current.scheduledStartUnix)
                }
                current.summary
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { summary(it) }
                current.imageUrl
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { image(it) }
                relays(
                    accountModel.outboxRelays.flow.value
                        .toList(),
                )
            }

        val signed =
            try {
                accountModel.signAndComputeBroadcast(template)
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isPublishing = false,
                        error = loadStringRes(Res.string.nest_error_publish_failed, t.message ?: t::class.simpleName.toString()),
                    )
                }
                return null
            }

        // Reset the form so the next open of the sheet starts fresh —
        // the VM is keyed by pubkey via `viewModel(key = …)` and is
        // reused across sheet open/close cycles.
        resetForm()
        return RoomLaunchInfo(
            addressValue = signed.address().toValue(),
            authBaseUrl = service,
            endpoint = endpoint,
            hostPubkey = hostPubkey,
            roomId = signed.dTag(),
            kind = MeetingSpaceEvent.KIND,
        )
    }

    data class FormState(
        val roomName: String,
        val summary: String,
        val serviceUrl: String,
        val endpointUrl: String,
        val imageUrl: String,
        val isPublishing: Boolean,
        val error: String?,
        val isUploadingImage: Boolean = false,
        /** When true, publish as `status=PLANNED` + `["starts", <unix>]`. */
        val scheduled: Boolean = false,
        /** Unix seconds of the scheduled start; 0 = not yet picked. */
        val scheduledStartUnix: Long = 0L,
    ) {
        val canSubmit: Boolean
            get() =
                roomName.isNotBlank() &&
                    serviceUrl.isNotBlank() &&
                    endpointUrl.isNotBlank() &&
                    (!scheduled || scheduledStartUnix > 0L)

        companion object {
            fun defaults() =
                FormState(
                    roomName = "",
                    summary = "",
                    serviceUrl = DEFAULT_SERVICE_URL,
                    endpointUrl = DEFAULT_ENDPOINT_URL,
                    imageUrl = "",
                    isPublishing = false,
                    error = null,
                )
        }
    }

    /**
     * Captured fields needed to launch the room screen. Mirrors the
     * `EXTRA_*` set the activity expects; pulled out here so the sheet
     * is a thin renderer.
     */
    data class RoomLaunchInfo(
        val addressValue: String,
        val authBaseUrl: String,
        val endpoint: String,
        val hostPubkey: String,
        val roomId: String,
        val kind: Int,
    )

    companion object {
        /**
         * Public nostrnests deployment — a blank form produces a working
         * room here. Users can edit either field to point at their own
         * moq-auth / moq-relay pair.
         *
         * The auth sidecar (moq-auth) and the WebTransport relay
         * (moq-rs) live on different hosts: moq-auth speaks regular
         * HTTPS on :443 to mint JWTs; moq.nostrnests.com:4443 is the
         * QUIC/WebTransport endpoint and does NOT accept TCP. Don't
         * collapse them back into a single URL — OkHttp can't speak
         * HTTP/3, so a TCP POST to :4443 will hang and fail. Both
         * defaults are emitted on the kind-30312 event as the deployed
         * `streaming` (relay) and `auth` (sidecar) tags.
         */
        const val DEFAULT_SERVICE_URL: String = "https://moq-auth.nostrnests.com"
        const val DEFAULT_ENDPOINT_URL: String = "https://moq.nostrnests.com:4443"
    }
}
