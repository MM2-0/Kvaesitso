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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_hosts_and_guests
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.podcasts.PodcastPerson

/**
 * A "Hosts & Guests" strip: the Podcasting-2.0 `podcast:person` credits for a show or episode,
 * rendered as a horizontally scrollable row of avatar + name + role.
 *
 * A person is usually a free-text credit (name + image URL + web link), not a Nostr user, so it's
 * drawn with the app's default profile-image loader and its link opens externally. But when the
 * publisher's `href` points at an `npub`/`nprofile`, we upgrade the card to a real Nostr profile —
 * the standard [ClickableUserPicture] + [UsernameDisplay], tappable through to the profile.
 */
@Composable
fun PodcastPeople(
    persons: List<PodcastPerson>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val people = persons.filter { it.isValid() }
    if (people.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        Text(
            text = stringRes(Res.string.podcast_hosts_and_guests),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.grayText,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(people) { person ->
                PersonItem(person, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun PersonItem(
    person: PodcastPerson,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pubKey = remember(person) { person.nostrPubKey() }

    if (pubKey != null) {
        LoadUser(pubKey) { user ->
            if (user != null) {
                NostrPersonCard(user, person.role, accountViewModel, nav)
            } else {
                FreeTextPersonCard(person, accountViewModel)
            }
        }
    } else {
        FreeTextPersonCard(person, accountViewModel)
    }
}

/** A person that resolved to a real Nostr identity — the standard profile treatment. */
@Composable
private fun NostrPersonCard(
    user: User,
    role: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    PersonCardScaffold(
        onClick = { nav.nav(routeFor(user)) },
        role = role,
        avatar = { ClickableUserPicture(user, 56.dp, accountViewModel) },
        name = {
            UsernameDisplay(
                user,
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                accountViewModel = accountViewModel,
            )
        },
    )
}

/** A free-text `podcast:person` credit — default image loader, external link. */
@Composable
private fun FreeTextPersonCard(
    person: PodcastPerson,
    accountViewModel: AccountViewModel,
) {
    val uriHandler = LocalUriHandler.current
    val href = person.href

    PersonCardScaffold(
        onClick = href?.let { { runCatching { uriHandler.openUri(it) } } },
        role = person.role,
        avatar = {
            RobohashFallbackAsyncImage(
                robot = person.name,
                model = person.img,
                contentDescription = person.name,
                modifier = Modifier.size(56.dp).clip(CircleShape),
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
            )
        },
        name = {
            Text(
                text = person.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/** Shared 72dp centered card layout: avatar, name, and an optional role line. */
@Composable
private fun PersonCardScaffold(
    onClick: (() -> Unit)?,
    role: String?,
    avatar: @Composable () -> Unit,
    name: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(72.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        avatar()
        name()
        role?.takeIf { it.isNotEmpty() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
