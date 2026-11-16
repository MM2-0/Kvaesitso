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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunAggregator
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_system_prompt
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_id
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_moved_out
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_op_create
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_op_delete
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_op_move
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_op_restore
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_op_update
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_auth_failure
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_auth_success
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_channel_created
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_channel_deleted
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_channel_updated
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_event_created
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_event_deleted
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_media_uploaded
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_member_added
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_member_removed
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_object
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_rate_limit
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_unknown
import com.vitorpamplona.amethyst.commons.resources.kc_agent_capability_head
import com.vitorpamplona.amethyst.commons.resources.kc_agent_capability_interface
import com.vitorpamplona.amethyst.commons.resources.kc_agent_capability_operations
import com.vitorpamplona.amethyst.commons.resources.kc_agent_capability_profile
import com.vitorpamplona.amethyst.commons.resources.kc_agent_capability_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_capability_workers
import com.vitorpamplona.amethyst.commons.resources.kc_agent_cvm_encryption
import com.vitorpamplona.amethyst.commons.resources.kc_agent_cvm_ephemeral
import com.vitorpamplona.amethyst.commons.resources.kc_agent_cvm_open_stream
import com.vitorpamplona.amethyst.commons.resources.kc_agent_cvm_oversized
import com.vitorpamplona.amethyst.commons.resources.kc_agent_cvm_tools_count
import com.vitorpamplona.amethyst.commons.resources.kc_agent_dev
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_free
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_hidden
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_package
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_payout
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_published
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_release_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_version
import com.vitorpamplona.amethyst.commons.resources.kc_agent_instructions
import com.vitorpamplona.amethyst.commons.resources.kc_agent_managed_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_parallel_sessions
import com.vitorpamplona.amethyst.commons.resources.kc_agent_persona_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_personas
import com.vitorpamplona.amethyst.commons.resources.kc_agent_pill_shared
import com.vitorpamplona.amethyst.commons.resources.kc_agent_policy_anyone
import com.vitorpamplona.amethyst.commons.resources.kc_agent_policy_nobody
import com.vitorpamplona.amethyst.commons.resources.kc_agent_policy_owner_only
import com.vitorpamplona.amethyst.commons.resources.kc_agent_profile_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_project_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_reminder_due
import com.vitorpamplona.amethyst.commons.resources.kc_agent_repositories
import com.vitorpamplona.amethyst.commons.resources.kc_agent_respond_allowlist
import com.vitorpamplona.amethyst.commons.resources.kc_agent_respond_anyone
import com.vitorpamplona.amethyst.commons.resources.kc_agent_respond_owner_only
import com.vitorpamplona.amethyst.commons.resources.kc_agent_respond_to
import com.vitorpamplona.amethyst.commons.resources.kc_agent_team_catalog_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_team_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_test
import com.vitorpamplona.amethyst.commons.resources.kc_agent_visibility_unlisted
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_approval_denied
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_approval_granted
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_approval_requested
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_approver
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_cancelled
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_completed
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_def
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_deny
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_error
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_failed
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_grant
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_note
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_pr
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_step_completed
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_step_failed
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_step_started
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_task
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_trigger
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_triggered
import com.vitorpamplona.amethyst.commons.resources.kc_agent_who_can_add
import com.vitorpamplona.amethyst.commons.resources.kind_card_private_pill
import com.vitorpamplona.amethyst.commons.resources.members
import com.vitorpamplona.amethyst.commons.resources.website
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.timeAbsoluteNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.observeAnnouncedServerName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.agentProfiles.AgentProfileEvent
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.buzz.arArtifacts.ArtifactEvent
import com.vitorpamplona.quartz.buzz.arArtifacts.ArtifactRemovalEvent
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.ArtifactOp
import com.vitorpamplona.quartz.buzz.audit.AuditAction
import com.vitorpamplona.quartz.buzz.audit.AuditEntryEvent
import com.vitorpamplona.quartz.buzz.erReminders.EventReminderEvent
import com.vitorpamplona.quartz.buzz.managedAgents.ManagedAgentEvent
import com.vitorpamplona.quartz.buzz.managedAgents.RespondTo
import com.vitorpamplona.quartz.buzz.mpProjects.ProjectEvent
import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectVisibility
import com.vitorpamplona.quartz.buzz.teamCatalog.TeamCatalogEvent
import com.vitorpamplona.quartz.buzz.teams.TeamEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalDenyEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalGrantEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalDeniedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalGrantedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalRequestedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCancelledEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowDefEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepStartedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggeredEvent
import com.vitorpamplona.quartz.contextvm.cep06Announcements.CvmServerAnnouncementEvent
import com.vitorpamplona.quartz.contextvm.cep06Announcements.CvmToolsListEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.openagents.nipCapCapabilities.discovery.CapabilityDiscoveryEvent
import com.vitorpamplona.quartz.openagents.nipExtExtensions.listing.ExtensionListingEvent
import com.vitorpamplona.quartz.openagents.nipExtExtensions.release.ExtensionReleaseEvent
import org.jetbrains.compose.resources.StringResource

/*
 * Cards for the AI-agent kinds: Buzz agents, personas, teams, projects, artifacts, workflows and
 * their audit trail, OpenAgents capabilities and extensions, and ContextVM server announcements.
 * Their content is JSON for software or NIP-44 ciphertext, so the text-note fallback printed
 * either raw. Each card shows what the record says instead.
 */

// ---------------------------------------------------------------------------------------------
// Agents, personas, teams, projects
// ---------------------------------------------------------------------------------------------

/** A Buzz agent profile (kind 10100): the agent's name, type, status and who may add it to channels. */
@Composable
fun RenderAgentProfile(baseNote: Note) {
    val event = baseNote.event as? AgentProfileEvent ?: return
    val profile = remember(event) { event.profileOrNull() }
    val name = profile?.displayName?.ifBlank { null } ?: profile?.name?.ifBlank { null }
    val agentType = profile?.agentType?.ifBlank { null }
    val status = profile?.status?.ifBlank { null }
    val kindTitle = stringRes(Res.string.kc_agent_profile_title)

    KindCard(
        icon = MaterialSymbols.Assistant,
        title = name ?: kindTitle,
        subtitle = agentType ?: if (name != null) kindTitle else null,
        status = status?.let { readableToken(it) },
        statusTone = status?.let { agentStatusTone(it) },
    ) {
        KindCardField(stringRes(Res.string.kc_agent_who_can_add), profile?.channelAddPolicy?.let { channelAddPolicyLabel(it) })
        KindCardChips(profile?.capabilities.orEmpty().filter { it.isNotBlank() })
    }
}

/** A Buzz managed agent (kind 30177): the agent, its model and the persona it runs. */
@Composable
fun RenderManagedAgent(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ManagedAgentEvent ?: return
    val agent = remember(event) { event.agentOrNull() }
    if (agent == null) {
        RenderPrivateKindCard(baseNote, MaterialSymbols.Assistant)
        return
    }
    val agentKey = remember(event) { hex64OrNull(event.agentPubKey()) }
    val personaAddress =
        remember(event) {
            agent.personaId?.ifBlank { null }?.let { Address(PersonaEvent.KIND, event.pubKey, it) }
        }
    val kindTitle = stringRes(Res.string.kc_agent_managed_title)

    KindCard(
        icon = MaterialSymbols.Assistant,
        title = agent.name.ifBlank { kindTitle },
        subtitle = kindTitle,
    ) {
        agentKey?.let { KindCardPerson(it, accountViewModel, nav) }
        KindCardMeta(
            listOf(
                agent.model,
                agent.provider,
                parallelLabel(agent.parallelism),
            ),
        )
        KindCardField(stringRes(Res.string.kc_agent_respond_to), respondToLabel(agent.respondTo))
        KindCardProse(stringRes(Res.string.buzz_persona_system_prompt), baseNote, agent.systemPrompt, backgroundColor, accountViewModel, nav)
        personaAddress?.let { KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** A Buzz agent persona (kind 30175): name, description, model and system prompt. */
@Composable
fun RenderPersona(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? PersonaEvent ?: return
    val persona = remember(event) { event.personaOrNull() }
    val shared = remember(event) { event.isShared() }
    val kindTitle = stringRes(Res.string.kc_agent_persona_title)
    val name = persona?.displayName?.ifBlank { null } ?: event.slug().ifBlank { null }

    KindCard(
        icon = MaterialSymbols.Face,
        title = name ?: kindTitle,
        subtitle = if (name != null) kindTitle else null,
        status = if (shared) stringRes(Res.string.kc_agent_pill_shared) else null,
    ) {
        if (persona != null) {
            KindCardProse(null, baseNote, persona.description, backgroundColor, accountViewModel, nav)
            KindCardMeta(
                listOf(
                    persona.model,
                    persona.provider,
                    persona.runtime,
                    persona.parallelism?.let { parallelLabel(it) },
                ),
            )
            KindCardField(stringRes(Res.string.kc_agent_respond_to), persona.respondTo?.ifBlank { null }?.let { respondToLabel(it) })
            KindCardProse(stringRes(Res.string.buzz_persona_system_prompt), baseNote, persona.systemPrompt, backgroundColor, accountViewModel, nav)
        }
    }
}

/** A Buzz agent team (kind 30176): its name, description, instructions and personas. */
@Composable
fun RenderAgentTeam(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? TeamEvent ?: return
    val team = remember(event) { event.teamOrNull() }
    val personas = remember(team) { team?.personaIds.orEmpty().filter { it.isNotBlank() } }
    val kindTitle = stringRes(Res.string.kc_agent_team_title)
    val name = team?.name?.ifBlank { null } ?: event.teamId().ifBlank { null }

    KindCard(
        icon = MaterialSymbols.Groups,
        title = name ?: kindTitle,
        subtitle = if (name != null) kindTitle else null,
    ) {
        KindCardProse(null, baseNote, team?.description, backgroundColor, accountViewModel, nav)
        KindCardProse(stringRes(Res.string.kc_agent_instructions), baseNote, team?.instructions, backgroundColor, accountViewModel, nav)
        if (personas.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.kc_agent_personas))
            KindCardChips(personas, max = MAX_NAME_CHIPS)
        }
    }
}

/** A Buzz team template (kind 30178): the team and the agents it is made of. */
@Composable
fun RenderAgentTeamCatalog(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? TeamCatalogEvent ?: return
    val catalog = remember(event) { event.catalogOrNull() }
    val shared = remember(event) { event.isShared() }
    val members =
        remember(catalog) {
            catalog
                ?.members
                .orEmpty()
                .map { it.displayName }
                .filter { it.isNotBlank() }
        }
    val kindTitle = stringRes(Res.string.kc_agent_team_catalog_title)
    val name = catalog?.name?.ifBlank { null } ?: event.teamId().ifBlank { null }

    KindCard(
        icon = MaterialSymbols.Groups,
        title = name ?: kindTitle,
        subtitle = if (name != null) kindTitle else null,
        status = if (shared) stringRes(Res.string.kc_agent_pill_shared) else null,
    ) {
        KindCardProse(null, baseNote, catalog?.description, backgroundColor, accountViewModel, nav)
        KindCardProse(stringRes(Res.string.kc_agent_instructions), baseNote, catalog?.instructions, backgroundColor, accountViewModel, nav)
        if (members.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.members))
            KindCardChips(members, max = MAX_NAME_CHIPS)
        }
    }
}

/** A Buzz project (kind 30621): a named group of git repositories. */
@Composable
fun RenderAgentProject(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ProjectEvent ?: return
    val repos = remember(event) { event.members().map { it.address.dTag }.filter { it.isNotBlank() } }
    val unlisted = remember(event) { event.visibility() == ProjectVisibility.UNLISTED }
    val kindTitle = stringRes(Res.string.kc_agent_project_title)
    val name = event.displayName().ifBlank { null }

    KindCard(
        icon = MaterialSymbols.Folder,
        title = name ?: kindTitle,
        subtitle = if (name != null) kindTitle else null,
        status = if (unlisted) stringRes(Res.string.kc_agent_visibility_unlisted) else null,
        statusTone = KindTone.Neutral,
    ) {
        KindCardProse(null, baseNote, event.description(), backgroundColor, accountViewModel, nav)
        if (repos.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.kc_agent_repositories))
            KindCardChips(repos, max = MAX_NAME_CHIPS)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Artifacts and audit
// ---------------------------------------------------------------------------------------------

/** A Buzz artifact revision (kind 45010): its title, what happened to it, and its body. */
@Composable
fun RenderAgentArtifact(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ArtifactEvent ?: return
    val op = remember(event) { event.op() }
    val title = remember(event) { event.title()?.ifBlank { null } }
    val body = remember(event) { event.textBody() }
    val type = remember(event) { event.type() }
    val opLabel = artifactOpLabel(op) ?: kindName(event.kind)

    KindCard(
        icon = if (op == ArtifactOp.DELETE) MaterialSymbols.Delete else MaterialSymbols.Description,
        title = title ?: opLabel,
        subtitle = if (title != null) opLabel else null,
        tone = artifactOpTone(op),
    ) {
        KindCardMeta(listOf(type))
        if (body != null) {
            KindCardText(baseNote, body, backgroundColor, accountViewModel, nav)
        } else {
            KindCardCode(event.content, maxLines = 6)
        }
    }
}

/** A Buzz artifact removal marker (kind 45011): the artifact left this channel. */
@Composable
fun RenderAgentArtifactRemoval(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ArtifactRemovalEvent ?: return
    val artifactId = remember(event) { event.artifactId()?.ifBlank { null }?.toShortDisplay() }
    val replaced = remember(event) { event.replacedRevision() }

    KindCard(
        icon = MaterialSymbols.AutoMirrored.DriveFileMove,
        title = stringRes(Res.string.kc_agent_artifact_moved_out),
        tone = KindTone.Neutral,
    ) {
        if (replaced != null) {
            KindCardQuoteEvent(replaced, quotesLeft, backgroundColor, accountViewModel, nav)
        } else {
            KindCardField(stringRes(Res.string.kc_agent_artifact_id), artifactId, monospace = true, maxLines = 1)
        }
    }
}

/** A Buzz audit-log entry (kind 48001): the action, who did it, and on what. */
@Composable
fun RenderAgentAuditEntry(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? AuditEntryEvent ?: return
    val action = remember(event) { event.action() }
    val actor = remember(event) { event.actor() }
    val objectId = remember(event) { event.objectId()?.ifBlank { null }?.toShortDisplay() }
    val detail = remember(event) { event.detail().ifBlank { null } }

    KindCard(
        icon = MaterialSymbols.History,
        title = auditActionLabel(action),
        subtitle = stringRes(Res.string.kc_agent_audit_unknown),
        tone = auditActionTone(action),
    ) {
        actor?.let { KindCardPerson(it, accountViewModel, nav) }
        KindCardField(stringRes(Res.string.kc_agent_audit_object), objectId, monospace = true, maxLines = 1)
        detail?.let { KindCardCode(it, maxLines = 4) }
    }
}

// ---------------------------------------------------------------------------------------------
// Workflows
// ---------------------------------------------------------------------------------------------

/** A Buzz workflow definition (kind 30620): its name and YAML recipe. */
@Composable
fun RenderWorkflowDefinition(baseNote: Note) {
    val event = baseNote.event as? WorkflowDefEvent ?: return
    val name = remember(event) { event.name()?.ifBlank { null } }
    val kindTitle = stringRes(Res.string.kc_agent_wf_def)

    KindCard(
        icon = MaterialSymbols.Checklist,
        title = name ?: kindTitle,
        subtitle = if (name != null) kindTitle else event.workflowId().ifBlank { null },
    ) {
        KindCardCode(event.yaml(), maxLines = 6)
    }
}

/** A manual workflow trigger (kind 46020): which workflow was started and the task it was given. */
@Composable
fun RenderWorkflowTrigger(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? WorkflowTriggerEvent ?: return
    val payload = remember(event) { WorkflowRunAggregator.payload(event.content) }
    val workflow = remember(event) { event.workflowId()?.ifBlank { null } ?: payload?.workflow?.ifBlank { null } }
    val definition =
        remember(event) {
            event.workflowId()?.ifBlank { null }?.let { Address(WorkflowDefEvent.KIND, event.pubKey, it) }
        }

    KindCard(
        icon = MaterialSymbols.PlayArrow,
        title = stringRes(Res.string.kc_agent_wf_trigger),
        subtitle = workflow,
    ) {
        // The task is what the person asked for: the heart of the card. Without a known workflow
        // name, the definition quote is the only way to say what ran.
        KindCardText(baseNote, payload?.task ?: event.content, backgroundColor, accountViewModel, nav)
        if (workflow == null) {
            definition?.let { KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav) }
        }
    }
}

/**
 * The relay-emitted lifecycle of a workflow run (kinds 46001-46012), drawn as one step of a
 * timeline: the title says what happened ("Workflow step failed"), the subtitle names the step or
 * the workflow, and the tone follows the outcome. All share one JSON body naming the run (the id of
 * the trigger event), the task, the step and any error, so one card serves them.
 */
@Composable
fun RenderWorkflowLifecycle(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event ?: return
    val header = workflowLifecycleHeader(event) ?: return
    val payload = remember(event) { WorkflowRunAggregator.payload(event.content) }
    val runId = remember(payload) { payload?.run?.let { hex64OrNull(it) } }
    val approver = remember(event) { (event as? WorkflowApprovalRequestedEvent)?.approver() }

    val step = payload?.step?.ifBlank { null }
    val workflow = payload?.workflow?.ifBlank { null }
    val task = payload?.task?.ifBlank { null }
    // The task is prose someone wrote, so it goes in the body (translatable), not the subtitle.
    val subtitle = (if (header.isStep) step else null) ?: workflow

    KindCard(
        icon = header.icon,
        title = stringRes(header.title),
        subtitle = subtitle,
        tone = header.tone,
    ) {
        if (payload != null) {
            KindCardProse(stringRes(Res.string.kc_agent_wf_task), baseNote, task, backgroundColor, accountViewModel, nav)
            KindCardRichField(stringRes(Res.string.kc_agent_wf_error), baseNote, payload.error, backgroundColor, accountViewModel, nav)
            KindCardRichField(stringRes(Res.string.kc_agent_wf_note), baseNote, payload.note, backgroundColor, accountViewModel, nav)
            KindCardField(stringRes(Res.string.kc_agent_wf_pr), payload.pr, maxLines = 1)
        } else {
            KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
        }
        approver?.let {
            KindCardSection(stringRes(Res.string.kc_agent_wf_approver))
            KindCardPerson(it, accountViewModel, nav)
        }
        // The trigger event repeats the task; quote it only when this step does not name one.
        if (task == null) {
            runId?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
        }
    }
}

/** An approver's grant (kind 46030) or denial (kind 46031) of a paused workflow run. */
@Composable
fun RenderWorkflowApprovalDecision(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event ?: return
    val granted =
        when (event) {
            is ApprovalGrantEvent -> true
            is ApprovalDenyEvent -> false
            else -> return
        }
    // Amethyst's runner uses the trigger event id as the approval token, so a 64-hex token can be
    // quoted as the run it decides. Anything else is an opaque hash and shows nothing.
    val runId =
        remember(event) {
            val token = (event as? ApprovalGrantEvent)?.tokenHash() ?: (event as? ApprovalDenyEvent)?.tokenHash()
            token?.let { hex64OrNull(it) }
        }

    KindCard(
        icon = if (granted) MaterialSymbols.CheckCircle else MaterialSymbols.Cancel,
        title = stringRes(if (granted) Res.string.kc_agent_wf_grant else Res.string.kc_agent_wf_deny),
        tone = if (granted) KindTone.Positive else KindTone.Negative,
    ) {
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
        runId?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

// ---------------------------------------------------------------------------------------------
// OpenAgents capabilities and extensions
// ---------------------------------------------------------------------------------------------

/** An OpenAgents capability (kind 30180): what it does, how to reach it, and who serves it. */
@Composable
fun RenderCapabilityDiscovery(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CapabilityDiscoveryEvent ?: return
    val data = remember(event) { CapabilityCardData(event) }

    KindCard(
        icon = MaterialSymbols.Extension,
        title = stringRes(Res.string.kc_agent_capability_title),
        subtitle = data.path ?: event.dTag().ifBlank { null },
        status =
            when {
                data.isTest -> stringRes(Res.string.kc_agent_test)
                data.isDev -> stringRes(Res.string.kc_agent_dev)
                else -> null
            },
        statusTone = KindTone.Warning,
    ) {
        KindCardProse(null, baseNote, data.summary, backgroundColor, accountViewModel, nav)
        KindCardMeta(data.transports + listOfNotNull(if (data.isHead) stringRes(Res.string.kc_agent_capability_head) else null))
        KindCardField(stringRes(Res.string.kc_agent_capability_profile), data.profile)
        KindCardField(stringRes(Res.string.kc_agent_capability_interface), data.interfaceId, monospace = true, maxLines = 1)
        if (data.operations.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.kc_agent_capability_operations))
            KindCardChips(data.operations, max = MAX_NAME_CHIPS)
        }
        if (data.workers.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.kc_agent_capability_workers))
            KindCardUsers(data.workers, accountViewModel, nav, max = 3)
        }
    }
}

private class CapabilityCardData(
    event: CapabilityDiscoveryEvent,
) {
    val path: String? = event.qualifiedId()?.path()
    val summary: String? = event.summary()?.ifBlank { null }
    val isHead: Boolean = event.isHead()
    val profile: String? = event.profile()
    val interfaceId: String? = event.interfaceId()
    val operations: List<String> = event.operations().filter { it.isNotBlank() }
    val transports: List<String> = event.transports().filter { it.isNotBlank() }
    val workers: List<HexKey> = event.linkedPubKeys()
    val isTest: Boolean = event.isTest()
    val isDev: Boolean = event.isDev()
}

/** An OpenAgents extension listing (kind 30184): the extension, its state and current release. */
@Composable
fun RenderExtensionListing(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ExtensionListingEvent ?: return
    val title = remember(event) { event.title() }
    val description = remember(event) { event.description() }
    val published = remember(event) { event.isPublished() }
    val hidden = remember(event) { event.isHidden() }
    val releaseId = remember(event) { event.release()?.id }
    val slug = event.slug().ifBlank { null }

    KindCard(
        icon = MaterialSymbols.Extension,
        title = title ?: slug ?: kindName(event.kind),
        status =
            when {
                hidden -> stringRes(Res.string.kc_agent_ext_hidden)
                published -> stringRes(Res.string.kc_agent_ext_published)
                else -> null
            },
        statusTone = if (hidden) KindTone.Neutral else KindTone.Positive,
    ) {
        KindCardProse(null, baseNote, description, backgroundColor, accountViewModel, nav)
        if (title != null) KindCardField(stringRes(Res.string.kc_agent_ext_package), slug, monospace = true, maxLines = 1)
        releaseId?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** An OpenAgents extension release (kind 3184): package, version and price. */
@Composable
fun RenderExtensionRelease(baseNote: Note) {
    val event = baseNote.event as? ExtensionReleaseEvent ?: return
    val slug = remember(event) { event.packageSlug() }
    val version = remember(event) { event.version() }
    val feeMsat = remember(event) { event.feeMsat() }
    val payout = remember(event) { event.payout() }
    val paid = feeMsat > 0L

    KindCard(
        icon = MaterialSymbols.Download,
        title = stringRes(Res.string.kc_agent_ext_release_title),
        subtitle = slug,
        tone = if (paid) KindTone.Money else KindTone.Primary,
        status =
            if (paid) {
                kindCardMsats(feeMsat)
            } else {
                stringRes(Res.string.kc_agent_ext_free)
            },
        statusTone = if (paid) KindTone.Money else KindTone.Positive,
    ) {
        KindCardMeta(listOf(version?.let { stringRes(Res.string.kc_agent_ext_version, it) }))
        if (payout != null) {
            // A lightning address reads as is; anything else is a key or an id worth shortening.
            val isAddress = payout.contains('@')
            KindCardField(
                stringRes(Res.string.kc_agent_ext_payout),
                if (isAddress) payout else payout.toShortDisplay(),
                monospace = !isAddress,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// ContextVM
// ---------------------------------------------------------------------------------------------

/** A ContextVM server announcement (CEP-6): the server's name, blurb and what it supports. */
@Composable
fun RenderCvmServerAnnouncement(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CvmServerAnnouncementEvent ?: return
    val surface = remember(event) { event.discovery() }
    val name = surface.name?.ifBlank { null }

    KindCard(
        icon = MaterialSymbols.Dns,
        title = name ?: kindName(event.kind),
        subtitle = if (name != null) kindName(event.kind) else null,
    ) {
        KindCardProse(null, baseNote, surface.about, backgroundColor, accountViewModel, nav)
        KindCardField(stringRes(Res.string.website), surface.website, maxLines = 1)
        KindCardChips(
            listOfNotNull(
                if (surface.supportsEncryption) stringRes(Res.string.kc_agent_cvm_encryption) else null,
                if (surface.supportsEphemeralEncryption) stringRes(Res.string.kc_agent_cvm_ephemeral) else null,
                if (surface.supportsOversizedTransfer) stringRes(Res.string.kc_agent_cvm_oversized) else null,
                if (surface.supportsOpenStream) stringRes(Res.string.kc_agent_cvm_open_stream) else null,
            ),
        )
    }
}

/** A ContextVM server's `tools/list` announcement (CEP-6): the server and the tools it offers. */
@Composable
fun RenderCvmToolsList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val event = baseNote.event as? CvmToolsListEvent ?: return
    val names = remember(event) { event.tools()?.names?.toList() }
    val serverName = observeAnnouncedServerName(event.pubKey, accountViewModel)

    if (names == null) {
        RenderPrivateKindCard(baseNote, MaterialSymbols.Manufacturing)
        return
    }

    KindCard(
        icon = MaterialSymbols.Manufacturing,
        title = serverName ?: kindName(event.kind),
        subtitle = pluralStringRes(Res.plurals.kc_agent_cvm_tools_count, names.size, names.size),
    ) {
        KindCardChips(names)
    }
}

// ---------------------------------------------------------------------------------------------
// Encrypted and machine-only kinds
// ---------------------------------------------------------------------------------------------

/** A self-encrypted Buzz reminder (kind 30300): only when it is due shows. */
@Composable
fun RenderEventReminder(baseNote: Note) {
    val event = baseNote.event as? EventReminderEvent ?: return
    val notBefore = remember(event) { event.notBefore() }
    val alt = remember(event) { event.alt()?.takeIf { !looksLikeOpaquePayload(it) } }

    KindCard(
        icon = MaterialSymbols.Notifications,
        title = kindName(event.kind),
        subtitle = alt,
        tone = KindTone.Neutral,
        status = stringRes(Res.string.kind_card_private_pill),
    ) {
        notBefore?.let { KindCardField(stringRes(Res.string.kc_agent_reminder_due), timeAbsoluteNoDot(it)) }
    }
}

/** An encrypted agent turn metric (kind 44200): token usage and cost for the owner only. */
@Composable
fun RenderAgentTurnMetric(baseNote: Note) = RenderPrivateKindCard(baseNote, MaterialSymbols.AutoMirrored.ShowChart)

/** An encrypted agent memory (kind 30174). */
@Composable
fun RenderEngram(baseNote: Note) = RenderPrivateKindCard(baseNote, MaterialSymbols.Lock)

/** An encrypted live telemetry or control frame between an agent and its observer (kind 24200). */
@Composable
fun RenderObserverFrame(baseNote: Note) = RenderPrivateKindCard(baseNote, MaterialSymbols.Visibility)

/** An encrypted device-pairing message (kind 24134). */
@Composable
fun RenderAgentPairing(baseNote: Note) = RenderPrivateKindCard(baseNote, MaterialSymbols.Link)

/** An encrypted push-notification lease (kind 30350). */
@Composable
fun RenderPushLease(baseNote: Note) = RenderPrivateKindCard(baseNote, MaterialSymbols.Notifications)

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

/** Names (personas, members, repositories, operations) shown as chips before the "+N" chip. */
private const val MAX_NAME_CHIPS = 6

private fun hex64OrNull(value: String): HexKey? = value.takeIf { it.isValid() }

/** An agent's free-form `status` word, colored by what it most likely means. */
private fun agentStatusTone(status: String): KindTone =
    when (status.lowercase()) {
        "online", "active", "available", "ready", "idle" -> KindTone.Positive
        "busy", "working", "starting", "pending" -> KindTone.Warning
        "error", "failed", "crashed", "disabled" -> KindTone.Negative
        "offline", "inactive", "stopped", "paused" -> KindTone.Neutral
        else -> KindTone.Primary
    }

/** "3 sessions at a time"; nothing for the default of one, which is not worth a word. */
@Composable
private fun parallelLabel(parallelism: Int): String? = if (parallelism > 1) pluralStringRes(Res.plurals.kc_agent_parallel_sessions, parallelism, parallelism) else null

private class WorkflowLifecycleHeader(
    val title: StringResource,
    val icon: MaterialSymbol,
    val tone: KindTone,
    val isStep: Boolean = false,
)

private fun workflowLifecycleHeader(event: Event): WorkflowLifecycleHeader? =
    when (event) {
        is WorkflowTriggeredEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_triggered, MaterialSymbols.PlayArrow, KindTone.Warning)
        is WorkflowStepStartedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_step_started, MaterialSymbols.Bolt, KindTone.Warning, isStep = true)
        is WorkflowStepCompletedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_step_completed, MaterialSymbols.Check, KindTone.Positive, isStep = true)
        is WorkflowStepFailedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_step_failed, MaterialSymbols.Error, KindTone.Negative, isStep = true)
        is WorkflowCompletedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_completed, MaterialSymbols.CheckCircle, KindTone.Positive)
        is WorkflowFailedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_failed, MaterialSymbols.Error, KindTone.Negative)
        is WorkflowCancelledEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_cancelled, MaterialSymbols.Cancel, KindTone.Neutral)
        is WorkflowApprovalRequestedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_approval_requested, MaterialSymbols.Gavel, KindTone.Warning)
        is WorkflowApprovalGrantedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_approval_granted, MaterialSymbols.CheckCircle, KindTone.Positive)
        is WorkflowApprovalDeniedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_approval_denied, MaterialSymbols.Close, KindTone.Negative)
        else -> null
    }

@Composable
private fun channelAddPolicyLabel(code: String): String =
    when (code) {
        "anyone" -> stringRes(Res.string.kc_agent_policy_anyone)
        "owner_only" -> stringRes(Res.string.kc_agent_policy_owner_only)
        "nobody" -> stringRes(Res.string.kc_agent_policy_nobody)
        else -> code
    }

@Composable
private fun respondToLabel(respondTo: RespondTo): String =
    when (respondTo) {
        RespondTo.OWNER_ONLY -> stringRes(Res.string.kc_agent_respond_owner_only)
        RespondTo.ALLOWLIST -> stringRes(Res.string.kc_agent_respond_allowlist)
        RespondTo.ANYONE -> stringRes(Res.string.kc_agent_respond_anyone)
    }

/** A persona's `respond_to` is a free string; the known values read like the managed-agent enum. */
@Composable
private fun respondToLabel(code: String): String =
    when (code) {
        "owner-only", "owner_only" -> stringRes(Res.string.kc_agent_respond_owner_only)
        "allowlist" -> stringRes(Res.string.kc_agent_respond_allowlist)
        "anyone" -> stringRes(Res.string.kc_agent_respond_anyone)
        else -> code
    }

@Composable
private fun artifactOpLabel(op: ArtifactOp?): String? =
    when (op) {
        ArtifactOp.CREATE -> stringRes(Res.string.kc_agent_artifact_op_create)
        ArtifactOp.UPDATE -> stringRes(Res.string.kc_agent_artifact_op_update)
        ArtifactOp.MOVE -> stringRes(Res.string.kc_agent_artifact_op_move)
        ArtifactOp.DELETE -> stringRes(Res.string.kc_agent_artifact_op_delete)
        ArtifactOp.RESTORE -> stringRes(Res.string.kc_agent_artifact_op_restore)
        null -> null
    }

private fun artifactOpTone(op: ArtifactOp?): KindTone =
    when (op) {
        ArtifactOp.CREATE, ArtifactOp.RESTORE -> KindTone.Positive
        ArtifactOp.DELETE -> KindTone.Negative
        ArtifactOp.UPDATE, ArtifactOp.MOVE, null -> KindTone.Primary
    }

@Composable
private fun auditActionLabel(action: AuditAction?): String =
    when (action) {
        AuditAction.EVENT_CREATED -> stringRes(Res.string.kc_agent_audit_event_created)
        AuditAction.EVENT_DELETED -> stringRes(Res.string.kc_agent_audit_event_deleted)
        AuditAction.CHANNEL_CREATED -> stringRes(Res.string.kc_agent_audit_channel_created)
        AuditAction.CHANNEL_UPDATED -> stringRes(Res.string.kc_agent_audit_channel_updated)
        AuditAction.CHANNEL_DELETED -> stringRes(Res.string.kc_agent_audit_channel_deleted)
        AuditAction.MEMBER_ADDED -> stringRes(Res.string.kc_agent_audit_member_added)
        AuditAction.MEMBER_REMOVED -> stringRes(Res.string.kc_agent_audit_member_removed)
        AuditAction.AUTH_SUCCESS -> stringRes(Res.string.kc_agent_audit_auth_success)
        AuditAction.AUTH_FAILURE -> stringRes(Res.string.kc_agent_audit_auth_failure)
        AuditAction.RATE_LIMIT_EXCEEDED -> stringRes(Res.string.kc_agent_audit_rate_limit)
        AuditAction.MEDIA_UPLOADED -> stringRes(Res.string.kc_agent_audit_media_uploaded)
        AuditAction.UNKNOWN, null -> stringRes(Res.string.kc_agent_audit_unknown)
    }

private fun auditActionTone(action: AuditAction?): KindTone =
    when (action) {
        AuditAction.CHANNEL_CREATED, AuditAction.MEMBER_ADDED, AuditAction.AUTH_SUCCESS -> KindTone.Positive
        AuditAction.EVENT_DELETED, AuditAction.CHANNEL_DELETED, AuditAction.MEMBER_REMOVED, AuditAction.AUTH_FAILURE -> KindTone.Negative
        AuditAction.RATE_LIMIT_EXCEEDED -> KindTone.Warning
        AuditAction.EVENT_CREATED, AuditAction.CHANNEL_UPDATED, AuditAction.MEDIA_UPLOADED -> KindTone.Primary
        AuditAction.UNKNOWN, null -> KindTone.Neutral
    }
