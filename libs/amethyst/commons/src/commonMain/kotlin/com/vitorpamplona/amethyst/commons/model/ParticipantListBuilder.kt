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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey

class ParticipantListBuilder {
    private fun addFollowsThatDirectlyParticipateOnToSet(
        baseNote: Note,
        followingSet: Set<HexKey>?,
        set: MutableSet<User>,
    ) {
        baseNote.author?.let { author ->
            if (author !in set && (followingSet == null || author.pubkeyHex in followingSet)) {
                set.add(author)
            }
        }

        // Breaks these searchers down to avoid the memory use of creating multiple lists
        baseNote.replies.forEach { reply ->
            reply.author?.let { author ->
                if (author !in set && (followingSet == null || author.pubkeyHex in followingSet)) {
                    set.add(author)
                }
            }
        }

        baseNote.boosts.forEach { boost ->
            boost.author?.let { author ->
                if (author !in set && (followingSet == null || author.pubkeyHex in followingSet)) {
                    set.add(author)
                }
            }
        }

        baseNote.zaps.forEach { zapPair ->
            zapPair.key.author?.let { author ->
                if (author !in set && (followingSet == null || author.pubkeyHex in followingSet)) {
                    set.add(author)
                }
            }
        }

        baseNote.reactions.forEach { reactionSet ->
            reactionSet.value.forEach { reaction ->
                reaction.author?.let { author ->
                    if (author !in set && (followingSet == null || author.pubkeyHex in followingSet)) {
                        set.add(author)
                    }
                }
            }
        }
    }

    fun followsThatParticipateOnDirect(
        baseNote: Note?,
        followingSet: Set<HexKey>?,
    ): Set<User> {
        if (baseNote == null) return mutableSetOf()

        val set = mutableSetOf<User>()
        addFollowsThatDirectlyParticipateOnToSet(baseNote, followingSet, set)
        return set
    }

    fun followsThatParticipateOn(
        baseNote: Note?,
        followingSet: Set<HexKey>?,
    ): Set<User> {
        if (baseNote == null) return mutableSetOf()

        val mySet = mutableSetOf<User>()
        addFollowsThatDirectlyParticipateOnToSet(baseNote, followingSet, mySet)

        baseNote.replies.forEach { addFollowsThatDirectlyParticipateOnToSet(it, followingSet, mySet) }

        baseNote.boosts.forEach {
            it.replyTo?.forEach { addFollowsThatDirectlyParticipateOnToSet(it, followingSet, mySet) }
        }

        LocalCache.getPublicChatChannelIfExists(baseNote.idHex)?.notes?.forEach { _, it ->
            addFollowsThatDirectlyParticipateOnToSet(it, followingSet, mySet)
        }

        return mySet
    }

    fun countFollowsThatParticipateOn(
        baseNote: Note?,
        followingSet: Set<HexKey>?,
    ): Int {
        if (baseNote == null) return 0

        val list = followsThatParticipateOn(baseNote, followingSet)

        return list.size
    }
}
