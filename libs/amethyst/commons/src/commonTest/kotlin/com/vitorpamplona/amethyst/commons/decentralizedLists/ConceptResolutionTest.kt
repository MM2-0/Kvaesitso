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
package com.vitorpamplona.amethyst.commons.decentralizedLists

import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Brainstorm ADR 0004: which definition governs, never by timestamp, and whether it agrees. */
class ConceptResolutionTest {
    private val avi = "b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450"
    private val reader = "1".repeat(64)
    private val assistant = "2".repeat(64)
    private val community = "39998:$avi:github-accounts"

    private val communityHeader = dListHeaderView(arrayOf(arrayOf("d", "github-accounts"), arrayOf("required", "github-username")), "github-accounts")

    private fun copy(vararg extra: Array<String>) = dListHeaderView(arrayOf(arrayOf("d", "github-accounts"), arrayOf("b", community, "pointer"), arrayOf("required", "github-username"), *extra), "github-accounts")

    @Test
    fun copiesAreLookedForAtTheCommunitySlug() {
        val candidates = copyCandidates(community, reader, assistant)

        assertEquals(
            listOf(
                DefinitionSource.PERSONAL to Address(39998, reader, "github-accounts"),
                DefinitionSource.ASSISTANT to Address(39998, assistant, "github-accounts"),
            ),
            candidates,
        )
        // The community's own author has no copy of it, and a 9998 has no slug to look under.
        assertEquals(listOf(DefinitionSource.ASSISTANT to Address(39998, assistant, "github-accounts")), copyCandidates(community, avi, assistant))
        assertEquals(emptyList(), copyCandidates("9998:$avi:x", reader, null))
    }

    @Test
    fun theReadersCopyGovernsThenTheAssistantsThenTheCommunity() {
        val mine = copy(arrayOf("optional", "description"))
        val theirs = copy()

        val personal = resolveConcept(community, communityHeader, communityHeader, listOf(DefinitionSource.PERSONAL to mine, DefinitionSource.ASSISTANT to theirs))!!
        assertEquals(DefinitionSource.PERSONAL, personal.source)
        assertEquals(DefinitionAgreement.DIFFERS, personal.agreement)
        assertEquals(listOf(DefinitionDifference.FIELDS), personal.differences)

        val assistantCopy = resolveConcept(community, communityHeader, communityHeader, listOf(DefinitionSource.PERSONAL to null, DefinitionSource.ASSISTANT to theirs))!!
        assertEquals(DefinitionSource.ASSISTANT, assistantCopy.source)
        assertEquals(DefinitionAgreement.AGREES, assistantCopy.agreement)

        val none = resolveConcept(community, communityHeader, communityHeader, emptyList())!!
        assertEquals(DefinitionSource.COMMUNITY, none.source)
        assertEquals(DefinitionAgreement.NO_LOCAL_COPY, none.agreement)
        assertEquals(community, none.communityCoordinate)
    }

    @Test
    fun aHeaderThatOnlySharesTheSlugIsNotACopy() {
        val lookalike = dListHeaderView(arrayOf(arrayOf("d", "github-accounts"), arrayOf("required", "handle")), "github-accounts")

        val resolved = resolveConcept(community, communityHeader, communityHeader, listOf(DefinitionSource.PERSONAL to lookalike))!!

        assertEquals(DefinitionSource.COMMUNITY, resolved.source)
    }

    @Test
    fun anItemFiledUnderACopyStillReachesItsCommunityConcept() {
        val listedCoordinate = "39998:$assistant:github-accounts"

        // The community header could not be read: the lister's copy draws, and the tier-three key survives.
        val resolved = resolveConcept(listedCoordinate, copy(), null, emptyList())!!

        assertEquals(DefinitionSource.LISTED, resolved.source)
        assertEquals(DefinitionAgreement.UNKNOWN, resolved.agreement)
        assertEquals(community, resolved.communityCoordinate)
        assertNull(resolveConcept(null, null, null, emptyList()))
    }
}
