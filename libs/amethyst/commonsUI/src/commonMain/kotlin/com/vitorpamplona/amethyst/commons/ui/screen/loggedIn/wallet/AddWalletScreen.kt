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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.brand.PaymentBrandIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.wallet_add_cashu_description
import com.vitorpamplona.amethyst.commons.resources.wallet_add_cashu_title
import com.vitorpamplona.amethyst.commons.resources.wallet_add_choose_type
import com.vitorpamplona.amethyst.commons.resources.wallet_add_clink_description
import com.vitorpamplona.amethyst.commons.resources.wallet_add_clink_title
import com.vitorpamplona.amethyst.commons.resources.wallet_add_nwc_description
import com.vitorpamplona.amethyst.commons.resources.wallet_add_nwc_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import androidx.compose.material3.Icon as Material3Icon

/**
 * Wallet-type chooser. Each card routes to a dedicated setup screen for the
 * selected wallet kind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWalletScreen(
    @Suppress("UNUSED_PARAMETER") accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.wallet_add_choose_type)) },
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                                contentDescription = stringRes(Res.string.back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WalletTypeCard(
                icon = PaymentBrandIcons.Nwc,
                title = stringRes(Res.string.wallet_add_nwc_title),
                description = stringRes(Res.string.wallet_add_nwc_description),
                // Replace the chooser in the back stack rather than stacking
                // the form on top: once the user picks a wallet type, the
                // chooser has done its job. Without this, completing the
                // add-wallet form pops back to the chooser, which is a
                // pointless dead end.
                onClick = { nav.popUpTo(Route.WalletAddNwc(), Route.WalletAdd::class) },
            )
            WalletTypeCard(
                icon = PaymentBrandIcons.Cashu,
                title = stringRes(Res.string.wallet_add_cashu_title),
                description = stringRes(Res.string.wallet_add_cashu_description),
                // Route through the find-or-create wizard, not straight to the
                // mint manager: a portable NIP-60 wallet may already exist on
                // relays, and creating one here would clobber it (kind:17375 is
                // replaceable). The wizard offers "create new" when none is found.
                onClick = { nav.popUpTo(Route.CashuWalletWizard, Route.WalletAdd::class) },
            )
            WalletTypeCard(
                icon = PaymentBrandIcons.Clink,
                title = stringRes(Res.string.wallet_add_clink_title),
                description = stringRes(Res.string.wallet_add_clink_description),
                onClick = { nav.popUpTo(Route.WalletAddClinkDebit(), Route.WalletAdd::class) },
            )
        }
    }
}

@Composable
private fun WalletTypeCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        WalletTypeCardContent(icon = icon, title = title, description = description)
    }
}

@Composable
private fun WalletTypeCardContent(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Material3Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            // Brand marks carry their own colors; Unspecified keeps them.
            tint = Color.Unspecified,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            symbol = MaterialSymbols.AutoMirrored.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}
