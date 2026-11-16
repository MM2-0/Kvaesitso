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
package com.vitorpamplona.amethyst.commons.notifications

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip19Bech32.toNpub

/** Deep-link URIs consumed by the app's `uriToRoute` when a notification is tapped. */
object NotificationRoutes {
    private const val ACCOUNT = "?account="
    private const val SCROLL_TO = "&scrollTo="

    fun accountNpub(account: Account): String =
        account.signer.pubKey
            .hexToByteArray()
            .toNpub()

    /**
     * Opens the note directly (used for replies, mentions, media, git).
     *
     * A note delivered inside an envelope (a NIP-17 private note or reply) cannot be
     * addressed this way — see [privateNoteUri].
     */
    fun noteUri(
        note: Note,
        accountNpub: String,
    ): String =
        if (note.rumorHost != null) {
            privateNoteUri(note.idHex, accountNpub)
        } else {
            note.toNEvent() + ACCOUNT + accountNpub
        }

    /**
     * Opens a NIP-17 private note or reply by the rumor's own id.
     *
     * [Note.toNEvent] cannot address one: for a rumor it encodes the kind-1059 gift wrap
     * that delivered it, because the rumor id must never appear in anything that leaves
     * for a relay (`RumorHostCitationTest` pins that, and [Note.isPrivateRumor] guards the
     * publish paths). Two things then go wrong on a tap. The wrap is unfetchable — the
     * general event finder asks the account's read relays for an id only the DM inbox
     * relays ever held. And the wrap note emits exactly once, from `consumeRegularEvent`,
     * *before* it is decrypted, so `routeFor` sees a null `innerEventId` and the redirect
     * never fires again (the later `event = copyNoContent()` is a plain assignment).
     *
     * The rumor id has neither problem: its note emits when the rumor is consumed, and the
     * always-on one-week gift-wrap tail re-delivers and re-unwraps the envelope on every
     * cold start, so a push-recent message always comes back on its own. This URI is read
     * only by `MainActivity.uriToRoute`, inside our own process, and the route it produces
     * never puts the id in a REQ — see `Route.EventRedirect.isPrivate`.
     */
    fun privateNoteUri(
        rumorId: String,
        accountNpub: String,
    ): String = "privatenote?id=$rumorId&account=$accountNpub"

    /**
     * Opens a NIP-17 / NIP-04 private chatroom (DM notifications).
     *
     * A DM must NOT deep-link through its own note: [Note.toNEvent] cites the kind-1059
     * gift wrap that delivered the rumor (it has to — the rumor id is the private event's
     * identity and resolves to nothing on a public relay, see `RumorHostCitationTest`).
     * Resolving that nevent back to a room needs the wrap *and* seal notes to still be in
     * the in-memory `LocalCache`. A push usually wakes a cold process, so by the time the
     * user taps, the cache is empty — and no relay will ever serve that wrap again, so the
     * tap landed on a `Route.EventRedirect` that could never resolve.
     *
     * The room key is derived locally and needs no event at all, so this survives the
     * process dying. Same idea as [relayGroupUri], which routes a Buzz DM through the
     * channel's naddr rather than the message.
     */
    fun chatroomUri(
        room: ChatroomKey,
        accountNpub: String,
    ): String = "chatroom?id=${room.users.joinToString(",")}&account=$accountNpub"

    // ---------------------------------------------------------------------
    // Conversation shortcut ids
    //
    // Stable for the life of the chat and scoped to the account, so the same counterparty
    // under two logins does not collapse into one launcher entry pointing at the wrong
    // inbox. Members are sorted because a room key is a set — ordering must not create a
    // second shortcut for a chat that already has one.
    // ---------------------------------------------------------------------

    fun chatroomShortcutId(
        room: ChatroomKey,
        accountNpub: String,
    ): String = "dm:$accountNpub:" + room.users.sorted().joinToString(",")

    fun marmotShortcutId(
        nostrGroupId: String,
        accountNpub: String,
    ): String = "marmot:$accountNpub:$nostrGroupId"

    fun relayGroupShortcutId(
        channelNAddr: String,
        accountNpub: String,
    ): String = "relaygroup:$accountNpub:$channelNAddr"

    /** Opens the Notifications tab, scrolled to [scrollToId] (used for zaps, reactions, chess). */
    fun notificationsUri(
        accountNpub: String,
        scrollToId: String,
    ): String = "notifications$ACCOUNT$accountNpub$SCROLL_TO$scrollToId"

    /**
     * Opens a Marmot group chatroom (welcome + group message).
     *
     * Note the query here rides on an *opaque* URI (a scheme with no `//`), so its
     * `?account=` can only be read by [String.findQueryParameterValue] — see the note
     * there; `java.net.URI.rawQuery` is null for this shape.
     */
    fun marmotUri(
        nostrGroupId: String,
        accountNpub: String,
    ): String = "marmot:$nostrGroupId$ACCOUNT$accountNpub"

    /**
     * Opens a NIP-29 relay-group / Buzz chatroom. [channelNAddr] is the channel's
     * kind-39000 naddr, which `MainActivity.uriToRoute` already routes to
     * `Route.RelayGroup` via the standard nostr-entity path.
     */
    fun relayGroupUri(
        channelNAddr: String,
        accountNpub: String,
    ): String = "$channelNAddr$ACCOUNT$accountNpub"
}
