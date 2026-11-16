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
package com.vitorpamplona.amethyst.commons.ui.navigation.host

import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.CordnGroupChatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitNewIssueScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitNewPullRequestScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitRepositoryCodeScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitRepositoryIssuesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitRepositoryPullsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitRepositoryScreen
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address

/**
 * The shared screens that need the JVM: the git repository browser (it clones over HTTP with
 * OkHttp) and the cordn group chat (its attachments go through the JVM Blossom client).
 * Android and Desktop both register these next to [sharedDestinations]; iOS has no git browser yet.
 */
fun NavDestinations.jvmDestinations(accountViewModel: AccountViewModel) {
    composableFromEndArgs<Route.GitRepository> { GitRepositoryScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEndArgs<Route.GitRepositoryCode> { GitRepositoryCodeScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEndArgs<Route.GitRepositoryIssues> { GitRepositoryIssuesScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEndArgs<Route.GitRepositoryPulls> { GitRepositoryPullsScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEndArgs<Route.CordnGroupChat> {
        CordnGroupChatScreen(it.coordinatorPubKey, it.gid, accountViewModel, nav)
    }
    composableFromEndArgs<Route.GitRepositoryNewIssue> { GitNewIssueScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEndArgs<Route.GitRepositoryNewPullRequest> { GitNewPullRequestScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
}
