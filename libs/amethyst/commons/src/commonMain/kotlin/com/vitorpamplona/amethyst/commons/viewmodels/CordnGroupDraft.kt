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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.cordn.spec00Coordinator.CordnCoordinatorDiscovery
import com.vitorpamplona.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A cordn group being filled in, across the screens that fill it in.
 *
 * The form outgrew one screen. Choosing who is in the group is a search, a
 * result list and a roster with a per-person admin toggle -- a screen's worth
 * of surface, and the thing every later step depends on, because which
 * coordinator can serve the group is decided by which of these people it holds
 * a KeyPackage for.
 *
 * Held in a ViewModel rather than in `remember` because a Compose Navigation
 * destination is disposed when you navigate off it: a half-typed name and a
 * finished discovery run would both be gone on the way back from picking
 * people. Scoped to the Activity by its caller (the same thing the chess lobby
 * and board do) so both screens see one draft, keyed per account so switching
 * accounts cannot inherit another one's roster.
 *
 * Deliberately headless and free of protocol: it holds what the user has typed
 * and nothing derived from a coordinator. Coverage -- who a coordinator can
 * actually reach -- is a live query each screen makes for itself, because
 * caching a "cannot be reached" here would outlive the truth of it.
 */
class CordnGroupDraft : ViewModel() {
    var name by mutableStateOf("")
    var description by mutableStateOf("")

    /**
     * Who the group is for, in the order they were added.
     *
     * Ordered rather than a set so the roster does not reshuffle under the
     * user's finger as they build it, and so the invitations go out in the
     * order they were asked for.
     */
    var roster by mutableStateOf<List<HexKey>>(emptyList())
        private set

    /**
     * Members who may also add and remove, NOT counting the creator.
     *
     * The creator is added back when the metadata is built: `spec/01.md` §5.3
     * makes a non-empty admin list permanent, so one that left out the person
     * creating the group would produce a group nobody present could administer.
     */
    var coAdmins by mutableStateOf<Set<HexKey>>(emptySet())
        private set

    /** Empty `admin_pubkeys`, which §5.3 makes egalitarian permanently. */
    var egalitarian by mutableStateOf(false)

    /** The chosen coordinator, or null while the manual fields are in use. */
    var selected by mutableStateOf<HexKey?>(null)

    /**
     * Whether the user chose the coordinator themselves.
     *
     * Once they have, the best-covering one stops being offered: a selection
     * that moved on its own after the person had made one would be the screen
     * overruling them.
     */
    var userPicked by mutableStateOf(false)

    var pubKeyInput by mutableStateOf("")
    var relaysInput by mutableStateOf("")

    /**
     * How the coordinator section was left.
     *
     * Here rather than in the screen because the screen does not survive a
     * trip to a profile, and the coordinator rows are the one place on this
     * form you can tap through to one. Coming back to a collapsed picker with
     * the discovery thrown away reads as having lost your work, and the
     * discovery is a ~30-second round trip to every relay -- the most
     * expensive thing on the page to have to do twice.
     */
    var coordinatorOpen by mutableStateOf(false)

    /** The last discovery run, kept so returning does not mean running it again. */
    var discovered by mutableStateOf<CordnCoordinatorDiscovery.Result?>(null)

    var showStale by mutableStateOf(false)
    var showAllLive by mutableStateOf(false)

    fun add(pubKey: HexKey) {
        if (pubKey !in roster) roster = roster + pubKey
    }

    fun remove(pubKey: HexKey) {
        roster = roster - pubKey
        coAdmins = coAdmins - pubKey
    }

    fun toggleAdmin(pubKey: HexKey) {
        coAdmins = if (pubKey in coAdmins) coAdmins - pubKey else coAdmins + pubKey
    }

    /**
     * `admin_pubkeys` for the group about to be created.
     *
     * Empty in egalitarian mode and the creator plus their choices otherwise --
     * never the choices alone, for the reason on [coAdmins].
     */
    fun adminPubKeys(creator: HexKey): List<HexKey> = CordnGroupMetadata.adminsForNewGroup(creator, coAdmins, egalitarian)

    /** Forgets the draft, so the next new group does not start as this one. */
    fun clear() {
        name = ""
        description = ""
        roster = emptyList()
        coAdmins = emptySet()
        egalitarian = false
        selected = null
        userPicked = false
        pubKeyInput = ""
        relaysInput = ""
        coordinatorOpen = false
        discovered = null
        showStale = false
        showAllLive = false
    }
}
