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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.geohashChat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.geohashChat.GeohashChatViewModel
import com.vitorpamplona.amethyst.commons.chats.publicChannels.dal.ChannelFeedViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserPicture
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.geohash_chat_nickname_description
import com.vitorpamplona.amethyst.commons.resources.geohash_chat_nickname_placeholder
import com.vitorpamplona.amethyst.commons.resources.geohash_chat_nickname_title
import com.vitorpamplona.amethyst.commons.resources.geohash_chat_no_relays
import com.vitorpamplona.amethyst.commons.resources.geohash_chat_post_as_me
import com.vitorpamplona.amethyst.commons.resources.geohash_chat_post_as_self_body
import com.vitorpamplona.amethyst.commons.resources.geohash_chat_post_as_self_title
import com.vitorpamplona.amethyst.commons.resources.geohash_chat_posting_anonymously
import com.vitorpamplona.amethyst.commons.resources.geohash_chat_posting_as_yourself
import com.vitorpamplona.amethyst.commons.resources.geohash_chat_relays_title
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.search.SearchSeed
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.rememberCoarseLocationPermission
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SearchTopBarAction
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.LocalChatActingIdentities
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.LocalChatDisplayNameResolver
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.LocalChatReactOverride
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.LocalChatShowSelfAuthorName
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.LocalChatSuppressGeohash
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.datasource.ChannelFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.send.ChannelNewMessageViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.send.EditFieldRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * A Bitchat-interoperable public geohash location chat: everyone physically (or
 * "teleported") in the same geohash cell shares an ephemeral, relay-broadcast
 * room. Messages are signed with a per-geohash throwaway identity, so posting
 * here does not reveal the account's npub.
 *
 * The room runs the **same** screen as every other chat — the cell's
 * [GeohashChatChannel] feeds [ChannelFeedViewModel] rendered through the shared
 * [RefreshingChatroomFeedView], and the composer is the shared [EditFieldRow] on a
 * geohash-aware [ChannelNewMessageViewModel] (mention/@-tagging, custom emojis,
 * uploads, drafts — with the per-cell signer + PoW + n/t tags). The only
 * geohash-specific bits injected into the renderer are three composition-locals:
 * [LocalChatActingIdentities] (own-message alignment/highlight under the per-cell
 * key), [LocalChatReactOverride] (anonymous reactions), and
 * [LocalChatDisplayNameResolver] (the Bitchat `n` nickname + ✈ teleport marker).
 */
@Composable
fun GeohashChatScreen(
    geohash: String,
    teleported: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadGeohashChannel(geohash) { channel ->
        GeohashChatRoom(channel, teleported, accountViewModel, nav)
    }
}

@Composable
private fun GeohashChatRoom(
    channel: GeohashChatChannel,
    teleported: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val geohash = channel.geohash
    val relays = remember(channel) { channel.relays().toList() }

    // Identity/reactions helper (per-cell pubkeys + anonymous react).
    val identity: GeohashChatViewModel = rememberViewModel(key = "GeohashIdentity/$geohash") { GeohashChatViewModel() }
    identity.init(geohash, accountViewModel)

    val feedViewModel: ChannelFeedViewModel =
        rememberViewModel(
            key = "geohash:${geohash}ChannelFeedViewModel",
            factory = ChannelFeedViewModel.Factory(channel, accountViewModel.account),
        )

    // The shared composer, made geohash-aware (per-cell signer + PoW + n/t tags) in its send path.
    val newMessageModel: ChannelNewMessageViewModel = rememberViewModel(key = "geohash:${geohash}NewMessage") { ChannelNewMessageViewModel() }
    newMessageModel.init(accountViewModel)
    newMessageModel.load(channel)

    // The nickname lives on-device (the throwaway key has no kind-0 profile, and kind-20000 messages
    // are ephemeral), so restore the account's saved global handle into the composer on open.
    LaunchedEffect(newMessageModel) {
        val saved = withContext(Dispatchers.IO) { accountViewModel.account.geohashIdentity.nickname() }
        if (saved.isNotBlank()) newMessageModel.geohashNickname = saved
    }

    // Teleport is a fact, not a per-message choice: the app decides whether the sender is physically
    // in this cell. When the device's location is known we compare it to the channel cell (objective);
    // otherwise we fall back to how the user arrived — the map picker passes teleported=true, "near me"
    // and manual entry default to false. This drives the ["t","teleport"] tag honestly. rememberPermissionState
    // only reads the current grant (it never prompts), so opening a location chat can't trigger a GPS dialog.
    val locationPermission = rememberCoarseLocationPermission()
    val locationSource = LocalAppServices.current.deviceLocation
    LaunchedEffect(locationPermission.isGranted) {
        if (locationPermission.isGranted) {
            locationSource.setLocationPermission(true)
        }
    }
    val deviceLocation by locationSource.preciseGeohashStateFlow
        .collectAsStateWithLifecycle()
    val isTeleported =
        remember(deviceLocation, geohash, teleported) {
            when (val loc = deviceLocation) {
                is LocationResult.Success -> {
                    // Compare on the common prefix: the device fix is fixed-precision (8 chars), so a
                    // finer (longer) cell can never be a prefix of it — treat a shared prefix as "here".
                    val device = loc.geoHash.toString()
                    val prefix = minOf(device.length, geohash.length)
                    device.take(prefix) != geohash.take(prefix)
                }
                else -> teleported
            }
        }
    LaunchedEffect(isTeleported) { newMessageModel.geohashTeleported = isTeleported }

    WatchLifecycleAndUpdateModel(feedViewModel)
    ChannelFilterAssemblerSubscription(channel, accountViewModel.dataSources().channel, accountViewModel)

    val myPubKeys by identity.myPubKeys.collectAsStateWithLifecycle()

    var showPostAsSelfWarning by remember { mutableStateOf(false) }

    if (showPostAsSelfWarning) {
        AlertDialog(
            onDismissRequest = { showPostAsSelfWarning = false },
            title = { Text(stringRes(Res.string.geohash_chat_post_as_self_title)) },
            text = {
                Text(
                    stringRes(Res.string.geohash_chat_post_as_self_body),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    newMessageModel.geohashPostAsSelf = true
                    showPostAsSelfWarning = false
                }) { Text(stringRes(Res.string.geohash_chat_post_as_me)) }
            },
            dismissButton = {
                TextButton(onClick = { showPostAsSelfWarning = false }) { Text(stringRes(Res.string.cancel)) }
            },
        )
    }

    DisappearingScaffold(
        isInvertedLayout = true,
        topBar = { GeohashChatTopBar(geohash, relays, nav) },
        accountViewModel = accountViewModel,
        allowBarHide = false,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePaddingSafe()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(
                    LocalChatShowSelfAuthorName provides true,
                    LocalChatSuppressGeohash provides geohash,
                    LocalChatActingIdentities provides myPubKeys,
                    LocalChatReactOverride provides { note, reaction ->
                        identity.react(note, reaction, newMessageModel.geohashPostAsSelf)
                    },
                    LocalChatDisplayNameResolver provides { note ->
                        // Author line = nickname (throwaway keys have no profile) + a ✈ marker for
                        // teleported senders (not physically in the cell). Folded into the name so it
                        // needs no extra renderer seam.
                        val event = note.event as? GeohashChatEvent
                        val nick = event?.nickname()?.takeIf { it.isNotBlank() }
                        val isTeleported = event?.isTeleported() == true
                        when {
                            nick != null && isTeleported -> "$nick ✈"
                            nick != null -> nick
                            isTeleported -> "${note.author?.pubkeyDisplayHex().orEmpty()} ✈"
                            else -> null
                        }
                    },
                ) {
                    RefreshingChatroomFeedView(
                        feedContentState = feedViewModel.feedState,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        routeForLastRead = "Channel/geohash:$geohash",
                        onWantsToReply = newMessageModel::reply,
                        onWantsToEditDraft = {},
                    )
                }
            }

            if (newMessageModel.geohashTeleported) {
                TeleportIndicatorRow()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GeohashIdentityAvatar(
                    model = newMessageModel,
                    accountViewModel = accountViewModel,
                    onRequestPostAsSelf = { showPostAsSelfWarning = true },
                )
                // Nudge the field left into the composer's fixed 10dp inset so the picture sits close
                // to the field rather than across a wide gap.
                Box(Modifier.weight(1f).offset(x = (-4).dp)) {
                    EditFieldRow(
                        channelScreenModel = newMessageModel,
                        accountViewModel = accountViewModel,
                        onSendNewMessage = feedViewModel.feedState::sendToTop,
                        nav = nav,
                    )
                }
            }
        }
    }
}

/**
 * The composer's identity control — the one affordance that replaces the always-on nickname field and
 * "post as me" chip. Shows the account's picture when posting as yourself, or an incognito avatar when
 * posting under the anonymous per-cell key. Tap flips between the two (routing through the "post as me"
 * warning when switching to your real identity); long-press opens the nickname editor.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GeohashIdentityAvatar(
    model: ChannelNewMessageViewModel,
    accountViewModel: AccountViewModel,
    onRequestPostAsSelf: () -> Unit,
) {
    var showNickname by remember { mutableStateOf(false) }
    if (showNickname) {
        GeohashNicknameDialog(model, accountViewModel) { showNickname = false }
    }

    val postAsSelf = model.geohashPostAsSelf
    val avatarModifier =
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = { if (postAsSelf) model.geohashPostAsSelf = false else onRequestPostAsSelf() },
                onLongClick = { showNickname = true },
            )

    Column(
        modifier = Modifier.padding(start = 8.dp, end = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (postAsSelf) {
            val picture by observeUserPicture(accountViewModel.userProfile(), accountViewModel)
            RobohashFallbackAsyncImage(
                robot = accountViewModel.userProfile().pubkeyHex,
                model = picture,
                contentDescription = stringRes(Res.string.geohash_chat_posting_as_yourself),
                modifier = avatarModifier,
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
                autoPlayGif = false,
            )
        } else {
            Box(
                avatarModifier.background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                SymbolIcon(
                    symbol = MaterialSymbols.PersonOff,
                    contentDescription = stringRes(Res.string.geohash_chat_posting_anonymously),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Discoverability affordance: a compact caption under the anonymous avatar that shows the
            // chosen nickname, or invites setting one when blank. Tapping it opens the same editor as
            // long-pressing the avatar, so the nickname control is reachable without knowing the gesture.
            val nickname = model.geohashNickname
            Text(
                text = nickname.ifBlank { "+ name" },
                style = MaterialTheme.typography.labelSmall,
                color = if (nickname.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .widthIn(max = 56.dp)
                        .padding(top = 2.dp)
                        .clickable { showNickname = true },
            )
        }
    }
}

/** Sets/edits the Bitchat `n` nickname carried on this cell's messages — reached by long-pressing the avatar. */
@Composable
private fun GeohashNicknameDialog(
    model: ChannelNewMessageViewModel,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(model.geohashNickname) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val trimmed = text.trim()
                model.geohashNickname = trimmed
                // Persist the global handle so it survives app restarts (see GeohashChatIdentityState).
                accountViewModel.account.geohashIdentity.setNickname(trimmed)
                onDismiss()
            }) { Text(stringRes(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
        title = { Text(stringRes(Res.string.geohash_chat_nickname_title)) },
        text = {
            Column {
                Text(
                    stringRes(Res.string.geohash_chat_nickname_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(stringRes(Res.string.geohash_chat_nickname_placeholder)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
    )
}

/** A read-only line telling the user their messages carry the ✈ teleport marker (the app sets this). */
@Composable
private fun TeleportIndicatorRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "✈ Teleported — you're not in this cell",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GeohashChatTopBar(
    geohash: String,
    relays: List<NormalizedRelayUrl>,
    nav: INav,
) {
    var showRelays by remember { mutableStateOf(false) }
    if (showRelays) {
        GeohashRelayListDialog(geohash, relays) { showRelays = false }
    }

    TopBarExtensibleWithBackButton(
        title = {
            Column {
                // Lead with the place — users care about "Palo Alto", not "9q9jh". Falls back to the
                // #geohash while the reverse-geocode resolves (or when no geocoder is available).
                LoadCityName(
                    geohashStr = geohash,
                    onLoading = {
                        Text("#$geohash")
                    },
                ) { cityName ->
                    Text(cityName)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showRelays = true },
                ) {
                    val relayText = if (relays.size == 1) "1 relay" else "${relays.size} relays"
                    Text(
                        "#$geohash · $relayText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SymbolIcon(
                        symbol = MaterialSymbols.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        // Search this cell rather than everywhere: the box opens holding `geo:<geohash>`.
        actions = { SearchTopBarAction(SearchSeed.byGeohash(geohash), nav) },
        // Hidden in a list/detail screen's detail pane, where there is nothing to go back to.
        nav = nav,
    )
}

/** Lists the geo-relays this cell broadcasts to — the answer to "which 5 relays?" when the count is tapped. */
@Composable
private fun GeohashRelayListDialog(
    geohash: String,
    relays: List<NormalizedRelayUrl>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.close)) } },
        title = { Text(stringRes(Res.string.geohash_chat_relays_title, geohash)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (relays.isEmpty()) {
                    Text(
                        stringRes(Res.string.geohash_chat_no_relays),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    relays.forEach { relay ->
                        Text(relay.displayUrl(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}
