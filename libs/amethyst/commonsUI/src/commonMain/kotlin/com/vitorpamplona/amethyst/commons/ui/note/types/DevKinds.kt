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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiRollup
import com.vitorpamplona.amethyst.commons.nip34Git.ci.CiState
import com.vitorpamplona.amethyst.commons.nip34Git.ui.ciLogTailLines
import com.vitorpamplona.amethyst.commons.nip34Git.ui.ciSymbol
import com.vitorpamplona.amethyst.commons.nip34Git.ui.conclusionLabel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_ci_full_log
import com.vitorpamplona.amethyst.commons.resources.git_ci_in_progress
import com.vitorpamplona.amethyst.commons.resources.git_ci_queued
import com.vitorpamplona.amethyst.commons.resources.git_repo_branches
import com.vitorpamplona.amethyst.commons.resources.git_repo_tags
import com.vitorpamplona.amethyst.commons.resources.kc_dev_app_id
import com.vitorpamplona.amethyst.commons.resources.kc_dev_asset_bundle_untitled
import com.vitorpamplona.amethyst.commons.resources.kc_dev_certificate
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_accepts_secrets
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_admission
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_admission_maintainer
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_admission_open
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_admission_operator
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_artifacts
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_billing
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_billing_free
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_billing_out_of_band
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_coordinator
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_coordinator_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_execution
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_execution_automatic
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_execution_request
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_exit_code
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_job_cancelled
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_job_failed
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_job_finished
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_job_passed
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_jobs
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_maintainers
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_manual_trigger_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_policy_unknown
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_readiness_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_repositories
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_run_cancelled
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_run_failed
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_run_finished
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_run_in_progress
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_run_passed
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_run_queued
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_runners
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_running_jobs
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_secrets
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_service_active
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_service_request_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_service_status
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_service_stop_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_trigger_manual
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_trigger_pull_request
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_trigger_push
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_trigger_schedule
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_until
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_workflows
import com.vitorpamplona.amethyst.commons.resources.kc_dev_expired
import com.vitorpamplona.amethyst.commons.resources.kc_dev_expires
import com.vitorpamplona.amethyst.commons.resources.kc_dev_feed_authors
import com.vitorpamplona.amethyst.commons.resources.kc_dev_feed_subtitle
import com.vitorpamplona.amethyst.commons.resources.kc_dev_feed_untitled
import com.vitorpamplona.amethyst.commons.resources.kc_dev_files_count
import com.vitorpamplona.amethyst.commons.resources.kc_dev_grasp_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_identity_proof_subtitle
import com.vitorpamplona.amethyst.commons.resources.kc_dev_identity_proof_title
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ipv4
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ipv6
import com.vitorpamplona.amethyst.commons.resources.kc_dev_napplet_snapshot
import com.vitorpamplona.amethyst.commons.resources.kc_dev_nns_subtitle
import com.vitorpamplona.amethyst.commons.resources.kc_dev_permissions
import com.vitorpamplona.amethyst.commons.resources.kc_dev_reason
import com.vitorpamplona.amethyst.commons.resources.kc_dev_repo_state
import com.vitorpamplona.amethyst.commons.resources.kc_dev_repo_state_archived
import com.vitorpamplona.amethyst.commons.resources.kc_dev_repo_state_head
import com.vitorpamplona.amethyst.commons.resources.kc_dev_revoked
import com.vitorpamplona.amethyst.commons.resources.kc_dev_servers
import com.vitorpamplona.amethyst.commons.resources.kc_dev_site_snapshot
import com.vitorpamplona.amethyst.commons.resources.kc_dev_source
import com.vitorpamplona.amethyst.commons.resources.kind_card_private_pill
import com.vitorpamplona.amethyst.commons.resources.media_server_status_offline
import com.vitorpamplona.amethyst.commons.resources.media_server_status_online
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.timeAbsoluteNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.stringOrNull
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetBundle.AssetBundleEvent
import com.vitorpamplona.quartz.experimental.zapstore.identityProof.IdentityProofEvent
import com.vitorpamplona.quartz.feedDefinition.FeedDefinitionEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip01Core.tags.references.HttpUrlFormatter
import com.vitorpamplona.quartz.nip34Git.ci.coordinatorAdvertisement.CiCoordinatorAdvertisementEvent
import com.vitorpamplona.quartz.nip34Git.ci.jobResult.CiJobResultEvent
import com.vitorpamplona.quartz.nip34Git.ci.manualTrigger.CiManualTriggerEvent
import com.vitorpamplona.quartz.nip34Git.ci.repositoryStatus.CiRepositoryStatusEvent
import com.vitorpamplona.quartz.nip34Git.ci.requestReadiness.CiRequestReadinessListEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceRequest.CiServiceRequestEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceStop.CiServiceStopEvent
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiAdmissionPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiBillingPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiExecutionPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiTrigger
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowStatus
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import com.vitorpamplona.quartz.nip34Git.grasp.UserGraspListEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.nip34Git.state.tags.RefTag
import com.vitorpamplona.quartz.nip5aStaticWebsites.SiteSnapshotEvent
import com.vitorpamplona.quartz.nip5dNapplets.snapshot.NappletSnapshotEvent
import com.vitorpamplona.quartz.nip78AppData.AppDataEvent
import org.jetbrains.compose.resources.StringResource

/*
 * Cards for the developer-tooling kinds: NIP-34 CI (runs, jobs, coordinators, service requests),
 * repository state and grasp server lists, app-store proofs and asset bundles, nSite and nApplet
 * snapshots, feed definitions, NIP-78 app data and NNS records. Most of them carry their meaning
 * in tags and have empty or machine content, so the text fallback showed nothing useful.
 */

private const val MAX_BRANCH_ROWS = 4
private const val MAX_TAG_CHIPS = 6
private const val MAX_SERVER_CHIPS = 4
private const val MAX_ARTIFACTS = 3
private const val MAX_FILE_ROWS = 4
private const val LOG_TAIL_LINES = 6

// ---------------------------------------------------------------------------------------------
// NIP-34 CI
// ---------------------------------------------------------------------------------------------

/** Kind 39842: a workflow run's live progress (queued, running, concluded). */
@Composable
fun RenderCiWorkflowProgress(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiWorkflowProgressEvent ?: return
    val info =
        remember(event) {
            CiRunInfo(
                workflow = event.workflow()?.path,
                repository = event.repositories().firstOrNull()?.dTag,
                commit = event.commit(),
                trigger = event.trigger(),
                pullRequest = event.pullRequest()?.pullRequestId,
            )
        }
    val status = remember(event) { event.status() }
    val conclusion = remember(event) { event.conclusion() }
    val jobs = remember(event) { event.jobResults().map { it.jobId } }
    val running = remember(event) { event.inProgressJobs() }

    val title: String
    val pill: String?
    val state: CiState
    when (status) {
        CiWorkflowStatus.QUEUED -> {
            state = CiState.PENDING
            title = stringRes(Res.string.kc_dev_ci_run_queued)
            pill = stringRes(Res.string.git_ci_queued)
        }

        CiWorkflowStatus.IN_PROGRESS -> {
            state = CiState.PENDING
            title = stringRes(Res.string.kc_dev_ci_run_in_progress)
            pill = stringRes(Res.string.git_ci_in_progress)
        }

        else -> {
            state = CiRollup.stateOf(conclusion)
            title = stringRes(runTitle(state))
            pill = conclusion?.let { stringRes(conclusionLabel(it)) }
        }
    }

    KindCard(
        icon = if (status == CiWorkflowStatus.QUEUED) MaterialSymbols.Schedule else ciSymbol(state),
        title = title,
        subtitle = info.workflowName,
        tone = ciTone(state),
        status = pill,
    ) {
        CiRunMeta(info)
        KindCardField(stringRes(Res.string.kc_dev_ci_running_jobs), running.joinToString(", "))
        KindCardField(stringRes(Res.string.kc_dev_ci_jobs), jobs.joinToString(", "))
        CiPullRequestQuote(info.pullRequest, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** Kind 9842: the final result of a workflow run. */
@Composable
fun RenderCiWorkflowResult(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiWorkflowResultEvent ?: return
    val info =
        remember(event) {
            CiRunInfo(
                workflow = event.workflow()?.path,
                repository = event.repositories().firstOrNull()?.dTag,
                commit = event.commit(),
                trigger = event.trigger(),
                pullRequest = event.pullRequest()?.pullRequestId,
            )
        }
    val conclusion = remember(event) { event.conclusion() }
    val jobs = remember(event) { event.jobResults().map { it.jobId } }
    val state = CiRollup.stateOf(conclusion)

    KindCard(
        icon = ciSymbol(state),
        title = stringRes(runTitle(state)),
        subtitle = info.workflowName,
        tone = ciTone(state),
        status = conclusion?.let { stringRes(conclusionLabel(it)) },
    ) {
        CiRunMeta(info)
        KindCardField(stringRes(Res.string.kc_dev_ci_jobs), jobs.joinToString(", "))
        CiPullRequestQuote(info.pullRequest, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** Kind 9841: one job of a workflow run, with the log tail of a failure, the full log and artifacts. */
@Composable
fun RenderCiJobResult(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiJobResultEvent ?: return
    val info =
        remember(event) {
            CiRunInfo(
                workflow = event.workflow()?.path,
                repository = event.repositories().firstOrNull()?.dTag,
                commit = event.commit(),
                trigger = event.trigger(),
                pullRequest = event.pullRequest()?.pullRequestId,
            )
        }
    val conclusion = remember(event) { event.conclusion() }
    val state = CiRollup.stateOf(conclusion)
    val failed = state == CiState.FAILURE
    val tail = remember(event, failed) { if (failed) readableLogTail(event.logTail(), LOG_TAIL_LINES) else "" }
    val subtitle = remember(event) { listOfNotNull(event.name() ?: event.jobId(), info.workflowName).joinToString("  ·  ") }
    val exitCode = remember(event) { event.exitCode()?.takeIf { it != 0 }?.toString() }
    val artifacts = remember(event) { event.artifacts() }

    KindCard(
        icon = ciSymbol(state),
        title = stringRes(jobTitle(state)),
        subtitle = subtitle,
        tone = ciTone(state),
        status = conclusion?.let { stringRes(conclusionLabel(it)) },
    ) {
        CiRunMeta(info)
        KindCardField(stringRes(Res.string.kc_dev_ci_exit_code), exitCode, monospace = true, maxLines = 1)
        KindCardCode(tail, maxLines = LOG_TAIL_LINES)
        event.logsUrl()?.let { url ->
            ClickableUrlOrBlossom(
                urlText = stringRes(Res.string.git_ci_full_log),
                url = url,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (artifacts.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.kc_dev_ci_artifacts))
            artifacts.take(MAX_ARTIFACTS).forEach { artifact ->
                ClickableUrlOrBlossom(
                    urlText = artifact.name ?: artifact.fileName ?: artifact.url,
                    url = artifact.url,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            KindCardMore(artifacts.size - MAX_ARTIFACTS)
        }
        CiPullRequestQuote(info.pullRequest, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** Kind 9840: a maintainer asks a coordinator to replay a workflow at a commit. */
@Composable
fun RenderCiManualTrigger(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiManualTriggerEvent ?: return
    val info =
        remember(event) {
            CiRunInfo(
                workflow = event.workflow()?.path,
                repository = event.repositories().firstOrNull()?.dTag,
                commit = event.commit(),
                trigger = null,
                pullRequest = event.pullRequest()?.pullRequestId,
            )
        }
    val coordinator = remember(event) { listOfNotNull(event.coordinator()) }

    KindCard(
        icon = MaterialSymbols.PlayArrow,
        title = stringRes(Res.string.kc_dev_ci_manual_trigger_title),
        subtitle = info.workflowName,
    ) {
        CiRunMeta(info)
        CiCoordinatorChip(coordinator, accountViewModel, nav)
        CiPullRequestQuote(info.pullRequest, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** Kind 9843: a maintainer asks a coordinator to start serving a repository. */
@Composable
fun RenderCiServiceRequest(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiServiceRequestEvent ?: return
    val repository = remember(event) { event.repository() }
    val coordinator = remember(event) { listOfNotNull(event.coordinator()) }
    CiServiceCard(
        icon = MaterialSymbols.PlayCircle,
        title = stringRes(Res.string.kc_dev_ci_service_request_title),
        tone = KindTone.Primary,
        repository = repository,
        coordinator = coordinator,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** Kind 9844: a maintainer asks a coordinator to stop serving a repository. */
@Composable
fun RenderCiServiceStop(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiServiceStopEvent ?: return
    val repository = remember(event) { event.repository() }
    val coordinator = remember(event) { listOfNotNull(event.coordinator()) }
    CiServiceCard(
        icon = MaterialSymbols.Stop,
        title = stringRes(Res.string.kc_dev_ci_service_stop_title),
        tone = KindTone.Neutral,
        repository = repository,
        coordinator = coordinator,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
private fun CiServiceCard(
    icon: MaterialSymbol,
    title: String,
    tone: KindTone,
    repository: ATag?,
    coordinator: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    KindCard(icon = icon, title = title, subtitle = repository?.dTag, tone = tone) {
        CiCoordinatorChip(coordinator, accountViewModel, nav)
    }
}

/** Kind 19843: a CI coordinator advertising its software, runners and admission policies. */
@Composable
fun RenderCiCoordinatorAdvertisement(baseNote: Note) {
    val event = baseNote.event as? CiCoordinatorAdvertisementEvent ?: return
    val software = remember(event) { event.software()?.let { listOfNotNull(it.name, it.version).joinToString(" ") } }
    val runners =
        remember(event) {
            (event.runnerFamilies() + event.runnerSelectors().map { it.toValue() }).distinct()
        }
    val admission = remember(event) { event.admission() }
    val execution = remember(event) { event.execution() }
    val billing = remember(event) { event.billing() }
    val acceptsSecrets = remember(event) { event.acceptsSecretUpdates() }
    val liveUntil = rememberAtNow(event) { now -> if (event.isLive(now)) event.validExpiration() else null }

    KindCard(
        icon = MaterialSymbols.Dns,
        title = stringRes(Res.string.kc_dev_ci_coordinator_title),
        subtitle = software,
        status = availabilityPill(liveUntil),
        statusTone = availabilityTone(liveUntil),
    ) {
        KindCardField(stringRes(Res.string.kc_dev_ci_admission), admission?.let { stringRes(admissionLabel(it)) })
        KindCardField(stringRes(Res.string.kc_dev_ci_execution), execution?.let { stringRes(executionLabel(it)) })
        KindCardField(stringRes(Res.string.kc_dev_ci_billing), billing?.let { stringRes(billingLabel(it)) })
        KindCardSectionChips(stringRes(Res.string.kc_dev_ci_runners), runners)
        KindCardMeta(
            listOf(
                liveUntil?.let { stringRes(Res.string.kc_dev_ci_until, timeAbsoluteNoDot(it)) },
                if (acceptsSecrets) stringRes(Res.string.kc_dev_ci_accepts_secrets) else null,
            ),
        )
    }
}

/** Kind 19844: a coordinator's list of repositories (and maintainers) it is ready to serve. */
@Composable
fun RenderCiRequestReadinessList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CiRequestReadinessListEvent ?: return
    val repositories = remember(event) { event.repositories().map { it.dTag } }
    val maintainers = remember(event) { event.maintainers().map { it.pubKey } }
    val liveUntil = rememberAtNow(event) { now -> if (event.isLive(now)) event.validExpiration() else null }

    KindCard(
        icon = MaterialSymbols.Checklist,
        title = stringRes(Res.string.kc_dev_ci_readiness_title),
        status = availabilityPill(liveUntil),
        statusTone = availabilityTone(liveUntil),
    ) {
        KindCardSectionChips(stringRes(Res.string.kc_dev_ci_repositories), repositories)
        if (maintainers.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.kc_dev_ci_maintainers))
            KindCardUsers(maintainers, accountViewModel, nav)
        }
        KindCardMeta(listOf(liveUntil?.let { stringRes(Res.string.kc_dev_ci_until, timeAbsoluteNoDot(it)) }))
    }
}

/** Kind 39844: a coordinator's status for one repository: workflows, runners and secret names. */
@Composable
fun RenderCiRepositoryStatus(baseNote: Note) {
    val event = baseNote.event as? CiRepositoryStatusEvent ?: return
    val repository = remember(event) { event.selectedRepository()?.dTag ?: event.repositories().firstOrNull()?.dTag }
    val acting = remember(event) { event.isActing() }
    val workflows = remember(event) { event.workflowPaths() }
    val runners =
        remember(event) {
            (event.runnerFamilies() + event.runnerSelectors().map { it.toValue() }).distinct()
        }
    val secrets = remember(event) { event.secrets().map { it.name } }
    val liveUntil = rememberAtNow(event) { now -> if (event.isLive(now)) event.validExpiration() else null }

    KindCard(
        icon = MaterialSymbols.Dns,
        title = stringRes(if (acting) Res.string.kc_dev_ci_service_active else Res.string.kc_dev_ci_service_status),
        subtitle = repository,
        tone = if (acting) KindTone.Positive else KindTone.Neutral,
        status = availabilityPill(liveUntil),
        statusTone = availabilityTone(liveUntil),
    ) {
        KindCardSectionChips(stringRes(Res.string.kc_dev_ci_workflows), workflows)
        KindCardSectionChips(stringRes(Res.string.kc_dev_ci_runners), runners)
        KindCardSectionChips(stringRes(Res.string.kc_dev_ci_secrets), secrets)
        KindCardMeta(listOf(liveUntil?.let { stringRes(Res.string.kc_dev_ci_until, timeAbsoluteNoDot(it)) }))
    }
}

/** The run context shared by the CI run kinds. */
private data class CiRunInfo(
    val workflow: String?,
    val repository: String?,
    val commit: String?,
    val trigger: CiTrigger?,
    val pullRequest: HexKey?,
) {
    /** The workflow file's name, without the `.ngit/act/workflows/` folder it sits in. */
    val workflowName: String? = workflow?.substringAfterLast('/')?.ifBlank { null }
}

/** Repository, short commit and trigger of a run, on one muted line. */
@Composable
private fun CiRunMeta(info: CiRunInfo) {
    KindCardMeta(
        listOf(
            info.repository,
            info.commit?.take(8),
            info.trigger?.let { stringRes(triggerLabel(it)) },
        ),
    )
}

@Composable
private fun CiCoordinatorChip(
    coordinator: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (coordinator.isEmpty()) return
    KindCardSection(stringRes(Res.string.kc_dev_ci_coordinator))
    KindCardUsers(coordinator, accountViewModel, nav)
}

@Composable
private fun CiPullRequestQuote(
    pullRequest: HexKey?,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (pullRequest == null) return
    KindCardQuoteEvent(pullRequest, quotesLeft, backgroundColor, accountViewModel, nav)
}

@Composable
private fun availabilityPill(liveUntil: Long?): String = stringRes(if (liveUntil != null) Res.string.media_server_status_online else Res.string.media_server_status_offline)

private fun availabilityTone(liveUntil: Long?): KindTone = if (liveUntil != null) KindTone.Positive else KindTone.Neutral

/**
 * The last [maxLines] readable lines of a job's log tail. Runners like `act` log JSON lines; for
 * those, only each line's `msg` survives, since the rest is bookkeeping. Plain logs keep their lines.
 */
private fun readableLogTail(
    log: String,
    maxLines: Int,
): String {
    val lines = ciLogTailLines(log).filter { it.isNotBlank() }
    if (lines.isEmpty()) return ""
    val jsonLines = lines.count { it.trimStart().startsWith("{") }
    val readable =
        if (jsonLines * 2 >= lines.size) {
            lines.mapNotNull { line ->
                parseJsonObjectOrNull(line)?.let { it.stringOrNull("msg") ?: it.stringOrNull("message") }
            }
        } else {
            lines
        }
    return readable
        .flatMap { it.lines() }
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .takeLast(maxLines)
        .joinToString("\n")
}

private fun ciTone(state: CiState): KindTone =
    when (state) {
        CiState.SUCCESS -> KindTone.Positive
        CiState.FAILURE -> KindTone.Negative
        CiState.PENDING -> KindTone.Warning
        CiState.CANCELLED, CiState.NEUTRAL -> KindTone.Neutral
    }

private fun runTitle(state: CiState): StringResource =
    when (state) {
        CiState.SUCCESS -> Res.string.kc_dev_ci_run_passed
        CiState.FAILURE -> Res.string.kc_dev_ci_run_failed
        CiState.PENDING -> Res.string.kc_dev_ci_run_in_progress
        CiState.CANCELLED -> Res.string.kc_dev_ci_run_cancelled
        CiState.NEUTRAL -> Res.string.kc_dev_ci_run_finished
    }

private fun jobTitle(state: CiState): StringResource =
    when (state) {
        CiState.SUCCESS -> Res.string.kc_dev_ci_job_passed
        CiState.FAILURE -> Res.string.kc_dev_ci_job_failed
        CiState.CANCELLED -> Res.string.kc_dev_ci_job_cancelled
        CiState.PENDING, CiState.NEUTRAL -> Res.string.kc_dev_ci_job_finished
    }

private fun triggerLabel(trigger: CiTrigger): StringResource =
    when (trigger) {
        CiTrigger.PUSH -> Res.string.kc_dev_ci_trigger_push
        CiTrigger.PULL_REQUEST -> Res.string.kc_dev_ci_trigger_pull_request
        CiTrigger.SCHEDULE -> Res.string.kc_dev_ci_trigger_schedule
        CiTrigger.MANUAL -> Res.string.kc_dev_ci_trigger_manual
    }

private fun admissionLabel(policy: CiAdmissionPolicy): StringResource =
    when (policy) {
        CiAdmissionPolicy.OPERATOR_SELECTED -> Res.string.kc_dev_ci_admission_operator
        CiAdmissionPolicy.MAINTAINER_REQUEST -> Res.string.kc_dev_ci_admission_maintainer
        CiAdmissionPolicy.OPEN -> Res.string.kc_dev_ci_admission_open
        CiAdmissionPolicy.UNKNOWN -> Res.string.kc_dev_ci_policy_unknown
    }

private fun executionLabel(policy: CiExecutionPolicy): StringResource =
    when (policy) {
        CiExecutionPolicy.AUTOMATIC -> Res.string.kc_dev_ci_execution_automatic
        CiExecutionPolicy.REQUEST_REQUIRED -> Res.string.kc_dev_ci_execution_request
        CiExecutionPolicy.UNKNOWN -> Res.string.kc_dev_ci_policy_unknown
    }

private fun billingLabel(policy: CiBillingPolicy): StringResource =
    when (policy) {
        CiBillingPolicy.NOT_REQUIRED -> Res.string.kc_dev_ci_billing_free
        CiBillingPolicy.OUT_OF_BAND -> Res.string.kc_dev_ci_billing_out_of_band
        CiBillingPolicy.UNKNOWN -> Res.string.kc_dev_ci_policy_unknown
    }

// ---------------------------------------------------------------------------------------------
// NIP-34 repository state and grasp servers
// ---------------------------------------------------------------------------------------------

/** Kind 30618: a repository's branch tips, tags and HEAD. */
@Composable
fun RenderGitRepositoryState(baseNote: Note) {
    val event = baseNote.event as? GitRepositoryStateEvent ?: return
    val head = remember(event) { event.head() }
    // The HEAD branch first, so the default branch is always among the rows shown.
    val branches = remember(event) { event.branches().sortedByDescending { it.shortName == head } }
    val tags = remember(event) { event.tagRefs().map { it.shortName } }
    val archived = branches.isEmpty() && tags.isEmpty()

    KindCard(
        icon = MaterialSymbols.Commit,
        title = stringRes(Res.string.kc_dev_repo_state),
        subtitle = event.dTag(),
        tone = if (archived) KindTone.Neutral else KindTone.Primary,
        status = if (archived) stringRes(Res.string.kc_dev_repo_state_archived) else null,
    ) {
        if (!archived) {
            KindCardMeta(listOf(head?.let { stringRes(Res.string.kc_dev_repo_state_head, it) }))
        }
        BranchRows(branches)
        KindCardSectionChips(stringRes(Res.string.git_repo_tags), tags, MAX_TAG_CHIPS)
    }
}

@Composable
private fun BranchRows(refs: List<RefTag>) {
    if (refs.isEmpty()) return
    KindCardSection(stringRes(Res.string.git_repo_branches))
    refs.take(MAX_BRANCH_ROWS).forEach { ref ->
        KindCardField(ref.shortName, ref.commit.take(8), monospace = true, maxLines = 1)
    }
    KindCardMore(refs.size - MAX_BRANCH_ROWS)
}

/** Kind 10317: the user's preferred grasp (git-over-nostr hosting) servers, in order. */
@Composable
fun RenderUserGraspList(baseNote: Note) {
    val event = baseNote.event as? UserGraspListEvent ?: return
    val servers = remember(event) { event.graspsNorm().map { it.displayUrl() } }

    KindCard(
        icon = MaterialSymbols.Storage,
        title = stringRes(Res.string.kc_dev_grasp_title),
    ) {
        KindCardChips(servers)
    }
}

// ---------------------------------------------------------------------------------------------
// App stores and verifiers
// ---------------------------------------------------------------------------------------------

/** Kind 30509: Zapstore's proof that an APK signing certificate belongs to this Nostr key. */
@Composable
fun RenderIdentityProof(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? IdentityProofEvent ?: return
    val certificate = remember(event) { (event.certificateHash() ?: event.dTag()).toShortDisplay() }
    val revoked = remember(event) { event.isRevoked() }
    val reason = remember(event) { event.revocationReason() }
    val expiry = remember(event) { event.expiry() }
    val expired = rememberAtNow(event) { now -> event.isExpired(now) }

    KindCard(
        icon = MaterialSymbols.Security,
        title = stringRes(Res.string.kc_dev_identity_proof_title),
        subtitle = stringRes(Res.string.kc_dev_identity_proof_subtitle),
        tone = if (revoked || expired) KindTone.Negative else KindTone.Primary,
        status =
            when {
                revoked -> stringRes(Res.string.kc_dev_revoked)
                expired -> stringRes(Res.string.kc_dev_expired)
                else -> null
            },
    ) {
        KindCardField(stringRes(Res.string.kc_dev_certificate), certificate, monospace = true, maxLines = 1)
        if (revoked) {
            KindCardRichField(stringRes(Res.string.kc_dev_reason), baseNote, reason, backgroundColor, accountViewModel, nav)
        } else if (expiry != null && !expired) {
            KindCardField(stringRes(Res.string.kc_dev_expires), timeAbsoluteNoDot(expiry))
        }
    }
}

/** Kind 9401: WalletScrutiny registers the files of one release by hash, for build verifiers. */
@Composable
fun RenderAssetBundle(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? AssetBundleEvent ?: return
    val productId = remember(event) { event.productId() }
    val files = remember(event) { event.files() }
    val listing =
        remember(event) {
            files.take(MAX_FILE_ROWS).joinToString("\n") { file ->
                listOfNotNull(file.filename, file.hash.toShortDisplay()).joinToString("  ")
            }
        }

    KindCard(
        icon = MaterialSymbols.FolderZip,
        title = stringRes(Res.string.kc_dev_asset_bundle_untitled),
        subtitle = productId,
    ) {
        KindCardMeta(
            listOf(
                event.version(),
                event.platform(),
                if (files.isNotEmpty()) pluralStringRes(Res.plurals.kc_dev_files_count, files.size, files.size) else null,
            ),
        )
        KindCardProse(null, baseNote, event.note(), backgroundColor, accountViewModel, nav)
        KindCardCode(listing, maxLines = MAX_FILE_ROWS)
        KindCardMore(files.size - MAX_FILE_ROWS)
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-5A / NIP-5D snapshots
// ---------------------------------------------------------------------------------------------

/** Kind 5128: one immutable version of an nSite, quoting the site it captures. */
@Composable
fun RenderSiteSnapshot(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? SiteSnapshotEvent ?: return
    val site = remember(event) { event.snapshotOf() }
    val name = remember(event) { event.title() ?: site?.dTag?.ifBlank { null } }

    KindCard(
        icon = MaterialSymbols.Language,
        title = stringRes(Res.string.kc_dev_site_snapshot),
        subtitle = name,
    ) {
        SnapshotFacts(baseNote, remember(event) { SnapshotInfo(event.description(), event.paths().size, event.source(), event.servers()) }, backgroundColor, accountViewModel, nav)
        site?.let { KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** Kind 5129: one immutable build of an nApplet. */
@Composable
fun RenderNappletSnapshot(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? NappletSnapshotEvent ?: return
    val requires = remember(event) { event.required() }
    val title = remember(event) { event.title() }

    KindCard(
        icon = MaterialSymbols.Extension,
        title = stringRes(Res.string.kc_dev_napplet_snapshot),
        subtitle = title,
    ) {
        SnapshotFacts(baseNote, remember(event) { SnapshotInfo(event.description(), if (event.isValid()) 1 else 0, event.source(), event.servers(), plainText = true) }, backgroundColor, accountViewModel, nav)
        KindCardSectionChips(stringRes(Res.string.kc_dev_permissions), requires)
    }
}

/**
 * What a site or napplet snapshot card shows, parsed once per event: a snapshot can carry thousands
 * of path tags, and the card only needs how many there are.
 */
private class SnapshotInfo(
    val description: String?,
    val fileCount: Int,
    val source: String?,
    serverUrls: List<String>,
    val plainText: Boolean = false,
) {
    val servers = serverUrls.map { HttpUrlFormatter.displayUrl(it) }
}

@Composable
private fun SnapshotFacts(
    baseNote: Note,
    info: SnapshotInfo,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (info.plainText) {
        info.description?.let { Text(it) }
    } else {
        KindCardProse(null, baseNote, info.description, backgroundColor, accountViewModel, nav)
    }
    KindCardMeta(listOf(if (info.fileCount > 0) pluralStringRes(Res.plurals.kc_dev_files_count, info.fileCount, info.fileCount) else null))
    KindCardField(stringRes(Res.string.kc_dev_source), info.source, maxLines = 1)
    KindCardSectionChips(stringRes(Res.string.kc_dev_servers), info.servers, MAX_SERVER_CHIPS)
}

// ---------------------------------------------------------------------------------------------
// Feeds, app data, NNS
// ---------------------------------------------------------------------------------------------

/** Kind 31890: a shared custom feed (title, emoji, its hashtags and authors). */
@Composable
fun RenderFeedDefinition(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? FeedDefinitionEvent ?: return
    val title = remember(event) { listOfNotNull(event.emoji(), event.title()).joinToString(" ").ifBlank { null } }
    val hashtags = remember(event) { event.hashtags().map { "#$it" } }
    val authors = remember(event) { event.taggedUserIds() }
    // KindCardText draws on the card's own background; this only satisfies its parameter.
    val textBackground = remember { mutableStateOf(Color.Transparent) }

    KindCard(
        icon = MaterialSymbols.AutoMirrored.Feed,
        title = title ?: stringRes(Res.string.kc_dev_feed_untitled),
        subtitle = if (title != null) stringRes(Res.string.kc_dev_feed_subtitle) else null,
    ) {
        // The content is the feed's machine config; KindCardText skips it when it is JSON.
        KindCardText(baseNote, event.feedConfigJson(), textBackground, accountViewModel, nav)
        KindCardChips(hashtags, max = 8)
        if (authors.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.kc_dev_feed_authors))
            KindCardUsers(authors, accountViewModel, nav)
        }
    }
}

/**
 * Kind 78: NIP-78 app data. It is app-private, so the card names the app (`d`) and never shows the
 * content.
 */
@Composable
fun RenderAppData(baseNote: Note) {
    val event = baseNote.event as? AppDataEvent ?: return
    val appId = remember(event) { event.dTag() }

    KindCard(
        icon = MaterialSymbols.Settings,
        title = kindName(event.kind),
        tone = KindTone.Neutral,
        status = stringRes(Res.string.kind_card_private_pill),
    ) {
        KindCardField(stringRes(Res.string.kc_dev_app_id), appId, monospace = true, maxLines = 1)
    }
}

/** Kind 30053: an NNS name record pointing a name at an IP address. */
@Composable
fun RenderNNS(baseNote: Note) {
    val event = baseNote.event as? NNSEvent ?: return
    val name = remember(event) { event.dTag() }

    KindCard(
        icon = MaterialSymbols.Dns,
        title = stringRes(Res.string.kc_dev_nns_subtitle),
        subtitle = name,
    ) {
        KindCardField(stringRes(Res.string.kc_dev_ipv4), event.ip4(), monospace = true, maxLines = 1)
        KindCardField(stringRes(Res.string.kc_dev_ipv6), event.ip6(), monospace = true, maxLines = 1)
        KindCardMeta(listOf(event.version()))
    }
}

// ---------------------------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------------------------
