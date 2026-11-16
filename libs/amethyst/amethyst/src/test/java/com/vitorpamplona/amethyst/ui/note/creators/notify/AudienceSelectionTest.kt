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
package com.vitorpamplona.amethyst.ui.note.creators.notify

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.amethyst.commons.model.composer.AudienceCap
import com.vitorpamplona.amethyst.commons.model.composer.AudienceList
import com.vitorpamplona.amethyst.commons.model.composer.AudienceListKind
import com.vitorpamplona.amethyst.commons.model.composer.AudienceSelection
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide who a bulk add would actually put in a note's audience.
 *
 * These matter more than they look: a private note is gift-wrapped once per
 * recipient, and the recipient list is readable by every recipient — so
 * "selected by default" is a privacy and a cost decision, not a convenience.
 */
class AudienceSelectionTest {
    // User pins a few addressable note shells on construction; empty shells are
    // enough here since none of these rules read them.
    private val noContext = UserContext { addr -> AddressableNote(addr) }

    private fun user(hex: String) = User(hex, noContext)

    private val alice = user("aa".repeat(32))
    private val bruno = user("bb".repeat(32))
    private val carla = user("cc".repeat(32))
    private val dev = user("dd".repeat(32))

    private fun listOfPeople(
        public: List<User> = emptyList(),
        private: List<User> = emptyList(),
    ) = AudienceList(
        id = "close-friends",
        kind = AudienceListKind.PEOPLE_LIST,
        title = "Close friends",
        publicMembers = persistentListOf(*public.toTypedArray()),
        privateMembers = persistentListOf(*private.toTypedArray()),
    )

    private fun members(
        list: AudienceList,
        alreadyIn: Set<String> = emptySet(),
        hidden: Set<String> = emptySet(),
    ) = AudienceSelection.buildMembers(list, alreadyIn, hidden)

    @Test
    fun ordinaryMembersStartSelected() {
        val rows = members(listOfPeople(public = listOf(alice, bruno)))

        assertEquals(setOf(alice.pubkeyHex, bruno.pubkeyHex), AudienceSelection.defaultSelection(rows))
    }

    @Test
    fun privateMembersStartDeselected() {
        val rows = members(listOfPeople(public = listOf(alice), private = listOf(carla)))

        // Adding a private member publishes their pubkey to every other
        // recipient, so it has to be a deliberate tap.
        assertEquals(setOf(alice.pubkeyHex), AudienceSelection.defaultSelection(rows))
        assertTrue(rows.first { it.pubkeyHex == carla.pubkeyHex }.isPrivateMember)
    }

    @Test
    fun mutedPeopleStartDeselected() {
        val rows = members(listOfPeople(public = listOf(alice, bruno)), hidden = setOf(bruno.pubkeyHex))

        assertEquals(setOf(alice.pubkeyHex), AudienceSelection.defaultSelection(rows))
    }

    @Test
    fun alreadyAddedPeopleAreSelectedButNeverReAdded() {
        val rows = members(listOfPeople(public = listOf(alice, bruno)), alreadyIn = setOf(alice.pubkeyHex))
        val selection = AudienceSelection.defaultSelection(rows)

        // Alice counts in the header so the number tells the truth...
        assertTrue(alice.pubkeyHex in selection)
        // ...but confirming only adds Bruno.
        assertEquals(listOf(bruno.pubkeyHex), AudienceSelection.pendingAdditions(rows, selection).map { it.pubkeyHex })
        // and select-all must not be able to turn her off.
        assertFalse(alice.pubkeyHex in AudienceSelection.toggleableIds(rows))
    }

    @Test
    fun peopleInBothMemberSetsAppearOnce() {
        val rows = members(listOfPeople(public = listOf(alice, bruno), private = listOf(alice)))

        assertEquals(2, rows.size)
    }

    @Test
    fun someoneInBothHalvesOfAListIsNotTreatedAsPrivate() {
        // Carla is in the encrypted half but also publicly listed, so adding her
        // discloses nothing new. Warning about her would be noise that trains the
        // user to ignore the badge that matters.
        val rows = members(listOfPeople(public = listOf(alice, carla), private = listOf(carla)))
        val carlaRow = rows.first { it.pubkeyHex == carla.pubkeyHex }

        assertFalse(carlaRow.isPrivateMember)
        assertTrue(carla.pubkeyHex in AudienceSelection.defaultSelection(rows))
    }

    @Test
    fun aListBiggerThanTheHardCapOpensWithNothingNewSelected() {
        // Selecting all of an oversized list would land the review in a state the
        // confirm button refuses, and the only way out would be ~100 individual
        // taps. Start empty instead.
        val crowd = (1..AudienceSelection.HARD_CAP + 5).map { user("%064x".format(it)) }
        val rows = members(listOfPeople(public = crowd))

        assertTrue(AudienceSelection.defaultSelection(rows, currentAudienceSize = 0).isEmpty())
        // The same list is fine once it fits.
        assertEquals(3, AudienceSelection.defaultSelection(members(listOfPeople(public = crowd.take(3)))).size)
    }

    @Test
    fun anOversizedListStillShowsWhoIsAlreadyThere() {
        val crowd = (1..AudienceSelection.HARD_CAP + 5).map { user("%064x".format(it)) }
        val rows = members(listOfPeople(public = crowd), alreadyIn = setOf(crowd[0].pubkeyHex))

        assertEquals(setOf(crowd[0].pubkeyHex), AudienceSelection.defaultSelection(rows))
    }

    @Test
    fun capsDiscloseThenRefuse() {
        assertEquals(AudienceCap.Fine, AudienceSelection.capFor(0, AudienceSelection.SOFT_CAP))
        assertEquals(
            AudienceCap.OverSoft(AudienceSelection.SOFT_CAP + 1),
            AudienceSelection.capFor(1, AudienceSelection.SOFT_CAP),
        )
        assertEquals(
            AudienceCap.OverHard(AudienceSelection.HARD_CAP + 1),
            AudienceSelection.capFor(1, AudienceSelection.HARD_CAP),
        )
    }

    @Test
    fun capCountsTheAudienceAlreadyInTheComposer() {
        // 20 already p-tagged plus 10 more is over the soft cap even though
        // neither number is on its own.
        assertEquals(AudienceCap.OverSoft(30), AudienceSelection.capFor(20, 10))
    }

    @Test
    fun addingDedupesAgainstWhoIsAlreadyThere() {
        val addition = AudienceSelection.addToAudience(listOf(alice), listOf(alice, bruno), emptyMap(), null)

        assertEquals(listOf(bruno.pubkeyHex), addition.newcomers.map { it.pubkeyHex })
    }

    @Test
    fun addingRecordsProvenanceOnlyForPeopleTheAddIntroduced() {
        // Alice is already p-tagged — say she is the author of the note being
        // replied to. A list that happens to contain her must NOT claim her, or
        // removing that list's chip would drop her from the reply's p tags.
        val addition =
            AudienceSelection.addToAudience(
                current = listOf(alice),
                incoming = listOf(alice, bruno),
                provenance = emptyMap(),
                fromListTag = "close-friends",
            )

        assertFalse(alice.pubkeyHex in addition.provenance)
        assertEquals(setOf("close-friends"), addition.provenance[bruno.pubkeyHex])

        // ...so undoing the list leaves Alice exactly where she was.
        val removal = AudienceSelection.removeListFromProvenance(addition.provenance, "close-friends")
        assertEquals(setOf(bruno.pubkeyHex), removal.orphaned)
        assertFalse(alice.pubkeyHex in removal.orphaned)
    }

    @Test
    fun aListThatUnMutesSomebodyClaimsThemToo() {
        // Bruno is in pTags but muted, so he is not in the audience. The list
        // un-mutes him, which genuinely changes the outcome — so undoing the list
        // has to be able to take him back out again.
        val addition =
            AudienceSelection.addToAudience(
                current = listOf(alice, bruno),
                incoming = listOf(bruno),
                provenance = emptyMap(),
                fromListTag = "close-friends",
                currentlyMuted = setOf(bruno.pubkeyHex),
            )

        assertTrue(addition.newcomers.isEmpty())
        assertEquals(setOf(bruno.pubkeyHex), addition.unmutes)
        assertEquals(setOf("close-friends"), addition.provenance[bruno.pubkeyHex])

        val removal = AudienceSelection.removeListFromProvenance(addition.provenance, "close-friends")
        assertEquals(setOf(bruno.pubkeyHex), removal.orphaned)
    }

    @Test
    fun anUnmutedPersonAlreadyInTheAudienceIsNotClaimed() {
        val addition =
            AudienceSelection.addToAudience(
                current = listOf(alice),
                incoming = listOf(alice),
                provenance = emptyMap(),
                fromListTag = "close-friends",
                currentlyMuted = emptySet(),
            )

        assertTrue(addition.unmutes.isEmpty())
        assertTrue(addition.provenance.isEmpty())
    }

    @Test
    fun addingTheSameListTwiceDoesNotDuplicateProvenance() {
        val first = AudienceSelection.addToAudience(emptyList(), listOf(alice), emptyMap(), "work")
        val second = AudienceSelection.addToAudience(listOf(alice), listOf(alice), first.provenance, "work")

        assertTrue(second.newcomers.isEmpty())
        assertEquals(setOf("work"), second.provenance[alice.pubkeyHex])
    }

    @Test
    fun addingWithoutAListRecordsNoProvenance() {
        val addition = AudienceSelection.addToAudience(emptyList(), listOf(alice), emptyMap(), null)

        assertEquals(listOf(alice.pubkeyHex), addition.newcomers.map { it.pubkeyHex })
        assertTrue(addition.provenance.isEmpty())
    }

    @Test
    fun removingAListOnlyOrphansPeopleThatListAloneBroughtIn() {
        val provenance =
            mapOf(
                alice.pubkeyHex to setOf("close-friends"),
                bruno.pubkeyHex to setOf("close-friends", "work"),
                carla.pubkeyHex to setOf("work"),
            )

        val removal = AudienceSelection.removeListFromProvenance(provenance, "close-friends")

        // Alice came only from the removed list, so she goes.
        assertEquals(setOf(alice.pubkeyHex), removal.orphaned)
        // Bruno is also in Work, so he stays — with Work as his remaining source.
        assertEquals(setOf("work"), removal.provenance[bruno.pubkeyHex])
        assertEquals(setOf("work"), removal.provenance[carla.pubkeyHex])
        assertFalse(alice.pubkeyHex in removal.provenance)
    }

    @Test
    fun removingAListThatWasNeverAddedChangesNothing() {
        val provenance = mapOf(alice.pubkeyHex to setOf("work"))

        val removal = AudienceSelection.removeListFromProvenance(provenance, "close-friends")

        assertTrue(removal.orphaned.isEmpty())
        assertEquals(provenance, removal.provenance)
    }

    @Test
    fun groupChipsCountOnlyPeopleStillInTheAudience() {
        val provenance =
            mapOf(
                alice.pubkeyHex to setOf("close-friends"),
                bruno.pubkeyHex to setOf("close-friends"),
                dev.pubkeyHex to setOf("close-friends"),
            )
        val lists = listOf(listOfPeople(public = listOf(alice, bruno, dev)))

        // Dev was removed from the audience by hand; the chip must say 2, not 3.
        val chips =
            AudienceSelection.activeGroupChips(
                provenance = provenance,
                audience = setOf(alice.pubkeyHex, bruno.pubkeyHex),
                lists = lists,
            )

        assertEquals(1, chips.size)
        assertEquals("Close friends", chips.single().title)
        assertEquals(2, chips.single().count)
    }

    @Test
    fun groupChipsSkipListsThatNoLongerExist() {
        val chips =
            AudienceSelection.activeGroupChips(
                provenance = mapOf(alice.pubkeyHex to setOf("deleted-list")),
                audience = setOf(alice.pubkeyHex),
                lists = emptyList(),
            )

        assertTrue(chips.isEmpty())
    }

    @Test
    fun listTitleSearchIsCaseInsensitiveAndBlankMatchesEverything() {
        val list = listOfPeople(public = listOf(alice))

        assertTrue(list.matches(""))
        assertTrue(list.matches("  "))
        assertTrue(list.matches("CLOSE"))
        assertTrue(list.matches(" friends "))
        assertFalse(list.matches("work"))
    }
}
