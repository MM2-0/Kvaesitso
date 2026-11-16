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
package com.vitorpamplona.amethyst.commons.ui.note.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.article
import com.vitorpamplona.amethyst.commons.resources.content_warning_with_reason
import com.vitorpamplona.amethyst.commons.resources.kind_video
import com.vitorpamplona.amethyst.commons.resources.share_as_qr_kind_picture
import com.vitorpamplona.amethyst.commons.resources.share_as_qr_thumbnail_hidden_sensitive
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.collectContentWarningReasons
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.gallery.GalleryThumbnail
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.gallery.UrlImageView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.nip71Video.alt
import com.vitorpamplona.quartz.nip71Video.blurhash
import com.vitorpamplona.quartz.nip71Video.mimeType
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetas

private val CardHeight = 72.dp
private val ThumbSize = 56.dp
private val ThumbShape = RoundedCornerShape(9.dp)

/**
 * A fixed-height preview of the note being shared, shown above the QR code.
 *
 * The height is fixed on purpose: it keeps the QR code in the same screen position for every
 * note, so the screen is predictable to hold up to a scanner. That is why this does not use
 * [com.vitorpamplona.amethyst.commons.ui.note.NoteCompose] — see the design spec for the full reasoning,
 * but in short, `isQuotedNote` never reaches the media renderer and note images render at their
 * natural aspect ratio with no height ceiling.
 */
@Composable
fun SharedNoteCard(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(CardHeight).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Plain SensitivityWarning is NOT enough here: it gates on event.isSensitiveOrNSFW(),
        // which only reads the note-level content-warning tag / nsfw hashtag. GalleryThumbnail's
        // inner gate (GalleryThumb.kt:236) reads each media entry's per-imeta `contentWarning`,
        // but none of GalleryThumb.kt's four media-construction sites ever set that field, so it
        // is always null and that inner gate is permanently dead on this path. A note whose only
        // warning lives inside an `imeta` tag (e.g. a kind 20 PictureEvent) would then render
        // completely unblurred. `hasImetaContentWarning` below checks every imeta for the mere
        // PRESENCE of a `content-warning` key, not for non-blank reason text: an imeta warning
        // with an empty reason string (`["content-warning"]` with no second element) still counts
        // — collectContentWarningReasons()'s `takeIf { it.isNotBlank() }` would silently drop that
        // exact case, which is what let it slip the gate before. collectContentWarningReasons()
        // is still called for the human-readable *reason text*, shown in the covered box's
        // accessibility label when one exists — it never drives the show/hide decision.
        //
        // `note.event` is a plain field read that never recomposes if this composable is
        // rendered before the note's event has arrived over the relay (id-only reference, e.g.
        // straight off a deep link). observeNote() subscribes both the relay finder and the
        // LocalCache flow, so `event` below updates — and this whole gate recomputes — the
        // moment the event loads or changes, the same idiom GalleryThumbnail itself already uses
        // (GalleryThumb.kt:78).
        //
        // This deliberately does NOT use the shared ContentWarningGate: its overlay
        // (ContentWarningOverlayBody, SensitivityWarning.kt:254+) opens with an 80.dp icon box
        // that consumes this card's entire 56.dp thumbnail height, pushing the title, reasons,
        // and "Show anyway" button below the clipped, tappable area. Reshaping that shared
        // composable was rejected — six other screens depend on its current layout — so this
        // call site renders its own compact, permanently-covered state instead: no reveal
        // affordance, because this screen is held up in public and pointed at someone else's
        // camera. `accountViewModel.showSensitiveContent()` is still honoured exactly as
        // ContentWarningGate honours it (SensitivityWarning.kt:138-140), so a user who has
        // opted into seeing sensitive content globally sees the real thumbnail here too. Either
        // way the thumbnail box stays a fixed 56.dp, keeping this Row's height fixed at 72.dp.
        //
        // `nav` is passed because GalleryThumbnail's signature demands it, but it is unused
        // there (GalleryThumb.kt:76) — navigation comes from ClickableNote at its other call
        // site. This card is not tappable.
        val noteState by observeNote(note, accountViewModel)
        val event = noteState.note.event
        val reasons = remember(event) { event?.let { collectContentWarningReasons(it) } ?: emptySet() }
        val hasImetaContentWarning =
            remember(event) {
                event?.imetas()?.any { it.properties.containsKey(ContentWarningTag.TAG_NAME) } ?: false
            }
        val isSensitive =
            remember(event, hasImetaContentWarning) {
                event != null && (event.isSensitiveOrNSFW() || hasImetaContentWarning)
            }
        val showSensitiveContent by accountViewModel.showSensitiveContent().collectAsStateWithLifecycle()
        val isGated = isSensitive && showSensitiveContent != true

        // Thumbnail source, in priority order:
        //  1. structured media event (kind 20/21/22, gallery, live clip) -> GalleryThumbnail;
        //  2. an image carried in an `imeta` tag of an otherwise-unstructured note (a kind 1
        //     image post — content is typically just the media URL) -> render that image;
        //  3. no media at all -> the author's round AVATAR, which (unlike GalleryThumbnail's own
        //     DisplayGalleryAuthorBanner fallback, a banner crop) cannot be mistaken for the
        //     note's own picture (F7).
        // hasStructuredMedia() mirrors GalleryThumbnail's own per-kind checks (GalleryThumb.kt:
        // 82-186); contentImage covers the case GalleryThumbnail does NOT handle — a bare image
        // URL in an imeta on a kind 1 — so those posts show the picture instead of avatar + URL.
        val hasStructuredMedia = remember(event) { event != null && hasStructuredMedia(event) }
        val contentImage = remember(event) { event?.let { firstContentImage(it) } }

        Box(Modifier.size(ThumbSize).clip(ThumbShape)) {
            if (isGated) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Warning,
                        contentDescription =
                            reasons.firstOrNull()?.let { stringRes(Res.string.content_warning_with_reason, it) }
                                ?: stringRes(Res.string.share_as_qr_thumbnail_hidden_sensitive),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (hasStructuredMedia) {
                GalleryThumbnail(note, accountViewModel, nav)
            } else if (contentImage != null) {
                // UrlImageView crops to fill (ContentScale.Crop) and honours the account's
                // show-images setting and blossom bridge on its own; the enclosing 56.dp Box
                // bounds it so the card height stays fixed. No extra SensitivityWarning: the
                // isGated branch above already covered the sensitive case.
                UrlImageView(contentImage, accountViewModel)
            } else {
                NoteAuthorPicture(note, ThumbSize, accountViewModel)
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = note.author?.toBestDisplayName() ?: "",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = secondaryLineFor(event, isGated, contentImage != null),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Whether [event] carries the kind of structured media [GalleryThumbnail] would render (a
 * profile-gallery entry, kind 20 picture, kind 21/22 video, or live-activity clip with a video
 * URL) — used only to pick between the media thumbnail and the author-avatar fallback (F7).
 * Mirrors GalleryThumbnail's own when-branch conditions (GalleryThumb.kt:82-186) rather than
 * duplicating its full [com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent] construction.
 */
private fun hasStructuredMedia(event: Event): Boolean =
    when (event) {
        is ProfileGalleryEntryEvent -> event.urls().isNotEmpty()
        is PictureEvent -> event.imetaTags().isNotEmpty()
        is VideoEvent -> event.imetaTags().isNotEmpty()
        is LiveActivitiesClipEvent -> event.videoUrl() != null
        else -> false
    }

/** True when this `imeta` describes an image — by declared mime type, or failing that its URL. */
internal fun IMetaTag.isImage(): Boolean {
    val mime = mimeType()?.firstOrNull()
    return if (mime != null) mime.startsWith("image/") else RichTextParser.isImageUrl(url)
}

/**
 * The first image carried in an [event]'s `imeta` tags, as a renderable [MediaUrlImage], or null
 * if the note has none. Covers the common kind-1 image post whose content is just a media URL —
 * a case [GalleryThumbnail] does not handle (its when-branches fall through to the author banner).
 * Only images are returned; a video-only imeta yields null and the card falls back to the avatar
 * rather than feeding a video URL to the image loader.
 */
internal fun firstContentImage(event: Event): MediaUrlImage? {
    val imeta = event.imetas().firstOrNull { it.isImage() } ?: return null
    return MediaUrlImage(
        url = imeta.url,
        description = imeta.alt()?.firstOrNull(),
        blurhash = imeta.blurhash()?.firstOrNull(),
        mimeType = imeta.mimeType()?.firstOrNull(),
    )
}

/**
 * The one-or-two-line description under the author name: an article's title, else the note's own
 * text (with any embedded media URLs stripped), else the image's alt text, else a kind label.
 *
 * F3: when [isGated] (the same sensitive-and-not-opted-in decision computed for the thumbnail,
 * passed in rather than recomputed) title, content and alt text are all skipped in favor of the
 * neutral kind label — otherwise this line would render the sensitive note's own text unblurred
 * right next to the covered thumbnail, which is exactly what `Text.kt`'s `SensitivityWarning`
 * wrapping exists to prevent for note bodies elsewhere in the app. The author name stays visible
 * either way; only this line is affected.
 *
 * [hasContentImage] lets an image-only post whose text and alt are both empty fall back to a
 * "Picture" label rather than a blank line.
 */
@Composable
private fun secondaryLineFor(
    event: Event?,
    isGated: Boolean,
    hasContentImage: Boolean,
): String {
    if (event == null) return ""

    // F10: remembered against (event, isGated) so a long article body is not re-trimmed on every
    // recomposition — only when the underlying event or the gate decision actually changes.
    val bodyText = remember(event, isGated) { secondaryBodyTextFor(event, isGated) }
    if (bodyText != null) return bodyText

    return when {
        event is PictureEvent -> stringRes(Res.string.share_as_qr_kind_picture)
        event is VideoEvent -> stringRes(Res.string.kind_video)
        event is LongFormContentEvent -> stringRes(Res.string.article)
        hasContentImage -> stringRes(Res.string.share_as_qr_kind_picture)
        else -> ""
    }
}

// Plain (non-@Composable) so it can be wrapped in `remember` — `stringRes` calls, which the
// kind-label fallback needs, are not allowed inside a remember calculation lambda.
internal fun secondaryBodyTextFor(
    event: Event,
    isGated: Boolean,
): String? {
    // Gated: never surface the note's own title, content or alt text, only the kind label above.
    if (isGated) return null

    if (event is LongFormContentEvent) {
        val title = event.title()
        if (!title.isNullOrBlank()) return title
    }

    // An image-only post's content is typically just the media URL(s). Strip any that appear
    // verbatim (only when the note actually has imeta media, so a plain article — no imeta — is
    // never scanned) so the line does not show a bare CDN URL. If nothing meaningful remains,
    // fall through to the alt text, then the kind label.
    val mediaUrls = event.imetas().map { it.url }
    val stripped =
        if (mediaUrls.isEmpty()) {
            event.content
        } else {
            mediaUrls.fold(event.content) { acc, url -> acc.replace(url, "") }
        }
    // F10: bounded prefix — this line only ever shows two lines of bodySmall text, so there is
    // no need to trim() a full long-form article body (potentially tens of KB) to get there.
    val content = stripped.take(MAX_SECONDARY_LINE_CHARS).trim()
    if (content.isNotEmpty()) return content

    return event
        .imetas()
        .firstOrNull { it.isImage() }
        ?.alt()
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }
}

private const val MAX_SECONDARY_LINE_CHARS = 280
