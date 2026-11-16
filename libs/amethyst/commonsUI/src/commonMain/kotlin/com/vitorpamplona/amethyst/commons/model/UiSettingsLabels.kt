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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accent_color_blue
import com.vitorpamplona.amethyst.commons.resources.accent_color_green
import com.vitorpamplona.amethyst.commons.resources.accent_color_orange
import com.vitorpamplona.amethyst.commons.resources.accent_color_pink
import com.vitorpamplona.amethyst.commons.resources.accent_color_purple
import com.vitorpamplona.amethyst.commons.resources.accent_color_red
import com.vitorpamplona.amethyst.commons.resources.chat_layout_bubbles
import com.vitorpamplona.amethyst.commons.resources.chat_layout_compact
import com.vitorpamplona.amethyst.commons.resources.chat_layout_open
import com.vitorpamplona.amethyst.commons.resources.connectivity_type_always
import com.vitorpamplona.amethyst.commons.resources.connectivity_type_never
import com.vitorpamplona.amethyst.commons.resources.connectivity_type_unmetered_wifi_only
import com.vitorpamplona.amethyst.commons.resources.content_warning_hide_all_sensitive_content_option
import com.vitorpamplona.amethyst.commons.resources.content_warning_see_warnings_option
import com.vitorpamplona.amethyst.commons.resources.content_warning_show_all_sensitive_content_option
import com.vitorpamplona.amethyst.commons.resources.dark
import com.vitorpamplona.amethyst.commons.resources.font_family_monospace
import com.vitorpamplona.amethyst.commons.resources.font_family_sans_serif
import com.vitorpamplona.amethyst.commons.resources.font_family_serif
import com.vitorpamplona.amethyst.commons.resources.font_family_system
import com.vitorpamplona.amethyst.commons.resources.font_size_huge
import com.vitorpamplona.amethyst.commons.resources.font_size_large
import com.vitorpamplona.amethyst.commons.resources.font_size_normal
import com.vitorpamplona.amethyst.commons.resources.font_size_small
import com.vitorpamplona.amethyst.commons.resources.gallery_type_classic
import com.vitorpamplona.amethyst.commons.resources.gallery_type_modern
import com.vitorpamplona.amethyst.commons.resources.light
import com.vitorpamplona.amethyst.commons.resources.note_layout_author_column
import com.vitorpamplona.amethyst.commons.resources.note_layout_edge_to_edge
import com.vitorpamplona.amethyst.commons.resources.note_layout_full_width
import com.vitorpamplona.amethyst.commons.resources.system
import com.vitorpamplona.amethyst.commons.resources.ui_feature_set_type_complete
import com.vitorpamplona.amethyst.commons.resources.ui_feature_set_type_performance
import com.vitorpamplona.amethyst.commons.resources.ui_feature_set_type_simplified
import org.jetbrains.compose.resources.StringResource

// Display labels for the settings enums. The enums live in headless commons; their labels are
// Compose resources, so they are attached here, in the UI module.

val ThemeType.resourceId: StringResource
    get() =
        when (this) {
            ThemeType.SYSTEM -> Res.string.system
            ThemeType.LIGHT -> Res.string.light
            ThemeType.DARK -> Res.string.dark
        }

val AccentColorType.resourceId: StringResource
    get() =
        when (this) {
            AccentColorType.PURPLE -> Res.string.accent_color_purple
            AccentColorType.BLUE -> Res.string.accent_color_blue
            AccentColorType.GREEN -> Res.string.accent_color_green
            AccentColorType.ORANGE -> Res.string.accent_color_orange
            AccentColorType.RED -> Res.string.accent_color_red
            AccentColorType.PINK -> Res.string.accent_color_pink
        }

val FontFamilyType.resourceId: StringResource
    get() =
        when (this) {
            FontFamilyType.SYSTEM -> Res.string.font_family_system
            FontFamilyType.SANS_SERIF -> Res.string.font_family_sans_serif
            FontFamilyType.SERIF -> Res.string.font_family_serif
            FontFamilyType.MONOSPACE -> Res.string.font_family_monospace
        }

val FontSizeType.resourceId: StringResource
    get() =
        when (this) {
            FontSizeType.SMALL -> Res.string.font_size_small
            FontSizeType.NORMAL -> Res.string.font_size_normal
            FontSizeType.LARGE -> Res.string.font_size_large
            FontSizeType.HUGE -> Res.string.font_size_huge
        }

val ConnectivityType.resourceId: StringResource
    get() =
        when (this) {
            ConnectivityType.ALWAYS -> Res.string.connectivity_type_always
            ConnectivityType.WIFI_ONLY -> Res.string.connectivity_type_unmetered_wifi_only
            ConnectivityType.NEVER -> Res.string.connectivity_type_never
        }

val FeatureSetType.resourceId: StringResource
    get() =
        when (this) {
            FeatureSetType.COMPLETE -> Res.string.ui_feature_set_type_complete
            FeatureSetType.SIMPLIFIED -> Res.string.ui_feature_set_type_simplified
            FeatureSetType.PERFORMANCE -> Res.string.ui_feature_set_type_performance
        }

val ProfileGalleryType.resourceId: StringResource
    get() =
        when (this) {
            ProfileGalleryType.CLASSIC -> Res.string.gallery_type_classic
            ProfileGalleryType.MODERN -> Res.string.gallery_type_modern
        }

val NoteLayoutType.resourceId: StringResource
    get() =
        when (this) {
            NoteLayoutType.AUTHOR_COLUMN -> Res.string.note_layout_author_column
            NoteLayoutType.FULL_WIDTH -> Res.string.note_layout_full_width
            NoteLayoutType.EDGE_TO_EDGE -> Res.string.note_layout_edge_to_edge
        }

val ChatLayoutType.resourceId: StringResource
    get() =
        when (this) {
            ChatLayoutType.BUBBLES -> Res.string.chat_layout_bubbles
            ChatLayoutType.COMPACT -> Res.string.chat_layout_compact
            ChatLayoutType.OPEN -> Res.string.chat_layout_open
        }

val WarningType.resourceId: StringResource
    get() =
        when (this) {
            WarningType.WARN -> Res.string.content_warning_see_warnings_option
            WarningType.SHOW -> Res.string.content_warning_show_all_sensitive_content_option
            WarningType.HIDE -> Res.string.content_warning_hide_all_sensitive_content_option
        }
