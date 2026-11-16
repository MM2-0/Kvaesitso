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
package com.vitorpamplona.amethyst.commons.ui.note.nip22Comments

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.external_id_scope
import com.vitorpamplona.amethyst.commons.resources.external_url_scope
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaCapture
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewCard
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.rememberUrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import com.vitorpamplona.quartz.nip73ExternalIds.topics.HashtagId
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId

/**
 * The scope a screen is currently dedicated to, if any: a normalized external id
 * (e.g. [ExternalId.toScope]) for the URL thread screen, or a community address value for the
 * community screen. A screen built entirely around one scope provides this so comments sharing
 * that same scope don't redundantly repeat the preview the screen itself already shows.
 */
val LocalCurrentExternalScope = staticCompositionLocalOf<String?> { null }

@Composable
fun DisplayExternalId(
    externalId: ExternalId,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (externalId) {
        is GeohashId -> {
            DisplayGeohashExternalId(externalId, accountViewModel, nav)
        }

        is HashtagId -> {
            DisplayHashtagExternalId(externalId, accountViewModel, nav)
        }

        is UrlId -> {
            DisplayUrlExternalId(externalId, accountViewModel, nav)
        }

        else -> {
            DisplayGenericExternalId(externalId)
        }
    }
}

@Composable
fun DisplayUrlExternalId(
    externalId: UrlId,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val url = externalId.url
    val chip =
        @Composable {
            DisplayExternalIdChip(
                symbol = MaterialSymbols.Link,
                contentDescription = stringRes(id = Res.string.external_url_scope),
                label = url,
                linkInteractionListener = { nav.nav(Route.Url(url)) },
            )
        }

    // Respect the data-saver/privacy gate: fetching the preview reaches out to the
    // third-party page, so when previews are off this stays a plain link chip.
    if (LocalMediaCapture.current == null && !accountViewModel.settings.showUrlPreview()) {
        chip()
        return
    }

    when (val state = rememberUrlPreviewState(url, accountViewModel)) {
        is UrlPreviewState.Loaded -> {
            UrlPreviewCard(url, state.previewInfo, onCardClick = { nav.nav(Route.Url(url)) })
        }

        else -> {
            chip()
        }
    }
}

@Composable
fun DisplayGenericExternalId(externalId: ExternalId) {
    DisplayExternalIdChip(
        symbol = MaterialSymbols.Public,
        contentDescription = stringRes(id = Res.string.external_id_scope),
        label = externalId.toScope(),
        linkInteractionListener = null,
    )
}

@Composable
private fun DisplayExternalIdChip(
    symbol: MaterialSymbol,
    contentDescription: String,
    label: String,
    linkInteractionListener: LinkInteractionListener?,
) {
    Row(modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            symbol = symbol,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(StdHorzSpacer)

        Text(
            text =
                if (linkInteractionListener != null) {
                    buildAnnotatedString {
                        withLink(
                            LinkAnnotation.Clickable("externalId", null, linkInteractionListener),
                        ) {
                            append(label)
                        }
                    }
                } else {
                    buildAnnotatedString { append(label) }
                },
            style =
                LocalTextStyle.current.copy(
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
