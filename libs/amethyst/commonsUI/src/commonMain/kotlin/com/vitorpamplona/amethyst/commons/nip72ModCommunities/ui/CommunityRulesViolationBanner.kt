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
package com.vitorpamplona.amethyst.commons.nip72ModCommunities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.community_rules_violation_author_denied
import com.vitorpamplona.amethyst.commons.resources.community_rules_violation_icon
import com.vitorpamplona.amethyst.commons.resources.community_rules_violation_kind_not_allowed
import com.vitorpamplona.amethyst.commons.resources.community_rules_violation_kind_size_exceeded
import com.vitorpamplona.amethyst.commons.resources.community_rules_violation_max_size_exceeded
import com.vitorpamplona.amethyst.commons.resources.community_rules_violation_quota_exceeded
import com.vitorpamplona.amethyst.commons.resources.community_rules_violation_stale_rules
import com.vitorpamplona.amethyst.commons.resources.community_rules_violation_wot_gate_failed
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesValidator

/**
 * Inline banner shown above the composer's send button when the current draft
 * violates the latest NIP-9B `kind:34551` rules document for the community
 * being posted into. The send button is disabled in lockstep — see
 * `CommentPostViewModel.canPost`.
 */
@Composable
fun CommunityRulesViolationBanner(violation: CommunityRulesValidator.Violation) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.Warning,
            contentDescription = stringRes(Res.string.community_rules_violation_icon),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = describeViolation(violation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun describeViolation(violation: CommunityRulesValidator.Violation): String =
    when (violation) {
        is CommunityRulesValidator.Violation.AuthorDenied -> {
            stringRes(Res.string.community_rules_violation_author_denied)
        }

        is CommunityRulesValidator.Violation.KindNotAllowed -> {
            stringRes(Res.string.community_rules_violation_kind_not_allowed, violation.kind)
        }

        is CommunityRulesValidator.Violation.KindSizeExceeded -> {
            stringRes(
                Res.string.community_rules_violation_kind_size_exceeded,
                violation.sizeBytes,
                violation.maxBytes,
            )
        }

        is CommunityRulesValidator.Violation.MaxSizeExceeded -> {
            stringRes(
                Res.string.community_rules_violation_max_size_exceeded,
                violation.sizeBytes,
                violation.maxBytes,
            )
        }

        is CommunityRulesValidator.Violation.QuotaExceeded -> {
            stringRes(
                Res.string.community_rules_violation_quota_exceeded,
                violation.postsToday,
                violation.maxPerDay,
            )
        }

        is CommunityRulesValidator.Violation.WotGateFailed -> {
            stringRes(Res.string.community_rules_violation_wot_gate_failed)
        }

        is CommunityRulesValidator.Violation.StaleRules -> {
            stringRes(Res.string.community_rules_violation_stale_rules)
        }
    }
