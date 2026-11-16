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
package com.vitorpamplona.amethyst.commons.wot

import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The order lists of people take for [account]: the ones they follow ([follows]) first, then the
 * Web of Trust provider's highest scores, then everyone else. [then] breaks ties inside each
 * group; by default the sort is stable and keeps the list's own order there.
 *
 * Follows and scores are read once per person before the sort, so a long list doesn't look
 * anyone up on every comparison.
 */
fun <T> List<T>.sortedByFollowsThenTrust(
    account: IAccount,
    pubkeyOf: (T) -> HexKey,
    then: Comparator<T> = Comparator { _, _ -> 0 },
    /** The kind 3 by default; lists that put anyone the user knows first pass the wider set. */
    follows: Set<HexKey> = account.followingKeySet(),
): List<T> {
    if (size < 2) return this
    val keys =
        associateWith {
            val pubkey = pubkeyOf(it)
            // Negated so ascending order puts follows, then high scores, first.
            if (pubkey in follows) FOLLOWED else -(account.trustRankOf(pubkey) ?: 0)
        }
    return sortedWith(compareBy<T> { keys[it] ?: 0 }.then(then))
}

fun List<User>.sortedByFollowsThenTrust(
    account: IAccount,
    then: Comparator<User> = Comparator { _, _ -> 0 },
    follows: Set<HexKey> = account.followingKeySet(),
): List<User> = sortedByFollowsThenTrust(account, User::pubkeyHex, then, follows)

fun List<HexKey>.sortedKeysByFollowsThenTrust(
    account: IAccount,
    follows: Set<HexKey> = account.followingKeySet(),
): List<HexKey> = sortedByFollowsThenTrust(account, { it }, follows = follows)

/** Below any score (scores are 1..100), so follows lead. */
private const val FOLLOWED = Int.MIN_VALUE
