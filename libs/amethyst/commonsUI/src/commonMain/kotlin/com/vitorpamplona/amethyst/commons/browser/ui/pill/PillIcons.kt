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
package com.vitorpamplona.amethyst.commons.browser.ui.pill

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Action
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Security
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access
import com.vitorpamplona.amethyst.commons.resources.browser_pill_add_home
import com.vitorpamplona.amethyst.commons.resources.browser_pill_back
import com.vitorpamplona.amethyst.commons.resources.browser_pill_back_to_app
import com.vitorpamplona.amethyst.commons.resources.browser_pill_console
import com.vitorpamplona.amethyst.commons.resources.browser_pill_copy
import com.vitorpamplona.amethyst.commons.resources.browser_pill_desktop
import com.vitorpamplona.amethyst.commons.resources.browser_pill_edit_address
import com.vitorpamplona.amethyst.commons.resources.browser_pill_favorite_add
import com.vitorpamplona.amethyst.commons.resources.browser_pill_favorite_remove
import com.vitorpamplona.amethyst.commons.resources.browser_pill_find
import com.vitorpamplona.amethyst.commons.resources.browser_pill_forward
import com.vitorpamplona.amethyst.commons.resources.browser_pill_full_screen
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_site_data
import com.vitorpamplona.amethyst.commons.resources.browser_pill_other_browser
import com.vitorpamplona.amethyst.commons.resources.browser_pill_permission_camera
import com.vitorpamplona.amethyst.commons.resources.browser_pill_permission_location
import com.vitorpamplona.amethyst.commons.resources.browser_pill_permission_microphone
import com.vitorpamplona.amethyst.commons.resources.browser_pill_reload
import com.vitorpamplona.amethyst.commons.resources.browser_pill_security_http
import com.vitorpamplona.amethyst.commons.resources.browser_pill_security_https
import com.vitorpamplona.amethyst.commons.resources.browser_pill_security_sandbox
import com.vitorpamplona.amethyst.commons.resources.browser_pill_security_tor
import com.vitorpamplona.amethyst.commons.resources.browser_pill_share
import com.vitorpamplona.amethyst.commons.resources.browser_pill_site_settings
import com.vitorpamplona.amethyst.commons.resources.browser_pill_stop
import com.vitorpamplona.amethyst.commons.resources.browser_pill_text_size
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tile_copy
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tile_desktop
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tile_find
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tile_full
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tile_home
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tile_other
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tile_text
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_title
import org.jetbrains.compose.resources.StringResource
import androidx.compose.material3.Icon as Material3Icon

/** Tor's onion, as a vector (the same artwork as the Android `ic_tor` drawable). */
val OnionIcon: ImageVector by lazy {
    ImageVector
        .Builder(name = "Onion", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = PathParser().parsePathString(ONION_PATH).toNodes(), fill = SolidColor(Color.Black))
        .build()
}

private const val ONION_PATH = "M17.578,12.201c-0.76,-0.692 -1.721,-1.251 -2.704,-1.81 -0.446,-0.246 -1.81,-1.318 -1.34,-2.838l-0.851,-0.358c1.342,-2.078 3.085,-4.134 5.229,-6.056 -1.721,0.581 -3.24,1.476 -4.379,3.062 0.67,-1.407 1.765,-2.793 2.972,-4.201 -1.654,1.185 -3.084,2.525 -3.979,4.313l0.627,-2.503c-0.894,1.608 -1.52,3.24 -1.766,4.871l-1.317,-0.535 -0.223,0.178c1.162,2.078 0.559,3.174 -0.022,3.553 -1.162,0.783 -2.838,1.788 -3.688,2.659 -1.609,1.654 -2.078,3.218 -1.921,5.296 0.157,2.66 2.101,4.873 4.67,5.744 1.14,0.38 2.19,0.424 3.352,0.424 1.877,0 3.799,-0.491 5.207,-1.676a6.551,6.551 0,0 0,2.369 -5.027,6.875 6.875,0 0,0 -2.236,-5.096zM14.025,21.073c-0.09,0.402 -0.38,0.894 -0.737,1.341 0.134,-0.246 0.246,-0.492 0.313,-0.76 0.559,-1.989 0.805,-2.904 0.537,-5.095 -0.045,-0.224 -0.135,-0.938 -0.471,-1.721 -0.468,-1.185 -1.184,-2.303 -1.272,-2.548 -0.157,-0.38 -0.38,-1.989 -0.403,-3.084 0.023,0.938 0.089,2.659 0.335,3.329 0.067,0.225 0.715,1.229 1.185,2.459 0.312,0.849 0.38,1.632 0.446,1.854 0.224,1.007 -0.045,2.705 -0.401,4.313 -0.111,0.581 -0.426,1.252 -0.828,1.766 0.225,-0.313 0.402,-0.715 0.537,-1.185 0.269,-0.938 0.38,-2.145 0.356,-2.905 -0.021,-0.446 -0.222,-1.407 -0.558,-2.278 -0.201,-0.47 -0.492,-0.961 -0.692,-1.297 -0.224,-0.335 -0.224,-1.072 -0.313,-1.921 0.021,0.916 -0.068,1.385 0.156,2.033 0.134,0.379 0.625,0.916 0.759,1.43 0.201,0.693 0.402,1.453 0.381,1.922 0,0.536 -0.022,1.52 -0.269,2.593 -0.157,0.804 -0.515,1.497 -1.095,1.943 0.246,-0.312 0.38,-0.625 0.447,-0.938 0.089,-0.469 0.111,-0.916 0.156,-1.475a5.96,5.96 0,0 0,-0.111 -1.721c-0.179,-0.805 -0.469,-1.608 -0.604,-2.168 0.022,0.626 0.269,1.408 0.381,2.235 0.089,0.604 0.044,1.206 0.021,1.742 -0.021,0.627 -0.223,1.722 -0.492,2.258 -0.268,-0.112 -0.357,-0.269 -0.537,-0.491 -0.223,-0.291 -0.357,-0.604 -0.491,-0.962a5.043,5.043 0,0 1,-0.291 -0.915,3.071 3.071,0 0,1 0.559,-2.213c0.469,-0.671 0.559,-0.716 0.715,-1.497 -0.223,0.692 -0.379,0.759 -0.871,1.341 -0.559,0.647 -0.648,1.586 -0.648,2.346 0,0.313 0.134,0.671 0.246,1.007 0.134,0.356 0.268,0.714 0.447,0.982 0.134,0.223 0.313,0.379 0.469,0.491 -0.581,-0.156 -1.184,-0.379 -1.564,-0.692 -0.938,-0.805 -1.765,-2.167 -1.877,-3.375 -0.089,-0.982 0.804,-2.413 2.078,-3.128 1.073,-0.626 1.318,-1.319 1.542,-2.459 -0.313,0.983 -0.626,1.833 -1.654,2.348 -1.475,0.804 -2.235,2.1 -2.167,3.352 0.112,1.586 0.737,2.682 2.011,3.554 0.291,0.2 0.693,0.401 1.118,0.559 -1.587,-0.381 -1.788,-0.604 -2.324,-1.229 0,-0.045 -0.134,-0.135 -0.134,-0.156 -0.715,-0.805 -1.609,-2.19 -1.922,-3.464 -0.112,-0.447 -0.224,-0.916 -0.089,-1.363 0.581,-2.101 1.854,-2.905 3.128,-3.775 0.313,-0.225 0.626,-0.426 0.916,-0.649 0.715,-0.559 0.894,-2.012 1.05,-2.838 -0.29,1.006 -0.603,2.258 -1.162,2.659 -0.29,0.224 -0.648,0.402 -0.938,0.604 -1.318,0.894 -2.637,1.743 -3.24,3.91 -0.134,0.56 -0.044,0.962 0.089,1.498 0.335,1.317 1.229,2.748 1.989,3.597l0.134,0.135c0.335,0.381 0.76,0.67 1.274,0.871a5.945,5.945 0,0 1,-1.296 -0.469c-2.078,-1.005 -3.463,-3.173 -3.553,-4.939 -0.179,-3.597 1.542,-4.647 3.151,-5.966 0.894,-0.737 2.145,-1.095 2.86,-2.413 0.134,-0.291 0.224,-0.916 0.045,-1.587 -0.067,-0.224 -0.402,-1.028 -0.537,-1.207l1.989,0.872c-0.044,0.938 -0.067,1.698 0.112,2.391 0.2,0.76 1.184,1.854 1.586,3.129 0.783,2.41 0.583,5.561 0.023,8.019z"

/** The glyph for [action]; null means it's drawn with [OnionIcon]. */
fun pillSymbolFor(action: Action): MaterialSymbol? =
    when (action) {
        Action.BACK -> MaterialSymbols.AutoMirrored.ArrowBack
        Action.FORWARD -> MaterialSymbols.AutoMirrored.ArrowForward
        Action.RELOAD -> MaterialSymbols.Refresh
        Action.STOP -> MaterialSymbols.Close
        Action.FAVORITE -> MaterialSymbols.Star
        Action.SHARE -> MaterialSymbols.Share
        Action.BACK_TO_APP -> MaterialSymbols.Home
        Action.COPY_LINK -> MaterialSymbols.ContentCopy
        Action.EDIT_ADDRESS -> MaterialSymbols.Edit
        Action.FIND_IN_PAGE -> MaterialSymbols.Search
        Action.TEXT_SIZE -> MaterialSymbols.FormatSize
        Action.DESKTOP_SITE -> MaterialSymbols.DesktopWindows
        Action.ADD_TO_HOME_SCREEN -> MaterialSymbols.AddToHomeScreen
        Action.OPEN_IN_BROWSER_APP -> MaterialSymbols.OpenInBrowser
        Action.OPEN_FULL_SCREEN -> MaterialSymbols.OpenInFull
        Action.TOR -> null
        Action.ACCESS_INFO -> MaterialSymbols.Shield
        Action.SITE_SETTINGS -> MaterialSymbols.Tune
        Action.SITE_DATA -> MaterialSymbols.Cookie
        Action.CONSOLE -> MaterialSymbols.Code
    }

fun pillLabelFor(
    action: Action,
    isFavorite: Boolean = false,
): StringResource =
    when (action) {
        Action.BACK -> Res.string.browser_pill_back
        Action.FORWARD -> Res.string.browser_pill_forward
        Action.RELOAD -> Res.string.browser_pill_reload
        Action.STOP -> Res.string.browser_pill_stop
        Action.FAVORITE -> if (isFavorite) Res.string.browser_pill_favorite_remove else Res.string.browser_pill_favorite_add
        Action.SHARE -> Res.string.browser_pill_share
        Action.BACK_TO_APP -> Res.string.browser_pill_back_to_app
        Action.COPY_LINK -> Res.string.browser_pill_copy
        Action.EDIT_ADDRESS -> Res.string.browser_pill_edit_address
        Action.FIND_IN_PAGE -> Res.string.browser_pill_find
        Action.TEXT_SIZE -> Res.string.browser_pill_text_size
        Action.DESKTOP_SITE -> Res.string.browser_pill_desktop
        Action.ADD_TO_HOME_SCREEN -> Res.string.browser_pill_add_home
        Action.OPEN_IN_BROWSER_APP -> Res.string.browser_pill_other_browser
        Action.OPEN_FULL_SCREEN -> Res.string.browser_pill_full_screen
        Action.TOR -> Res.string.browser_pill_tor_title
        Action.ACCESS_INFO -> Res.string.browser_pill_access
        Action.SITE_SETTINGS -> Res.string.browser_pill_site_settings
        Action.SITE_DATA -> Res.string.browser_pill_info_site_data
        Action.CONSOLE -> Res.string.browser_pill_console
    }

/**
 * The short label a page-action tile shows under its icon (tiles are ~88dp wide); the full
 * [pillLabelFor] wording stays the accessibility description.
 */
fun pillTileLabelFor(action: Action): StringResource =
    when (action) {
        Action.COPY_LINK -> Res.string.browser_pill_tile_copy
        Action.FIND_IN_PAGE -> Res.string.browser_pill_tile_find
        Action.TEXT_SIZE -> Res.string.browser_pill_tile_text
        Action.DESKTOP_SITE -> Res.string.browser_pill_tile_desktop
        Action.ADD_TO_HOME_SCREEN -> Res.string.browser_pill_tile_home
        Action.OPEN_IN_BROWSER_APP -> Res.string.browser_pill_tile_other
        Action.OPEN_FULL_SCREEN -> Res.string.browser_pill_tile_full
        else -> pillLabelFor(action)
    }

fun securityLabel(security: Security): StringResource =
    when (security) {
        Security.TOR -> Res.string.browser_pill_security_tor
        Security.HTTPS -> Res.string.browser_pill_security_https
        Security.HTTP -> Res.string.browser_pill_security_http
        Security.SANDBOX -> Res.string.browser_pill_security_sandbox
    }

fun permissionLabel(permission: BrowserSitePermission): StringResource =
    when (permission) {
        BrowserSitePermission.CAMERA -> Res.string.browser_pill_permission_camera
        BrowserSitePermission.MICROPHONE -> Res.string.browser_pill_permission_microphone
        BrowserSitePermission.LOCATION -> Res.string.browser_pill_permission_location
    }

fun permissionSymbol(permission: BrowserSitePermission): MaterialSymbol =
    when (permission) {
        BrowserSitePermission.CAMERA -> MaterialSymbols.Videocam
        BrowserSitePermission.MICROPHONE -> MaterialSymbols.Mic
        BrowserSitePermission.LOCATION -> MaterialSymbols.LocationOn
    }

/** The colour that signals [security]: the error tone for plain HTTP, the Tor accent for onion routing. */
@Composable
fun securityTint(security: Security): Color =
    when (security) {
        Security.HTTP -> MaterialTheme.colorScheme.error
        Security.TOR -> MaterialTheme.colorScheme.tertiary
        Security.SANDBOX -> MaterialTheme.colorScheme.primary
        Security.HTTPS -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/** Draws [action]'s icon (the onion for Tor). */
@Composable
fun PillActionIcon(
    action: Action,
    tint: Color,
    size: Dp = 24.dp,
    filled: Boolean = false,
    contentDescription: String? = null,
) {
    val symbol = pillSymbolFor(action)
    if (symbol != null) {
        Icon(symbol, contentDescription = contentDescription, modifier = Modifier.size(size), tint = tint, filled = filled)
    } else {
        Material3Icon(OnionIcon, contentDescription = contentDescription, modifier = Modifier.size(size), tint = tint)
    }
}

/** Draws the badge for [security] in its signal colour. */
@Composable
fun SecurityIcon(
    security: Security,
    size: Dp = 18.dp,
    tint: Color = securityTint(security),
) {
    when (security) {
        Security.TOR -> Material3Icon(OnionIcon, contentDescription = null, modifier = Modifier.size(size), tint = tint)
        Security.HTTPS -> Icon(MaterialSymbols.Lock, contentDescription = null, modifier = Modifier.size(size), tint = tint)
        Security.HTTP -> Icon(MaterialSymbols.NoEncryption, contentDescription = null, modifier = Modifier.size(size), tint = tint)
        Security.SANDBOX -> Icon(MaterialSymbols.Shield, contentDescription = null, modifier = Modifier.size(size), tint = tint, filled = true)
    }
}
