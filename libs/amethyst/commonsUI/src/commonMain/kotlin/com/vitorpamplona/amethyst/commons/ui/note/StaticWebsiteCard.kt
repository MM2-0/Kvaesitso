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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.favorites.favoriteCoordinateOf
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.napplet.isLaunchable
import com.vitorpamplona.amethyst.commons.napplet.unsupportedRequired
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_card_kind
import com.vitorpamplona.amethyst.commons.resources.napplet_card_permissions
import com.vitorpamplona.amethyst.commons.resources.napplet_card_unsupported
import com.vitorpamplona.amethyst.commons.resources.nsite_open
import com.vitorpamplona.amethyst.commons.resources.nsite_root_site
import com.vitorpamplona.amethyst.commons.resources.nsite_servers
import com.vitorpamplona.amethyst.commons.resources.nsite_source
import com.vitorpamplona.amethyst.commons.resources.nsite_website_kind
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppLauncher
import com.vitorpamplona.amethyst.commons.ui.platform.PrefetchNappletFiles
import com.vitorpamplona.amethyst.commons.ui.platform.rememberAppLauncher
import com.vitorpamplona.amethyst.commons.ui.platform.rememberNappletIconModel
import com.vitorpamplona.amethyst.commons.ui.platform.runsNappletsInline
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.favorites.FavoriteToggleButton
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import org.jetbrains.compose.resources.stringResource

internal val CardShape = RoundedCornerShape(16.dp)
internal val IconShape = RoundedCornerShape(14.dp)
internal val CardPadding = 12.dp
internal val IconSize = 56.dp

/**
 * What a napplet card shows and gates on, read off its manifest once per event (`remember(event)`)
 * instead of re-scanning the tags on every recomposition.
 */
@Immutable
class NappletCardInfo(
    event: NappletManifest,
) {
    val title: String? = event.title()
    val description: String = event.description()
    val source: String? = event.source()
    val servers: List<String> = event.servers()
    val required: List<String> = event.required()
    val artifactFiles: List<PathTag> = listOfNotNull(event.artifactFile())

    /** Valid NIP-5D, and every required domain is one this shell implements. */
    val launchable: Boolean = event.isLaunchable()

    /** The required domains this shell doesn't implement: why a valid napplet has no Open. */
    val unsupported: List<String> = event.unsupportedRequired()
}

/** What a site card shows, read off a [RootSiteEvent] or [NamedSiteEvent] once per event. */
@Immutable
class SiteCardInfo private constructor(
    val title: String?,
    val description: String?,
    val source: String?,
    val servers: List<String>,
    val identifier: String?,
    val icon: String?,
    val paths: List<PathTag>,
) {
    companion object {
        fun of(event: Event): SiteCardInfo? =
            when (event) {
                is RootSiteEvent -> SiteCardInfo(event.title(), event.description(), event.source(), event.servers(), null, event.icon(), event.paths())
                is NamedSiteEvent -> SiteCardInfo(event.title(), event.description(), event.source(), event.servers(), event.identifier(), event.icon(), event.paths())
                else -> null
            }
    }
}

/**
 * The feed card for a NIP-5A site or NIP-5D napplet, on every platform: what it is and what it would
 * access, a star that pins it to the favorite apps, and Open where the platform can run it
 * ([AppLauncher.opensNapplets]). Its files download while it is on screen, so Open starts at once.
 * Renders nothing for any other event; NoteCompose sends invalid napplet manifests to the fallback
 * card.
 */
@Composable
fun StaticWebsiteManifestCard(
    event: Event?,
    /** The screen the card is on: where the platform runs apps as screens, Open opens one there. */
    nav: INav? = null,
) {
    when (event) {
        is RootNappletEvent -> NappletManifestCard(event, identifier = null, nav)
        is NamedNappletEvent -> NappletManifestCard(event, identifier = event.identifier(), nav)
        is RootSiteEvent, is NamedSiteEvent -> SiteCard(event, nav)
        else -> {}
    }
}

@Composable
private fun NappletManifestCard(
    event: NappletManifest,
    identifier: String?,
    nav: INav?,
) {
    val info = remember(event) { NappletCardInfo(event) }
    val coordinate = remember(event) { favoriteCoordinateOf(event as Event) }
    val launcher = rememberAppLauncher()
    val icon = rememberNappletIconModel(coordinate)
    PrefetchNappletFiles((event as Event).pubKey, identifier.orEmpty(), info.artifactFiles, info.servers)
    val canOpen = info.launchable && launcher.opensNapplets
    val label = info.title ?: identifier ?: NAPPLET_LABEL
    // Where napplets run at all, a card without Open says why instead of just lacking the button.
    val notice =
        if (launcher.opensNapplets && info.unsupported.isNotEmpty()) {
            stringResource(Res.string.napplet_card_unsupported, info.unsupported.joinToString(", "))
        } else {
            null
        }

    StaticWebsiteCard(
        title = info.title,
        description = info.description,
        source = info.source,
        servers = info.servers,
        identifier = identifier,
        isNapplet = true,
        requires = info.required,
        icon = icon,
        notice = notice,
        // The card itself never executes code: Open and Run here hand the napplet to the platform's sandbox.
        onOpen =
            if (canOpen) {
                {
                    InlineNappletRuns.stopApp(coordinate)
                    launcher.openNapplet(event, nav)
                }
            } else {
                null
            },
        headerActions = {
            InlineStopButton(coordinate)
            FavoriteToggleButton(coordinate, label, icon)
        },
        stage =
            if (canOpen && runsNappletsInline) {
                { InlineNappletStage(event, coordinate, label, icon) }
            } else {
                null
            },
    )
}

@Composable
private fun SiteCard(
    event: Event,
    nav: INav?,
) {
    val info = remember(event) { SiteCardInfo.of(event) } ?: return
    val coordinate = remember(event) { favoriteCoordinateOf(event) }
    val launcher = rememberAppLauncher()
    PrefetchNappletFiles(event.pubKey, info.identifier.orEmpty(), info.paths, info.servers)

    StaticWebsiteCard(
        title = info.title,
        description = info.description,
        source = info.source,
        servers = info.servers,
        identifier = info.identifier,
        isNapplet = false,
        icon = info.icon,
        onOpen = if (info.paths.isNotEmpty() && launcher.opensNapplets) ({ launcher.openSite(event, nav) }) else null,
        headerActions = { FavoriteToggleButton(coordinate, info.title ?: info.identifier ?: NSITE_LABEL, info.icon) },
    )
}

// A favorite's label when the event has no title or identifier (a root napplet or site without one).
private const val NAPPLET_LABEL = "Napplet"
private const val NSITE_LABEL = "nsite"

/**
 * The inert, host-agnostic preview card for a NIP-5A static site or NIP-5D napplet event, styled like
 * an app-store entry: square [icon] (with a colored monogram fallback), name, a type label, a short
 * description, and an **Open** button. The technical bits a typical user doesn't care about — the
 * declared capabilities, Blossom servers, and source URL — are tucked behind a "What it can access"
 * disclosure; capabilities are also re-confirmed at the consent prompt when the napplet actually uses
 * one. It never executes applet code; launching runs in the platform host's sandbox via [onOpen]
 * (a null [onOpen] — no paths — hides the button).
 *
 * Every platform's feed renders this identical card, through [StaticWebsiteManifestCard], so they
 * can't drift.
 */
@Composable
fun StaticWebsiteCard(
    title: String?,
    description: String?,
    source: String?,
    servers: List<String>,
    identifier: String?,
    isNapplet: Boolean,
    requires: List<String> = emptyList(),
    icon: String? = null,
    // Shown under the description, e.g. why there is no Open button.
    notice: String? = null,
    onOpen: (() -> Unit)? = null,
    // Optional trailing slot in the header row (the favorite star).
    headerActions: @Composable (() -> Unit)? = null,
    // Optional slot under the description: where a napplet runs inside the note.
    stage: @Composable (() -> Unit)? = null,
) {
    val displayTitle = title?.ifBlank { null } ?: identifier?.ifBlank { null } ?: stringResource(Res.string.nsite_root_site)
    val kindLabel = stringResource(if (isNapplet) Res.string.napplet_card_kind else Res.string.nsite_website_kind)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
                .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, displayTitle)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = kindLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            headerActions?.let {
                Spacer(Modifier.width(4.dp))
                it()
            }

            onOpen?.let {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = it) { Text(stringResource(Res.string.nsite_open)) }
            }
        }

        description?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        stage?.invoke()

        if (requires.isNotEmpty() || !source.isNullOrBlank() || servers.isNotEmpty()) {
            DetailsDisclosure(requires = requires, source = source, servers = servers)
        }
    }
}

/** Square app icon: the manifest [iconUrl] when present, otherwise a colored monogram from [title]. */
@Composable
internal fun AppIcon(
    iconUrl: String?,
    title: String,
) {
    if (!iconUrl.isNullOrBlank()) {
        AsyncImage(
            model = iconUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(IconSize).clip(IconShape),
        )
    } else {
        Box(
            modifier =
                Modifier
                    .size(IconSize)
                    .clip(IconShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.trim().take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Tap-to-expand "What it can access": capability rows (icon + name) plus source/servers details. */
@Composable
private fun DetailsDisclosure(
    requires: List<String>,
    source: String?,
    servers: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            MaterialSymbols.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.napplet_card_permissions),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }

    AnimatedVisibility(
        visible = expanded,
        // Expand/collapse straight down from the top (matching the card) with a fade — never a
        // sideways slide. Each child fills width so nothing grows in from the left as it lays out.
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            requires.forEach { capability ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        capabilitySymbol(capability),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = capability.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            source?.takeIf { it.isNotBlank() }?.let {
                LabeledUrl(stringResource(Res.string.nsite_source), it)
            }

            if (servers.isNotEmpty()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(Res.string.nsite_servers),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    servers.forEach { ClickableUrl(it) }
                }
            }
        }
    }
}

@Composable
private fun LabeledUrl(
    label: String,
    url: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        ClickableUrl(url)
    }
}

/** Best-effort capability → icon map for the disclosure (host-agnostic; unknown domains fall back). */
private fun capabilitySymbol(domain: String): MaterialSymbol =
    when (domain.lowercase()) {
        "identity" -> MaterialSymbols.AccountCircle
        "keys" -> MaterialSymbols.Key
        "relay" -> MaterialSymbols.Public
        "storage" -> MaterialSymbols.Storage
        "value" -> MaterialSymbols.Bolt
        "resource" -> MaterialSymbols.Language
        "upload" -> MaterialSymbols.Upload
        "shell" -> MaterialSymbols.Tune
        else -> MaterialSymbols.Lock
    }

/** A primary-colored, single-line clickable URL that opens in the platform's default handler. */
@Composable
private fun ClickableUrl(url: String) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = url.removePrefix("https://").removePrefix("http://"),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier.clickable {
                runCatching {
                    uriHandler.openUri(if (url.contains("://")) url else "https://$url")
                }
            },
    )
}
