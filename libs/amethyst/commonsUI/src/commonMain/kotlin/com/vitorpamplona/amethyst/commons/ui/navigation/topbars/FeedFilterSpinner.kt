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
package com.vitorpamplona.amethyst.commons.ui.navigation.topbars

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.feeds.CommunityName
import com.vitorpamplona.amethyst.commons.feeds.CustomFeedName
import com.vitorpamplona.amethyst.commons.feeds.FavoriteAlgoFeedName
import com.vitorpamplona.amethyst.commons.feeds.FeedDefinition
import com.vitorpamplona.amethyst.commons.feeds.GeoHashName
import com.vitorpamplona.amethyst.commons.feeds.HashtagName
import com.vitorpamplona.amethyst.commons.feeds.InterestSetName
import com.vitorpamplona.amethyst.commons.feeds.LabelName
import com.vitorpamplona.amethyst.commons.feeds.Name
import com.vitorpamplona.amethyst.commons.feeds.NoteBackedName
import com.vitorpamplona.amethyst.commons.feeds.PeopleListName
import com.vitorpamplona.amethyst.commons.feeds.RelayName
import com.vitorpamplona.amethyst.commons.feeds.TopNavLabel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserIsFollowingGeohash
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_manage
import com.vitorpamplona.amethyst.commons.resources.dvm_offline
import com.vitorpamplona.amethyst.commons.resources.feed_filter_select_an_option
import com.vitorpamplona.amethyst.commons.resources.feed_filter_selected
import com.vitorpamplona.amethyst.commons.resources.feed_group_communities
import com.vitorpamplona.amethyst.commons.resources.feed_group_custom
import com.vitorpamplona.amethyst.commons.resources.feed_group_dvms
import com.vitorpamplona.amethyst.commons.resources.feed_group_feeds
import com.vitorpamplona.amethyst.commons.resources.feed_group_hashtags
import com.vitorpamplona.amethyst.commons.resources.feed_group_interest_sets
import com.vitorpamplona.amethyst.commons.resources.feed_group_lists
import com.vitorpamplona.amethyst.commons.resources.feed_group_locations
import com.vitorpamplona.amethyst.commons.resources.feed_group_relays
import com.vitorpamplona.amethyst.commons.resources.follow_geohash
import com.vitorpamplona.amethyst.commons.resources.follow_list_algo_feed_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_all_favorite_dvms
import com.vitorpamplona.amethyst.commons.resources.follow_list_all_favorite_dvms_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_aroundme
import com.vitorpamplona.amethyst.commons.resources.follow_list_aroundme_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_community_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_curated
import com.vitorpamplona.amethyst.commons.resources.follow_list_curated_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_custom_feed_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_default_follows_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_geohash_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_global
import com.vitorpamplona.amethyst.commons.resources.follow_list_global_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_hashtag_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_info
import com.vitorpamplona.amethyst.commons.resources.follow_list_interest_set_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_kind3_follows_users_only
import com.vitorpamplona.amethyst.commons.resources.follow_list_kind3follows
import com.vitorpamplona.amethyst.commons.resources.follow_list_kind3follows_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_kind3follows_users_only
import com.vitorpamplona.amethyst.commons.resources.follow_list_kind3follows_users_only_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_mine
import com.vitorpamplona.amethyst.commons.resources.follow_list_mine_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_mute_list
import com.vitorpamplona.amethyst.commons.resources.follow_list_mute_list_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_notifications_all_follows_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_notifications_default_follows_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_notifications_global_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_notifications_mute_list_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_notifications_people_list_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_people_list_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_relay_summary
import com.vitorpamplona.amethyst.commons.resources.follow_list_teleport
import com.vitorpamplona.amethyst.commons.resources.follow_list_teleport_summary
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_follow
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_unfollow
import com.vitorpamplona.amethyst.commons.resources.open_dropdown_menu
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.resources.select_an_option
import com.vitorpamplona.amethyst.commons.resources.unfollow_geohash
import com.vitorpamplona.amethyst.commons.ui.components.InformationDialog
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSectionLabel
import com.vitorpamplona.amethyst.commons.ui.components.M3LazyActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.toasts.ToastSeverity
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.platform.AroundMeLocationLabel
import com.vitorpamplona.amethyst.commons.ui.platform.GeohashLocationPickerDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms.rememberDvmHeartbeatFresh
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.StringResource

@Composable
fun FeedFilterSpinner(
    placeholderCode: TopFilter,
    explainer: String,
    options: ImmutableList<FeedDefinition>,
    onSelect: (FeedDefinition) -> Unit,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    filtersNotifications: Boolean = false,
) {
    var optionsShowing by remember { mutableStateOf(false) }

    val selectAnOption = stringRes(id = Res.string.select_an_option)

    val selected =
        remember(placeholderCode, options) {
            // Match by both subclass and code string to avoid collisions between
            // TopFilter variants that derive `code` from the same Address (e.g.
            // PeopleList vs MuteList).
            options.firstOrNull {
                it.code::class == placeholderCode::class && it.code.code == placeholderCode.code
            }
        }

    val currentText = selected?.name?.let { displayName(it) } ?: selectAnOption

    val accessibilityDescription =
        if (selected != null) {
            stringRes(Res.string.feed_filter_selected, currentText)
        } else {
            stringRes(Res.string.feed_filter_select_an_option, selectAnOption)
        }

    val openDropdownLabel = stringRes(Res.string.open_dropdown_menu)

    val filter = selected?.code ?: placeholderCode

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.weight(1f, fill = false),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Size20Modifier)

                // Bound the Column so long filter names (e.g. DVM titles) get truncated
                // instead of wrapping to multiple lines and shoving the expand icon out.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    if (filter is TopFilter.Geohash) {
                        LoadCityName(
                            geohashStr = filter.tag,
                            onLoading = {
                                Row {
                                    Text(
                                        text = filter.tag,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = StdHorzSpacer)
                                    LoadingAnimation(indicatorSize = 12.dp, circleWidth = 2.dp)
                                }
                            },
                        ) { cityName ->
                            Text(
                                text = cityName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        val favoriteAlgoFeedAddress = (selected?.name as? FavoriteAlgoFeedName)?.note?.address
                        if (favoriteAlgoFeedAddress != null) {
                            val heartbeatFresh by rememberDvmHeartbeatFresh(favoriteAlgoFeedAddress, accountViewModel)
                            val offlineLabel = stringRes(Res.string.dvm_offline)
                            Text(
                                text = if (heartbeatFresh) currentText else "$currentText \u2022",
                                color = if (heartbeatFresh) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    if (heartbeatFresh) {
                                        Modifier
                                    } else {
                                        Modifier.semantics { contentDescription = "$currentText, $offlineLabel" }
                                    },
                            )
                        } else {
                            Text(
                                text = currentText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (filter is TopFilter.AroundMe) {
                        AroundMeLocationLabel()
                    }
                }

                Icon(
                    symbol = MaterialSymbols.ExpandMore,
                    contentDescription = explainer,
                    modifier = Size20Modifier,
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            optionsShowing = true
                        }.semantics {
                            role = Role.DropdownList
                            stateDescription = accessibilityDescription
                            onClick(label = openDropdownLabel) {
                                optionsShowing = true
                                return@onClick true
                            }
                        },
            )
        }

        // Save/unsave the active place to the followed-locations list (kind 10081), so a
        // teleported spot can be kept as a permanent chip — the "add to my interests" step.
        if (filter is TopFilter.Geohash) {
            FollowLocationToggle(filter.tag, accountViewModel)
        }
    }

    var teleporting by remember { mutableStateOf(false) }

    if (optionsShowing && options.isNotEmpty()) {
        GroupedFeedFilterDialog(
            title = explainer,
            options = options,
            filtersNotifications = filtersNotifications,
            onDismiss = { optionsShowing = false },
            onSelect = { definition ->
                optionsShowing = false
                // "Teleport" isn't a real filter — open the map picker and apply the chosen
                // place as this screen's Geohash filter via the normal onSelect path.
                if (definition.code is TopFilter.TeleportPicker) {
                    teleporting = true
                } else {
                    onSelect(definition)
                }
            },
        ) {
            RenderOption(it.name, accountViewModel)
        }
    }

    if (teleporting) {
        GeohashLocationPickerDialog(
            initialGeohash = (selected?.code as? TopFilter.Geohash)?.tag,
            onDismiss = { teleporting = false },
            onConfirm = { cell ->
                teleporting = false
                onSelect(FeedDefinition(code = TopFilter.Geohash(cell), name = GeoHashName(cell)))
            },
        )
    }
}

/** A compact toggle in the filter header to follow/unfollow the active geohash location. */
@Composable
private fun FollowLocationToggle(
    tag: String,
    accountViewModel: AccountViewModel,
) {
    val isFollowing by observeUserIsFollowingGeohash(tag, accountViewModel)
    IconButton(onClick = {
        if (!accountViewModel.isWriteable()) {
            accountViewModel.toastManager.toast(
                Res.string.read_only_user,
                if (isFollowing) Res.string.login_with_a_private_key_to_be_able_to_unfollow else Res.string.login_with_a_private_key_to_be_able_to_follow,
            )
        } else if (isFollowing) {
            accountViewModel.unfollowGeohash(tag)
        } else {
            accountViewModel.followGeohash(tag)
        }
    }) {
        Icon(
            symbol = if (isFollowing) MaterialSymbols.Bookmark else MaterialSymbols.BookmarkAdd,
            contentDescription =
                stringRes(
                    if (isFollowing) Res.string.unfollow_geohash else Res.string.follow_geohash,
                ),
            modifier = Size20Modifier,
            tint = if (isFollowing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A [Name]'s displayed title, read on every composition: a [LabelName] is a string resource, and a
 * note-backed name re-reads its note when the event arrives (the caller observes the note).
 */
@Composable
private fun displayName(name: Name): String = if (name is LabelName) stringRes(name.label.resource()) else name.name()

private fun TopNavLabel.resource(): StringResource =
    when (this) {
        TopNavLabel.ALL_FOLLOWS -> Res.string.follow_list_kind3follows
        TopNavLabel.ALL_USER_FOLLOWS -> Res.string.follow_list_kind3follows_users_only
        TopNavLabel.DEFAULT_FOLLOWS -> Res.string.follow_list_kind3_follows_users_only
        TopNavLabel.GLOBAL -> Res.string.follow_list_global
        TopNavLabel.CURATED -> Res.string.follow_list_curated
        TopNavLabel.AROUND_ME -> Res.string.follow_list_aroundme
        TopNavLabel.TELEPORT -> Res.string.follow_list_teleport
        TopNavLabel.MUTE_LIST -> Res.string.follow_list_mute_list
        TopNavLabel.MINE -> Res.string.follow_list_mine
        TopNavLabel.ALL_FAVORITE_DVMS -> Res.string.follow_list_all_favorite_dvms
        TopNavLabel.MANAGE_CUSTOM_FEEDS -> Res.string.custom_feeds_manage
    }

/** Short "what does this filter show" blurb, or null when the row is an action, not a filter. */
private fun TopNavLabel.descriptionResource(): StringResource? =
    when (this) {
        TopNavLabel.ALL_FOLLOWS -> Res.string.follow_list_kind3follows_summary
        TopNavLabel.ALL_USER_FOLLOWS -> Res.string.follow_list_kind3follows_users_only_summary
        TopNavLabel.DEFAULT_FOLLOWS -> Res.string.follow_list_default_follows_summary
        TopNavLabel.GLOBAL -> Res.string.follow_list_global_summary
        TopNavLabel.CURATED -> Res.string.follow_list_curated_summary
        TopNavLabel.AROUND_ME -> Res.string.follow_list_aroundme_summary
        TopNavLabel.TELEPORT -> Res.string.follow_list_teleport_summary
        TopNavLabel.MUTE_LIST -> Res.string.follow_list_mute_list_summary
        TopNavLabel.MINE -> Res.string.follow_list_mine_summary
        TopNavLabel.ALL_FAVORITE_DVMS -> Res.string.follow_list_all_favorite_dvms_summary
        TopNavLabel.MANAGE_CUSTOM_FEEDS -> null
    }

/**
 * The description behind the ⓘ affordance for a filter row: built-ins get their own text,
 * dynamic entries (lists, hashtags, relays, …) a generic per-type one.
 *
 * The Notifications screen uses the same picker, but there a filter narrows who can notify
 * you rather than which posts a feed shows, so it gets its own text ([notificationDescriptionRes]).
 */
private fun FeedDefinition.descriptionRes(filtersNotifications: Boolean): StringResource? = if (filtersNotifications) notificationDescriptionRes() else feedDescriptionRes()

private fun FeedDefinition.feedDescriptionRes(): StringResource? =
    when (val n = name) {
        is LabelName -> n.label.descriptionResource()
        is HashtagName -> Res.string.follow_list_hashtag_summary
        is GeoHashName -> Res.string.follow_list_geohash_summary
        is CommunityName -> Res.string.follow_list_community_summary
        is PeopleListName -> Res.string.follow_list_people_list_summary
        is RelayName -> Res.string.follow_list_relay_summary
        is InterestSetName -> Res.string.follow_list_interest_set_summary
        is FavoriteAlgoFeedName -> Res.string.follow_list_algo_feed_summary
        is CustomFeedName -> Res.string.follow_list_custom_feed_summary
    }

/**
 * What a filter does to the Notifications feed (`NotificationFeedFilter`): Global keeps everything
 * that tags you, Curated drops authors outside your Web of Trust, and a list keeps only its own
 * authors, though reactions and replies to your posts pass from anyone. The other filters get no
 * ⓘ here rather than feed text that does not describe them.
 */
private fun FeedDefinition.notificationDescriptionRes(): StringResource? =
    when (val n = name) {
        is LabelName -> {
            when (n.label) {
                TopNavLabel.GLOBAL -> Res.string.follow_list_notifications_global_summary
                TopNavLabel.CURATED -> Res.string.follow_list_curated_summary
                TopNavLabel.ALL_FOLLOWS, TopNavLabel.ALL_USER_FOLLOWS -> Res.string.follow_list_notifications_all_follows_summary
                TopNavLabel.DEFAULT_FOLLOWS -> Res.string.follow_list_notifications_default_follows_summary
                TopNavLabel.MUTE_LIST -> Res.string.follow_list_notifications_mute_list_summary
                else -> null
            }
        }
        is PeopleListName -> Res.string.follow_list_notifications_people_list_summary
        else -> null
    }

@Composable
fun RenderOption(
    option: Name,
    accountViewModel: AccountViewModel,
) {
    when (option) {
        is GeoHashName -> {
            LoadCityName(option.geoHashTag) {
                Text(text = "/g/$it", fontSize = Font14SP, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Note-backed names: subscribe to the note so the displayed title updates as
        // the corresponding event arrives from relays. The displayed string itself is
        // produced by Name.name(), which already has the right precedence rules.
        is PeopleListName, is CommunityName, is FavoriteAlgoFeedName -> {
            val backed = option as NoteBackedName
            val noteState by observeNote(backed.note, accountViewModel)
            val name = displayName(option)
            val appDefAddress = (option as? FavoriteAlgoFeedName)?.note?.address
            val heartbeatFresh =
                if (appDefAddress != null) {
                    rememberDvmHeartbeatFresh(appDefAddress, accountViewModel).value
                } else {
                    true
                }
            val offlineLabel = stringRes(Res.string.dvm_offline)
            Text(
                text = if (heartbeatFresh) name else "$name \u2022",
                fontSize = Font14SP,
                color =
                    if (heartbeatFresh) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier =
                    if (heartbeatFresh) {
                        Modifier
                    } else {
                        Modifier.semantics { contentDescription = "$name, $offlineLabel" }
                    },
            )
        }

        // Pure names: no relay subscription needed.
        is HashtagName,
        is LabelName,
        is RelayName,
        is InterestSetName,
        is CustomFeedName,
        -> {
            Text(
                text = displayName(option),
                fontSize = Font14SP,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private enum class FeedGroup(
    val labelRes: StringResource,
) {
    FEEDS(Res.string.feed_group_feeds),
    CUSTOM(Res.string.feed_group_custom),
    RELAYS(Res.string.feed_group_relays),
    HASHTAGS(Res.string.feed_group_hashtags),
    INTEREST_SETS(Res.string.feed_group_interest_sets),
    LOCATIONS(Res.string.feed_group_locations),
    DVMS(Res.string.feed_group_dvms),
    COMMUNITIES(Res.string.feed_group_communities),
    LISTS(Res.string.feed_group_lists),
}

private fun FeedDefinition.group(): FeedGroup =
    when (name) {
        is HashtagName -> {
            FeedGroup.HASHTAGS
        }

        is CommunityName -> {
            FeedGroup.COMMUNITIES
        }

        is PeopleListName -> {
            FeedGroup.LISTS
        }

        is RelayName -> {
            FeedGroup.RELAYS
        }

        is GeoHashName -> {
            FeedGroup.LOCATIONS
        }

        is FavoriteAlgoFeedName -> {
            FeedGroup.DVMS
        }

        is InterestSetName -> {
            FeedGroup.INTEREST_SETS
        }

        is CustomFeedName -> {
            FeedGroup.CUSTOM
        }

        is LabelName -> {
            when (code) {
                is TopFilter.AroundMe -> FeedGroup.LOCATIONS
                is TopFilter.TeleportPicker -> FeedGroup.LOCATIONS
                is TopFilter.Global -> FeedGroup.RELAYS
                is TopFilter.Selected -> FeedGroup.RELAYS
                is TopFilter.AllFavoriteAlgoFeeds -> FeedGroup.DVMS
                is TopFilter.CustomFeed -> FeedGroup.CUSTOM
                else -> FeedGroup.FEEDS
            }
        }
    }

private fun groupFeedDefinitions(options: ImmutableList<FeedDefinition>): List<Pair<FeedGroup, List<FeedDefinition>>> {
    val grouped = options.groupBy { it.group() }
    return FeedGroup.entries.mapNotNull { group ->
        grouped[group]?.takeIf { it.isNotEmpty() }?.let { group to it }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupedFeedFilterDialog(
    title: String,
    options: ImmutableList<FeedDefinition>,
    filtersNotifications: Boolean,
    onSelect: (FeedDefinition) -> Unit,
    onDismiss: () -> Unit,
    onRenderItem: @Composable (FeedDefinition) -> Unit,
) {
    val grouped = remember(options) { groupFeedDefinitions(options) }
    var infoItem by remember { mutableStateOf<Pair<String, String>?>(null) }

    infoItem?.let { (title, text) ->
        InformationDialog(
            title = title,
            textContent = text,
            severity = ToastSeverity.INFO,
            onDismiss = { infoItem = null },
        )
    }

    M3LazyActionDialog(title = title, onDismiss = onDismiss) {
        grouped.forEach { (group, items) ->
            item(key = group) {
                GroupSection(
                    label = stringRes(group.labelRes),
                    items = items,
                    isChipLayout = group == FeedGroup.HASHTAGS,
                    filtersNotifications = filtersNotifications,
                    onSelect = onSelect,
                    onInfo = { title, text -> infoItem = title to text },
                    onRenderItem = onRenderItem,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun GroupSection(
    label: String,
    items: List<FeedDefinition>,
    isChipLayout: Boolean,
    filtersNotifications: Boolean,
    onSelect: (FeedDefinition) -> Unit,
    onInfo: (title: String, text: String) -> Unit,
    onRenderItem: @Composable (FeedDefinition) -> Unit,
) {
    val infoLabel = stringRes(Res.string.follow_list_info)

    M3ActionSection {
        Column {
            M3ActionSectionLabel(label)

            if (isChipLayout) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items.forEach { entry ->
                        val name = displayName(entry.name)
                        val infoText = entry.descriptionRes(filtersNotifications)?.let { stringRes(it) }
                        Surface(
                            modifier =
                                Modifier.combinedClickable(
                                    onLongClickLabel = infoLabel,
                                    onLongClick = infoText?.let { text -> { onInfo(name, text) } },
                                    onClick = { onSelect(entry) },
                                ),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            color = Color.Transparent,
                        ) {
                            Text(
                                text = name,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            } else {
                items.forEach { entry ->
                    val entryName = displayName(entry.name)
                    val infoText = entry.descriptionRes(filtersNotifications)?.let { stringRes(it) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(entry) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        FeedIcon(
                            item = entry,
                            modifier = Size20Modifier,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) { onRenderItem(entry) }
                        if (infoText != null) {
                            FilterInfoButton(
                                label = infoLabel,
                                onClick = { onInfo(entryName, infoText) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A compact ⓘ button that opens the filter's description dialog without selecting the row. */
@Composable
private fun FilterInfoButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(32.dp)
                .semantics { contentDescription = label }
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(
            symbol = MaterialSymbols.Info,
            contentDescription = null,
            modifier = Size16Modifier,
            tint = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

@Composable
private fun FeedIcon(
    item: FeedDefinition,
    modifier: Modifier = Modifier,
) {
    val icon =
        when (item.code) {
            is TopFilter.Global -> {
                MaterialSymbols.Public
            }

            is TopFilter.Selected -> {
                MaterialSymbols.FilterAlt
            }

            is TopFilter.AroundMe -> {
                MaterialSymbols.LocationOn
            }

            is TopFilter.TeleportPicker -> {
                MaterialSymbols.TravelExplore
            }

            is TopFilter.AllFollows -> {
                MaterialSymbols.Groups
            }

            is TopFilter.AllUserFollows -> {
                MaterialSymbols.Person
            }

            is TopFilter.DefaultFollows -> {
                MaterialSymbols.Groups
            }

            is TopFilter.MuteList -> {
                MaterialSymbols.AutoMirrored.VolumeOff
            }

            is TopFilter.PeopleList -> {
                MaterialSymbols.AutoMirrored.ViewList
            }

            is TopFilter.FavoriteAlgoFeed -> {
                MaterialSymbols.AutoAwesome
            }

            is TopFilter.AllFavoriteAlgoFeeds -> {
                MaterialSymbols.AutoAwesome
            }

            is TopFilter.CustomFeed -> {
                if (item.route != null) MaterialSymbols.Settings else MaterialSymbols.Tune
            }

            else -> {
                when (item.name) {
                    is GeoHashName -> MaterialSymbols.LocationOn
                    is RelayName -> MaterialSymbols.Storage
                    is CommunityName -> MaterialSymbols.Groups
                    is PeopleListName -> MaterialSymbols.AutoMirrored.ViewList
                    is FavoriteAlgoFeedName -> MaterialSymbols.AutoAwesome
                    else -> MaterialSymbols.Person
                }
            }
        }
    Icon(
        symbol = icon,
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
