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
package com.vitorpamplona.amethyst.commons.model.navigation

import com.vitorpamplona.amethyst.commons.privacylock.LockScope

/**
 * Which privacy lock guards this screen, or null for one no lock guards. Private messages are the
 * conversation list, every private chat room (NIP-04/17 DMs, Marmot and Cordn groups, Buzz DMs) and
 * the screens that start or forward one; public chats and channels stay open. The wallet is every
 * wallet and Cashu screen.
 */
fun Route.lockScope(): LockScope? =
    when (this) {
        is Route.Message,
        is Route.Room,
        is Route.RoomByAuthor,
        is Route.NewGroupDM,
        is Route.ShareToDM,
        is Route.MarmotGroupList,
        is Route.MarmotGroupChat,
        is Route.MarmotGroupInfo,
        is Route.MarmotGroupEditInfo,
        is Route.CordnGroupList,
        is Route.CordnGroupChat,
        is Route.CordnGroupInfo,
        is Route.CordnInvitations,
        // Drafts hold unsent private messages too.
        is Route.Drafts,
        is Route.BuzzDmList,
        is Route.BuzzNewDm,
        -> LockScope.Messages

        is Route.Wallet,
        is Route.WalletSend,
        is Route.WalletReceive,
        is Route.WalletTransactions,
        is Route.WalletDetail,
        is Route.WalletAdd,
        is Route.WalletAddNwc,
        is Route.WalletAddClinkDebit,
        is Route.CashuWallet,
        is Route.CashuWalletMints,
        is Route.CashuWalletWizard,
        is Route.CashuWalletCreated,
        is Route.CashuWalletSettings,
        is Route.CashuMintRecommendations,
        is Route.SendPayment,
        is Route.TopUpMint,
        is Route.ReloadMint,
        is Route.ManualZapSplitPayment,
        -> LockScope.Wallet

        // Any lock on also guards the secret-key backup: on desktop the device prompt there approves
        // at once, so without this the nsec would be one click from anyone at the keyboard.
        is Route.AccountBackup -> LockScope.KeyBackup

        else -> null
    }
