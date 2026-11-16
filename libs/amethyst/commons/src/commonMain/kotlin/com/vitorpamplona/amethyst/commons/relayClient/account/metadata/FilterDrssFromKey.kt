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
package com.vitorpamplona.amethyst.commons.relayClient.account.metadata

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.drss.feedList.DrssFeedListEvent
import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgressAppDataEvent
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgressEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceFolderEvent
import com.vitorpamplona.quartz.drss.readWriteLists.DrssReadWriteListsEvent
import com.vitorpamplona.quartz.drss.show.DrssShowEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip32Labeling.tags.LabelTag
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent

/**
 * The drss.io reader's own lists: folders, feed lists, provenance and shows, and the article
 * curation sets drss saves into (follow sets already come with the follows).
 */
val DrssListsFromKeyKinds =
    listOf(
        DrssListFolderEvent.KIND,
        DrssFeedListEvent.KIND,
        DrssProvenanceFolderEvent.KIND,
        DrssProvenanceEvent.KIND,
        DrssShowEvent.KIND,
        ArticleCurationSetEvent.KIND,
    )

/** How many listening-progress records to load: one per episode ever played. */
const val DRSS_PROGRESS_LIMIT = 500

/**
 * The account's drss.io data: its lists, its listening progress and its read-and-write
 * preference. drss keeps listening progress in kind 30078, labelled `l` = `listen-progress` so
 * relays can pick those out of every app's preferences; the kind-30054 records it wrote before
 * (also the podcast-episode kind, so an account's own episodes come along) load until drss has
 * moved them.
 */
fun filterDrssFromKey(
    relay: NormalizedRelayUrl,
    pubkeys: List<HexKey>,
    since: Long?,
): List<RelayBasedFilter> {
    if (pubkeys.isEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ACCOUNT_DATA,
                    accountPubKeys = pubkeys,
                    kinds = DrssListsFromKeyKinds,
                    authors = pubkeys,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ACCOUNT_DATA,
                    accountPubKeys = pubkeys,
                    kinds = listOf(DrssListenProgressAppDataEvent.KIND),
                    authors = pubkeys,
                    tags = mapOf(LabelTag.TAG_NAME to listOf(DrssListenProgressAppDataEvent.LABEL)),
                    limit = DRSS_PROGRESS_LIMIT * pubkeys.size,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ACCOUNT_DATA,
                    accountPubKeys = pubkeys,
                    kinds = listOf(DrssListenProgressEvent.KIND),
                    authors = pubkeys,
                    limit = DRSS_PROGRESS_LIMIT * pubkeys.size,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ACCOUNT_DATA,
                    accountPubKeys = pubkeys,
                    kinds = listOf(DrssReadWriteListsEvent.KIND),
                    authors = pubkeys,
                    tags = mapOf("d" to listOf(DrssReadWriteListsEvent.D_TAG)),
                    since = since,
                ),
        ),
    )
}
