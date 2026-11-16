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
package com.vitorpamplona.amethyst.commons.ui.navigation

/** Which entry of Android's share sheet the user picked. */
enum class ShareTarget {
    /** The default "New Post" target: a kind-1 note with the shared text/media. */
    NEW_POST,

    /** "Send as DM": a NIP-17 private message. */
    DIRECT_MESSAGE,

    /** "New Highlight": a NIP-84 highlight of the shared passage. */
    HIGHLIGHT,

    /** "New Picture": a NIP-68 picture post, straight into the picture feed. */
    PICTURE,

    /** "New Short": a NIP-71 kind 22 video, straight into the Shorts feed. */
    SHORT_VIDEO,

    /** "New Video": a NIP-71 video, straight into the Video feed. */
    VIDEO,

    /**
     * "Scan QR code": read a QR out of a shared picture instead of posting it.
     *
     * The common way a QR code reaches someone is as a screenshot or a photo in a chat, not as
     * something in front of the camera. Without this the only way to use one was to display it on
     * a second screen and point the phone at it.
     */
    SCAN_QR,
}

/**
 * Tells the share targets apart. Every SEND intent-filter resolves to MainActivity; which target
 * the user picked is only visible in the component class name of the launching intent (the
 * `<activity-alias>` name), so each alias below maps to the composer it opens.
 */
object ShareIntentRouting {
    /**
     * Simple class name of the `<activity-alias>` declared in AndroidManifest.xml
     * (android:name=".ui.ShareAsDMAlias"). MUST stay in sync with the manifest —
     * renaming the alias there without updating this constant silently routes
     * "Send as DM" shares to the New Post composer (no build error).
     */
    const val SHARE_AS_DM_ALIAS_SIMPLE_NAME = "ShareAsDMAlias"

    /**
     * Simple class name of the `<activity-alias>` declared in AndroidManifest.xml
     * (android:name=".ui.ShareAsHighlightAlias"). MUST stay in sync with the manifest —
     * see the caveat on [SHARE_AS_DM_ALIAS_SIMPLE_NAME].
     */
    const val SHARE_AS_HIGHLIGHT_ALIAS_SIMPLE_NAME = "ShareAsHighlightAlias"

    /** See the caveat on [SHARE_AS_DM_ALIAS_SIMPLE_NAME]. */
    const val SHARE_AS_PICTURE_ALIAS_SIMPLE_NAME = "ShareAsPictureAlias"

    /** See the caveat on [SHARE_AS_DM_ALIAS_SIMPLE_NAME]. */
    const val SHARE_AS_SHORT_VIDEO_ALIAS_SIMPLE_NAME = "ShareAsShortVideoAlias"

    /** See the caveat on [SHARE_AS_DM_ALIAS_SIMPLE_NAME]. */
    const val SHARE_AS_VIDEO_ALIAS_SIMPLE_NAME = "ShareAsVideoAlias"

    /** See the caveat on [SHARE_AS_DM_ALIAS_SIMPLE_NAME]. */
    const val SCAN_QR_ALIAS_SIMPLE_NAME = "ScanQrCodeAlias"

    private val TARGET_BY_ALIAS =
        mapOf(
            SHARE_AS_DM_ALIAS_SIMPLE_NAME to ShareTarget.DIRECT_MESSAGE,
            SHARE_AS_HIGHLIGHT_ALIAS_SIMPLE_NAME to ShareTarget.HIGHLIGHT,
            SHARE_AS_PICTURE_ALIAS_SIMPLE_NAME to ShareTarget.PICTURE,
            SHARE_AS_SHORT_VIDEO_ALIAS_SIMPLE_NAME to ShareTarget.SHORT_VIDEO,
            SHARE_AS_VIDEO_ALIAS_SIMPLE_NAME to ShareTarget.VIDEO,
            SCAN_QR_ALIAS_SIMPLE_NAME to ShareTarget.SCAN_QR,
        )

    /**
     * Resolves the launching component to its target. Flavors can change the resolved package
     * prefix, so the match is on the simple name; anything that isn't one of our aliases (chiefly
     * MainActivity itself) is the default New Post target.
     */
    fun targetOf(componentClassName: String?): ShareTarget {
        if (componentClassName == null) return ShareTarget.NEW_POST

        return TARGET_BY_ALIAS.entries
            .firstOrNull { componentClassName.endsWith(".${it.key}") }
            ?.value
            ?: ShareTarget.NEW_POST
    }
}
