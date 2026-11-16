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
package com.vitorpamplona.amethyst.commons.feeds

import com.vitorpamplona.quartz.drss.eventList.DrssEventListEvent
import com.vitorpamplona.quartz.drss.feedList.DrssFeedListEvent
import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceFolderEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.request.EncryptionKeyRequestEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.response.EncryptionKeyTransferEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutTemplateEvent
import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.experimental.kanban.card.KanbanCardEvent
import com.vitorpamplona.quartz.experimental.postingStreak.PostingStreakEvent
import com.vitorpamplona.quartz.experimental.profileTheme.active.ActiveProfileThemeEvent
import com.vitorpamplona.quartz.experimental.profileTheme.definition.ThemeDefinitionEvent
import com.vitorpamplona.quartz.experimental.topEight.TopEightEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetBundle.AssetBundleEvent
import com.vitorpamplona.quartz.experimental.zapstore.identityProof.IdentityProofEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.LegacyKeyPackageEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.UnrecognizedShape
import com.vitorpamplona.quartz.nip18Reposts.BaseRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.draft.LongFormDraftEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.stickers.StickerPackEvent
import com.vitorpamplona.quartz.nip34Git.ci.coordinatorAdvertisement.CiCoordinatorAdvertisementEvent
import com.vitorpamplona.quartz.nip34Git.ci.jobResult.CiJobResultEvent
import com.vitorpamplona.quartz.nip34Git.ci.manualTrigger.CiManualTriggerEvent
import com.vitorpamplona.quartz.nip34Git.ci.repositoryStatus.CiRepositoryStatusEvent
import com.vitorpamplona.quartz.nip34Git.ci.requestReadiness.CiRequestReadinessListEvent
import com.vitorpamplona.quartz.nip34Git.ci.secretUpdate.CiSecretUpdateEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceRequest.CiServiceRequestEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceStop.CiServiceStopEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.SiteSnapshotEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDevFee.MostroDevFeePaymentEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDispute.MostroDisputeEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.MostroInfoEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.MostroUserRatingEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.robosatsRating.RoboSatsCoordinatorRatingEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.ProductCollectionEvent
import com.vitorpamplona.quartz.nip99Classifieds.shipping.ShippingOptionEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPrivateNoteStorage.PnsEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * A repost (kind 6 / kind 16) wraps another event. When that inner kind has no
 * typed Quartz class — e.g. a kind some app invented that Quartz does not model,
 * wrapped in a generic repost — Amethyst can neither parse nor render it, so the repost would
 * show as a permanently blank card. Feeds use this predicate in their acceptance
 * allow-list to drop such reposts, mirroring how regular unknown-kind events are
 * never displayed (no UI component renders a bare [Event]).
 *
 * Returns true only for reposts whose boosted content is displayable, so it can
 * replace the `is RepostEvent || is GenericRepostEvent` clause in a feed's
 * acceptance allow-list. Non-reposts return false (they are admitted by the
 * other clauses).
 *
 * A kind Quartz types but Amethyst has no card for ([TYPED_WITHOUT_A_CARD]) is hidden too: it would
 * fall through to the plain-text card and show its raw `content` — a CI log tail, an empty theme.
 *
 * A kind shared by a carded class and uncarded ones ([KINDS_WITH_A_CARD_BY_CLASS]) is decided per
 * event: the repost is shown only when the event it embeds parses to a class with a card. A shared
 * kind whose carded shape is the usual one ([KINDS_USUALLY_WITH_A_CARD]) is shown unless the event it
 * embeds proves otherwise.
 *
 * Conservative: a repost that declares no boosted `k` kind is assumed renderable
 * — we only hide when we can positively prove the inner kind is unknown.
 *
 * The `returns(true) implies non-null` contract lets it stand in for the two
 * `is` checks in an allow-list without losing the chain's non-null smart-cast
 * (the `&& filterParams.match(noteEvent, …)` tail relies on it).
 */
@OptIn(ExperimentalContracts::class)
fun Event?.isRenderableRepost(): Boolean {
    contract { returns(true) implies (this@isRenderableRepost != null) }
    if (this !is BaseRepostEvent) return false
    val boostedKind = boostedKind()
    if (boostedKind != null && boostedKind in KINDS_WITH_A_CARD_BY_CLASS) return embeddedEvent()?.hasACardOfItsShape() == true
    if (boostedKind != null && boostedKind in KINDS_USUALLY_WITH_A_CARD) return embeddedEvent()?.hasACardOfItsShape() ?: true
    return boostedKind == null || (EventFactory.isKnownKind(boostedKind) && boostedKind !in TYPED_WITHOUT_A_CARD)
}

/**
 * Kinds several apps share, where Quartz picks the class from the tags and only some of the classes
 * have a card: 30301 (Kanban board, WalletScrutiny verification, or an encrypted planner's task),
 * 30302 (Kanban card, or a Fieldbook membership record), 38385 (Mostro instance terms, or a
 * Paygress / bondtrade / game record), 30405 (Open Markets collection, or a Shopstr encrypted cart)
 * and 30406 (Open Markets shipping option, or a Shopstr escrow record). The kind alone cannot say whether a repost of one shows
 * anything, so [isRenderableRepost] decides on the reposted event itself.
 */
val KINDS_WITH_A_CARD_BY_CLASS: Set<Int> =
    setOf(
        KanbanBoardEvent.KIND,
        KanbanCardEvent.KIND,
        MostroInfoEvent.KIND,
        ProductCollectionEvent.KIND,
        ShippingOptionEvent.KIND,
        // drss.io's lists, folders and provenance notes; other apps' events on these numbers
        // (room listings, votes, Ditto ratings) have no card.
        DrssFeedListEvent.KIND,
        DrssListFolderEvent.KIND,
        DrssEventListEvent.KIND,
        DrssProvenanceFolderEvent.KIND,
        DrssProvenanceEvent.KIND,
    )

/**
 * Shared kinds where the carded shape is the one a repost almost always carries: 30054 is a
 * Podcasting-2.0 episode or drss.io's listening progress, both with cards. A repost that embeds
 * nothing is shown, as it was before the kind was split; one that embeds another app's shape is not.
 */
val KINDS_USUALLY_WITH_A_CARD: Set<Int> =
    setOf(
        Podcasting20EpisodeEvent.KIND,
    )

/**
 * False for the shapes of a shared kind that Amethyst draws no card for: the `UnrecognizedKind…Event`
 * classes `EventFactory` builds for other apps' formats ([UnrecognizedShape]); true otherwise.
 */
fun Event.hasACardOfItsShape(): Boolean = this !is UnrecognizedShape

/**
 * The event a repost carries in its `content` (NIP-18 asks for the stringified event). Parsed only
 * for the shared kinds above, which are rare in a feed, so the JSON parse costs nothing overall.
 * Without it a repost of a shared kind stays hidden: on 30301 the commonest shape by far is the
 * planner's encrypted task, so assuming a board would be wrong more often than right.
 */
private fun BaseRepostEvent.embeddedEvent(): Event? =
    when (this) {
        is RepostEvent -> containedPost()
        is GenericRepostEvent -> containedPost()
        else -> null
    }

/**
 * Kinds Quartz parses but Amethyst draws no card for, so a repost of one stays hidden exactly as it
 * was while the kind was untyped. These were typed after the 2026-10-08 relay census so that the
 * stores, search and graph understand them; typing a kind must not, by itself, put it in a feed.
 *
 * Remove a kind from here in the change that gives it a card.
 */
val TYPED_WITHOUT_A_CARD: Set<Int> =
    setOf(
        // The Nostr CI family: shown as a status badge and a runs sheet on the PR / patch it ran for,
        // never as a card of its own. (NIP-34 cover notes, 1624, have a card: RenderGitCoverNoteEvent.)
        CiManualTriggerEvent.KIND,
        CiJobResultEvent.KIND,
        CiWorkflowResultEvent.KIND,
        CiServiceRequestEvent.KIND,
        CiServiceStopEvent.KIND,
        CiWorkflowProgressEvent.KIND,
        CiCoordinatorAdvertisementEvent.KIND,
        CiRequestReadinessListEvent.KIND,
        CiRepositoryStatusEvent.KIND,
        CiSecretUpdateEvent.KIND,
        // P2P-trading side traffic: ratings, disputes, fee receipts. (Instance terms, 38385, have
        // a card: see KINDS_WITH_A_CARD_BY_CLASS.)
        MostroUserRatingEvent.KIND,
        MostroDisputeEvent.KIND,
        MostroDevFeePaymentEvent.KIND,
        RoboSatsCoordinatorRatingEvent.KIND,
        // Encrypted, key-material or key-transfer traffic: nothing to show.
        PnsEvent.KIND,
        LegacyKeyPackageEvent.KIND,
        EncryptionKeyRequestEvent.KIND,
        EncryptionKeyTransferEvent.KIND,
        // Siblings of kinds that have cards, which the cards do not handle yet.
        SiteSnapshotEvent.KIND,
        LongFormDraftEvent.KIND,
        StickerPackEvent.KIND,
        WorkoutTemplateEvent.KIND,
        // App-release trust: Zapstore identity proofs and WalletScrutiny asset bundles. (The
        // verdicts and Kanban boards on 30301, and Kanban cards on 30302, have cards: see
        // KINDS_WITH_A_CARD_BY_CLASS.)
        IdentityProofEvent.KIND,
        AssetBundleEvent.KIND,
        // Profile customisation: belongs on a profile, not in a feed.
        ActiveProfileThemeEvent.KIND,
        ThemeDefinitionEvent.KIND,
        PostingStreakEvent.KIND,
        TopEightEvent.KIND,
    )
