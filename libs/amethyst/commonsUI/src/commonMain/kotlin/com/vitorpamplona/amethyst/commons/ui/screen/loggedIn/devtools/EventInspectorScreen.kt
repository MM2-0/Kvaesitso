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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.devtools.EventCodes
import com.vitorpamplona.amethyst.commons.devtools.EventInspectResult
import com.vitorpamplona.amethyst.commons.devtools.EventInspection
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.devtools_event_find_on_relays
import com.vitorpamplona.amethyst.commons.resources.devtools_event_id_mismatch
import com.vitorpamplona.amethyst.commons.resources.devtools_event_input_label
import com.vitorpamplona.amethyst.commons.resources.devtools_event_sig_bad
import com.vitorpamplona.amethyst.commons.resources.devtools_event_title
import com.vitorpamplona.amethyst.commons.resources.devtools_event_unparseable
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_usage_title
import com.vitorpamplona.amethyst.commons.resources.devtools_open_in_app
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.DateSkeletonFormatter
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeOfDayFormatter
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindNameFor
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.toPrettyJson
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The event inspector. Opened from a note's menu it shows that note's event as read-only,
 * syntax-coloured JSON with its nevent, naddr and npub: the app verified the event before it got
 * here, and every other field is right there in the JSON. Opened from the dev tools hub it takes a
 * pasted event (or a relay frame, or an nembed) and only speaks up about the id or signature when
 * one of them fails, since a pasted event has not been verified by anyone.
 */
@Composable
fun EventInspectorScreen(
    noteKey: String?,
    nav: INav,
) {
    val cached = remember(noteKey) { noteKey?.let { LocalCache.getNoteIfExists(it)?.event } }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.devtools_event_title), nav) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
        ) {
            if (cached != null) {
                CachedEventDetails(cached, nav)
            } else {
                PastedEventInspector(nav)
            }
        }
    }
}

@Composable
private fun CachedEventDetails(
    event: Event,
    nav: INav,
) {
    val json = remember(event) { event.toPrettyJson() }
    val codes = remember(event) { EventCodes.of(event) }
    val notes = rememberEventJsonNotes(event)

    DevToolsJsonBlock(json, note = notes)
    EventCodeRows(codes, Modifier.padding(top = 8.dp))
    EventActions(event, canOpen = true, nav)
}

@Composable
private fun PastedEventInspector(nav: INav) {
    var input by rememberSaveableText()
    val result = rememberEventInspection(input).value

    DevToolsInput(input, { input = it }, stringRes(Res.string.devtools_event_input_label), minLines = 4, maxLines = 12)

    when (result) {
        EventInspectResult.Empty -> {}

        is EventInspectResult.Unparseable ->
            DevToolsCheckRow(
                MaterialSymbols.ErrorOutline,
                stringRes(Res.string.devtools_event_unparseable, result.message),
                MaterialTheme.colorScheme.error,
            )

        is EventInspectResult.Parsed -> PastedEventDetails(result.inspection, nav)
    }
}

@Composable
private fun PastedEventDetails(
    inspection: EventInspection,
    nav: INav,
) {
    val bad = MaterialTheme.colorScheme.error
    if (!inspection.idValid) {
        DevToolsCheckRow(MaterialSymbols.Error, stringRes(Res.string.devtools_event_id_mismatch, inspection.computedId), bad)
    }
    if (!inspection.signatureValid) {
        DevToolsCheckRow(MaterialSymbols.Error, stringRes(Res.string.devtools_event_sig_bad), bad)
    }

    EventCodeRows(inspection.codes)
    EventActions(inspection.event, canOpen = inspection.isValid, nav)
}

@Composable
private fun EventCodeRows(
    codes: EventCodes,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        DevToolsValueRow("nevent", codes.nevent)
        codes.naddr?.let { DevToolsValueRow("naddr", it) }
        DevToolsValueRow("npub", codes.npub)
    }
}

@Composable
private fun EventActions(
    event: Event,
    canOpen: Boolean,
    nav: INav,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        OutlinedButton(onClick = { nav.nav(Route.DevToolsEventFinder(event.id)) }) {
            Text(stringRes(Res.string.devtools_event_find_on_relays))
        }
        OutlinedButton(onClick = { nav.nav(Route.DevToolsKindUsage(event.kind)) }) {
            Text(stringRes(Res.string.devtools_kind_usage_title, event.kind))
        }
        if (canOpen) {
            OutlinedButton(onClick = { nav.nav(Route.Note(event.id)) }) {
                Text(stringRes(Res.string.devtools_open_in_app))
            }
        }
    }
}

/** Past this many distinct users, the rest of a long `p` list keeps its bare keys: each name costs a relay subscription. */
private const val MAX_NAMED_USERS = 100

/** The least time between two re-colourings of the JSON while names load. */
private const val NAME_BATCH_MS = 250L

/** Tags whose first value is a Unix timestamp. */
private val TIMESTAMP_TAGS = setOf("expiration", "published_at", "start", "end")

private val FullDateFormatter = DateSkeletonFormatter("yMMMd")

/**
 * The faded notes [DevToolsJsonBlock] adds to [event]'s JSON: `created_at` and timestamp tags
 * as a date, `kind` and `k` tags as the kind's name, the author and tagged users as their
 * display names (loaded from relays while the screen is open), and `a` tags as both.
 */
@Composable
private fun rememberEventJsonNotes(event: Event): (JsonValueContext) -> String? {
    val pubkeys = remember(event) { referencedPubkeys(event) }
    val names = rememberUserNames(pubkeys)
    val kindNames by produceState(emptyMap<Int, String>(), event) {
        value = referencedKinds(event).associateWith { kindNameFor(it) }
    }
    val timeOfDay = rememberTimeOfDayFormatter()

    return remember(names, kindNames, timeOfDay) {
        { context -> noteFor(context, names, kindNames, timeOfDay)?.ifBlank { null } }
    }
}

private fun noteFor(
    context: JsonValueContext,
    names: Map<HexKey, String>,
    kinds: Map<Int, String>,
    timeOfDay: (Long) -> String,
): String? {
    if (context.index < 0) {
        return when (context.key) {
            "created_at" -> context.value.toLongOrNull()?.let { formatDate(it, timeOfDay) }
            "kind" -> context.value.toIntOrNull()?.let { kinds[it] }
            "pubkey" -> names[context.value]
            else -> null
        }
    }
    if (context.key != "tags") return null

    val head = context.arrayHead ?: return null
    return when (context.index) {
        pubkeyIndex(head) -> names[context.value]

        1 ->
            when (head) {
                "k", "K" -> context.value.toIntOrNull()?.let { kinds[it] }
                "a", "A" -> Address.parse(context.value)?.let { listOfNotNull(kinds[it.kind], names[it.pubKeyHex]).joinToString(" \u00B7 ") }
                in TIMESTAMP_TAGS -> context.value.toLongOrNull()?.let { formatDate(it, timeOfDay) }
                else -> null
            }

        else -> null
    }
}

/** Where a tag carries a user's pubkey: `p` first, the NIP-10 `e` author fifth, the NIP-18 `q` author fourth. */
private fun pubkeyIndex(tagName: String): Int =
    when (tagName) {
        "p", "P" -> 1
        "e" -> 4
        "q" -> 3
        else -> -1
    }

/** Up to the end of year 9999: past it a value is not a date anyone meant, and `* 1000` overflows a Long. */
private const val MAX_NOTED_SECONDS = 253_402_300_799L

private fun formatDate(
    seconds: Long,
    timeOfDay: (Long) -> String,
): String? {
    if (seconds !in 0..MAX_NOTED_SECONDS) return null
    val millis = seconds * 1000
    return FullDateFormatter.format(millis) + ", " + timeOfDay(millis)
}

private fun referencedPubkeys(event: Event): List<HexKey> {
    val keys = LinkedHashSet<HexKey>()
    keys.add(event.pubKey)
    event.tags.forEach { tag ->
        val name = tag.getOrNull(0) ?: return@forEach
        val key =
            when (name) {
                "a", "A" -> tag.getOrNull(1)?.let { Address.parse(it)?.pubKeyHex }
                else -> tag.getOrNull(pubkeyIndex(name))
            }
        if (key != null && key.length == 64 && Hex.isHex(key)) keys.add(key)
        if (keys.size >= MAX_NAMED_USERS) return keys.toList()
    }
    return keys.toList()
}

private fun referencedKinds(event: Event): Set<Int> {
    val kinds = LinkedHashSet<Int>()
    kinds.add(event.kind)
    event.tags.forEach { tag ->
        when (tag.getOrNull(0)) {
            "k", "K" -> tag.getOrNull(1)?.toIntOrNull()
            "a", "A" -> tag.getOrNull(1)?.let { Address.parse(it)?.kind }
            else -> null
        }?.let { kinds.add(it) }
    }
    return kinds
}

/**
 * Display names for [pubkeys] from their kind 0, keeping a relay subscription open for each
 * while on screen so names that are not cached yet fill in. A user with no name is left out.
 */
@Composable
private fun rememberUserNames(pubkeys: List<HexKey>): Map<HexKey, String> {
    val users by produceState(emptyList<User>(), pubkeys) {
        value = withContext(Dispatchers.Default) { pubkeys.mapNotNull { LocalCache.checkGetOrCreateUser(it) } }
    }

    users.forEach { user -> key(user.pubkeyHex) { UserFinderFilterAssemblerSubscription(user) } }

    val names by produceState(emptyMap<HexKey, String>(), users) {
        if (users.isEmpty()) return@produceState
        combine(users.map { user -> user.metadata().flow.map { user.pubkeyHex to it?.info?.bestName() } }) { pairs ->
            buildMap { pairs.forEach { (key, name) -> if (!name.isNullOrBlank()) put(key, name) } }
        }.distinctUntilChanged().conflate().collect {
            value = it
            // Names arrive one profile at a time; each new map re-colours the whole JSON, so
            // take at most a few batches a second.
            delay(NAME_BATCH_MS)
        }
    }
    return names
}
