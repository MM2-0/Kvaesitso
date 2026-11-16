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
package com.vitorpamplona.amethyst.commons.ui.components.toasts

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.amethyst.commons.notices.UserNoticeSink
import com.vitorpamplona.amethyst.commons.ui.components.toasts.multiline.MultiErrorToastMsg
import com.vitorpamplona.amethyst.commons.ui.components.toasts.multiline.UserBasedErrorMessage
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource

/**
 * The messages the app shows the user: one dialog at a time ([toasts] is the one on screen), the
 * rest queued behind it, so a second message never silently replaces the first. Closing one
 * ([clearToasts]) shows the next. A message saying the same thing as one already shown or queued
 * is dropped, and the queue is capped so a burst of failures can't stack up dialogs.
 */
@Stable
class ToastManager : UserNoticeSink {
    /** The message on screen, or null. */
    val toasts = MutableStateFlow<ToastMsg?>(null)

    private val lock = KmpLock()
    private val pending = ArrayDeque<ToastMsg>()

    /** Closes the message on screen and shows the next one, if any. */
    fun clearToasts() {
        lock.withLock { toasts.value = pending.removeFirstOrNull() }
    }

    private fun show(msg: ToastMsg) {
        lock.withLock {
            val current = toasts.value
            when {
                current == null -> toasts.value = msg
                msg.dedupeKey != null && (current.dedupeKey == msg.dedupeKey || pending.any { it.dedupeKey == msg.dedupeKey }) -> Unit
                pending.size < MAX_PENDING -> pending.addLast(msg)
            }
        }
    }

    /** Headless code reports here through [UserNoticeSink]; the dialog words the notice. */
    override fun notify(notice: UserNotice) {
        show(NoticeToastMsg(notice))
    }

    fun toast(
        title: String,
        message: String,
    ) {
        show(StringToastMsg(title, message))
    }

    fun toast(
        title: String,
        message: String,
        action: () -> Unit,
    ) {
        show(ActionableStringToastMsg(title, message, action))
    }

    fun toast(
        titleResId: StringResource,
        resourceId: StringResource,
    ) {
        show(ResourceToastMsg(titleResId, resourceId))
    }

    fun toast(
        titleResId: StringResource,
        message: String?,
        throwable: Throwable,
    ) {
        show(ThrowableToastMsg(titleResId, message, throwable))
    }

    fun toast(
        titleResId: StringResource,
        description: StringResource,
        throwable: Throwable,
    ) {
        show(ThrowableToastMsg2(titleResId, description, throwable))
    }

    fun toast(
        titleResId: StringResource,
        resourceId: StringResource,
        vararg params: String,
    ) {
        show(ResourceToastMsg(titleResId, resourceId, params))
    }

    fun toast(
        titleResId: StringResource,
        message: String,
        user: User?,
    ) {
        toast(titleResId, UserBasedErrorMessage(message, user))
    }

    /** Errors with the same title gather in one list dialog, whether it is on screen or still queued. */
    fun toast(
        titleResId: StringResource,
        data: UserBasedErrorMessage,
    ) {
        lock.withLock {
            val open = (listOfNotNull(toasts.value) + pending).firstOrNull { it is MultiErrorToastMsg && it.titleResId == titleResId } as MultiErrorToastMsg?
            if (open != null) {
                open.add(data)
                return
            }
        }
        show(MultiErrorToastMsg(titleResId).also { it.add(data) })
    }

    private companion object {
        /** Messages waiting behind the one on screen; beyond this, new ones are dropped. */
        const val MAX_PENDING = 10
    }
}
