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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routeForUser
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chips_n_more
import com.vitorpamplona.amethyst.commons.resources.kind_card_linked_event
import com.vitorpamplona.amethyst.commons.resources.kind_card_more
import com.vitorpamplona.amethyst.commons.resources.kind_card_private_pill
import com.vitorpamplona.amethyst.commons.resources.kind_card_sats
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ActivityBadge
import com.vitorpamplona.amethyst.commons.ui.note.ActivityCardFrame
import com.vitorpamplona.amethyst.commons.ui.note.ActivityPill
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.elements.LocalNowSeconds
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.StatusAmber
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip31Alts.alt

/*
 * Building blocks for the cards of the kinds that are records rather than posts: protocol
 * messages, app state, moderation actions, job requests. They follow the activity cards (reactions,
 * zaps): a rounded frame washed in the card's tone, a filled badge with the kind's glyph, the title
 * saying what happened, an optional status pill, then compact facts, people chips and the event the
 * record acts on. Sharing the frame keeps the hundred-odd small renderers looking like one family.
 */

/**
 * What a card is about, which picks its color: [Primary] for ordinary records, [Positive] for
 * success and additions, [Negative] for failures, bans and removals, [Warning] for pending or
 * attention, [Money] for payments, [Neutral] for private or inert data.
 */
enum class KindTone { Primary, Positive, Negative, Warning, Money, Neutral }

@Composable
fun KindTone.color(): Color =
    when (this) {
        KindTone.Primary -> MaterialTheme.colorScheme.primary
        KindTone.Positive -> MaterialTheme.colorScheme.allGoodColor
        KindTone.Negative -> MaterialTheme.colorScheme.error
        KindTone.Warning -> StatusAmber
        KindTone.Money -> MaterialTheme.colorScheme.bitcoinColor
        KindTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/**
 * The background inner notes and rich text must draw on inside a card: transparent, so the card's
 * wash shows through. Passing the feed's background instead would paint them a solid block.
 */
private val LocalKindCardBackground = staticCompositionLocalOf<MutableState<Color>?> { null }

/**
 * What the note row drawing a card allows it: how many more levels of quotes ([quotesLeft]) and
 * whether text may load link previews and inline media ([canPreview], the text path's
 * `canPreview && !makeItShort`). Card primitives never exceed it, so a card whose text quotes
 * another card (or itself, through an naddr) stops where the row's budget stops.
 */
@Immutable
class KindCardRow(
    val quotesLeft: Int,
    val canPreview: Boolean,
)

private val LocalKindCardRow = staticCompositionLocalOf { KindCardRow(quotesLeft = 1, canPreview = true) }

/** Whether a card may load link previews and inline media here (false inside quotes and short rows). */
@Composable
fun kindCardCanPreview(): Boolean = LocalKindCardRow.current.canPreview

/** Publishes the note row's quote budget and preview permission to the card primitives below it. */
@Composable
fun ProvideKindCardRow(
    quotesLeft: Int,
    canPreview: Boolean,
    content: @Composable () -> Unit,
) {
    val row = remember(quotesLeft, canPreview) { KindCardRow(quotesLeft, canPreview) }
    CompositionLocalProvider(LocalKindCardRow provides row, content = content)
}

/**
 * [compute] at the app's shared coarse clock ([LocalNowSeconds], ticking every 30 s), for a card
 * whose state depends on the time: online/offline, live/ended, expired. Re-evaluated as the clock
 * ticks, but the card only recomposes when the answer changes, not on every tick.
 */
@Composable
fun <T> rememberAtNow(
    key: Any?,
    compute: (now: Long) -> T,
): T {
    val nowState = LocalNowSeconds.current
    val value by remember(key, nowState) { derivedStateOf { compute(nowState.value) } }
    return value
}

/**
 * The frame of a kind card: a [tone]-washed rounded frame, a badge with [icon], [title] and
 * [subtitle] beside it, an optional [status] pill on the right, then [body].
 *
 * [iconTint], when given, overrides the tone's color for the badge, the wash and the pill.
 *
 * With [framed] false the header and body draw straight on the note row, with no wash or rounded
 * box: for a card that is the whole body of a note, where the row already frames it.
 */
@Composable
fun KindCard(
    icon: MaterialSymbol,
    title: String,
    subtitle: String? = null,
    iconTint: Color? = null,
    tone: KindTone = KindTone.Primary,
    status: String? = null,
    statusTone: KindTone? = null,
    framed: Boolean = true,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val accent = iconTint ?: tone.color()
    val pillColor = statusTone?.color() ?: accent
    val cleanSubtitle = subtitle?.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }

    if (!framed) {
        // On the row's own background: inner notes and rich text keep drawing on it.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KindCardHeader(icon, title, cleanSubtitle, accent, status, pillColor)
            if (body != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    body()
                }
            }
        }
        return
    }

    ActivityCardFrame(tint = accent) { transparentBackground ->
        KindCardHeader(icon, title, cleanSubtitle, accent, status, pillColor)
        if (body != null) {
            CompositionLocalProvider(LocalKindCardBackground provides transparentBackground) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    body()
                }
            }
        }
    }
}

/** The badge, title, subtitle and status pill that open every kind card. */
@Composable
private fun KindCardHeader(
    icon: MaterialSymbol,
    title: String,
    subtitle: String?,
    accent: Color,
    status: String?,
    pillColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActivityBadge(tint = accent) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = Color.White,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        status?.let { ActivityPill(it, pillColor) }
    }
}

/**
 * One fact of a kind card: a muted label in a fixed column and its value beside it, so a card's
 * facts line up as a table. Nothing is drawn when [value] is null or blank. [monospace] values
 * (hashes, ids, paths) sit in a small code chip.
 */
@Composable
fun KindCardField(
    label: String,
    value: String?,
    monospace: Boolean = false,
    maxLines: Int = 3,
) {
    if (value.isNullOrBlank()) return
    Row(verticalAlignment = Alignment.Top) {
        KindCardFieldLabel(label)
        if (monospace) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The label column of a card's fact rows, fixed in width so the rows line up as a table. */
@Composable
private fun KindCardFieldLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(104.dp).padding(end = 8.dp, top = 1.dp),
    )
}

/**
 * A fact row like [KindCardField] whose value someone wrote in their own words (a revocation
 * reason, a role's description, a workflow note): drawn by [KindCardText], so it can be translated
 * and its links and mentions work. Nothing is drawn for blank text or a machine payload.
 */
@Composable
fun KindCardRichField(
    label: String,
    note: Note,
    text: String?,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (text.isNullOrBlank()) return
    val readable = remember(text) { !looksLikeOpaquePayload(text) }
    if (!readable) return
    Row(verticalAlignment = Alignment.Top) {
        KindCardFieldLabel(label)
        Box(Modifier.weight(1f)) {
            ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                KindCardText(note, text, backgroundColor, accountViewModel, nav)
            }
        }
    }
}

/**
 * Paragraph-length text someone wrote (a system prompt, a team's instructions, a description),
 * under an optional small [label] heading and drawn by [KindCardText]: translatable, with links,
 * mentions and the long-text expander. Nothing, not even the heading, for blank text or a payload.
 */
@Composable
fun KindCardProse(
    label: String?,
    note: Note,
    text: String?,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (text.isNullOrBlank()) return
    val readable = remember(text) { !looksLikeOpaquePayload(text) }
    if (!readable) return
    label?.let { KindCardSection(it) }
    // Secondary to the card's title, so one step smaller than a note's body text.
    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
        KindCardText(note, text, backgroundColor, accountViewModel, nav)
    }
}

/** A small heading that groups what follows it in a card: "Servers", "Repositories", "Jobs". */
@Composable
fun KindCardSection(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** A muted line of short facts joined by dots: "3 files · 1.2 MB · mainnet". */
@Composable
fun KindCardMeta(parts: List<String?>) {
    val text = remember(parts) { parts.filterNot { it.isNullOrBlank() }.joinToString("  ·  ") }
    if (text.isEmpty()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A [KindCardSection] heading over [labels] as chips; nothing when there are none. */
@Composable
fun KindCardSectionChips(
    label: String,
    labels: List<String>,
    max: Int = 12,
) {
    if (labels.isEmpty()) return
    KindCardSection(label)
    KindCardChips(labels, max)
}

/** "+N more" under a list a card shows only the start of; nothing when [count] is 0. */
@Composable
fun KindCardMore(count: Int) {
    if (count <= 0) return
    KindCardMeta(listOf(pluralStringRes(Res.plurals.chips_n_more, count, count)))
}

/** Short labels (tags, roles, capabilities, relays) as a wrapping row of chips, [max] at most. */
@Composable
fun KindCardChips(
    labels: List<String>,
    max: Int = 12,
) {
    if (labels.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.take(max).forEach { KindCardChip(it) }
        if (labels.size > max) KindCardChip(stringRes(Res.string.kind_card_more, labels.size - max), muted = true)
    }
}

@Composable
private fun KindCardChip(
    label: String,
    muted: Boolean = false,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * A block of machine text (a log, a diff, a filter) in a monospaced, inset box. Only the lines that
 * can show are laid out: a payload can be megabytes, and Text would shape all of it to draw six lines.
 */
@Composable
fun KindCardCode(
    text: String,
    maxLines: Int = 8,
) {
    if (text.isBlank()) return
    val visible = remember(text, maxLines) { firstLines(text, maxLines, maxChars = maxLines * MAX_CODE_CHARS_PER_LINE) }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = visible,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val MAX_CODE_CHARS_PER_LINE = 200

/** The first [maxLines] lines of [text], at most [maxChars] characters, with "…" when cut. */
private fun firstLines(
    text: String,
    maxLines: Int,
    maxChars: Int,
): String {
    var end = 0
    var lines = 0
    while (end < text.length && end < maxChars) {
        if (text[end] == '\n') {
            lines++
            if (lines >= maxLines) break
        }
        end++
    }
    return if (end >= text.length) text else text.substring(0, end).trimEnd() + "…"
}

/**
 * Free text the author wrote (a reason, a description, a comment), with links and mentions, behind
 * the note's content warning unless [gated] is false because the caller already gates the card.
 * [screened] is true when the caller already decided [text] is words, not a machine payload.
 */
@Composable
fun KindCardText(
    note: Note,
    text: String?,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    quotesLeft: Int = 1,
    gated: Boolean = true,
    screened: Boolean = false,
) {
    if (text.isNullOrBlank()) return
    val opaque = remember(text, screened) { !screened && looksLikeOpaquePayload(text) }
    if (opaque) return
    val tags = remember(note.event) { note.event?.tags?.toImmutableListOfLists() } ?: return
    val cardBackground = LocalKindCardBackground.current ?: backgroundColor
    val row = LocalKindCardRow.current

    val viewer: @Composable () -> Unit = {
        TranslatableRichTextViewer(
            content = text,
            canPreview = row.canPreview,
            quotesLeft = minOf(quotesLeft, row.quotesLeft),
            modifier = Modifier,
            tags = tags,
            backgroundColor = cardBackground,
            id = note.idHex,
            callbackUri = note.toNostrUri(),
            authorPubKey = note.author?.pubkeyHex,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    // The text path gated content warnings (NIP-36) here; a card's text is the same author text.
    // [gated] is false only when the caller already wraps the whole card in that gate.
    if (gated) SensitivityWarning(note = note, accountViewModel = accountViewModel, content = viewer) else viewer()
}

/** A JSON array opens with a value: a string, an object, an array or a number. "[spam] links" doesn't. */
private val JSON_ARRAY_START = Regex("^\\[\\s*[\\[{\"0-9-]")

/** Long single tokens that are meant to be shown: links, invoices, ecash and bech32 entities. */
private val READABLE_TOKEN_PREFIXES =
    listOf("nostr:", "lightning:", "lnbc", "lntb", "lnurl", "lno1", "cashu", "npub1", "nprofile1", "note1", "nevent1", "naddr1", "nrelay1")

/** An amount of sats for a card's pill or line, grouped like every other amount in the app: "21,000 sats". */
@Composable
fun kindCardSats(sats: Long): String = stringRes(Res.string.kind_card_sats, formatGrouped(sats))

/** Like [kindCardSats], from millisats, keeping a fraction instead of rounding a sub-sat fee down to "0". */
@Composable
fun kindCardMsats(msats: Long): String = stringRes(Res.string.kind_card_sats, if (msats % 1000 == 0L) formatGrouped(msats / 1000) else (msats / 1000.0).toString())

/**
 * A machine token made readable: `pay_invoice` and `timed-out` become "Pay invoice" and "Timed out"
 * (or, with [capitalize] false, "pay invoice"). For protocol words a card shows when it has no
 * translated label of its own for them.
 */
fun readableToken(
    raw: String,
    capitalize: Boolean = true,
): String {
    val spaced = raw.replace('_', ' ').replace('-', ' ')
    return if (capitalize) spaced.replaceFirstChar { if (it.isLowerCase()) it.titlecaseChar() else it } else spaced
}

/**
 * True for content that is a blob rather than words: JSON, ciphertext, a base64 image. Other
 * apps reuse many of these kinds with their own payloads, so a card checks before printing content.
 *
 * Only content that is a blob through and through counts: prose that carries one long token (an
 * invoice, a bare naddr, a cashu token) is text, and the rich-text viewer knows how to draw it.
 */
fun looksLikeOpaquePayload(text: String): Boolean {
    val trimmed = text.trim()
    // A JSON document is a payload for software, never prose.
    if (trimmed.startsWith('{') && trimmed.endsWith('}')) return true
    if (trimmed.endsWith(']') && JSON_ARRAY_START.containsMatchIn(trimmed)) return true
    // NIP-04 ciphertext (base64 with an initialization vector), short or long. Ciphertext has no
    // spaces, which keeps a sentence that happens to end in "?iv=…" out.
    if (trimmed.none { it.isWhitespace() } && EncryptedInfo.isNIP04(trimmed)) return true
    // One unbroken run of 120+ characters (line breaks allowed, as in wrapped base64) that is not
    // a link, an invoice or a nostr entity: NIP-44 ciphertext, a base64 image, a hex dump.
    if (trimmed.length < 120 || trimmed.any { it == ' ' || it == '\t' }) return false
    if (trimmed.contains("://")) return false
    return READABLE_TOKEN_PREFIXES.none { trimmed.startsWith(it, ignoreCase = true) }
}

/**
 * The people a kind card is about, as compact chips (picture and name) that open the profile.
 * Past [max] people, a "+N" chip stands for the rest.
 */
@Composable
fun KindCardUsers(
    pubKeys: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
    max: Int = 6,
) {
    val people = remember(pubKeys) { pubKeys.distinct() }
    if (people.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        people.take(max).forEach { KindCardPerson(it, accountViewModel, nav) }
        if (people.size > max) KindCardChip(stringRes(Res.string.kind_card_more, people.size - max), muted = true)
    }
}

/** One person as a chip: their picture and name, opening their profile. */
@Composable
fun KindCardPerson(
    pubKey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .clickable { nav.nav(routeForUser(pubKey)) }
                .padding(start = 2.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
    ) {
        UserPicture(pubKey, 22.dp, Modifier.clip(CircleShape), accountViewModel, nav)
        LoadUser(pubKey) { user ->
            if (user != null) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    UsernameDisplay(user, fontWeight = FontWeight.Medium, accountViewModel = accountViewModel)
                }
            }
        }
    }
}

/**
 * Quotes [note], the event a kind card acts on, as a short embedded card. Until the event arrives
 * (or when no relay has it) a one-line link to it stands in, not a large empty box.
 */
@Composable
fun KindCardQuote(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val budget = minOf(quotesLeft, LocalKindCardRow.current.quotesLeft)
    if (budget <= 0) return
    val event by observeNoteEvent<Event>(note, accountViewModel)
    if (event == null) {
        KindCardMissingReference(note, accountViewModel, nav)
        return
    }
    val parentBackground = LocalKindCardBackground.current ?: backgroundColor
    // The quoted note starts its own card tree: its cards draw on their own wash, not this one's.
    // Its row (makeItShort) publishes its own, smaller quote budget and no previews.
    CompositionLocalProvider(LocalKindCardBackground provides null) {
        NoteCompose(
            baseNote = note,
            modifier = MaterialTheme.colorScheme.replyModifier,
            isQuotedNote = true,
            unPackReply = ReplyRenderType.NONE,
            makeItShort = true,
            quotesLeft = budget - 1,
            parentBackgroundColor = parentBackground,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun KindCardMissingReference(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val label = remember(note) { note.idDisplayNote() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                .clickable { routeFor(note, accountViewModel.account)?.let { nav.nav(it) } }
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Link,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringRes(Res.string.kind_card_linked_event),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Quotes the event with id [eventId], loading it if it is not cached yet. */
@Composable
fun KindCardQuoteEvent(
    eventId: HexKey,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (minOf(quotesLeft, LocalKindCardRow.current.quotesLeft) <= 0) return
    LoadNote(eventId) { note ->
        note?.let { KindCardQuote(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** Quotes the addressable event at [address], loading it if it is not cached yet. */
@Composable
fun KindCardQuoteAddress(
    address: Address,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (minOf(quotesLeft, LocalKindCardRow.current.quotesLeft) <= 0) return
    LoadAddressableNote(address) { note ->
        note?.let { KindCardQuote(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/**
 * The card for a kind whose payload is encrypted or meant only for software: one quiet line with
 * the kind's name, a "Private" pill and the NIP-31 `alt` description when the author wrote one.
 * Never the raw content, which is ciphertext or JSON for a machine.
 */
@Composable
fun RenderPrivateKindCard(
    note: Note,
    icon: MaterialSymbol = MaterialSymbols.Lock,
) {
    val event = note.event ?: return
    val alt = remember(event) { event.alt()?.takeIf { !looksLikeOpaquePayload(it) } }
    KindCard(
        icon = icon,
        title = kindName(event.kind),
        subtitle = alt,
        tone = KindTone.Neutral,
        status = stringRes(Res.string.kind_card_private_pill),
    )
}

@Preview
@Composable
private fun KindCardPreview() {
    ThemeComparisonColumn {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KindCard(
                icon = MaterialSymbols.Gavel,
                title = "Removed from group",
                subtitle = "Spamming the channel",
                tone = KindTone.Negative,
                status = "Ban",
            ) {
                KindCardField("Group", "nostr-devs")
                KindCardField("Commit", "94f5288", monospace = true)
                KindCardMeta(listOf("3 files", "1.2 MB", "mainnet"))
                KindCardChips(listOf("admin", "moderator"))
                KindCardCode("{\"status\":\"ok\"}")
            }
            KindCard(
                icon = MaterialSymbols.Lock,
                title = "Gift wrap",
                tone = KindTone.Neutral,
                status = "Private",
            )
        }
    }
}
