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
package com.vitorpamplona.amethyst.commons.feeds

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.drss.DrssTree
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip51Lists.interestSets.InterestSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.drss.feedList.DrssFeedListEvent
import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.interestSet.InterestSetEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinition as CustomFeedDefinition

@Stable
class TopNavFilterState(
    val account: Account,
    val scope: CoroutineScope,
) {
    val allFollows =
        FeedDefinition(
            code = TopFilter.AllFollows,
            name = LabelName(TopNavLabel.ALL_FOLLOWS),
        )

    val userFollows =
        FeedDefinition(
            code = TopFilter.AllUserFollows,
            name = LabelName(TopNavLabel.ALL_USER_FOLLOWS),
        )

    val kind3Follows =
        FeedDefinition(
            code = TopFilter.DefaultFollows,
            name = LabelName(TopNavLabel.DEFAULT_FOLLOWS),
        )

    val globalFollow =
        FeedDefinition(
            code = TopFilter.Global,
            name = LabelName(TopNavLabel.GLOBAL),
        )

    // Notifications-only curated mode; in Notifications, Global itself shows
    // every event that p-tags the user.
    val selectedFollow =
        FeedDefinition(
            code = TopFilter.Selected,
            name = LabelName(TopNavLabel.CURATED),
        )

    val aroundMe =
        FeedDefinition(
            code = TopFilter.AroundMe,
            name = LabelName(TopNavLabel.AROUND_ME),
        )

    // A UI-only entry: selecting it opens the map picker (handled in FeedFilterSpinner)
    // and applies the chosen place as a TopFilter.Geohash for this screen's feed.
    val teleport =
        FeedDefinition(
            code = TopFilter.TeleportPicker,
            name = LabelName(TopNavLabel.TELEPORT),
        )

    val muteListFollow =
        FeedDefinition(
            code = TopFilter.MuteList(account.muteList.getMuteListAddress()),
            name = LabelName(TopNavLabel.MUTE_LIST),
        )

    val mineFollow =
        FeedDefinition(
            code = TopFilter.Mine,
            name = LabelName(TopNavLabel.MINE),
        )

    val allFavoriteAlgoFeedsFollow =
        FeedDefinition(
            code = TopFilter.AllFavoriteAlgoFeeds,
            name = LabelName(TopNavLabel.ALL_FAVORITE_DVMS),
        )

    val defaultLists = persistentListOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, muteListFollow)

    val defaultNotificationLists = persistentListOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, selectedFollow, globalFollow, muteListFollow)

    fun mergePeopleLists(
        peopleLists: List<AddressableNote>,
        followLists: List<AddressableNote>,
        favoriteFollowSets: List<AddressableNote> = emptyList(),
        drssLists: List<AddressableNote> = emptyList(),
    ): List<FeedDefinition> {
        val peopleListsDefs =
            peopleLists.map {
                FeedDefinition(
                    TopFilter.PeopleList(it.address),
                    PeopleListName(it),
                )
            }

        val followListsDefs =
            followLists.map {
                FeedDefinition(
                    TopFilter.PeopleList(it.address),
                    PeopleListName(it),
                )
            }

        // NIP-51 kind 10021: follow sets the user favorited, including other people's. The
        // user's own sets are already listed above, so those are not repeated.
        val listed = peopleLists.mapTo(HashSet()) { it.address }
        val favoriteDefs =
            favoriteFollowSets.mapNotNull {
                if (it.address in listed) {
                    null
                } else {
                    FeedDefinition(
                        TopFilter.PeopleList(it.address),
                        PeopleListName(it),
                    )
                }
            }

        // drss.io's folders and feed lists: a river of everyone in them.
        val drssDefs =
            drssLists.map {
                FeedDefinition(
                    TopFilter.PeopleList(it.address),
                    PeopleListName(it),
                )
            }

        return (peopleListsDefs + followListsDefs + favoriteDefs + drssDefs).sortedBy { it.name.name() }
    }

    val livePeopleListsFlow: Flow<List<FeedDefinition>> =
        combine(
            account.followSets.peopleListNotes,
            account.starterPacks.starterPackNotes,
            account.favoriteFollowSetsList.flowNotes,
            account.drss.folderAndFeedListNotes,
            ::mergePeopleLists,
        ).onStart {
            emit(
                mergePeopleLists(
                    account.followSets.peopleListNotes.value,
                    account.starterPacks.starterPackNotes.value,
                    account.favoriteFollowSetsList.flowNotes.value,
                    account.drss.folderAndFeedListNotes.value,
                ),
            )
        }

    fun mergeInterests(
        hashtagList: Set<String>,
        geotagList: Set<String>,
        communityList: List<AddressableNote>,
        relayList: Set<NormalizedRelayUrl>,
        favoriteAlgoFeedsList: List<AddressableNote>,
        interestSetList: List<InterestSet>,
    ): List<FeedDefinition> {
        val hashtags =
            hashtagList.map {
                FeedDefinition(
                    TopFilter.Hashtag(it),
                    HashtagName(it),
                )
            }

        val geotags =
            geotagList.map {
                FeedDefinition(
                    TopFilter.Geohash(it),
                    GeoHashName(it),
                )
            }

        val communities =
            communityList.map { communityNote ->
                FeedDefinition(
                    TopFilter.Community(communityNote.address),
                    CommunityName(communityNote),
                )
            }

        val relays =
            relayList.map { relayUrl ->
                FeedDefinition(
                    TopFilter.Relay(relayUrl.url),
                    RelayName(relayUrl),
                )
            }

        // Favorites can only be added through FavoriteAlgoFeedToggle, which itself checks
        // that the AppDefinitionEvent advertises kind 5300. Don't re-check here: on
        // cold start the AppDefinitionEvent may not be in cache yet, and dropping
        // the entry means the persisted TopFilter.FavoriteAlgoFeed can't find its chip
        // in the spinner (user sees "Select an option" while the banner fires the
        // RPC — the bug we had before this change).
        val favoriteAlgoFeeds =
            favoriteAlgoFeedsList.map { feedNote ->
                FeedDefinition(
                    TopFilter.FavoriteAlgoFeed(feedNote.address),
                    FavoriteAlgoFeedName(feedNote),
                )
            }

        // Only show the "All favorite algo feeds" meta-chip when there is at least one
        // real favorite to merge; otherwise the chip opens to an empty feed.
        val allFavorites =
            if (favoriteAlgoFeeds.isNotEmpty()) listOf(allFavoriteAlgoFeedsFollow) else emptyList()

        val interestSets =
            interestSetList.map { set ->
                FeedDefinition(
                    TopFilter.InterestSet(InterestSetEvent.createAddress(account.signer.pubKey, set.identifier)),
                    InterestSetName(set),
                )
            }

        return (communities + hashtags + geotags + relays + allFavorites + favoriteAlgoFeeds + interestSets).sortedBy { it.name.name() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveInterestFlows: Flow<List<FeedDefinition>> =
        combine(
            account.interestList.flow,
            account.geohashList.flow,
            account.communityList.flowNotes,
            account.favoriteRelayList.flow,
            combine(
                account.favoriteAlgoFeedsList.flowNotes,
                account.interestSets.listFeedFlow,
                ::Pair,
            ),
        ) { hashtagList, geotagList, communityList, relayList, favAndInterest ->
            mergeInterests(hashtagList, geotagList, communityList, relayList, favAndInterest.first, favAndInterest.second)
        }.onStart {
            emit(
                mergeInterests(
                    account.interestList.flow.value,
                    account.geohashList.flow.value,
                    account.communityList.flowNotes.value,
                    account.favoriteRelayList.flow.value,
                    account.favoriteAlgoFeedsList.flowNotes.value,
                    account.interestSets.listFeedFlow.value,
                ),
            )
        }

    /**
     * The favorite relays (kind 10012) alone, for the catalogs that take no other interests but do
     * offer Global: a relay feed is everything on that relay, so it narrows any kind.
     */
    val liveFavoriteRelaysFlow: Flow<List<FeedDefinition>> =
        account.favoriteRelayList.flow
            .map { relays -> relays.map { FeedDefinition(TopFilter.Relay(it.url), RelayName(it)) }.sortedBy { it.name.name() } }
            .onStart {
                emit(
                    account.favoriteRelayList.flow.value
                        .map { FeedDefinition(TopFilter.Relay(it.url), RelayName(it)) }
                        .sortedBy { it.name.name() },
                )
            }

    private val _kind3GlobalPeopleRoutes =
        combineTransform(
            livePeopleListsFlow,
            liveInterestFlows,
        ) { peopleLists, interests ->
            emit(
                listOf(
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow),
                    peopleLists,
                    interests,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _badgeRoutes =
        combineTransform(livePeopleListsFlow, liveFavoriteRelaysFlow) { peopleLists, relays ->
            emit(
                listOf(
                    listOf(allFollows, userFollows, kind3Follows, globalFollow, mineFollow),
                    peopleLists,
                    relays,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _communityRoutes =
        combineTransform(livePeopleListsFlow, liveFavoriteRelaysFlow) { peopleLists, relays ->
            emit(
                listOf(
                    listOf(allFollows, userFollows, kind3Follows, globalFollow, mineFollow),
                    peopleLists,
                    relays,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _musicRoutes =
        combineTransform(
            livePeopleListsFlow,
            liveInterestFlows,
        ) { peopleLists, interests ->
            emit(
                listOf(
                    // Same content-style catalog as kind3GlobalPeopleRoutes, plus "Mine" so the
                    // music + playlists screens can show only the user's own published items.
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, mineFollow),
                    peopleLists,
                    interests,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _gitRepositoryRoutes =
        combineTransform(
            livePeopleListsFlow,
            liveInterestFlows,
        ) { peopleLists, interests ->
            emit(
                listOf(
                    // Git repository announcements can be narrowed by author, hashtag and geohash,
                    // so this mirrors the kind3 catalog plus "Mine" — the user's own repositories.
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, mineFollow),
                    peopleLists,
                    interests,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _workoutRoutes =
        combineTransform(
            livePeopleListsFlow,
            liveInterestFlows,
        ) { peopleLists, interests ->
            emit(
                listOf(
                    // Workout records can be narrowed by author, hashtag and geohash, so this
                    // mirrors the kind3 catalog plus "Mine" — the user's own training.
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, mineFollow),
                    peopleLists,
                    interests,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _highlightsRoutes =
        combineTransform(
            livePeopleListsFlow,
            liveInterestFlows,
        ) { peopleLists, interests ->
            emit(
                listOf(
                    // Highlights can be narrowed by author, hashtag and geohash, so this mirrors
                    // the kind3 catalog plus "Mine" — the user's own highlights.
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, mineFollow),
                    peopleLists,
                    interests,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _relayGroupsDiscoveryRoutes =
        combineTransform(
            livePeopleListsFlow,
            liveInterestFlows,
            account.relayGroupList.liveRelayGroupServers,
        ) { peopleLists, interests, joinedServers ->
            // A relay chip per host relay of every group the user joined (kind-10009), so they can
            // browse the OTHER groups on those relays without first having to Favorite them. Deduped
            // against relays that are already chips because they were favorited (kind-10012, already
            // in `interests`). Only in this catalog — other feeds (git, podcasts, …) don't want them.
            val alreadyChip = interests.mapNotNullTo(HashSet()) { (it.code as? TopFilter.Relay)?.url }
            val joinedRelayChips =
                joinedServers
                    .asSequence()
                    .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                    .filter { it.url !in alreadyChip }
                    .distinctBy { it.url }
                    .map { FeedDefinition(TopFilter.Relay(it.url), RelayName(it)) }
                    .sortedBy { it.name.name() }
                    .toList()
            emit(
                listOf(
                    // Relay-group discovery routes to relays by author, hashtag and geohash, plus
                    // favorited + joined-group relay chips; "Mine" (the joined-groups view) sits last
                    // in the base group to match the ordering every other feed uses.
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, mineFollow),
                    peopleLists,
                    interests,
                    joinedRelayChips,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _podcastRoutes =
        combineTransform(
            livePeopleListsFlow,
            liveInterestFlows,
        ) { peopleLists, interests ->
            emit(
                listOf(
                    // Same content-style catalog as kind3GlobalPeopleRoutes, plus "Mine" so the
                    // podcasts + episodes screens can show only the user's own published shows/episodes.
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, mineFollow),
                    peopleLists,
                    interests,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _kind3GlobalPeople =
        combineTransform(livePeopleListsFlow, liveFavoriteRelaysFlow) { peopleLists, relays ->
            emit(
                listOf(
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow),
                    peopleLists,
                    relays,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    // Author-only catalog for browse screens whose events carry no topical tags (no `t`/`g`/`a`),
    // e.g. the nApplet (NIP-5D) and nSite (NIP-5A) manifests. These can only be narrowed by author
    // or by relay, so the tag-based interest entries (hashtags, geohashes, communities, favorite
    // feeds, interest sets) and AroundMe — none of which can match a tag-less manifest — are
    // deliberately left out. Favorite relays stay: a relay feed is everything on that relay.
    private val _authorOnlyRoutes =
        combineTransform(livePeopleListsFlow, liveFavoriteRelaysFlow) { peopleLists, relays ->
            emit(
                listOf(
                    listOf(allFollows, userFollows, kind3Follows, globalFollow, mineFollow),
                    peopleLists,
                    relays,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _notificationLists =
        livePeopleListsFlow.transform { peopleLists ->
            emit(
                listOf(
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, selectedFollow, globalFollow),
                    peopleLists,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    val kind3GlobalPeopleRoutes =
        _kind3GlobalPeopleRoutes
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, defaultLists)

    /** Opens the screen that lists, creates, edits and deletes the user's own feeds. */
    val manageCustomFeeds =
        FeedDefinition(
            code = TopFilter.CustomFeed(""),
            name = LabelName(TopNavLabel.MANAGE_CUSTOM_FEEDS),
            route = Route.CustomFeeds,
        )

    /** The Home picker: [kind3GlobalPeopleRoutes] plus the feeds the user built. Only Home runs custom feeds. */
    val homeRoutes =
        combine(kind3GlobalPeopleRoutes, account.settings.customFeeds) { base, custom ->
            (base + custom.map { FeedDefinition(TopFilter.CustomFeed(it.id), CustomFeedName(it)) } + manageCustomFeeds).toImmutableList()
        }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, (defaultLists + manageCustomFeeds).toImmutableList())

    val kind3GlobalPeople =
        _kind3GlobalPeople
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, defaultLists)

    val authorOnlyRoutes =
        _authorOnlyRoutes
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf(allFollows, userFollows, kind3Follows, globalFollow, mineFollow, muteListFollow))

    val notificationLists =
        _notificationLists
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, defaultNotificationLists)

    val badgeRoutes =
        _badgeRoutes
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf(allFollows, userFollows, kind3Follows, globalFollow, mineFollow, muteListFollow))

    val communityRoutes =
        _communityRoutes
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf(allFollows, userFollows, kind3Follows, globalFollow, mineFollow, muteListFollow))

    val musicRoutes =
        _musicRoutes
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, mineFollow, muteListFollow))

    val gitRepositoryRoutes =
        _gitRepositoryRoutes
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, mineFollow, muteListFollow))

    val workoutRoutes =
        _workoutRoutes
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, mineFollow, muteListFollow))

    val highlightsRoutes =
        _highlightsRoutes
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, mineFollow, muteListFollow))

    val relayGroupsDiscoveryRoutes =
        _relayGroupsDiscoveryRoutes
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, mineFollow, muteListFollow))

    val podcastRoutes =
        _podcastRoutes
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf(allFollows, userFollows, kind3Follows, aroundMe, teleport, globalFollow, mineFollow, muteListFollow))

    fun destroy() {
        Log.d("Init") { "OnCleared: ${this::class.simpleName}" }
    }
}

@Stable
sealed class Name {
    abstract fun name(): String
}

/**
 * Names whose displayed title is derived from a Nostr note (community
 * definition, people list, DVM algo feed). UI layers can subscribe to
 * the underlying `note` so the title updates as the event arrives.
 */
interface NoteBackedName {
    val note: AddressableNote
}

@Stable
class GeoHashName(
    val geoHashTag: String,
) : Name() {
    override fun name() = "/g/$geoHashTag"
}

@Stable
class HashtagName(
    val hashTag: String,
) : Name() {
    override fun name() = "#$hashTag"
}

@Stable
class RelayName(
    val url: NormalizedRelayUrl,
) : Name() {
    override fun name() = url.displayUrl()
}

/** A built-in feed list. Headless: `commonsUI` gives each its localized title. */
enum class TopNavLabel {
    ALL_FOLLOWS,
    ALL_USER_FOLLOWS,
    DEFAULT_FOLLOWS,
    GLOBAL,
    CURATED,
    AROUND_ME,
    TELEPORT,
    MUTE_LIST,
    MINE,
    ALL_FAVORITE_DVMS,
    MANAGE_CUSTOM_FEEDS,
}

@Stable
class LabelName(
    val label: TopNavLabel,
) : Name() {
    override fun name() = " $label " // Space to make sure it goes first
}

@Stable
class PeopleListName(
    override val note: AddressableNote,
) : Name(),
    NoteBackedName {
    override fun name(): String {
        val noteEvent = note.event
        return if (noteEvent is FollowSetEvent) {
            noteEvent.titleOrName() ?: note.dTag()
        } else if (noteEvent is StarterPackEvent) {
            noteEvent.title() ?: note.dTag()
        } else if (noteEvent is DrssListFolderEvent || noteEvent is DrssFeedListEvent) {
            DrssTree.titleOf(noteEvent) ?: note.dTag().substringAfterLast('/')
        } else {
            note.dTag()
        }
    }
}

@Stable
class CommunityName(
    override val note: AddressableNote,
) : Name(),
    NoteBackedName {
    override fun name(): String {
        val definition = note.event as? CommunityDefinitionEvent
        val label = definition?.name()?.ifBlank { null } ?: note.dTag()
        return "/n/$label"
    }
}

@Stable
class FavoriteAlgoFeedName(
    override val note: AddressableNote,
) : Name(),
    NoteBackedName {
    override fun name(): String =
        (note.event as? AppDefinitionEvent)?.appMetaData()?.name?.takeIf { it.isNotBlank() }
            ?: note.dTag()
}

@Stable
class InterestSetName(
    val set: InterestSet,
) : Name() {
    override fun name() = "⁂ ${set.title}"
}

@Stable
class CustomFeedName(
    val feed: CustomFeedDefinition,
) : Name() {
    override fun name() = "${feed.emoji} ${feed.name}".trim()
}

@Immutable
class FeedDefinition(
    val code: TopFilter,
    val name: Name,
    val route: Route? = null,
)
