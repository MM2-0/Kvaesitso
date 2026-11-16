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
package com.vitorpamplona.amethyst.commons.relayClient.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip01Core.UserInfo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Shared, platform-agnostic observers for a single user's metadata (kind 0).
 *
 * Each observer both (a) opens a composition-scoped relay subscription for the
 * user via [UserFinderFilterAssemblerSubscription] — so metadata is fetched only
 * while the user is on screen — and (b) collects the resulting cache flow so the
 * UI recomposes when the metadata arrives. The `(user)` overloads read the
 * front end's [LocalUserFinder] / [LocalUserFinderAccount]; the explicit-param
 * overloads are for callers that already hold both (and for tests).
 *
 * These are metadata-only. Richer per-user observers that depend on account
 * subsystems not yet in commons (contact-card petnames, follow counts,
 * bookmarks, statuses) remain in the Android layer for now and layer on top of
 * the same subscription.
 */
@Composable
fun observeUserInfo(
    user: User,
    userFinder: UserFinderFilterAssembler,
    account: UserFinderAccount,
): State<UserInfo?> {
    UserFinderFilterAssemblerSubscription(user, account, userFinder)
    return user.metadata().flow.collectAsStateWithLifecycle()
}

@Composable
fun observeUserInfo(user: User): State<UserInfo?> = observeUserInfo(user, LocalUserFinder.current, LocalUserFinderAccount.current)

@Composable
fun observeUserPicture(
    user: User,
    userFinder: UserFinderFilterAssembler,
    account: UserFinderAccount,
    // Callers that already hold a subscription for the same user (e.g. an author avatar that
    // also observes the contact-card score) pass false to skip a redundant relay subscription.
    subscribe: Boolean = true,
): State<String?> {
    if (subscribe) {
        UserFinderFilterAssemblerSubscription(user, account, userFinder)
    }

    val flow =
        remember(user) {
            user
                .metadata()
                .flow
                .map { it?.info?.picture }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(
        user
            .metadataOrNull()
            ?.flow
            ?.value
            ?.info
            ?.picture,
    )
}

@Composable
fun observeUserPicture(user: User): State<String?> = observeUserPicture(user, LocalUserFinder.current, LocalUserFinderAccount.current)

@Composable
fun observeUserBanner(
    user: User,
    userFinder: UserFinderFilterAssembler,
    account: UserFinderAccount,
): State<String?> {
    UserFinderFilterAssemblerSubscription(user, account, userFinder)

    val flow =
        remember(user) {
            user
                .metadata()
                .flow
                .map { it?.info?.banner }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(
        user
            .metadataOrNull()
            ?.flow
            ?.value
            ?.info
            ?.banner,
    )
}

@Composable
fun observeUserBanner(user: User): State<String?> = observeUserBanner(user, LocalUserFinder.current, LocalUserFinderAccount.current)

@Composable
fun observeUserAboutMe(
    user: User,
    userFinder: UserFinderFilterAssembler,
    account: UserFinderAccount,
): State<String> {
    UserFinderFilterAssemblerSubscription(user, account, userFinder)

    val flow =
        remember(user) {
            user
                .metadata()
                .flow
                .map { it?.info?.about ?: "" }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(
        user
            .metadataOrNull()
            ?.flow
            ?.value
            ?.info
            ?.about ?: "",
    )
}

@Composable
fun observeUserAboutMe(user: User): State<String> = observeUserAboutMe(user, LocalUserFinder.current, LocalUserFinderAccount.current)

/**
 * The user's best available display name from their own metadata (kind 0),
 * falling back to a truncated pubkey. Metadata-only: it does NOT apply the
 * viewing account's private contact-card petname (that stays in the Android
 * layer, which wraps this).
 */
@Composable
fun observeUserName(
    user: User,
    userFinder: UserFinderFilterAssembler,
    account: UserFinderAccount,
): State<String> {
    UserFinderFilterAssemblerSubscription(user, account, userFinder)

    val flow =
        remember(user) {
            user
                .metadata()
                .flow
                .map { it?.info?.bestName() ?: user.toBestDisplayName() }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(user.toBestDisplayName())
}

@Composable
fun observeUserName(user: User): State<String> = observeUserName(user, LocalUserFinder.current, LocalUserFinderAccount.current)
