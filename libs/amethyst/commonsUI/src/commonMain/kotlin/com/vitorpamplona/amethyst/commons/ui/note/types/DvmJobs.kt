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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dvm_offline
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_clean_pill
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_encrypted_pill
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_events_count
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_flagged_pill
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_heartbeat_title
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_content_discovery
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_content_search
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_event_count
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_image_generation
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_image_to_video
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_malware_scan
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_op_return
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_people_search
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_pow
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_publish_schedule
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_summarization
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_text_extraction
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_text_generation
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_text_to_speech
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_timestamping
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_translation
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_user_discovery
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_video_conversion
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_video_translation
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_customer
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_for
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_providers
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_transaction
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_group
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_length
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_max_results
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_max_tokens
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_model
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_negative_prompt
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_pair
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_pow
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_range
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_ratio
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_since
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_size
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_temperature
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_until
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_people_count
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_publishes_at
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_request_title
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_result_title
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_results_count
import com.vitorpamplona.amethyst.commons.resources.language
import com.vitorpamplona.amethyst.commons.resources.media_server_status_online
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip90Dvms.DvmRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.DvmResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.DvmContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentSearch.DvmContentSearchRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentSearch.DvmContentSearchResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import com.vitorpamplona.quartz.nip90Dvms.eventCount.DvmEventCountRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventCount.DvmEventCountResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPowDelegation.DvmEventPowDelegationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPowDelegation.DvmEventPowDelegationResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPublishSchedule.DvmEventPublishScheduleRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPublishSchedule.DvmEventPublishScheduleResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventTimestamping.DvmEventTimestampingRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventTimestamping.DvmEventTimestampingResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.imageGeneration.DvmImageGenerationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.imageToVideo.DvmImageToVideoRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.malwareScanning.DvmMalwareScanRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.malwareScanning.DvmMalwareScanResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.opReturn.DvmOpReturnRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.opReturn.DvmOpReturnResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.peopleSearch.DvmPeopleSearchRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.peopleSearch.DvmPeopleSearchResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.firstAmount
import com.vitorpamplona.quartz.nip90Dvms.summarization.DvmSummarizationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.tags.dvmInputEventIds
import com.vitorpamplona.quartz.nip90Dvms.tags.dvmParams
import com.vitorpamplona.quartz.nip90Dvms.tags.inputs
import com.vitorpamplona.quartz.nip90Dvms.textExtraction.DvmTextExtractionRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.textGeneration.DvmTextGenerationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.textToSpeech.DvmTextToSpeechRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.translation.DvmTranslationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryRequest.DvmUserDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryResponse.DvmUserDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.videoConversion.DvmVideoConversionRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.videoTranslation.DvmVideoTranslationRequestEvent
import org.jetbrains.compose.resources.StringResource

/*
 * NIP-90 Data Vending Machines: one card for every job request (5000-5999), one for every job
 * result (6000-6999), and one for the DVM heartbeat. The request and result cards share a frame
 * and differ only in the job's name and icon, looked up by the request kind.
 *
 * A request leads with its input (the text, link or event the job runs on), folds its params into
 * one line, shows the bid as a pill and the DVMs asked as chips. A result leads with its output,
 * shows the price as a pill and quotes the request it answers once, compactly.
 *
 * Job feedback (7000, DvmStatusEvent) and content-discovery results (6300) keep their own
 * renderers, which NoteCompose dispatches before the base-class branches that land here.
 */

/** How many found events a search result quotes at most, so one result cannot flood the feed. */
private const val MAX_QUOTED = 2

private const val MAX_RESULT_USERS = 5

private const val NIP90_RESULT_KIND_OFFSET = 1000

private val HEX64 = Regex("[0-9a-fA-F]{64}")

/** Params that only tell the DVM where to read or write, not what the job is. */
private val PLUMBING_PARAMS = setOf("relay", "relays")

/** Params whose values are prose someone wrote, shown as translatable text rather than meta facts. */
private val PROSE_PARAMS = setOf("prompt", "negative_prompt", "instructions", "system_prompt", "description", "context")

private class DvmJob(
    val name: StringResource,
    val icon: MaterialSymbol,
)

/** The name and icon of the job a request kind (5000-5999) asks for, or null for an unknown job. */
private fun dvmJob(requestKind: Int): DvmJob? =
    when (requestKind) {
        DvmTextExtractionRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_text_extraction, MaterialSymbols.Description)
        DvmSummarizationRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_summarization, MaterialSymbols.AutoMirrored.Article)
        DvmTranslationRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_translation, MaterialSymbols.Translate)
        DvmTextGenerationRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_text_generation, MaterialSymbols.AutoAwesome)
        DvmImageGenerationRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_image_generation, MaterialSymbols.Image)
        DvmVideoConversionRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_video_conversion, MaterialSymbols.VideoSettings)
        DvmVideoTranslationRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_video_translation, MaterialSymbols.Movie)
        DvmImageToVideoRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_image_to_video, MaterialSymbols.VideoLibrary)
        DvmTextToSpeechRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_text_to_speech, MaterialSymbols.AutoMirrored.VolumeUp)
        DvmContentDiscoveryRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_content_discovery, MaterialSymbols.Explore)
        DvmUserDiscoveryRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_user_discovery, MaterialSymbols.People)
        DvmContentSearchRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_content_search, MaterialSymbols.Search)
        DvmPeopleSearchRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_people_search, MaterialSymbols.TravelExplore)
        DvmEventCountRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_event_count, MaterialSymbols.Numbers)
        DvmMalwareScanRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_malware_scan, MaterialSymbols.Security)
        DvmEventTimestampingRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_timestamping, MaterialSymbols.OpenTimestamps)
        DvmOpReturnRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_op_return, MaterialSymbols.CurrencyBitcoin)
        DvmEventPublishScheduleRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_publish_schedule, MaterialSymbols.Schedule)
        DvmEventPowDelegationRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_pow, MaterialSymbols.Manufacturing)
        else -> null
    }

/** NIP-90: a bare `["encrypted"]` tag means the inputs/params (or the result) are NIP-04 ciphertext. */
private fun TagArray.isDvmEncrypted(): Boolean = any { it.isNotEmpty() && it[0] == "encrypted" }

/** NIP-90 `["bid", <msats>]`: the most the customer will pay, in sats. */
private fun TagArray.dvmBidSats(): Long? = firstOrNull { it.size > 1 && it[0] == "bid" }?.get(1)?.toLongOrNull()?.div(1000)

/** Everything a request card shows, parsed once per event. */
private class DvmRequestInfo(
    val texts: List<String>,
    val urls: List<String>,
    val eventIds: List<HexKey>,
    /** The signed events a PoW or scheduled-publishing job carries: the first one, parsed, and how many. */
    val embedded: Event?,
    val embeddedCount: Int,
    val filterJson: String?,
    val params: List<Pair<String, String>>,
    val proseParams: List<Pair<String, String>>,
    val users: List<HexKey>,
    val bidSats: Long?,
    val providers: List<HexKey>,
)

private fun parseRequest(event: DvmRequestEvent): DvmRequestInfo {
    val inputs = event.tags.inputs()
    // These two kinds carry whole signed-event JSONs as their `text` inputs, not human text.
    val eventJsons =
        when (event) {
            is DvmEventPowDelegationRequestEvent -> event.eventJsons()
            is DvmEventPublishScheduleRequestEvent -> event.eventJsons()
            else -> emptyList()
        }
    val texts =
        if (eventJsons.isEmpty()) {
            inputs.filter { it.type == "text" || it.type == "prompt" }.map { it.value }
        } else {
            emptyList()
        }

    val users = mutableListOf<HexKey>()
    val params = LinkedHashMap<String, MutableList<String>>()
    event.tags.dvmParams().forEach { (key, values) ->
        when (key) {
            "user" -> values.filterTo(users) { it.isValid() }
            "users" -> values.forEach { value -> HEX64.findAll(value).mapTo(users) { it.value.lowercase() } }
            in PLUMBING_PARAMS -> Unit
            else -> {
                val value =
                    when (key) {
                        "since", "until" -> values.firstOrNull()?.toLongOrNull()?.let { formatDateTime(it * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT) }
                        "range" -> values.joinToString(" – ")
                        else -> values.joinToString(" ")
                    }
                if (!value.isNullOrBlank()) params.getOrPut(key) { mutableListOf() }.add(value)
            }
        }
    }

    return DvmRequestInfo(
        texts = texts,
        urls = inputs.filter { it.type == "url" }.map { it.value },
        eventIds = event.tags.dvmInputEventIds().distinct(),
        embedded = eventJsons.firstOrNull()?.let { Event.fromJsonOrNull(it) },
        embeddedCount = eventJsons.size,
        filterJson = (event as? DvmEventCountRequestEvent)?.filterJson()?.ifBlank { null },
        params = params.filterKeys { it !in PROSE_PARAMS }.map { (key, values) -> key to values.joinToString(", ") },
        proseParams = params.filterKeys { it in PROSE_PARAMS }.map { (key, values) -> key to values.joinToString("\n") },
        users = users.distinct(),
        bidSats = event.tags.dvmBidSats(),
        providers = event.serviceProviders(),
    )
}

@Composable
private fun dvmParamLabel(key: String): String =
    when (key) {
        "language" -> stringRes(Res.string.language)
        "model" -> stringRes(Res.string.kc_dvm_param_model)
        "max_results" -> stringRes(Res.string.kc_dvm_param_max_results)
        "max_tokens" -> stringRes(Res.string.kc_dvm_param_max_tokens)
        "temperature" -> stringRes(Res.string.kc_dvm_param_temperature)
        "length" -> stringRes(Res.string.kc_dvm_param_length)
        "size" -> stringRes(Res.string.kc_dvm_param_size)
        "ratio" -> stringRes(Res.string.kc_dvm_param_ratio)
        "negative_prompt" -> stringRes(Res.string.kc_dvm_param_negative_prompt)
        "pow" -> stringRes(Res.string.kc_dvm_param_pow)
        "range" -> stringRes(Res.string.kc_dvm_param_range)
        "since" -> stringRes(Res.string.kc_dvm_param_since)
        "until" -> stringRes(Res.string.kc_dvm_param_until)
        "group" -> stringRes(Res.string.kc_dvm_param_group)
        // Any other param is a DVM-specific protocol word, shown as the DVM wrote it.
        else -> key
    }

/** A group of people under a small heading: the providers asked, the customer, the user a job is for. */
@Composable
private fun DvmPeople(
    label: StringResource,
    pubKeys: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (pubKeys.isEmpty()) return
    KindCardSection(stringRes(label))
    KindCardUsers(pubKeys, accountViewModel, nav)
}

/** A NIP-90 job request (5000-5999): the job, its inputs and params, the bid, and the DVMs asked to run it. */
@Composable
fun RenderDvmRequest(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DvmRequestEvent ?: return
    val job = remember(event) { dvmJob(event.kind) }
    val title = job?.let { stringRes(Res.string.kc_dvm_request_title, stringRes(it.name)) } ?: kindName(event.kind)
    val icon = job?.icon ?: MaterialSymbols.Assistant
    val encrypted = remember(event) { event.tags.isDvmEncrypted() }

    if (encrypted) {
        // Inputs and params are ciphertext for the chosen DVM; only who was asked is public.
        val providers = remember(event) { event.serviceProviders() }
        KindCard(
            icon = icon,
            title = title,
            tone = KindTone.Neutral,
            status = stringRes(Res.string.kc_dvm_encrypted_pill),
        ) {
            DvmPeople(Res.string.kc_dvm_label_providers, providers, accountViewModel, nav)
        }
        return
    }

    val info = remember(event) { parseRequest(event) }

    KindCard(
        icon = icon,
        title = title,
        status = info.bidSats?.takeIf { it > 0 }?.let { kindCardSats(it) },
        statusTone = KindTone.Money,
    ) {
        // The input is the hero: the prompt or text, the link (media previews inline), the event.
        info.texts.forEach { KindCardText(baseNote, it, backgroundColor, accountViewModel, nav, quotesLeft) }
        info.urls.forEach { KindCardText(baseNote, it, backgroundColor, accountViewModel, nav, quotesLeft) }

        info.embedded?.let { inner ->
            KindCardText(baseNote, inner.content, backgroundColor, accountViewModel, nav, quotesLeft)
            KindCardMeta(
                listOf(
                    kindName(inner.kind),
                    if (event is DvmEventPublishScheduleRequestEvent) {
                        stringRes(Res.string.kc_dvm_publishes_at, formatDateTime(inner.createdAt * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT))
                    } else {
                        null
                    },
                    if (info.embeddedCount > 1) pluralStringRes(Res.plurals.kc_dvm_events_count, info.embeddedCount, info.embeddedCount) else null,
                ),
            )
        }

        info.filterJson?.let { KindCardCode(it, maxLines = 6) }

        info.eventIds.firstOrNull()?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
        if (info.eventIds.size > 1) {
            val more = info.eventIds.size - 1
            KindCardMore(more)
        }

        KindCardMeta(info.params.map { (key, value) -> stringRes(Res.string.kc_dvm_param_pair, dvmParamLabel(key), value) })
        // Params someone wrote in their own words read as text, with translation and links.
        info.proseParams.forEach { (key, value) ->
            KindCardRichField(dvmParamLabel(key), baseNote, value, backgroundColor, accountViewModel, nav)
        }

        DvmPeople(Res.string.kc_dvm_label_for, info.users, accountViewModel, nav)
        DvmPeople(Res.string.kc_dvm_label_providers, info.providers, accountViewModel, nav)
    }
}

/**
 * A NIP-90 job result (6000-6999): what the DVM produced (text, media links, found events or
 * people, a count, a verdict), the price it asks, and the request it answers, quoted compactly.
 */
@Composable
fun RenderDvmResult(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DvmResponseEvent ?: return
    val job = remember(event) { dvmJob(event.kind - NIP90_RESULT_KIND_OFFSET) }
    val title = job?.let { stringRes(Res.string.kc_dvm_result_title, stringRes(it.name)) } ?: kindName(event.kind)
    val icon = job?.icon ?: MaterialSymbols.Assistant
    val encrypted = remember(event) { event.tags.isDvmEncrypted() }
    val requestId = remember(event) { event.jobRequestId() }
    val customer = remember(event) { listOfNotNull(event.customer()) }
    val price =
        remember(event) {
            event.tags
                .firstAmount()
                ?.amount
                ?.div(1000)
                ?.takeIf { it > 0 }
        }?.let { kindCardSats(it) }

    if (encrypted) {
        KindCard(
            icon = icon,
            title = title,
            tone = KindTone.Neutral,
            status = stringRes(Res.string.kc_dvm_encrypted_pill),
        ) {
            KindCardMeta(listOf(price))
            DvmRequestOrCustomer(requestId, customer, quotesLeft, backgroundColor, accountViewModel, nav)
        }
        return
    }

    if (event is DvmMalwareScanResponseEvent) {
        val clean = remember(event) { event.isClean() }
        KindCard(
            icon = if (clean) MaterialSymbols.CheckCircle else MaterialSymbols.Warning,
            title = title,
            tone = if (clean) KindTone.Positive else KindTone.Negative,
            status = stringRes(if (clean) Res.string.kc_dvm_clean_pill else Res.string.kc_dvm_flagged_pill),
        ) {
            // A flagged verdict carries the scanner's finding as the content.
            if (!clean) KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
            KindCardMeta(listOf(price))
            DvmRequestOrCustomer(requestId, customer, quotesLeft, backgroundColor, accountViewModel, nav)
        }
        return
    }

    KindCard(
        icon = icon,
        title = title,
        subtitle = dvmResultSubtitle(event),
        tone = KindTone.Positive,
        status = price,
        statusTone = KindTone.Money,
    ) {
        DvmResultPayload(baseNote, event, quotesLeft, backgroundColor, accountViewModel, nav)
        DvmRequestOrCustomer(requestId, customer, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/**
 * The request a result answers, quoted once and compactly (its own quotes and previews folded),
 * or the customer's chip when the result does not point at its request.
 */
@Composable
private fun DvmRequestOrCustomer(
    requestId: HexKey?,
    customer: List<HexKey>,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (requestId != null) {
        KindCardQuoteEvent(requestId, minOf(quotesLeft, 1), backgroundColor, accountViewModel, nav)
    } else {
        DvmPeople(Res.string.kc_dvm_label_customer, customer, accountViewModel, nav)
    }
}

/** For results that are a set or a number, the count; null for the rest. */
@Composable
private fun dvmResultSubtitle(event: DvmResponseEvent): String? =
    when (event) {
        is DvmContentSearchResponseEvent -> {
            val count = remember(event) { event.resultTags().size }
            pluralStringRes(Res.plurals.kc_dvm_results_count, count, count)
        }

        is DvmPeopleSearchResponseEvent -> {
            val count =
                remember(event) {
                    event
                        .resultTags()
                        .mapNotNull(PTag::parseKey)
                        .distinct()
                        .size
                }
            pluralStringRes(Res.plurals.kc_dvm_people_count, count, count)
        }

        is DvmUserDiscoveryResponseEvent -> {
            val count =
                remember(event) {
                    event
                        .resultTags()
                        .mapNotNull(PTag::parseKey)
                        .distinct()
                        .size
                }
            pluralStringRes(Res.plurals.kc_dvm_people_count, count, count)
        }

        is DvmEventCountResponseEvent -> {
            event.count()?.let { count ->
                pluralStringRes(Res.plurals.kc_dvm_events_count, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), count)
            }
        }

        else -> {
            null
        }
    }

/** What the DVM produced, drawn by what the result kind's `content` holds. */
@Composable
private fun DvmResultPayload(
    baseNote: Note,
    event: DvmResponseEvent,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (event) {
        is DvmContentSearchResponseEvent -> {
            val found = remember(event) { event.resultTags() }
            found.take(MAX_QUOTED).forEach { tag ->
                ETag.parseId(tag)?.let {
                    KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav)
                } ?: ATag.parseAddress(tag)?.let {
                    KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }
            if (found.size > MAX_QUOTED) {
                val more = found.size - MAX_QUOTED
                KindCardMore(more)
            }
        }

        is DvmPeopleSearchResponseEvent -> {
            val people = remember(event) { event.resultTags().mapNotNull(PTag::parseKey).distinct() }
            KindCardUsers(people, accountViewModel, nav, max = MAX_RESULT_USERS)
        }

        is DvmUserDiscoveryResponseEvent -> {
            val people = remember(event) { event.resultTags().mapNotNull(PTag::parseKey).distinct() }
            KindCardUsers(people, accountViewModel, nav, max = MAX_RESULT_USERS)
        }

        // The count is the subtitle; anything else the DVM wrote is shown as text.
        is DvmEventCountResponseEvent -> {
            if (event.count() == null) KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
        }

        is DvmEventTimestampingResponseEvent -> {
            event.otsEventId()?.takeIf { it.isValid() }?.let {
                KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav)
            }
        }

        is DvmEventPublishScheduleResponseEvent -> {
            event.publishedEventId()?.takeIf { it.isValid() }?.let {
                KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav)
            }
        }

        is DvmOpReturnResponseEvent -> {
            KindCardField(stringRes(Res.string.kc_dvm_label_transaction), event.transactionId()?.toShortDisplay(), monospace = true)
        }

        // The mined event, as signed JSON: show what it says and what kind it is, not the JSON.
        is DvmEventPowDelegationResponseEvent -> {
            val mined = remember(event) { event.content.ifBlank { null }?.let { Event.fromJsonOrNull(it) } }
            mined?.let { inner ->
                KindCardText(baseNote, inner.content, backgroundColor, accountViewModel, nav, quotesLeft)
                KindCardMeta(listOf(kindName(inner.kind)))
            }
        }

        // Text and media results (summaries, translations, generated text, image/video/audio URLs).
        else -> {
            KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
        }
    }
}

/** A DVM heartbeat (11998): the DVM's status line, whether the beat is still fresh, and its NIP-89 announcement. */
@Composable
fun RenderDvmHeartbeat(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DvmHeartbeatEvent ?: return
    val fresh = rememberAtNow(event) { now -> event.isFreshAt(now) }
    val announcement =
        remember(event) {
            event.dTag().ifBlank { null }?.let { Address(AppDefinitionEvent.KIND, event.pubKey, it) }
        }

    KindCard(
        icon = MaterialSymbols.Sensors,
        title = stringRes(Res.string.kc_dvm_heartbeat_title),
        tone = if (fresh) KindTone.Positive else KindTone.Neutral,
        status = stringRes(if (fresh) Res.string.media_server_status_online else Res.string.dvm_offline),
    ) {
        // The DVM's own status message, in its own words.
        KindCardProse(null, baseNote, event.status(), backgroundColor, accountViewModel, nav)
        announcement?.let { KindCardQuoteAddress(it, minOf(quotesLeft, 1), backgroundColor, accountViewModel, nav) }
    }
}
