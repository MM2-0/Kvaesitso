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
package com.vitorpamplona.amethyst.commons.ui.cartridges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.cartridges.CartLinks
import com.vitorpamplona.amethyst.commons.cartridges.CartPlayerViewModel
import com.vitorpamplona.amethyst.commons.cartridges.cartSaveStore
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cart_button_o_description
import com.vitorpamplona.amethyst.commons.resources.cart_button_x_description
import com.vitorpamplona.amethyst.commons.resources.cart_could_not_load
import com.vitorpamplona.amethyst.commons.resources.cart_different_file_not_a_cart
import com.vitorpamplona.amethyst.commons.resources.cart_different_file_playing
import com.vitorpamplona.amethyst.commons.resources.cart_dpad_description
import com.vitorpamplona.amethyst.commons.resources.cart_game_ended
import com.vitorpamplona.amethyst.commons.resources.cart_game_error
import com.vitorpamplona.amethyst.commons.resources.cart_loading
import com.vitorpamplona.amethyst.commons.resources.cart_not_a_cart
import com.vitorpamplona.amethyst.commons.resources.cart_pause_menu
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.kiko.compose.KikoGame
import com.vitorpamplona.kiko.compose.KikoLabels

/** Plays the cart at [url] full screen, over whatever opened it. */
@Composable
fun CartPlayerDialog(
    url: String,
    sha256: String?,
    links: CartLinks?,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // Back is the player's to handle: it pauses the game before it closes anything.
        properties = edgeToEdgeDialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        SetDialogToEdgeToEdge()
        CartPlayer(url, sha256, links, accountViewModel, onDismiss)
    }
}

/**
 * The game, played by kiko-compose's [KikoGame] (screen, touch controls, keyboard and gamepad,
 * sound, pausing with the app), with the app's chrome around it: a pause button for touch
 * screens, a close button and the app's words. Back opens the game's pause menu; Back again
 * closes the player. A wide window plays full screen.
 */
@Composable
fun CartPlayer(
    url: String,
    sha256: String?,
    links: CartLinks?,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
) {
    val appStores = LocalAppServices.current.appStores
    val viewModel =
        // The links are part of the key: another note of this front cart may reach other carts.
        rememberViewModel(key = remember(url, links) { "cart:$url:${links?.fingerprint()}" }) {
            CartPlayerViewModel(url, sha256, accountViewModel.httpClientBuilder, appStores.cartSaveStore(), links)
        }
    // Made again when it was let go to keep only the latest few games in memory.
    val player = remember(viewModel) { viewModel.player() }
    PlatformBackHandler(onBack = { if (!player.openPauseMenu()) onClose() })
    val differentFile by viewModel.differentFile.collectAsState()

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        CartWindowMode(fullScreen = maxWidth > maxHeight)

        KikoGame(
            player = player,
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            labels = rememberCartLabels(differentFile?.takeIf { !it.playable }?.host),
            onExit = onClose,
        )

        IconButton(onClick = player::tapPause, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding()) {
            Icon(symbol = MaterialSymbols.Pause, contentDescription = stringRes(Res.string.cart_pause_menu), tint = Color.White)
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding()) {
            Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.close), tint = Color.White)
        }

        differentFile?.takeIf { it.playable }?.let {
            DifferentFileWarning(
                host = it.host,
                onDismiss = viewModel::dismissDifferentFile,
                // Under the pause and close buttons, clear of the touch controls at the bottom.
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(start = 12.dp, end = 12.dp, top = 52.dp),
            )
        }
    }
}

/** Says the server's file is not the one the post recorded, over the game it still plays. */
@Composable
private fun DifferentFileWarning(
    host: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(Res.string.cart_different_file_playing, host),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
        )
        IconButton(onClick = onDismiss) {
            Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.close), tint = Color.White)
        }
    }
}

/** The player's words in the app's language; [differentFileHost] explains a refused file. */
@Composable
private fun rememberCartLabels(differentFileHost: String?): KikoLabels {
    val loading = stringRes(Res.string.cart_loading)
    // The download worked, but the server's file is not the recorded one and is not a game.
    val noCart =
        if (differentFileHost != null) {
            stringRes(Res.string.cart_different_file_not_a_cart, differentFileHost)
        } else {
            stringRes(Res.string.cart_could_not_load)
        }
    val notACart = stringRes(Res.string.cart_not_a_cart)
    val ended = stringRes(Res.string.cart_game_ended)
    val error = stringRes(Res.string.cart_game_error)
    val pad = stringRes(Res.string.cart_dpad_description)
    val buttons = stringRes(Res.string.cart_button_o_description) + ", " + stringRes(Res.string.cart_button_x_description)
    return remember(loading, noCart, notACart, ended, error, pad, buttons) {
        KikoLabels(
            loading = loading,
            noCart = noCart,
            notACart = notACart,
            gameEnded = ended,
            gameError = error,
            directionPad = pad,
            actionButtons = buttons,
        )
    }
}
