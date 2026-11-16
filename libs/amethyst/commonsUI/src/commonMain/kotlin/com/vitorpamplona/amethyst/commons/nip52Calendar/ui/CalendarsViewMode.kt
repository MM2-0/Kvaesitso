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
package com.vitorpamplona.amethyst.commons.nip52Calendar.ui

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_view_day
import com.vitorpamplona.amethyst.commons.resources.calendar_view_feed
import com.vitorpamplona.amethyst.commons.resources.calendar_view_follows_going
import com.vitorpamplona.amethyst.commons.resources.calendar_view_month
import com.vitorpamplona.amethyst.commons.resources.calendar_view_week
import org.jetbrains.compose.resources.StringResource

/**
 * Lenses on the same appointment timeline. Calendar *collections* (kind 31924) live on their
 * own screen ([CalendarCollectionsScreen]) since they're a sibling feed, not a different view
 * of the appointment data.
 *
 * [FOLLOWS_GOING] is the one lens not drawn from the appointment timeline: it is built from the
 * RSVPs of the people in the selected list, showing the upcoming events they said they'd attend.
 */
enum class CalendarsViewMode(
    val labelRes: StringResource,
) {
    FEED(Res.string.calendar_view_feed),
    FOLLOWS_GOING(Res.string.calendar_view_follows_going),
    MONTH(Res.string.calendar_view_month),
    WEEK(Res.string.calendar_view_week),
    DAY(Res.string.calendar_view_day),
}
