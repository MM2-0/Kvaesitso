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
package com.vitorpamplona.amethyst.commons.model.navigation

import com.vitorpamplona.amethyst.commons.model.nip51Lists.BookmarkType
import com.vitorpamplona.amethyst.commons.search.QuerySerializer
import com.vitorpamplona.amethyst.commons.search.SearchQuery
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.serialization.Serializable

/**
 * A feed whose composer can be pre-loaded from an Android share. [attachments] holds the shared
 * content URIs and [message] the text sent alongside them; both are empty/null for every other way
 * of reaching the feed, which is how the share flow tells its own entries apart from an ordinary
 * bottom-bar visit.
 */
sealed interface MediaFeedRoute {
    val attachments: List<String>
    val message: String?
}

@Serializable
sealed class Route {
    @Serializable object Home : Route()

    @Serializable object Message : Route()

    /**
     * The mixed media feed. [attachments] and [message] are set by the "New Video" share target: the content
     * URIs of the shared files and the text Android sent alongside them, which pre-load the media
     * composer and its caption. Empty for every other way of reaching the feed.
     */
    @Serializable data class Video(
        override val attachments: List<String> = emptyList(),
        override val message: String? = null,
    ) : Route(),
        MediaFeedRoute

    @Serializable data class Discover(
        val initialTab: DiscoverTab? = null,
    ) : Route()

    @Serializable data class Notification(
        val scrollToEventId: String? = null,
    ) : Route()

    @Serializable object Polls : Route()

    @Serializable object Communities : Route()

    @Serializable object NewCommunity : Route()

    @Serializable object NewGitRepository : Route()

    @Serializable data class EditCommunity(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable object Badges : Route()

    @Serializable object ProfileBadges : Route()

    @Serializable object ProfileAppRecommendations : Route()

    @Serializable data class AwardBadge(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    /**
     * The picture feed. [attachments] and [message] are set by the "New Picture" share target: the content
     * URIs of the shared files and the text Android sent alongside them, which pre-load the media
     * composer and its caption. Empty for every other way of reaching the feed.
     */
    @Serializable data class Pictures(
        override val attachments: List<String> = emptyList(),
        override val message: String? = null,
    ) : Route(),
        MediaFeedRoute

    @Serializable object Workouts : Route()

    /** The signed-in user's own training summary, read from Health Connect. Not a feed. */
    @Serializable object MyFitness : Route()

    @Serializable object GitRepositories : Route()

    @Serializable object Highlights : Route()

    @Serializable object SoftwareApps : Route()

    @Serializable object Napplets : Route()

    @Serializable object Nsites : Route()

    @Serializable object Browser : Route()

    /**
     * A web page in the in-app browser, where the front end draws one (the desktop; Android opens its
     * own browser activity instead). A screen like any other, so the navigation layout places it: on
     * the stack, in a deck column, in a tab or in a window. [preferTor] keeps it on Tor whatever the
     * host's network choice; [id] tells two pages of the same address apart (each tab or window has
     * its own title).
     */
    @Serializable data class WebPage(
        val url: String,
        val preferTor: Boolean = false,
        val id: String,
    ) : Route()

    @Serializable object FavoriteApps : Route()

    @Serializable data class WebApp(
        val url: String,
    ) : Route()

    @Serializable data class NostrApp(
        val coordinate: String,
    ) : Route()

    @Serializable object ConnectedApps : Route()

    @Serializable data class Nip46Signer(
        /** When set (from a scanned/opened `nostrconnect://` offer), the screen connects that app on open. */
        val connectUri: String? = null,
    ) : Route()

    @Serializable object Nip46ConnectedApps : Route()

    @Serializable object RelayAuthSettings : Route()

    @Serializable data class ConnectedAppDetail(
        val coordinate: String,
    ) : Route()

    @Serializable data class SoftwareAppDetail(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable object Calendars : Route()

    @Serializable object CalendarCollections : Route()

    @Serializable
    data class NewCalendarEvent(
        val draft: String? = null,
    ) : Route()

    @Serializable
    data class EditCalendarEvent(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable
    data class NewCalendarCollection(
        val dTag: String? = null,
    ) : Route()

    @Serializable data class CalendarEventDetail(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    /**
     * The NIP-CC geocaching hub. [initialTab] lets a caller land on a specific tab — the
     * "N caches here" chip on the geohash screen opens [GeocacheTab.MAP], a profile's find
     * count opens [GeocacheTab.FINDS] — while the drawer entry opens the default.
     */
    @Serializable data class Geocaches(
        val initialTab: GeocacheTab? = null,
    ) : Route()

    /** A single kind 37516 cache listing. The landing point for naddr deep links and feed taps. */
    @Serializable data class GeocacheDetail(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    /**
     * The kind 7516 found-log composer for the cache at this address. Opened from the bottom
     * because it is a composer, not a destination. This is the only screen that may scan a
     * cache's verification secret.
     */
    @Serializable data class LogGeocacheFind(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    /**
     * The cache composer. [geohash] pre-fills the location when the composer is opened by
     * long-pressing the map, so the pin lands where the finger did.
     */
    @Serializable data class NewGeocache(
        val geohash: String? = null,
        val draft: String? = null,
    ) : Route()

    @Serializable data class EditGeocache(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    /** A kind 37517 curation list — an ordered itinerary of caches. */
    @Serializable data class GeocacheHunt(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    /**
     * The hunt composer. [seedCache] is an `a`-tag value set by "Add to a hunt" on a cache's
     * detail screen, so the hunt opens with that cache already on the itinerary — the moment
     * someone wants a cache in a hunt is the moment they are looking at the cache.
     */
    @Serializable data class NewGeocacheHunt(
        val seedCache: String? = null,
        val draft: String? = null,
    ) : Route()

    @Serializable data class EditGeocacheHunt(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable object Products : Route()

    /**
     * The short-video feed. [attachments] and [message] are set by the "New Short" share target: the content
     * URIs of the shared files and the text Android sent alongside them, which pre-load the media
     * composer and its caption. Empty for every other way of reaching the feed.
     */
    @Serializable data class Shorts(
        override val attachments: List<String> = emptyList(),
        override val message: String? = null,
    ) : Route(),
        MediaFeedRoute

    @Serializable object PublicChats : Route()

    @Serializable object FollowPacks : Route()

    @Serializable object LiveStreams : Route()

    @Serializable object Nests : Route()

    @Serializable data class NestLobby(
        val addressValue: String,
    ) : Route()

    @Serializable object Longs : Route()

    @Serializable object Articles : Route()

    @Serializable object MusicTracks : Route()

    @Serializable object MusicPlaylists : Route()

    @Serializable object PodcastEpisodes : Route()

    @Serializable object Podcasts : Route()

    @Serializable data class Podcast(
        val pubkey: String,
    ) : Route()

    @Serializable object PodcastAuthoring : Route()

    @Serializable object EditPodcastShow : Route()

    @Serializable
    data class NewPodcastEpisode(
        val dTag: String? = null,
    ) : Route()

    @Serializable object NewPodcastTrailer : Route()

    @Serializable
    data class NewMusicTrack(
        val dTag: String? = null,
    ) : Route()

    @Serializable
    data class NewMusicPlaylist(
        val dTag: String? = null,
    ) : Route()

    @Serializable
    data class AddToMusicPlaylist(
        val trackAddress: String,
    ) : Route()

    /** The user's drss.io folders and lists: the river's read list, the write list, and their tree. */
    @Serializable object DrssLists : Route()

    /** drss.io's listening queue: the episodes the user is part way through, then the river's. */
    @Serializable object DrssListen : Route()

    /** The user's drss.io listening history, by day, and the feeds they hear most. */
    @Serializable object DrssHistory : Route()

    /** Follow [pubKeyHex], usually a feed's bot, into one of the follow sets drss writes to. */
    @Serializable
    data class DrssFollowInto(
        val pubKeyHex: HexKey,
    ) : Route()

    /** Save the article at [articleAddress] into one of the collections drss writes to. */
    @Serializable
    data class DrssSaveTo(
        val articleAddress: String,
    ) : Route()

    /** Add the event [eventId] to one of the user's older drss collections (kind 30102). */
    @Serializable
    data class DrssCollectTo(
        val eventId: HexKey,
    ) : Route()

    /** Record which feed the user found through the article at [articleAddress], drss.io's provenance. */
    @Serializable
    data class DrssRecordProvenance(
        val articleAddress: String,
    ) : Route()

    /** Edit the user's drss.io show at [showAddress]. */
    @Serializable
    data class DrssShowEdit(
        val showAddress: String,
    ) : Route()

    @Serializable object Chess : Route()

    @Serializable object Wallet : Route()

    @Serializable
    data class WalletSend(
        val walletId: String,
    ) : Route()

    @Serializable
    data class WalletReceive(
        val walletId: String,
    ) : Route()

    @Serializable
    data class WalletTransactions(
        val walletId: String,
    ) : Route()

    @Serializable object OnchainTransactions : Route()

    @Serializable
    data class WalletDetail(
        val walletId: String,
    ) : Route()

    @Serializable object WalletAdd : Route()

    @Serializable data class WalletAddNwc(
        val nip47: String? = null,
    ) : Route()

    @Serializable object CashuWalletMints : Route()

    @Serializable data class WalletAddClinkDebit(
        val ndebit: String? = null,
    ) : Route()

    @Serializable object CashuWallet : Route()

    /** Find-or-create wizard, shown when no NIP-60 wallet is loaded. */
    @Serializable object CashuWalletWizard : Route()

    /** Celebratory interstitial after choosing to create a new wallet. */
    @Serializable object CashuWalletCreated : Route()

    @Serializable object CashuWalletSettings : Route()

    @Serializable object CashuMintRecommendations : Route()

    /**
     * Unified profile payment screen: collects amount/message/zap type and pays
     * through whichever rail (Lightning, CLINK offer, on-chain, Cashu) the
     * profile supports. [method] preselects a rail (see ProfilePaymentMethod
     * route keys); [lnAddressOverride] pays a specific lightning target instead
     * of the profile's kind:0 lud16; [btcAddressOverride] makes the on-chain
     * rail pay that announced address directly (plain send, no NIP-BC receipt)
     * instead of the recipient's pubkey-derived Taproot address.
     */
    @Serializable
    data class SendPayment(
        val userHex: String,
        val method: String? = null,
        val lnAddressOverride: String? = null,
        val btcAddressOverride: String? = null,
    ) : Route()

    /**
     * The search screen, optionally opened with the calling screen's own filter already in the
     * box: `from:<npub>` from a profile, `kind:article` from the articles feed, `geo:<geohash>`
     * from a location channel.
     *
     * [query] is the query written in the field's own token language rather than a structured
     * object, because that is what the field actually holds. It parses back to the same
     * [SearchQuery] through `QueryParser`, draws as chips through `SearchTokenTransformation`,
     * and stays editable — the reader can drop the seeded filter with a backspace, which a
     * separately-held base filter would not let them do.
     */
    @Serializable data class Search(
        val query: String? = null,
    ) : Route() {
        companion object {
            /**
             * The search screen seeded with [base]. The trailing space matters: a token only
             * settles into a chip once the caret has left it, and the caret opens at the end of
             * the seeded text.
             */
            fun of(base: SearchQuery): Search = Search(QuerySerializer.serialize(base).takeIf { it.isNotBlank() }?.plus(" "))
        }
    }

    @Serializable object SecurityFilters : Route()

    @Serializable object PrivacyLockSettings : Route()

    /** The feeds the user built for Home: list, create, edit, delete. */
    @Serializable object CustomFeeds : Route()

    /** Creates a custom feed, or edits the one with [id]. */
    @Serializable data class EditCustomFeed(
        val id: String? = null,
    ) : Route()

    @Serializable object WebOfTrust : Route()

    @Serializable object BlockedUsers : Route()

    @Serializable object SpammingUsers : Route()

    @Serializable object HiddenWords : Route()

    @Serializable object MutedThreads : Route()

    @Serializable object PrivacyOptions : Route()

    @Serializable object NamecoinSettings : Route()

    @Serializable object OtsSettings : Route()

    @Serializable object Bookmarks : Route()

    @Serializable object OldBookmarks : Route()

    @Serializable object PinnedNotes : Route()

    @Serializable object BookmarkedRepositories : Route()

    @Serializable object BookmarkedPodcasts : Route()

    @Serializable object BookmarkGroups : Route()

    @Serializable object InterestSets : Route()

    @Serializable data class InterestSetView(
        val dTag: String,
    ) : Route()

    @Serializable data class InterestSetMetadataEdit(
        val dTag: String? = null,
    ) : Route()

    @Serializable object ImportFollowsSelectUser : Route()

    @Serializable data class ImportFollowsPickFollows(
        val userHex: HexKey,
    ) : Route()

    @Serializable data class BookmarkGroupView(
        val dTag: String,
        val bookmarkType: BookmarkType,
    ) : Route()

    @Serializable data class BookmarkGroupMetadataEdit(
        val dTag: String? = null,
    ) : Route()

    @Serializable data class PostBookmarkManagement(
        val postId: String,
    ) : Route()

    @Serializable data class ArticleBookmarkManagement(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable object EmojiPacks : Route()

    @Serializable object MyEmojiList : Route()

    @Serializable object BrowseEmojiSets : Route()

    @Serializable data class EmojiPackView(
        val dTag: String,
    ) : Route()

    @Serializable data class EmojiPackMetadataEdit(
        val dTag: String? = null,
    ) : Route()

    @Serializable data class EmojiPackSelection(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable object WebBookmarks : Route()

    @Serializable object Drafts : Route()

    @Serializable object ScheduledPosts : Route()

    @Serializable object AllSettings : Route()

    @Serializable object AccountBackup : Route()

    @Serializable object Settings : Route()

    @Serializable object ComposeSettings : Route()

    @Serializable object UserSettings : Route()

    @Serializable object ReactionsSettings : Route()

    @Serializable object MessagesSettings : Route()

    @Serializable object AudioVisualizerSettings : Route()

    @Serializable object AccentColorSettings : Route()

    @Serializable object BottomBarSettings : Route()

    @Serializable object DrawerSettings : Route()

    @Serializable object HomeTabsSettings : Route()

    @Serializable object ProfileUiSettings : Route()

    @Serializable object SearchEngineSettings : Route()

    @Serializable object AgentKeysSettings : Route()

    /**
     * Routstr, the sats-paid AI nodes: the node picker, or the node announced at [node]
     * (`38421:<pubkey>:<d>`), or a top-up of the saved key at [topUpUrl] whose masked form is
     * [topUpMask]. The key itself never rides in the back stack.
     */
    @Serializable data class RoutstrSetup(
        val node: String? = null,
        val topUpUrl: String? = null,
        val topUpMask: String? = null,
    ) : Route()

    /**
     * The front page, about today; [day] (`yyyy-MM-dd`) shows that kept edition in its place, as a
     * notification or a link does, with the way back to today.
     */
    @Serializable data class FrontPage(
        val day: String? = null,
    ) : Route()

    @Serializable object VideoPlayerSettings : Route()

    @Serializable object CallSettings : Route()

    @Serializable object NowPlayingSettings : Route()

    @Serializable object NotificationSettings : Route()

    @Serializable object CalendarReminderSettings : Route()

    @Serializable object ResourceUsage : Route()

    @Serializable object Lists : Route()

    @Serializable data class MyPeopleListView(
        val dTag: String,
    ) : Route()

    @Serializable data class MyFollowPackView(
        val dTag: String,
    ) : Route()

    @Serializable data class PeopleListMetadataEdit(
        val dTag: String? = null,
    ) : Route()

    @Serializable data class FollowPackMetadataEdit(
        val dTag: String? = null,
    ) : Route()

    @Serializable data class PeopleListManagement(
        val userToAdd: HexKey,
    ) : Route()

    @Serializable object EditProfile : Route()

    @Serializable object EditRelays : Route()

    /** Diagnostic: explains why the app currently holds the subscriptions it holds. */
    @Serializable object ActiveSubscriptions : Route()

    @Serializable object EventSync : Route()

    /** Developer tools hub: NIP-19 converter, event inspector, kind crawler, relay console. */
    @Serializable object DevTools : Route()

    @Serializable data class DevToolsNip19(
        val input: String? = null,
    ) : Route()

    /** [noteKey] is a cached note's id or address, pre-filled into the inspector. */
    @Serializable data class DevToolsEventInspector(
        val noteKey: String? = null,
    ) : Route()

    @Serializable object DevToolsKinds : Route()

    /** Kind lookup for one [kind]: its name and how clients encode it, sampled from relays. */
    @Serializable data class DevToolsKindUsage(
        val kind: Int,
    ) : Route()

    @Serializable data class DevToolsEventFinder(
        val eventId: HexKey,
    ) : Route()

    @Serializable data class DevToolsRelayConsole(
        val url: String? = null,
    ) : Route()

    @Serializable object RequestToVanish : Route()

    @Serializable object VanishEvents : Route()

    @Serializable object EditMediaServers : Route()

    @Serializable object ManageBlossomBlobs : Route()

    @Serializable object ImportBlossomBlobs : Route()

    @Serializable object EditNestsServers : Route()

    /**
     * The "what does this cordn link point at" screen. [link] pre-fills it
     * when the screen opens from a deep link or a scanned QR code: a bare
     * `cordn1…` or a cordn.net share link, as `CordnLinkInspection` reads it.
     */
    @Serializable data class CordnLink(
        val link: String? = null,
    ) : Route()

    /**
     * One cordn room.
     *
     * Both halves are the address: a `gid` is unique only within one
     * coordinator (`spec/00.md` §4), so a route carrying the gid alone would
     * open whichever of two same-named groups happened to be found first.
     */
    @Serializable data class CordnGroupChat(
        val coordinatorPubKey: HexKey,
        val gid: String,
    ) : Route()

    @Serializable data class CordnGroupInfo(
        val coordinatorPubKey: HexKey,
        val gid: String,
    ) : Route()

    @Serializable object CordnGroupList : Route()

    @Serializable object CordnCreateGroup : Route()

    @Serializable object CordnCreateGroupMembers : Route()

    @Serializable object CordnInvitations : Route()

    @Serializable object CordnCoordinators : Route()

    @Serializable object CordnKeyPackages : Route()

    @Serializable object CordnBackup : Route()

    /** The one cordn entry in settings; everything else hangs off it. */
    @Serializable object CordnHub : Route()

    @Serializable object CordnMigrate : Route()

    @Serializable
    data class AgentConsole(
        val relayUrl: String,
    ) : Route()

    @Serializable object AgentAttestation : Route()

    @Serializable data class AgentPersonaEdit(
        val slug: String? = null,
    ) : Route()

    @Serializable object EditFavoriteAlgoFeeds : Route()

    @Serializable object EditPaymentTargets : Route()

    @Serializable object EditBolt12Offers : Route()

    @Serializable object UpdateReactionType : Route()

    @Serializable data class Nip47NWCSetup(
        val nip47: String? = null,
    ) : Route()

    @Serializable data class UpdateZapAmount(
        val nip47: String? = null,
    ) : Route()

    @Serializable data class Profile(
        val id: String,
    ) : Route()

    @Serializable data class QRDisplay(
        val pubkey: String,
        /**
         * Opens straight into the scanner instead of showing this user's own code. Set by the
         * launcher shortcut, whose entire purpose is to skip that step.
         */
        val startScanning: Boolean = false,
    ) : Route()

    /**
     * Decodes a QR out of an image the user shared into Amethyst, then goes wherever it points.
     *
     * [uri] is the shared image's content uri, as a string so the route stays serializable.
     */
    @Serializable data class ScanQrImage(
        val uri: String,
    ) : Route()

    @Serializable data class ContentDiscovery(
        val id: String,
    ) : Route()

    @Serializable data class Note(
        val id: String,
    ) : Route()

    /** Official NIP [number] (`01`, `7D`) in the in-app reader, scrolled to [anchor] when given. */
    @Serializable data class Nip(
        val number: String,
        val anchor: String? = null,
    ) : Route()

    /** Every NIP: the official index and the NIPs published on Nostr, searchable. */
    @Serializable object Nips : Route()

    /** An event kind: the NIPs that define it and a sample of its recent events. */
    @Serializable data class EventKind(
        val kind: Int,
    ) : Route()

    @Serializable data class ShareNoteAsImage(
        val id: String,
    ) : Route()

    @Serializable data class ShareNoteAsImageFile(
        val id: String,
    ) : Route()

    @Serializable data class ShareNoteAsQr(
        val id: String,
    ) : Route()

    @Serializable data class ContactListUsers(
        val noteId: String,
    ) : Route()

    @Serializable data class PollResults(
        val noteId: String,
    ) : Route()

    /** Every relay of a relay-list event (NIP-65, NIP-17, NIP-51 sets and lists…), in a lazy list. */
    @Serializable data class RelayListMembers(
        val noteId: String,
    ) : Route()

    @Serializable data class Hashtag(
        val hashtag: String,
    ) : Route()

    @Serializable data class Geohash(
        val geohash: String,
    ) : Route()

    @Serializable data class Url(
        val url: String,
    ) : Route()

    @Serializable data class ChessGame(
        val gameId: String,
    ) : Route()

    @Serializable data class Community(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable data class GitRepository(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable data class GitRepositoryCode(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable data class GitRepositoryIssues(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable data class GitRepositoryPulls(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable data class GitRepositoryNewIssue(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable data class GitRepositoryNewPullRequest(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable data class FollowPack(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable data class PublicChatChannel(
        val id: String,
        val draftId: HexKey? = null,
        val replyTo: HexKey? = null,
    ) : Route()

    @Serializable data class LiveActivityChannel(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
        val draftId: HexKey? = null,
        val replyTo: HexKey? = null,
    ) : Route()

    @Serializable data class BackupConflictReview(
        val slot: String,
    ) : Route()

    @Serializable data class RelayInfo(
        val url: String,
    ) : Route()

    @Serializable data class RelayManagement(
        val url: String,
    ) : Route()

    @Serializable data class RelayMembers(
        val url: String,
    ) : Route()

    @Serializable data class RelayFeed(
        val url: String,
    ) : Route()

    @Serializable data class EphemeralChat(
        val id: String,
        val relayUrl: String,
        val draftId: HexKey? = null,
        val replyTo: HexKey? = null,
    ) : Route()

    @Serializable object NewEphemeralChat : Route()

    @Serializable data class GeohashChat(
        val geohash: String,
        // True when the user is not physically in the cell (jumped in via teleport). Seeds the
        // composer's teleport toggle so their messages carry the ["t","teleport"] marker.
        val teleported: Boolean = false,
    ) : Route()

    @Serializable object NewGeohashChat : Route()

    @Serializable object GeohashChats : Route()

    @Serializable object GeohashTeleport : Route()

    @Serializable data class RelayGroup(
        val id: String,
        val relayUrl: String,
        val draftId: HexKey? = null,
        val replyTo: HexKey? = null,
        // NIP-29 invite code from a `wss://relay'id?code=…` link — auto-joins a closed
        // group when the screen opens. Null for a plain (view-only) group link.
        val inviteCode: String? = null,
    ) : Route()

    @Serializable data class RelayGroupServer(
        val relayUrl: String,
    ) : Route()

    @Serializable data class RelayGroupMembers(
        val id: String,
        val relayUrl: String,
    ) : Route()

    @Serializable data class RelayGroupThreads(
        val id: String,
        val relayUrl: String,
    ) : Route()

    @Serializable data class BuzzCanvas(
        val channelId: String,
        val relayUrl: String,
    ) : Route()

    @Serializable data class BuzzJobBoard(
        val channelId: String,
        val relayUrl: String,
    ) : Route()

    @Serializable data class BuzzAgentWork(
        val channelId: String,
        val relayUrl: String,
    ) : Route()

    @Serializable data class BuzzWorkflowBoard(
        val channelId: String,
        val relayUrl: String,
    ) : Route()

    @Serializable data class BuzzForumPost(
        val channelId: String,
        val relayUrl: String,
    ) : Route()

    @Serializable data class BuzzForumThread(
        val channelId: String,
        val relayUrl: String,
        val rootId: String,
    ) : Route()

    @Serializable data class RelayGroupCreate(
        val relayUrl: String,
        // Buzz only: start the create flow on a `forum` channel (threaded posts) instead of a
        // `stream` (chat) one — set by the community screen's per-section "+" buttons.
        val isForum: Boolean = false,
    ) : Route()

    @Serializable data class RelayGroupEdit(
        val id: String,
        val relayUrl: String,
    ) : Route()

    @Serializable object RelayGroups : Route()

    @Serializable
    data class BuzzDmList(
        val relayUrl: String,
    ) : Route()

    @Serializable
    data class BuzzNewDm(
        val relayUrl: String,
    ) : Route()

    @Serializable object RelayGroupBrowse : Route()

    // Concord Channels (encrypted communities). Addressed by community id + channel id
    // (both lowercase hex), never a host relay — a channel plane may be mirrored on all
    // of the community's relays.
    @Serializable data class Concord(
        val communityId: String,
        val channelId: String,
        val draftId: HexKey? = null,
        val replyTo: HexKey? = null,
    ) : Route()

    @Serializable data class ConcordServer(
        val communityId: String,
    ) : Route()

    @Serializable data class ConcordMembers(
        val communityId: String,
    ) : Route()

    @Serializable data class ConcordEdit(
        val communityId: String,
    ) : Route()

    @Serializable data class ConcordInviteLinks(
        val communityId: String,
    ) : Route()

    @Serializable object ConcordCreate : Route()

    // Deep-link target for a Concord invite link (naddr#fragment). Opens the join flow.
    @Serializable data class ConcordInvite(
        val link: String,
    ) : Route()

    @Serializable data class BuzzInvite(
        val link: String,
    ) : Route()

    @Serializable object Concords : Route()

    // The "minichat" of a chat message: its kind-1111 thread replies, opened from the message and
    // rendered as a chat-within-a-chat. Keyed by the root message id; the screen resolves the chat
    // context (Concord channel, public chat, relay group) from the note's gatherer. When opened from
    // a Concord reply whose parent message may not be cached, [concordCommunityId]/[concordChannelId]
    // carry the plane context (taken from the reply's channel), so the screen can still subscribe the
    // plane and backfill the parent — without them, a reply to an unloaded parent can't pick the relay.
    @Serializable data class ChatMinichat(
        val rootId: HexKey,
        val concordCommunityId: String? = null,
        val concordChannelId: String? = null,
    ) : Route()

    @Serializable data class ChannelMetadataEdit(
        val id: String? = null,
    ) : Route()

    @Serializable object MarmotGroupList : Route()

    @Serializable data class MarmotGroupChat(
        val nostrGroupId: String,
        val message: String? = null,
        val replyId: HexKey? = null,
    ) : Route()

    @Serializable data class MarmotGroupInfo(
        val nostrGroupId: String,
    ) : Route()

    @Serializable object CreateMarmotGroup : Route()

    @Serializable data class MarmotGroupEditInfo(
        val nostrGroupId: String,
    ) : Route()

    @Serializable data class NewGroupDM(
        val message: String? = null,
        val attachment: String? = null,
    ) : Route()

    // Full-screen chooser opened from the Messages FAB: explains each conversation type
    // (DM, Marmot group, Concord, public chat, relay group, disappearing chat) with its
    // pros/cons and routes to that type's creation flow.
    @Serializable object NewConversation : Route()

    @Serializable data class ShareToDM(
        val message: String? = null,
        val attachment: String? = null,
    ) : Route()

    @Serializable data class Room(
        val id: String,
        val message: String? = null,
        val attachment: String? = null,
        val replyId: HexKey? = null,
        val draftId: HexKey? = null,
        val expiresDays: Int? = null,
    ) : Route() {
        constructor(key: ChatroomKey, message: String? = null, attachment: String? = null, replyId: HexKey? = null, draftId: HexKey? = null, expiresDays: Int? = null) : this(
            id = key.users.joinToString(","),
            message = message,
            attachment = attachment,
            replyId = replyId,
            draftId = draftId,
            expiresDays = expiresDays,
        )

        fun toKey(): ChatroomKey = ChatroomKey(id.split(",").toSet())
    }

    @Serializable data class NewPublicMessage(
        val to: String,
        val replyId: HexKey? = null,
        val draftId: HexKey? = null,
    ) : Route() {
        constructor(users: Set<HexKey>, parentId: HexKey, draftId: HexKey? = null) : this(
            to = users.joinToString(","),
            replyId = parentId,
            draftId = draftId,
        )

        fun toKey(): Set<HexKey> = to.split(",").toSet()
    }

    @Serializable data class RoomByAuthor(
        val id: String,
    ) : Route()

    @Serializable data class ActiveCall(
        val callId: String,
        val peerPubKey: HexKey,
    ) : Route()

    @Serializable data class EventRedirect(
        val id: String,
        /**
         * The event is a NIP-17 rumor, so [id] is a private event id that must never reach
         * a relay: the screen watches LocalCache for it and issues no REQ. The envelope that
         * carries it comes back on its own through the always-on gift-wrap tail.
         */
        val isPrivate: Boolean = false,
    ) : Route()

    @Serializable
    data class NewProduct(
        val message: String? = null,
        val attachment: String? = null,
        val quote: String? = null,
        val draft: String? = null,
    ) : Route()

    @Serializable data object NewGoal : Route()

    /**
     * Manual workout composer. All fields are optional pre-fill, used when a
     * workout is detected from Health Connect and the user accepts the
     * suggestion; a blank [NewWorkout] opens the empty manual form.
     */
    @Serializable
    data class NewWorkout(
        val exercise: String? = null,
        val title: String? = null,
        val durationSeconds: Long = 0,
        val distanceMeters: Double = 0.0,
        val calories: Int = 0,
        val avgHeartRate: Int = 0,
        val maxHeartRate: Int = 0,
        val steps: Int = 0,
        val elevationGainMeters: Double = 0.0,
        val startTime: Long = 0,
        val source: String? = null,
    ) : Route()

    @Serializable
    data class NewLongFormPost(
        val draft: String? = null,
        val version: String? = null,
    ) : Route()

    @Serializable
    data class GeoPost(
        val geohash: String? = null,
        val message: String? = null,
        val attachment: String? = null,
        val replyTo: String? = null,
        val quote: String? = null,
        val draft: String? = null,
    ) : Route()

    @Serializable
    data class HashtagPost(
        val hashtag: String? = null,
        val message: String? = null,
        val attachment: String? = null,
        val replyTo: String? = null,
        val quote: String? = null,
        val draft: String? = null,
    ) : Route()

    @Serializable
    data class UrlPost(
        val url: String? = null,
        val message: String? = null,
        val attachment: String? = null,
        val replyTo: String? = null,
        val quote: String? = null,
        val draft: String? = null,
    ) : Route()

    @Serializable
    data class GenericCommentPost(
        val message: String? = null,
        val attachment: String? = null,
        val replyTo: String? = null,
        val quote: String? = null,
        val draft: String? = null,
        /** An inline code review: the file, commit and lines of a pull request's diff it is about. */
        val reviewPath: String? = null,
        val reviewCommit: String? = null,
        val reviewLine: Int? = null,
        val reviewRemovedSide: Boolean = false,
    ) : Route()

    @Serializable
    data class NewShortNote(
        val message: String? = null,
        val attachment: String? = null,
        val baseReplyTo: String? = null,
        val quote: String? = null,
        val fork: String? = null,
        val version: String? = null,
        val draft: String? = null,
        // When set, the composer produces a NIP-29 kind-11 group thread scoped to this group id and
        // published only to its host relay (see ShortNotePostViewModel.setGroupThread).
        val groupThreadId: String? = null,
        val groupThreadRelayUrl: String? = null,
    ) : Route()

    @Serializable
    data class NewPoll(
        val message: String? = null,
        val draft: String? = null,
    ) : Route()

    /**
     * The NIP-84 highlight composer. Opened by the "Add highlight" action (all fields null) or
     * when a browser shares a text selection to Amethyst — in which case the shared string has
     * already been run through SharedHighlightParser and the pieces arrive pre-split here.
     */
    @Serializable
    data class NewHighlight(
        val quote: String? = null,
        val url: String? = null,
        val prefix: String? = null,
        val suffix: String? = null,
        val comment: String? = null,
        val context: String? = null,
        // A nostr source (set when highlighting a nostr article/note rather than a web page):
        // an addressable coordinate (`a`), a specific event id (`e`) and the author (`p`).
        val sourceAddress: String? = null,
        val sourceEventId: String? = null,
        val author: String? = null,
    ) : Route()

    @Serializable data object NewHlsVideo : Route()

    @Serializable
    data class VoiceReply(
        val replyToNoteId: String,
        val recordingFilePath: String,
        val mimeType: String,
        val duration: Int,
        val amplitudes: String, // JSON-encoded List<Float>
    ) : Route()

    @Serializable
    data class ManualZapSplitPayment(
        val paymentId: String,
    ) : Route()

    @Serializable
    data class ReloadMint(
        val requestId: String,
    ) : Route()

    @Serializable
    data class TopUpMint(
        val mintUrl: String,
    ) : Route()
}

@Serializable
/**
 * The tabs of [Route.Geocaches]. Views over one shared feed state rather than separate
 * destinations, so the map costs no additional relay subscription and Back always returns to
 * whichever list the user arrived from.
 */
enum class GeocacheTab {
    NEARBY,
    MAP,
    HUNTS,
    FINDS,
    MINE,
}

enum class DiscoverTab {
    FOLLOWS,
    READS,
    ALGOS,
    LIVE,
    COMMUNITY,
    MARKETPLACE,
    CHATS,
}

fun isSameRoute(
    currentRoute: Route?,
    newRoute: Route,
): Boolean {
    if (currentRoute == null) return false

    // Opening the scanner is an action, not a place. After the user closes the scanner they are
    // still on this exact entry, so treating a repeat as a duplicate made the launcher shortcut
    // silently do nothing the second time. A fresh entry reopens the camera.
    if (newRoute is Route.QRDisplay && newRoute.startScanning) return false

    if (currentRoute == newRoute) {
        return true
    }

    if (newRoute is Route.EventRedirect) {
        return when (currentRoute) {
            is Route.Note -> newRoute.id == currentRoute.id
            is Route.PublicChatChannel -> newRoute.id == currentRoute.id
            else -> false
        }
    }

    return false
}
