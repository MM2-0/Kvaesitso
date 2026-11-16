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
package com.vitorpamplona.amethyst.commons.napplet.ui

import com.vitorpamplona.amethyst.commons.connectedApps.consent.ProfileField
import com.vitorpamplona.amethyst.commons.connectedApps.consent.counterpartyLabel
import com.vitorpamplona.amethyst.commons.connectedApps.consent.profileFieldChanges
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.consent_delete_about
import com.vitorpamplona.amethyst.commons.resources.consent_delete_count
import com.vitorpamplona.amethyst.commons.resources.consent_delete_events
import com.vitorpamplona.amethyst.commons.resources.consent_delete_nothing
import com.vitorpamplona.amethyst.commons.resources.consent_list_adds
import com.vitorpamplona.amethyst.commons.resources.consent_list_keeps
import com.vitorpamplona.amethyst.commons.resources.consent_list_names_more
import com.vitorpamplona.amethyst.commons.resources.consent_list_no_change
import com.vitorpamplona.amethyst.commons.resources.consent_list_removes
import com.vitorpamplona.amethyst.commons.resources.consent_list_unknown
import com.vitorpamplona.amethyst.commons.resources.consent_list_wipe
import com.vitorpamplona.amethyst.commons.resources.consent_profile_changes
import com.vitorpamplona.amethyst.commons.resources.consent_profile_field_about
import com.vitorpamplona.amethyst.commons.resources.consent_profile_field_banner
import com.vitorpamplona.amethyst.commons.resources.consent_profile_field_display_name
import com.vitorpamplona.amethyst.commons.resources.consent_profile_field_lightning
import com.vitorpamplona.amethyst.commons.resources.consent_profile_field_name
import com.vitorpamplona.amethyst.commons.resources.consent_profile_field_nip05
import com.vitorpamplona.amethyst.commons.resources.consent_profile_field_picture
import com.vitorpamplona.amethyst.commons.resources.consent_profile_field_website
import com.vitorpamplona.amethyst.commons.resources.consent_profile_no_change
import com.vitorpamplona.amethyst.commons.resources.consent_profile_removes
import com.vitorpamplona.amethyst.commons.resources.consent_profile_removes_other
import com.vitorpamplona.amethyst.commons.resources.consent_profile_unknown
import com.vitorpamplona.amethyst.commons.resources.consent_relays_no_change
import com.vitorpamplona.amethyst.commons.resources.consent_relays_wipe
import com.vitorpamplona.amethyst.commons.resources.consent_report_content
import com.vitorpamplona.amethyst.commons.resources.consent_report_person
import com.vitorpamplona.amethyst.commons.resources.consent_report_person_reason
import com.vitorpamplona.amethyst.commons.resources.napplet_op_app_login
import com.vitorpamplona.amethyst.commons.resources.napplet_op_blossom
import com.vitorpamplona.amethyst.commons.resources.napplet_op_blossom_delete
import com.vitorpamplona.amethyst.commons.resources.napplet_op_blossom_get
import com.vitorpamplona.amethyst.commons.resources.napplet_op_blossom_list
import com.vitorpamplona.amethyst.commons.resources.napplet_op_blossom_upload
import com.vitorpamplona.amethyst.commons.resources.napplet_op_http_auth
import com.vitorpamplona.amethyst.commons.resources.napplet_op_http_auth_unknown
import com.vitorpamplona.amethyst.commons.resources.napplet_op_relay_login_to
import com.vitorpamplona.amethyst.commons.resources.napplet_op_update_profile
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindNameFor
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent

/** One line telling the user what a people-list update changes, and whether it is alarming. */
class ListChange(
    val text: String,
    val warning: Boolean,
)

/**
 * For a follow list (kind 3) or mute list (kind 10000) the app is about to replace, what changes
 * compared with the list Amethyst already has: who is added, who is removed, and how many stay.
 *
 * A replaceable list is published whole, so a buggy app can wipe it in one signature. Saying
 * "Adds Nind · keeps the other 15" makes the normal case reassuring and the wipe impossible to miss.
 * Only the public `p` tags are compared; a mute list's private entries are encrypted and the app
 * rebuilds them itself.
 */
suspend fun listChange(
    account: Account?,
    kind: Int,
    tags: Array<Array<String>>,
): ListChange? {
    if (account == null) return null
    val current: Set<HexKey> =
        when (kind) {
            ContactListEvent.KIND ->
                account.kind3FollowList
                    .getFollowListEvent()
                    ?.tags
                    ?.taggedUserIds()
                    ?.toSet()
            MuteListEvent.KIND ->
                account.muteList
                    .getMuteList()
                    ?.tags
                    ?.taggedUserIds()
                    ?.toSet()
            else -> return null
        } ?: return ListChange(loadStringRes(Res.string.consent_list_unknown), warning = true)

    val next = tags.taggedUserIds().toSet()
    val added = next - current
    val removed = current - next
    val kept = (current intersect next).size

    if (next.isEmpty() && current.isNotEmpty()) {
        return ListChange(loadPluralStringRes(Res.plurals.consent_list_wipe, current.size, current.size), warning = true)
    }
    if (added.isEmpty() && removed.isEmpty()) return ListChange(loadStringRes(Res.string.consent_list_no_change), warning = false)

    val parts = mutableListOf<String>()
    if (added.isNotEmpty()) parts += loadStringRes(Res.string.consent_list_adds, names(added))
    if (removed.isNotEmpty()) parts += loadStringRes(Res.string.consent_list_removes, names(removed))
    if (kept > 0) parts += loadPluralStringRes(Res.plurals.consent_list_keeps, kept, kept)
    // Removing more than one person in a single update is unusual for a tap in an app: flag it.
    return ListChange(parts.joinToString(" · "), warning = removed.size > 1)
}

/**
 * For a relay list (kind 10002), which relays it adds and drops against the one Amethyst has. The
 * list is replaced whole, so an app that leaves a relay out drops it, and the notes people read from
 * it go with it: any removal is a warning.
 */
suspend fun relayListChange(
    account: Account?,
    kind: Int,
    tags: Array<Array<String>>,
): ListChange? {
    if (kind != AdvertisedRelayListEvent.KIND || account == null) return null
    val current =
        account.nip65RelayList
            .getNIP65RelayList()
            ?.tags
            ?.let(::relayUrls) ?: return ListChange(loadStringRes(Res.string.consent_list_unknown), warning = true)
    val next = relayUrls(tags)
    if (next.isEmpty() && current.isNotEmpty()) {
        return ListChange(loadPluralStringRes(Res.plurals.consent_relays_wipe, current.size, current.size), warning = true)
    }
    val added = next - current
    val removed = current - next
    if (added.isEmpty() && removed.isEmpty()) return ListChange(loadStringRes(Res.string.consent_relays_no_change), warning = false)

    val kept = (current intersect next).size
    val parts = mutableListOf<String>()
    if (added.isNotEmpty()) parts += loadStringRes(Res.string.consent_list_adds, relayNames(added))
    if (removed.isNotEmpty()) parts += loadStringRes(Res.string.consent_list_removes, relayNames(removed))
    if (kept > 0) parts += loadPluralStringRes(Res.plurals.consent_list_keeps, kept, kept)
    return ListChange(parts.joinToString(" · "), warning = removed.isNotEmpty())
}

private fun relayUrls(tags: Array<Array<String>>): Set<String> =
    tags.mapNotNullTo(HashSet()) { tag ->
        tag
            .getOrNull(1)
            ?.takeIf { tag[0] == "r" }
            ?.trimEnd('/')
            ?.lowercase()
    }

private suspend fun relayNames(urls: Set<String>): String {
    val shown = urls.take(2).map { it.substringAfter("://") }
    val more = urls.size - shown.size
    val listed = shown.joinToString(", ")
    return if (more > 0) loadPluralStringRes(Res.plurals.consent_list_names_more, more, listed, more) else listed
}

private suspend fun names(pubkeys: Set<HexKey>): String {
    val shown = pubkeys.take(2).map { counterpartyLabel(it) }
    val more = pubkeys.size - shown.size
    val listed = shown.joinToString(", ")
    return if (more > 0) loadPluralStringRes(Res.plurals.consent_list_names_more, more, listed, more) else listed
}

/**
 * For a NIP-09 deletion (kind 5), what it removes. The event itself is just ids, so the prompt
 * otherwise shows nothing: name the deleted kind ("Deletes 1 of your Reports"), and when the target
 * is cached and names a person, who it was about ("Deletes your Report about Vitor").
 */
suspend fun deletionChange(
    kind: Int,
    tags: Array<Array<String>>,
): ListChange? {
    if (kind != DeletionRequestEvent.KIND) return null
    val targets = tags.filter { it.size > 1 && (it[0] == "e" || it[0] == "a") }.map { it[1] }
    if (targets.isEmpty()) return ListChange(loadStringRes(Res.string.consent_delete_nothing), warning = false)
    val deletedKind = tags.firstOrNull { it.size > 1 && it[0] == "k" }?.get(1)?.toIntOrNull()
    val kindName = deletedKind?.let { kindNameFor(it) } ?: loadStringRes(Res.string.consent_delete_events)

    // One cached target that names a person reads best as "about Vitor".
    val subject =
        targets
            .singleOrNull()
            ?.let { LocalCache.getNoteIfExists(it)?.event }
            ?.tags
            ?.taggedUserIds()
            ?.firstOrNull()
    val text =
        if (subject != null) {
            loadStringRes(Res.string.consent_delete_about, kindName, counterpartyLabel(subject))
        } else {
            loadPluralStringRes(Res.plurals.consent_delete_count, targets.size, targets.size, kindName)
        }
    return ListChange(text, warning = targets.size > 1)
}

/**
 * For a NIP-56 report (kind 1984), who is being reported and why. The report renders as just its
 * reason ("Spam"); the person it accuses is the decision, so it leads the prompt in red.
 */
suspend fun reportChange(
    kind: Int,
    tags: Array<Array<String>>,
): ListChange? {
    if (kind != ReportEvent.KIND) return null
    val person = tags.firstOrNull { it.size > 1 && it[0] == "p" }
    val reason = person?.getOrNull(2)?.ifBlank { null }
    val text =
        when {
            person == null -> loadStringRes(Res.string.consent_report_content)
            reason != null -> loadStringRes(Res.string.consent_report_person_reason, counterpartyLabel(person[1]), reason)
            else -> loadStringRes(Res.string.consent_report_person, counterpartyLabel(person[1]))
        }
    return ListChange(text, warning = true)
}

/**
 * What signing this event does, as the rest of "<app> wants to …", for the kinds whose purpose is
 * the decision and that otherwise read as a kind number: logins and NIP-98 web requests. Used by
 * both the signer prompt and the capability prompt so the two never describe one request
 * differently. Null for every other kind.
 */
suspend fun signRequestSummary(
    kind: Int,
    tags: Array<Array<String>>,
): String? =
    when (kind) {
        RelayAuthEvent.KIND -> {
            val relay = tags.firstOrNull { it.size > 1 && it[0] == "relay" && it[1].isNotBlank() }?.get(1)
            if (relay == null) {
                loadStringRes(Res.string.napplet_op_app_login)
            } else {
                loadStringRes(Res.string.napplet_op_relay_login_to, relay.removePrefix("wss://").removePrefix("ws://").trimEnd('/'))
            }
        }
        MetadataEvent.KIND -> loadStringRes(Res.string.napplet_op_update_profile)
        BlossomAuthorizationEvent.KIND ->
            when (tags.firstOrNull { it.size > 1 && it[0] == "t" }?.get(1)) {
                "upload", "media" -> loadStringRes(Res.string.napplet_op_blossom_upload)
                "delete" -> loadStringRes(Res.string.napplet_op_blossom_delete)
                "list" -> loadStringRes(Res.string.napplet_op_blossom_list)
                "get" -> loadStringRes(Res.string.napplet_op_blossom_get)
                else -> loadStringRes(Res.string.napplet_op_blossom)
            }
        HTTPAuthorizationEvent.KIND -> {
            val url = tags.firstOrNull { it.size > 1 && it[0] == "u" }?.get(1)
            val method = tags.firstOrNull { it.size > 1 && it[0] == "method" }?.get(1)?.uppercase()
            if (url == null) {
                loadStringRes(Res.string.napplet_op_http_auth_unknown)
            } else {
                loadStringRes(Res.string.napplet_op_http_auth, method ?: "?", url.removePrefix("https://").removePrefix("http://"))
            }
        }
        else -> null
    }

private fun ProfileField.label() =
    when (this) {
        ProfileField.NAME -> Res.string.consent_profile_field_name
        ProfileField.DISPLAY_NAME -> Res.string.consent_profile_field_display_name
        ProfileField.PICTURE -> Res.string.consent_profile_field_picture
        ProfileField.BANNER -> Res.string.consent_profile_field_banner
        ProfileField.ABOUT -> Res.string.consent_profile_field_about
        ProfileField.WEBSITE -> Res.string.consent_profile_field_website
        ProfileField.NIP05 -> Res.string.consent_profile_field_nip05
        ProfileField.LIGHTNING -> Res.string.consent_profile_field_lightning
    }

/**
 * For a profile update (kind 0), which fields change against the profile Amethyst has. The preview
 * card shows the whole new profile, which looks the same whether one word changed or a field was
 * dropped; this line names the difference, in red when a field is removed (a profile is replaced
 * whole, so an app that omits a field erases it).
 */
suspend fun profileChange(
    account: Account?,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
): ListChange? {
    if (kind != MetadataEvent.KIND || account == null) return null
    val current = account.userMetadata.getUserMetadataEvent() ?: return ListChange(loadStringRes(Res.string.consent_profile_unknown), warning = true)
    val changes = profileFieldChanges(current, tags, content) ?: return null

    if (changes.changed.isEmpty() && changes.removed.isEmpty() && !changes.removesData) {
        return ListChange(loadStringRes(Res.string.consent_profile_no_change), warning = false)
    }

    val parts = mutableListOf<String>()
    if (changes.changed.isNotEmpty()) parts += loadStringRes(Res.string.consent_profile_changes, changes.changed.map { loadStringRes(it.label()) }.joinToString(", "))
    if (changes.removed.isNotEmpty()) parts += loadStringRes(Res.string.consent_profile_removes, changes.removed.map { loadStringRes(it.label()) }.joinToString(", "))
    if (parts.isEmpty()) parts += loadStringRes(Res.string.consent_profile_removes_other)
    return ListChange(parts.joinToString(" · "), warning = changes.removesData)
}
