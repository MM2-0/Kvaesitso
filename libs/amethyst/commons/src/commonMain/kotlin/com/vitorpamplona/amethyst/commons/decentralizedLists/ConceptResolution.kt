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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Which definition of a concept governs what the reader sees (Brainstorm ADR 0004,
 * `lib/conceptResolution.ts`). An item names a header with `z`; that header is either a
 * **community concept** or someone's **copy** of one (`["b", <community coordinate>, …]`).
 * Renderers never read the community header straight: they get the governing definition, the
 * first of
 *
 * 1. the reader's own copy (their key),
 * 2. their Tapestry Assistant's copy (the assistant their kind 10040 names),
 * 3. the community header itself, else the header the item named.
 *
 * Never by timestamp across authors: a stale assistant must not shadow a deliberate edit. A
 * header counts as a copy only when it points at the community concept; sharing its `d` is not
 * enough. Brainstorm adds a "house" copy between 2 and 3; that is Brainstorm's own account, so
 * there is none here.
 */
enum class DefinitionSource { PERSONAL, ASSISTANT, COMMUNITY, LISTED }

/** What a copy changed from the community concept. */
enum class DefinitionDifference { NAMES, DESCRIPTION, FIELDS, DISPLAY, LINKS }

/**
 * `NO_LOCAL_COPY`: the community header governs because nobody's copy applies.
 * `UNKNOWN`: a copy governs but the community header couldn't be read to compare.
 */
enum class DefinitionAgreement { AGREES, DIFFERS, NO_LOCAL_COPY, UNKNOWN }

@Immutable
data class ResolvedConcept(
    val governing: DListHeaderView,
    val community: DListHeaderView?,
    /** The community concept's coordinate: what a custom view (tier three) is keyed by. */
    val communityCoordinate: String?,
    val source: DefinitionSource,
    val agreement: DefinitionAgreement,
    val differences: List<DefinitionDifference>,
)

/**
 * The community concept a header stands for: the first coordinate its `b` points at, unless that
 * is itself (a canonical concept points at itself), else the header's own coordinate.
 */
fun communityCoordinateOf(
    headerCoordinate: String?,
    header: DListHeaderView,
): String? = header.pointers.firstOrNull { it != headerCoordinate && Address.parse(it)?.kind == AddressableListHeaderEvent.KIND } ?: headerCoordinate

/**
 * Where the reader's and their assistant's copies would be: the spec keys them on the community
 * concept's slug (`39998:<user>:<d>`, `assistant-designation.md`). Empty for a header that has no
 * coordinate (a 9998).
 */
fun copyCandidates(
    community: String?,
    reader: HexKey?,
    assistant: HexKey?,
): List<Pair<DefinitionSource, Address>> {
    val address = community?.let { Address.parse(it) } ?: return emptyList()
    if (address.kind != AddressableListHeaderEvent.KIND) return emptyList()
    return buildList {
        if (reader != null && reader != address.pubKeyHex) add(DefinitionSource.PERSONAL to Address(address.kind, reader, address.dTag))
        if (assistant != null && assistant != reader && assistant != address.pubKeyHex) add(DefinitionSource.ASSISTANT to Address(address.kind, assistant, address.dTag))
    }
}

/** What a copy changed. A field is its name, requirement and type; its description is wording within it. */
fun differencesBetween(
    copy: DListHeaderView,
    community: DListHeaderView,
): List<DefinitionDifference> =
    buildList {
        if (copy.singular != community.singular || copy.plural != community.plural) add(DefinitionDifference.NAMES)
        if ((copy.description ?: "") != (community.description ?: "")) add(DefinitionDifference.DESCRIPTION)
        if (copy.fields.map { Triple(it.name, it.requirement, it.type) } != community.fields.map { Triple(it.name, it.requirement, it.type) }) add(DefinitionDifference.FIELDS)
        if (copy.hints != community.hints || copy.image != community.image) add(DefinitionDifference.DISPLAY)
        if (copy.links != community.links) add(DefinitionDifference.LINKS)
    }

/**
 * The governing definition for one item. [listed] is the header the item's `z` names, with its
 * coordinate; [community] the community header when it was read (it is [listed] when the item
 * sits straight on the community list); [copies] the reader's and assistant's headers found at
 * [copyCandidates], in precedence order. Null when nothing could be read.
 */
fun resolveConcept(
    listedCoordinate: String?,
    listed: DListHeaderView?,
    community: DListHeaderView?,
    copies: List<Pair<DefinitionSource, DListHeaderView?>>,
): ResolvedConcept? {
    val communityCoordinate = listed?.let { communityCoordinateOf(listedCoordinate, it) } ?: listedCoordinate
    val copy = communityCoordinate?.let { coord -> copies.firstOrNull { (_, header) -> header != null && header.pointsAt(coord) } }
    if (copy != null) {
        val governing = copy.second!!
        val differences = community?.let { differencesBetween(governing, it) } ?: emptyList()
        return ResolvedConcept(
            governing = governing,
            community = community,
            communityCoordinate = communityCoordinate,
            source = copy.first,
            agreement =
                when {
                    community == null -> DefinitionAgreement.UNKNOWN
                    differences.isEmpty() -> DefinitionAgreement.AGREES
                    else -> DefinitionAgreement.DIFFERS
                },
            differences = differences,
        )
    }
    if (community != null) {
        return ResolvedConcept(community, community, communityCoordinate, DefinitionSource.COMMUNITY, DefinitionAgreement.NO_LOCAL_COPY, emptyList())
    }
    // The community header couldn't be read: the lister's own header is all there is.
    if (listed != null) {
        return ResolvedConcept(listed, null, communityCoordinate, DefinitionSource.LISTED, DefinitionAgreement.UNKNOWN, emptyList())
    }
    return null
}
