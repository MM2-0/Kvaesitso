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
package com.vitorpamplona.amethyst.commons.decentralizedLists.tiers

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListHeaderView
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListItemView
import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * Tier three (Brainstorm ADR 0007): the community concepts that have a view of their own, keyed by
 * the community concept's coordinate, the far end of the resolved chain, so the reader's copy,
 * their assistant's and the lister's all open the same view. A view reads fields only through the
 * governing definition; when the definition doesn't give it what it needs ([applies]), the item
 * is drawn the tier-two way. Take an entry out and its items read as their header's hints say.
 */
enum class DListConceptView(
    val community: String,
) {
    GITHUB_ACCOUNT("39998:b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450:github-accounts"),
    PLACE("39998:b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450:food-and-drink-places"),
    BOOK("39998:6aca05b812da97601151776d13de04ae71afc9d86da1408f0e72cffef72ece4b:books"),
    ;

    /** Whether the governing definition and this item give the view what it draws. */
    fun applies(
        item: TagArray,
        definition: DListHeaderView,
    ): Boolean =
        when (this) {
            GITHUB_ACCOUNT -> githubAccountOf(item, definition) != null
            PLACE -> placeOf(item, definition) != null
            BOOK -> bookOf(item) != null
        }

    /**
     * How the item reads where it is one among many (a list's rows, a quote), given how the
     * definition's hints read it. Still the definition's fields; nothing is fetched.
     */
    fun present(
        item: TagArray,
        definition: DListHeaderView,
        shown: DListItemView,
    ): DListItemView =
        when (this) {
            GITHUB_ACCOUNT -> githubPresentation(item, definition, shown)
            PLACE -> shown
            BOOK -> bookPresentation(item, shown)
        }

    companion object {
        private val byCommunity = entries.associateBy { it.community }

        fun of(communityCoordinate: String?): DListConceptView? = communityCoordinate?.let { byCommunity[it] }
    }
}

/** The view an item gets, or null for the tier-two card. */
fun conceptViewFor(
    communityCoordinate: String?,
    item: TagArray,
    definition: DListHeaderView,
): DListConceptView? = DListConceptView.of(communityCoordinate)?.takeIf { it.applies(item, definition) }

/** One value of a declared field: null when the definition doesn't declare it or the item leaves it empty. */
internal fun TagArray.declaredValue(
    definition: DListHeaderView,
    name: String,
): String? {
    if (definition.field(name) == null) return null
    return firstOrNull { it.size > 1 && it[0] == name && it[1].isNotBlank() }?.get(1)?.trim()?.ifEmpty { null }
}

@Immutable
data class GitHubAccount(
    val handle: String,
    val byline: String?,
) {
    /** GitHub serves an account's avatar at its handle. */
    val avatarUrl get() = "https://github.com/$handle.png?size=160"
}

const val GITHUB_HANDLE_FIELD = "github-username"
const val GITHUB_BYLINE_FIELD = "description"

private val GITHUB_HANDLE = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")

/**
 * The account the item names, read through the definition: null when the definition doesn't
 * declare the handle field, the item doesn't fill it, or it isn't a GitHub handle (which keeps the
 * avatar's address to github.com/<handle>).
 */
fun githubAccountOf(
    item: TagArray,
    definition: DListHeaderView,
): GitHubAccount? {
    val handle = item.declaredValue(definition, GITHUB_HANDLE_FIELD)?.removePrefix("@") ?: return null
    if (!GITHUB_HANDLE.matches(handle)) return null
    return GitHubAccount(handle, item.declaredValue(definition, GITHUB_BYLINE_FIELD))
}

/** A GitHub account among many: the handle first, its own avatar, the description as the line. */
fun githubPresentation(
    item: TagArray,
    definition: DListHeaderView,
    shown: DListItemView,
): DListItemView {
    val account = githubAccountOf(item, definition) ?: return shown
    val summary = account.byline ?: shown.summary?.takeIf { it != account.handle }
    return shown.copy(
        title = account.handle,
        summary = summary?.takeIf { it != account.handle },
        image = account.avatarUrl,
    )
}
