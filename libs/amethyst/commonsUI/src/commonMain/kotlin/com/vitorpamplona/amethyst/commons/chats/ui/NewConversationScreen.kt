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
package com.vitorpamplona.amethyst.commons.chats.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_invitations_entry_action
import com.vitorpamplona.amethyst.commons.resources.new_conversation_best_for
import com.vitorpamplona.amethyst.commons.resources.new_conversation_concord_best
import com.vitorpamplona.amethyst.commons.resources.new_conversation_concord_chip
import com.vitorpamplona.amethyst.commons.resources.new_conversation_concord_con_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_concord_cta
import com.vitorpamplona.amethyst.commons.resources.new_conversation_concord_pro_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_concord_pro_2
import com.vitorpamplona.amethyst.commons.resources.new_conversation_concord_tagline
import com.vitorpamplona.amethyst.commons.resources.new_conversation_concord_title
import com.vitorpamplona.amethyst.commons.resources.new_conversation_cons
import com.vitorpamplona.amethyst.commons.resources.new_conversation_cordn_best
import com.vitorpamplona.amethyst.commons.resources.new_conversation_cordn_chip
import com.vitorpamplona.amethyst.commons.resources.new_conversation_cordn_con_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_cordn_cta
import com.vitorpamplona.amethyst.commons.resources.new_conversation_cordn_pro_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_cordn_pro_2
import com.vitorpamplona.amethyst.commons.resources.new_conversation_cordn_tagline
import com.vitorpamplona.amethyst.commons.resources.new_conversation_cordn_title
import com.vitorpamplona.amethyst.commons.resources.new_conversation_dm_best
import com.vitorpamplona.amethyst.commons.resources.new_conversation_dm_chip
import com.vitorpamplona.amethyst.commons.resources.new_conversation_dm_con_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_dm_cta
import com.vitorpamplona.amethyst.commons.resources.new_conversation_dm_pro_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_dm_pro_2
import com.vitorpamplona.amethyst.commons.resources.new_conversation_dm_tagline
import com.vitorpamplona.amethyst.commons.resources.new_conversation_dm_title
import com.vitorpamplona.amethyst.commons.resources.new_conversation_ephemeral_best
import com.vitorpamplona.amethyst.commons.resources.new_conversation_ephemeral_chip
import com.vitorpamplona.amethyst.commons.resources.new_conversation_ephemeral_con_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_ephemeral_con_2
import com.vitorpamplona.amethyst.commons.resources.new_conversation_ephemeral_cta
import com.vitorpamplona.amethyst.commons.resources.new_conversation_ephemeral_pro_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_ephemeral_pro_2
import com.vitorpamplona.amethyst.commons.resources.new_conversation_ephemeral_tagline
import com.vitorpamplona.amethyst.commons.resources.new_conversation_ephemeral_title
import com.vitorpamplona.amethyst.commons.resources.new_conversation_location_best
import com.vitorpamplona.amethyst.commons.resources.new_conversation_location_chip
import com.vitorpamplona.amethyst.commons.resources.new_conversation_location_con_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_location_cta
import com.vitorpamplona.amethyst.commons.resources.new_conversation_location_pro_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_location_pro_2
import com.vitorpamplona.amethyst.commons.resources.new_conversation_location_tagline
import com.vitorpamplona.amethyst.commons.resources.new_conversation_location_title
import com.vitorpamplona.amethyst.commons.resources.new_conversation_marmot_best
import com.vitorpamplona.amethyst.commons.resources.new_conversation_marmot_chip
import com.vitorpamplona.amethyst.commons.resources.new_conversation_marmot_con_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_marmot_cta
import com.vitorpamplona.amethyst.commons.resources.new_conversation_marmot_pro_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_marmot_pro_2
import com.vitorpamplona.amethyst.commons.resources.new_conversation_marmot_tagline
import com.vitorpamplona.amethyst.commons.resources.new_conversation_marmot_title
import com.vitorpamplona.amethyst.commons.resources.new_conversation_pros
import com.vitorpamplona.amethyst.commons.resources.new_conversation_public_chat_best
import com.vitorpamplona.amethyst.commons.resources.new_conversation_public_chat_chip
import com.vitorpamplona.amethyst.commons.resources.new_conversation_public_chat_con_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_public_chat_con_2
import com.vitorpamplona.amethyst.commons.resources.new_conversation_public_chat_cta
import com.vitorpamplona.amethyst.commons.resources.new_conversation_public_chat_pro_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_public_chat_pro_2
import com.vitorpamplona.amethyst.commons.resources.new_conversation_public_chat_tagline
import com.vitorpamplona.amethyst.commons.resources.new_conversation_public_chat_title
import com.vitorpamplona.amethyst.commons.resources.new_conversation_relay_group_best
import com.vitorpamplona.amethyst.commons.resources.new_conversation_relay_group_chip
import com.vitorpamplona.amethyst.commons.resources.new_conversation_relay_group_con_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_relay_group_cta
import com.vitorpamplona.amethyst.commons.resources.new_conversation_relay_group_pro_1
import com.vitorpamplona.amethyst.commons.resources.new_conversation_relay_group_pro_2
import com.vitorpamplona.amethyst.commons.resources.new_conversation_relay_group_tagline
import com.vitorpamplona.amethyst.commons.resources.new_conversation_relay_group_title
import com.vitorpamplona.amethyst.commons.resources.new_conversation_section_direct
import com.vitorpamplona.amethyst.commons.resources.new_conversation_section_encrypted
import com.vitorpamplona.amethyst.commons.resources.new_conversation_section_relay
import com.vitorpamplona.amethyst.commons.resources.new_conversation_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import org.jetbrains.compose.resources.StringResource

// Per-type accent colors. Each protocol gets its own hue so private-vs-public reads at a glance
// instead of via text. Used at full strength for the icon tile (white glyph on top) and, tint-mixed,
// for the type's chip / expanded accents. In dark theme the accent is lightened for legibility on the
// dark ground (see [ConversationRow]); the solid tile keeps the saturated base either way.
private val ColorPrivate = Color(0xFF7C3AED)
private val ColorMarmot = Color(0xFF4F46E5)
private val ColorConcord = Color(0xFF0F766E)
private val ColorCordn = Color(0xFF14B8A6)
private val ColorPublic = Color(0xFFB45309)
private val ColorRelay = Color(0xFF2563EB)
private val ColorEphemeral = Color(0xFFC2410C)
private val ColorLocation = Color(0xFF0891B2)

/**
 * One selectable conversation type. Collapsed, a row shows only the icon, name, a short tagline, and
 * a one-word [chip] naming its deciding axis (scale / device-bound / moderation / live). Tapping the
 * row expands it to reveal [bestFor] and the [pros]/[cons] before the [cta] button routes to that
 * type's existing creation (or browse) flow. A type whose joining is its own flow (rather than
 * something that just shows up in the inbox) adds it as [secondary], an outlined button under
 * the main one, so "join" sits on the same card as "create" instead of floating elsewhere.
 */
@Immutable
private class ConversationType(
    val icon: MaterialSymbol,
    val color: Color,
    val title: StringResource,
    val tagline: StringResource,
    val chip: StringResource,
    val bestFor: StringResource,
    val cta: StringResource,
    val pros: List<StringResource>,
    val cons: List<StringResource>,
    val route: Route,
    val secondary: SecondaryAction? = null,
)

@Immutable
private class SecondaryAction(
    val label: StringResource,
    val route: Route,
)

@Immutable
private class ConversationSection(
    val header: StringResource,
    val types: List<ConversationType>,
)

// Grouped by intent so the six protocols read as three simple buckets: talk privately, run an
// encrypted group, or use a relay-aware room (where the relay can see/moderate who's there).
private val conversationSections =
    listOf(
        ConversationSection(
            header = Res.string.new_conversation_section_direct,
            types =
                listOf(
                    ConversationType(
                        icon = MaterialSymbols.Mail,
                        color = ColorPrivate,
                        title = Res.string.new_conversation_dm_title,
                        tagline = Res.string.new_conversation_dm_tagline,
                        chip = Res.string.new_conversation_dm_chip,
                        bestFor = Res.string.new_conversation_dm_best,
                        cta = Res.string.new_conversation_dm_cta,
                        pros = listOf(Res.string.new_conversation_dm_pro_1, Res.string.new_conversation_dm_pro_2),
                        cons = listOf(Res.string.new_conversation_dm_con_1),
                        route = Route.NewGroupDM(),
                    ),
                ),
        ),
        ConversationSection(
            header = Res.string.new_conversation_section_encrypted,
            types =
                listOf(
                    ConversationType(
                        icon = MaterialSymbols.Lock,
                        color = ColorMarmot,
                        title = Res.string.new_conversation_marmot_title,
                        tagline = Res.string.new_conversation_marmot_tagline,
                        chip = Res.string.new_conversation_marmot_chip,
                        bestFor = Res.string.new_conversation_marmot_best,
                        cta = Res.string.new_conversation_marmot_cta,
                        pros = listOf(Res.string.new_conversation_marmot_pro_1, Res.string.new_conversation_marmot_pro_2),
                        cons = listOf(Res.string.new_conversation_marmot_con_1),
                        route = Route.CreateMarmotGroup,
                    ),
                    ConversationType(
                        icon = MaterialSymbols.Groups,
                        color = ColorConcord,
                        title = Res.string.new_conversation_concord_title,
                        tagline = Res.string.new_conversation_concord_tagline,
                        chip = Res.string.new_conversation_concord_chip,
                        bestFor = Res.string.new_conversation_concord_best,
                        cta = Res.string.new_conversation_concord_cta,
                        pros = listOf(Res.string.new_conversation_concord_pro_1, Res.string.new_conversation_concord_pro_2),
                        cons = listOf(Res.string.new_conversation_concord_con_1),
                        route = Route.ConcordCreate,
                    ),
                    ConversationType(
                        icon = MaterialSymbols.Dns,
                        color = ColorCordn,
                        title = Res.string.new_conversation_cordn_title,
                        tagline = Res.string.new_conversation_cordn_tagline,
                        chip = Res.string.new_conversation_cordn_chip,
                        bestFor = Res.string.new_conversation_cordn_best,
                        cta = Res.string.new_conversation_cordn_cta,
                        pros = listOf(Res.string.new_conversation_cordn_pro_1, Res.string.new_conversation_cordn_pro_2),
                        // The §8 exposure, stated where the choice is made
                        // rather than discovered later: a coordinator learns
                        // who is in which group and when they talk, even though
                        // it can never read a word. The whole point of the
                        // exposure work was to say this before someone commits
                        // to it, and this is the first place it can be said.
                        cons = listOf(Res.string.new_conversation_cordn_con_1),
                        route = Route.CordnCreateGroup,
                        // Being invited is the other half of "start a
                        // conversation", so it sits on the same card as
                        // creating one. It is a button you press, never a
                        // count: a count would take a call to every
                        // coordinator, and every call to a coordinator is
                        // metadata (spec/00.md §8).
                        secondary = SecondaryAction(Res.string.cordn_invitations_entry_action, Route.CordnInvitations),
                    ),
                ),
        ),
        ConversationSection(
            header = Res.string.new_conversation_section_relay,
            types =
                listOf(
                    ConversationType(
                        icon = MaterialSymbols.Public,
                        color = ColorPublic,
                        title = Res.string.new_conversation_public_chat_title,
                        tagline = Res.string.new_conversation_public_chat_tagline,
                        chip = Res.string.new_conversation_public_chat_chip,
                        bestFor = Res.string.new_conversation_public_chat_best,
                        cta = Res.string.new_conversation_public_chat_cta,
                        pros = listOf(Res.string.new_conversation_public_chat_pro_1, Res.string.new_conversation_public_chat_pro_2),
                        cons = listOf(Res.string.new_conversation_public_chat_con_1, Res.string.new_conversation_public_chat_con_2),
                        route = Route.ChannelMetadataEdit(),
                    ),
                    ConversationType(
                        icon = MaterialSymbols.Dns,
                        color = ColorRelay,
                        title = Res.string.new_conversation_relay_group_title,
                        tagline = Res.string.new_conversation_relay_group_tagline,
                        chip = Res.string.new_conversation_relay_group_chip,
                        bestFor = Res.string.new_conversation_relay_group_best,
                        cta = Res.string.new_conversation_relay_group_cta,
                        pros = listOf(Res.string.new_conversation_relay_group_pro_1, Res.string.new_conversation_relay_group_pro_2),
                        cons = listOf(Res.string.new_conversation_relay_group_con_1),
                        route = Route.RelayGroupBrowse,
                    ),
                    ConversationType(
                        icon = MaterialSymbols.Timer,
                        color = ColorEphemeral,
                        title = Res.string.new_conversation_ephemeral_title,
                        tagline = Res.string.new_conversation_ephemeral_tagline,
                        chip = Res.string.new_conversation_ephemeral_chip,
                        bestFor = Res.string.new_conversation_ephemeral_best,
                        cta = Res.string.new_conversation_ephemeral_cta,
                        pros = listOf(Res.string.new_conversation_ephemeral_pro_1, Res.string.new_conversation_ephemeral_pro_2),
                        cons = listOf(Res.string.new_conversation_ephemeral_con_1, Res.string.new_conversation_ephemeral_con_2),
                        route = Route.NewEphemeralChat,
                    ),
                    ConversationType(
                        icon = MaterialSymbols.LocationOn,
                        color = ColorLocation,
                        title = Res.string.new_conversation_location_title,
                        tagline = Res.string.new_conversation_location_tagline,
                        chip = Res.string.new_conversation_location_chip,
                        bestFor = Res.string.new_conversation_location_best,
                        cta = Res.string.new_conversation_location_cta,
                        pros = listOf(Res.string.new_conversation_location_pro_1, Res.string.new_conversation_location_pro_2),
                        cons = listOf(Res.string.new_conversation_location_con_1),
                        route = Route.NewGeohashChat,
                    ),
                ),
        ),
    )

@Composable
fun NewConversationScreen(nav: INav) {
    // Accordion: at most one row expanded, keyed by its (unique) title resource key. The key is
    // the resource's String id rather than the StringResource itself, which is not Saveable.
    // Survives config changes so an opened card stays open on rotation.
    var expandedId by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.new_conversation_title), nav) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = pad.calculateTopPadding() + 4.dp,
                    bottom = pad.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            conversationSections.forEach { section ->
                item(key = section.header.key) {
                    Text(
                        text = stringRes(section.header),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.grayText,
                        modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                    )
                }

                section.types.forEach { type ->
                    item(key = type.title.key) {
                        ConversationRow(
                            type = type,
                            expanded = expandedId == type.title.key,
                            onToggle = { expandedId = if (expandedId == type.title.key) "" else type.title.key },
                            onCreate = { nav.nav(type.route) },
                            onSecondary = { type.secondary?.let { nav.nav(it.route) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    type: ConversationType,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCreate: () -> Unit,
    onSecondary: () -> Unit,
) {
    // Lighten the accent in dark mode so chip/label/checkmark text stays legible on the dark ground;
    // the solid icon tile keeps the saturated base color in both themes.
    val accent = if (isSystemInDarkTheme()) lerp(type.color, Color.White, 0.42f) else type.color

    ElevatedCard(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.animateContentSize().padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = type.color,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            symbol = type.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(Modifier.width(13.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringRes(type.title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringRes(type.tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(8.dp))

                AxisChip(stringRes(type.chip), accent)
            }

            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 13.dp, bottom = 12.dp),
                    color = accent.copy(alpha = 0.22f),
                )

                Column {
                    ColumnHeader(stringRes(Res.string.new_conversation_best_for))
                    Text(
                        text = stringRes(type.bestFor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                        modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        ColumnHeader(stringRes(Res.string.new_conversation_pros))
                        Spacer(Modifier.height(4.dp))
                        type.pros.forEach { ProConRow(it, accent, isPro = true) }
                    }
                    Column(Modifier.weight(1f)) {
                        ColumnHeader(stringRes(Res.string.new_conversation_cons))
                        Spacer(Modifier.height(4.dp))
                        type.cons.forEach { ProConRow(it, accent, isPro = false) }
                    }
                }

                Button(
                    onClick = onCreate,
                    modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = type.color, contentColor = Color.White),
                ) {
                    Text(stringRes(type.cta), fontWeight = FontWeight.Bold)
                }

                // Stacked rather than side by side: at phone width each half
                // would be ~150dp and the bold primary label would wrap.
                type.secondary?.let { secondary ->
                    OutlinedButton(
                        onClick = onSecondary,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, accent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                    ) {
                        Text(stringRes(secondary.label), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AxisChip(
    label: String,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.14f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ColumnHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.grayText,
    )
}

@Composable
private fun ProConRow(
    text: StringResource,
    accent: Color,
    isPro: Boolean,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Icon(
            symbol = if (isPro) MaterialSymbols.Check else MaterialSymbols.Close,
            contentDescription = null,
            tint = if (isPro) accent else MaterialTheme.colorScheme.grayText,
            modifier = Modifier.padding(top = 1.dp).size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringRes(text),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview
@Composable
private fun NewConversationScreenPreview() {
    ThemeComparisonColumn {
        NewConversationScreen(nav = EmptyNav())
    }
}
