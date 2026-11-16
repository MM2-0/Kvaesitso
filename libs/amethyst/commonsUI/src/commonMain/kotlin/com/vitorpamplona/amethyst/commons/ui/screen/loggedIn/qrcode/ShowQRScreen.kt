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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.Nip05State
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.profile_image
import com.vitorpamplona.amethyst.commons.resources.scan_qr
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.ObserveAndDisplayNIP05
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.KeepScreenBrightAndAwake
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.largeProfilePictureModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import kotlinx.coroutines.runBlocking

@Preview
@Composable
fun ShowQRScreenPreview() {
    val accountViewModel = mockAccountViewModel()
    runBlocking {
        accountViewModel.userProfile().metadata().newMetadata(
            UserMetadata().apply {
                name = "My Name"
                picture = "Picture"
                nip05 = null
                banner = "https://banner.com/test"
                website = "https://mywebsite.com/test"
                about = "This is the about me"
            },
            MetadataEvent(
                id = "",
                pubKey = "",
                createdAt = 0,
                tags = emptyArray(),
                content = "",
                sig = "",
            ),
        )
    }

    ShowQRScreen(
        pubkey = accountViewModel.userProfile().pubkeyHex,
        accountViewModel = accountViewModel,
        nav = EmptyNav(),
    )
}

@Composable
fun BackButton(onPress: () -> Unit) {
    IconButton(
        onClick = onPress,
    ) {
        ArrowBackIcon(MaterialTheme.colorScheme.onBackground)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowQRScreen(
    pubkey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    startScanning: Boolean = false,
) {
    LoadUser(pubkey) { user ->
        if (user != null) {
            ShowQRScreen(
                user = user,
                accountViewModel = accountViewModel,
                nav = nav,
                startScanning = startScanning,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowQRScreen(
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    startScanning: Boolean = false,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(
                "",
                nav,
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = pad.calculateTopPadding(),
                    bottom = pad.calculateBottomPadding(),
                ).consumeWindowInsets(pad)
                .imePaddingSafe(),
            verticalArrangement = Arrangement.SpaceAround,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShowQRBody(user, accountViewModel, nav, startScanning)
        }
    }
}

@Composable
fun ShowQRBody(
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    startScanning: Boolean = false,
) {
    // The launcher shortcut lands here already scanning; everything else starts on the user's own
    // code with a Scan button.
    var presenting by remember { mutableStateOf(!startScanning) }
    if (presenting) {
        PresentQR(user, accountViewModel) {
            presenting = false
        }
    } else {
        NIP19QrCodeScanner(accountViewModel) {
            // Either way the scanner closes; it used to be closed for us by a second, null call
            // that followed every successful scan.
            presenting = true
            if (it != null) nav.nav(it)
        }
    }
}

@Composable
fun PresentQR(
    user: User,
    accountViewModel: AccountViewModel,
    switchToScan: () -> Unit,
) {
    // The other phone's camera needs contrast, and this screen was the one place we showed a QR
    // code without asking for it (ShareNoteAsQrScreen has done so since it shipped).
    KeepScreenBrightAndAwake()

    RenderName(user, accountViewModel)

    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Size10dp),
    ) {
        QrCodeDrawer(user.toNostrUri())
    }

    Row(modifier = Modifier.padding(horizontal = 30.dp)) {
        FilledTonalButton(
            onClick = switchToScan,
            shape = RoundedCornerShape(Size35dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text(text = stringRes(Res.string.scan_qr))
        }
    }
}

@Composable
fun RenderName(
    user: User,
    accountViewModel: AccountViewModel,
) {
    Column {
        val userInfo by observeUserInfo(user, accountViewModel)

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RobohashFallbackAsyncImage(
                robot = user.pubkeyHex,
                model = userInfo?.info?.profilePicture(),
                contentDescription = stringRes(Res.string.profile_image),
                modifier = MaterialTheme.colorScheme.largeProfilePictureModifier,
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
                autoPlayGif =
                    accountViewModel.settings.autoPlayVideosFlow
                        .collectAsStateWithLifecycle()
                        .value,
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            CreateTextWithEmoji(
                text = userInfo?.info?.bestName() ?: "",
                tags = userInfo?.tags,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            WatchAndDisplayNip05Row(user, accountViewModel)
        }
    }
}

@Composable
fun WatchAndDisplayNip05Row(
    user: User,
    accountViewModel: AccountViewModel,
) {
    val nip05StateMetadata by user.nip05State().flow.collectAsStateWithLifecycle()

    when (val nip05State = nip05StateMetadata) {
        is Nip05State.Exists -> {
            ObserveAndDisplayNIP05(nip05State, accountViewModel)
        }

        else -> {
            Text(
                text = user.pubkeyDisplayHex(),
                fontSize = Font14SP,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
