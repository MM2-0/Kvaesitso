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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_archived
import com.vitorpamplona.amethyst.commons.resources.geocache_claimed
import com.vitorpamplona.amethyst.commons.resources.geocache_difficulty_short
import com.vitorpamplona.amethyst.commons.resources.geocache_first_to_find
import com.vitorpamplona.amethyst.commons.resources.geocache_found_it
import com.vitorpamplona.amethyst.commons.resources.geocache_hint_other_reading
import com.vitorpamplona.amethyst.commons.resources.geocache_hint_tap_to_reveal
import com.vitorpamplona.amethyst.commons.resources.geocache_invalid_proof
import com.vitorpamplona.amethyst.commons.resources.geocache_is_art
import com.vitorpamplona.amethyst.commons.resources.geocache_mission
import com.vitorpamplona.amethyst.commons.resources.geocache_needs_verification
import com.vitorpamplona.amethyst.commons.resources.geocache_size_large
import com.vitorpamplona.amethyst.commons.resources.geocache_size_micro
import com.vitorpamplona.amethyst.commons.resources.geocache_size_other
import com.vitorpamplona.amethyst.commons.resources.geocache_size_regular
import com.vitorpamplona.amethyst.commons.resources.geocache_size_small
import com.vitorpamplona.amethyst.commons.resources.geocache_terrain_short
import com.vitorpamplona.amethyst.commons.resources.geocache_type_multi
import com.vitorpamplona.amethyst.commons.resources.geocache_type_mystery
import com.vitorpamplona.amethyst.commons.resources.geocache_type_traditional
import com.vitorpamplona.amethyst.commons.resources.geocache_unnamed
import com.vitorpamplona.amethyst.commons.resources.geocache_verified_find
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.quartz.nip01Core.tags.geohash.toGeoHash
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.HintObfuscation
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSize
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheType
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Renders a map at the cache's location. The map is platform-specific (an Android tile view
 * today), so the host supplies it as this typed slot.
 *
 * [aspectRatio] is the card's call, not the host's: the card uses the same slot for a wide hero
 * and for a small square inset tucked into a photo, and a map that always picked its own shape
 * could only serve one of them.
 */
typealias GeocacheMap = @Composable (
    latitude: Double,
    longitude: Double,
    pinColor: Color,
    pinEmoji: String,
    pinAlpha: Float,
    aspectRatio: Float,
) -> Unit

/**
 * Whether a found log's embedded proof holds up.
 *
 * Deliberately not a boolean. "No verification attached" and "a verification that does not check
 * out" are different things and only one of them is suspicious, while "the listing has not been
 * fetched yet" must not render as either — a card that guesses here is the exact mistake
 * `GeocacheVerificationValidator` exists to prevent.
 */
enum class FoundLogProof {
    /** No `verification` tag, or the cache does not use verification at all. */
    NONE,

    /** A verification is attached but the listing needed to check it is not in hand. */
    UNKNOWN,

    /** Checked against the listing and it holds: the finder was physically at the cache. */
    VALID,

    /** Checked and rejected — wrong signer, wrong finder, wrong cache, or a bad signature. */
    INVALID,
}

private val HeroShape = RoundedCornerShape(14.dp)
private val InsetShape = RoundedCornerShape(10.dp)
private const val HERO_RATIO = 16f / 9f

/** The pin glyph for a cache type — shared so the map, the cards and the composer agree. */
fun CacheType?.geocacheEmoji(): String =
    when (this) {
        CacheType.TRADITIONAL -> "📦"
        CacheType.MULTI -> "🧭"
        CacheType.MYSTERY -> "❓"
        null -> "📍"
    }

fun CacheType?.geocacheLabelRes(): StringResource? =
    when (this) {
        CacheType.TRADITIONAL -> Res.string.geocache_type_traditional
        CacheType.MULTI -> Res.string.geocache_type_multi
        CacheType.MYSTERY -> Res.string.geocache_type_mystery
        null -> null
    }

fun CacheSize.geocacheLabelRes(): StringResource =
    when (this) {
        CacheSize.MICRO -> Res.string.geocache_size_micro
        CacheSize.SMALL -> Res.string.geocache_size_small
        CacheSize.REGULAR -> Res.string.geocache_size_regular
        CacheSize.LARGE -> Res.string.geocache_size_large
        CacheSize.OTHER -> Res.string.geocache_size_other
    }

/**
 * Self-contained card for a geocache listing (NIP-CC kind 37516).
 *
 * The layout spends its weight on the three questions a reader actually has — what is it, how far
 * away, and is it still up for grabs:
 *
 * - **One hero.** The cache's photo when it has one, with the map demoted to a corner inset that
 *   still answers "where"; the map alone when it does not. Both are 16:9. Stacking a square map
 *   *and* a wide photo, as this card first did, spent most of a phone screen before a word.
 * - **Ratings are a line, not chips.** `Traditional · Regular · D1 · T1` — the same four facts,
 *   one quiet row. As chips they outnumbered and outshouted the modifiers, which are the part
 *   worth seeing.
 * - **Colour only where it is rare.** First-to-find and verified-finds get a tint; art stays
 *   neutral; claimed and archived go muted, because they say "this one is over". A cache with
 *   every modifier set lights up four chips here rather than nine identical grey ones.
 *
 * [distance] and [claimed] are passed in rather than derived: distance needs the reader's
 * location, and a first-to-find claim needs the cache's logs. A card has neither.
 *
 * [showImages] is the reader's "automatically show images" setting, and it has no default on
 * purpose. The photo URL is supplied by whoever published the cache, so fetching it announces
 * the reader's IP to a stranger's server and spends their data — exactly what that setting
 * exists to let them refuse. A caller that cannot answer the question should pass `false`.
 */
@Composable
fun GeocacheCard(
    noteEvent: GeocacheListingEvent,
    showImages: Boolean,
    claimed: Boolean = false,
    distance: String? = null,
    map: GeocacheMap,
) {
    val name = remember(noteEvent) { noteEvent.cacheName()?.trim().orEmpty() }
    val description = remember(noteEvent) { noteEvent.content.trim() }
    val point = remember(noteEvent) { noteEvent.geocachePoint() }
    val type = remember(noteEvent) { noteEvent.cacheType() }
    val size = remember(noteEvent) { noteEvent.cacheSize() }
    val difficulty = remember(noteEvent) { noteEvent.difficulty() }
    val terrain = remember(noteEvent) { noteEvent.terrain() }
    val hint = remember(noteEvent) { noteEvent.hintOnWire()?.trim()?.ifBlank { null } }
    val mission = remember(noteEvent) { noteEvent.mission()?.trim()?.ifBlank { null } }
    val isArchived = remember(noteEvent) { noteEvent.isArchived() }
    val isFirstToFind = remember(noteEvent) { noteEvent.isFirstToFind() }
    val isArt = remember(noteEvent) { noteEvent.hasTypeModifier(TypeModifier.ART) }
    val needsVerification = remember(noteEvent) { noteEvent.requiresVerification() }
    val photo =
        remember(noteEvent) {
            noteEvent
                .images()
                .firstOrNull()
                ?.trim()
                ?.ifBlank { null }
        }

    // An archived cache, or one whose single claim is taken, is history rather than an
    // invitation — the map pin is dimmed to say so without hiding the cache.
    val over = isArchived || claimed
    val pinAlpha = if (over) 0.45f else 1f
    val accent = MaterialTheme.colorScheme.primary

    Column(MaterialTheme.colorScheme.replyModifier) {
        GeocacheHero(if (showImages) photo else null, point, accent, type.geocacheEmoji(), pinAlpha, map)

        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${type.geocacheEmoji()}  ${name.ifEmpty { stringResource(Res.string.geocache_unnamed) }}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (distance != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = distance,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        maxLines = 1,
                    )
                }
            }

            GeocacheSpecLine(type, size, difficulty, terrain)

            GeocacheChips(
                claimed = claimed,
                isArchived = isArchived,
                isFirstToFind = isFirstToFind,
                isArt = isArt,
                hasMission = mission != null,
                needsVerification = needsVerification,
            )

            if (description.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (mission != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "🗝  $mission",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (hint != null) {
                Spacer(Modifier.height(8.dp))
                GeocacheSpoilerHint(hint)
            }
        }
    }
}

/**
 * The photo if there is one, the map otherwise — never both stacked.
 *
 * With a photo, the map rides along as a small inset so "where" is still answered without
 * spending a second full-width block on it. With neither, the card opens straight on its title.
 */
@Composable
fun GeocacheHero(
    photo: String?,
    point: Pair<Double, Double>?,
    accent: Color,
    emoji: String,
    pinAlpha: Float,
    map: GeocacheMap,
) {
    when {
        photo != null ->
            Box(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp)) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(HERO_RATIO).clip(HeroShape),
                )
                if (point != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .width(76.dp)
                            .clip(InsetShape)
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        map(point.first, point.second, accent, emoji, pinAlpha, 1f)
                    }
                }
            }

        point != null ->
            Box(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp).clip(HeroShape)) {
                map(point.first, point.second, accent, emoji, pinAlpha, HERO_RATIO)
            }

        else -> Spacer(Modifier.height(2.dp))
    }
}

/**
 * `Traditional · Regular · D3 · T2` as a string, for callers that need the facts without the
 * [GeocacheSpecLine] styling — the hero renders it white on a scrim, which no amount of
 * parameters on a Text would express as clearly as handing over the words.
 */
@Composable
fun geocacheSpecSummary(
    type: CacheType?,
    size: CacheSize?,
    difficulty: Int?,
    terrain: Int?,
): String =
    listOfNotNull(
        type.geocacheLabelRes()?.let { stringResource(it) },
        size?.let { stringResource(it.geocacheLabelRes()) },
        difficulty?.let { stringResource(Res.string.geocache_difficulty_short, it) },
        terrain?.let { stringResource(Res.string.geocache_terrain_short, it) },
    ).joinToString("  ·  ")

/** The same facts for a whole listing. */
@Composable
fun GeocacheListingEvent.specSummary(): String = geocacheSpecSummary(cacheType(), cacheSize(), difficulty(), terrain())

/** `Traditional · Regular · D1 · T1` — the ratings, in one line they can be skimmed past. */
@Composable
fun GeocacheSpecLine(
    type: CacheType?,
    size: CacheSize?,
    difficulty: Int?,
    terrain: Int?,
) {
    val parts =
        listOfNotNull(
            type.geocacheLabelRes()?.let { stringResource(it) },
            size?.let { stringResource(it.geocacheLabelRes()) },
            difficulty?.let { stringResource(Res.string.geocache_difficulty_short, it) },
            terrain?.let { stringResource(Res.string.geocache_terrain_short, it) },
        )
    if (parts.isEmpty()) return

    Text(
        text = parts.joinToString("  ·  "),
        modifier = Modifier.padding(top = 3.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Only the facts that make one cache different from the next.
 *
 * The ratings moved to [SpecLine], so what is left is what a reader would change plans for: a
 * prize nobody has taken, a find that can be proved, a cache that is actually a piece of art —
 * and, muted, the two states that mean there is nothing left to go and get.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeocacheChips(
    claimed: Boolean,
    isArchived: Boolean,
    isFirstToFind: Boolean,
    isArt: Boolean,
    hasMission: Boolean,
    needsVerification: Boolean,
) {
    if (!claimed && !isArchived && !isFirstToFind && !isArt && !hasMission && !needsVerification) return

    // The tints are backgrounds rather than text colors on purpose: the theme's amber is
    // unreadable as text on a light ground, and onSurface over a wash reads in both themes.
    val gold = MaterialTheme.colorScheme.warningColor.copy(alpha = 0.22f)
    val green = MaterialTheme.colorScheme.allGoodColor.copy(alpha = 0.18f)
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

    FlowRow(
        modifier = Modifier.padding(top = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isFirstToFind && !claimed) GeocacheChip("🥇  " + stringResource(Res.string.geocache_first_to_find), gold, strong = true)
        if (needsVerification) GeocacheChip("🔐  " + stringResource(Res.string.geocache_needs_verification), green, strong = true)
        if (isArt) GeocacheChip("🎨  " + stringResource(Res.string.geocache_is_art), muted)
        if (hasMission) GeocacheChip("🗝  " + stringResource(Res.string.geocache_mission), muted)
        if (claimed) GeocacheChip("🏁  " + stringResource(Res.string.geocache_claimed), muted, dim = true)
        if (isArchived) GeocacheChip("🗃️  " + stringResource(Res.string.geocache_archived), muted, dim = true)
    }
}

/**
 * The hint: an invitation, then the text, then the other reading of the text.
 *
 * Shows *no* hint text while hidden — not even the encoded form. Geocaching clients have always
 * done it this way (Lightning Piggy's detail screen offers "Stuck? Tap to reveal the hint"), and
 * it is the stronger design for two reasons: a wall of ROT13 reads as corruption rather than as
 * a hint, and nothing on screen can spoil a find no matter which way round the publisher
 * encoded it.
 *
 * The third state is the one that matters. Publishers disagree about whether `hint` goes on the
 * wire plain or rotated (see [HintObfuscation]), so which form is the readable one is a guess,
 * and the guess assumes English — a plaintext Spanish hint scores as ciphertext and "reveals"
 * to noise. Tapping again shows the other rotation, so a misfire costs a tap rather than the
 * hint. Without that, a reader whose language the heuristic does not speak has no way to reach
 * their own hint at all.
 */
@Composable
fun GeocacheSpoilerHint(hint: String) {
    val readings = remember(hint) { listOf(HintObfuscation.revealed(hint), HintObfuscation.hidden(hint)) }
    var shown by remember(hint) { mutableStateOf(0) }

    Column(Modifier.clickable { shown = (shown + 1) % 3 }) {
        if (shown == 0) {
            Text(
                text = stringResource(Res.string.geocache_hint_tap_to_reveal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        } else {
            Text(
                text = readings[shown - 1],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (shown == 1) {
                Text(
                    text = stringResource(Res.string.geocache_hint_other_reading),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }
    }
}

/**
 * Self-contained card for a found log (NIP-CC kind 7516).
 *
 * The log's own message with a "Found it!" pill, and a proof badge only where [proof] has
 * actually been checked. A log is a claim until something verifies it, so nothing here upgrades
 * an attached verification into a verified one.
 *
 * [cacheName] is what the log is *about*, and without it the card says only "Found it!" — the
 * one thing a reader scrolling a feed already assumed. The cache is named by an `a` tag, so the
 * name costs a lookup the caller has to do anyway; pass null while it is still loading.
 *
 * [showImages] gates the log's photo for the same reason it gates the cache's — see [GeocacheCard].
 */
@Composable
fun GeocacheFoundLogCard(
    noteEvent: GeocacheFoundLogEvent,
    showImages: Boolean,
    proof: FoundLogProof = FoundLogProof.NONE,
    cacheName: String? = null,
) {
    val message = remember(noteEvent) { noteEvent.content.trim() }
    val photo =
        remember(noteEvent, showImages) {
            if (!showImages) {
                null
            } else {
                noteEvent
                    .images()
                    .firstOrNull()
                    ?.trim()
                    ?.ifBlank { null }
            }
        }

    Column(MaterialTheme.colorScheme.replyModifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "🎯  " + (cacheName ?: stringResource(Res.string.geocache_found_it)),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (cacheName != null || proof == FoundLogProof.VALID || proof == FoundLogProof.INVALID) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (cacheName != null) {
                        Text(
                            text = stringResource(Res.string.geocache_found_it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    }
                    when (proof) {
                        FoundLogProof.VALID ->
                            GeocacheChip(
                                "✅  " + stringResource(Res.string.geocache_verified_find),
                                MaterialTheme.colorScheme.allGoodColor.copy(alpha = 0.18f),
                                strong = true,
                            )
                        FoundLogProof.INVALID ->
                            GeocacheChip(
                                "⚠️  " + stringResource(Res.string.geocache_invalid_proof),
                                MaterialTheme.colorScheme.warningColor.copy(alpha = 0.22f),
                                strong = true,
                            )
                        FoundLogProof.NONE, FoundLogProof.UNKNOWN -> Unit
                    }
                }
            }

            if (message.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (photo != null) {
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(HERO_RATIO).clip(HeroShape),
                )
            }
        }
    }
}

/** One fact, on a [tint] wash. [strong] bolds it; [dim] fades it to "this is over". */
@Composable
fun GeocacheChip(
    label: String,
    tint: Color,
    strong: Boolean = false,
    dim: Boolean = false,
) {
    Text(
        text = label,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(tint).padding(horizontal = 9.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        color = if (dim) MaterialTheme.colorScheme.placeholderText else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

/**
 * The centre of the cache's finest published geohash, or null when it published none.
 *
 * A listing has no `lat`/`lon` tags — unlike a road event — so the geohash is the only location
 * there is, and the finest one is the only one precise enough to walk to.
 */
fun GeocacheListingEvent.geocachePoint(): Pair<Double, Double>? {
    val finest = location() ?: return null
    val decoded = runCatching { finest.toGeoHash() }.getOrNull() ?: return null
    return decoded.centerLat to decoded.centerLon
}
