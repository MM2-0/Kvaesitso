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
package com.vitorpamplona.amethyst.commons.nip56Reports.ui

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.explicit_content
import com.vitorpamplona.amethyst.commons.resources.harassment
import com.vitorpamplona.amethyst.commons.resources.illegal_behavior
import com.vitorpamplona.amethyst.commons.resources.impersonation
import com.vitorpamplona.amethyst.commons.resources.malware
import com.vitorpamplona.amethyst.commons.resources.nudity
import com.vitorpamplona.amethyst.commons.resources.other
import com.vitorpamplona.amethyst.commons.resources.profanity_hateful_speech
import com.vitorpamplona.amethyst.commons.resources.spam
import com.vitorpamplona.amethyst.commons.resources.violence
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip56Reports.ReportType

/**
 * [reportTypeLabel] for each reason. Resolved into a list first because `joinToString` is not
 * inline and so cannot call a composable, while `map` is.
 */
@Composable
fun reportTypeLabels(types: Collection<ReportType?>): List<String> = types.map { reportTypeLabel(it) }

/** The localized name of a NIP-56 report reason. An unrecognized reason reads as "Other". */
@Composable
fun reportTypeLabel(type: ReportType?): String =
    when (type) {
        ReportType.EXPLICIT -> stringRes(Res.string.explicit_content)
        ReportType.NUDITY -> stringRes(Res.string.nudity)
        ReportType.PROFANITY -> stringRes(Res.string.profanity_hateful_speech)
        ReportType.SPAM -> stringRes(Res.string.spam)
        ReportType.IMPERSONATION -> stringRes(Res.string.impersonation)
        ReportType.ILLEGAL -> stringRes(Res.string.illegal_behavior)
        ReportType.MALWARE -> stringRes(Res.string.malware)
        ReportType.OTHER -> stringRes(Res.string.other)
        ReportType.HARASSMENT -> stringRes(Res.string.harassment)
        ReportType.VIOLENCE -> stringRes(Res.string.violence)
        null -> stringRes(Res.string.other)
    }
