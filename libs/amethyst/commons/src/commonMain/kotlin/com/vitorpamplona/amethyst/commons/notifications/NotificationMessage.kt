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
package com.vitorpamplona.amethyst.commons.notifications

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User

/** What a notification is about. Each platform maps it to its own channel, accent color and icon. */
enum class NotificationTopic {
    DIRECT_MESSAGE,
    REPLY,
    MENTION,
    REACTION,
    REPOST,
    ZAP,
    MEDIA,
    ARTICLE,
    CODE,
    BADGE,
    CHESS,
    PAYMENT_RECEIVED,
}

/**
 * The content of one notification, ready to deliver: Android posts it to the shade,
 * the desktop shows it as an OS notification.
 */
data class NotificationMessage(
    val topic: NotificationTopic,
    /** Hex id of the event the notification is for. It is also the notification's key, so posting it again replaces it. */
    val id: String,
    val title: String,
    val body: String,
    /** Seconds since the epoch. */
    val time: Long,
    /** The sender's avatar. */
    val pictureUrl: String?,
    /** The deep link a tap opens, from [NotificationRoutes]. */
    val uri: String,
    /** An image to show expanded: a photo, a video poster, an image linked in the text. */
    val bigPictureUrl: String? = null,
    /** A small image over the avatar, such as a NIP-30 custom emoji reaction. */
    val badgeUrl: String? = null,
)

/**
 * A notification whose text depends on profiles and notes that may not be loaded yet.
 *
 * [render] builds the message from what the cache holds now. A platform can render once,
 * or load [users] and [notes] and render again until [isComplete] says the names are in.
 */
class NotificationDraft(
    val id: String,
    val users: List<User>,
    val notes: List<Note>,
    val isComplete: () -> Boolean,
    val render: suspend () -> NotificationMessage,
)
