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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.buzz.workflow.ApprovalDenyEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalGrantEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalRequestedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepStartedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.workflowChannel
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkflowRunAggregatorTest {
    private val requester = "a".repeat(64)
    private val approver = "b".repeat(64)
    private val channel = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    private val wf = "agent-build"
    private val runId = "1".repeat(64) // the trigger event id == run id == approval token

    private fun trigger(createdAt: Long = 1000): WorkflowTriggerEvent {
        val t = WorkflowTriggerEvent.build(wf, """{"task":"add dark mode"}""", createdAt) { workflowChannel(channel) }
        return WorkflowTriggerEvent(runId, requester, t.createdAt, t.tags, t.content, "sig")
    }

    private fun stepStarted(createdAt: Long = 1010): WorkflowStepStartedEvent {
        val t = WorkflowStepStartedEvent.build(channel, """{"run":"$runId","step":"build"}""", createdAt)
        return WorkflowStepStartedEvent("2".repeat(64), requester, t.createdAt, t.tags, t.content, "sig")
    }

    private fun approvalRequested(createdAt: Long = 1020): WorkflowApprovalRequestedEvent {
        val t = WorkflowApprovalRequestedEvent.build(channel, approver, """{"run":"$runId"}""", createdAt)
        return WorkflowApprovalRequestedEvent("3".repeat(64), requester, t.createdAt, t.tags, t.content, "sig")
    }

    private fun grant(createdAt: Long = 1030): ApprovalGrantEvent {
        val t = ApprovalGrantEvent.build(runId, "lgtm", createdAt)
        return ApprovalGrantEvent("4".repeat(64), approver, t.createdAt, t.tags, t.content, "sig")
    }

    private fun deny(createdAt: Long = 1030): ApprovalDenyEvent {
        val t = ApprovalDenyEvent.build(runId, "not yet", createdAt)
        return ApprovalDenyEvent("5".repeat(64), approver, t.createdAt, t.tags, t.content, "sig")
    }

    private fun completed(createdAt: Long = 1040): WorkflowCompletedEvent {
        val t = WorkflowCompletedEvent.build(channel, """{"run":"$runId","pr":"https://github.com/x/y/pull/7"}""", createdAt)
        return WorkflowCompletedEvent("6".repeat(64), requester, t.createdAt, t.tags, t.content, "sig")
    }

    private fun agg(events: List<Event>) = WorkflowRunAggregator.aggregate(events).single()

    @Test
    fun triggerOnlyIsTriggered() {
        val run = agg(listOf(trigger()))
        assertEquals(WorkflowRunState.TRIGGERED, run.state)
        assertEquals(wf, run.workflowId)
        assertEquals(channel, run.channel)
        assertEquals("add dark mode", run.task)
        assertEquals(requester, run.requester)
    }

    @Test
    fun stepStartedIsRunning() {
        assertEquals(WorkflowRunState.RUNNING, agg(listOf(trigger(), stepStarted())).state)
    }

    @Test
    fun approvalRequestedPausesTheRun() {
        val run = agg(listOf(trigger(), stepStarted(), approvalRequested()))
        assertEquals(WorkflowRunState.AWAITING_APPROVAL, run.state)
        assertEquals(approver, run.pendingApprover)
        assertEquals(runId, run.approvalToken)
    }

    @Test
    fun grantResumesToApproved() {
        val run = agg(listOf(trigger(), stepStarted(), approvalRequested(), grant()))
        assertEquals(WorkflowRunState.APPROVED, run.state)
    }

    @Test
    fun completedIsTerminalWithResult() {
        val run = agg(listOf(trigger(), stepStarted(), approvalRequested(), grant(), completed()))
        assertEquals(WorkflowRunState.COMPLETED, run.state)
        assertEquals("https://github.com/x/y/pull/7", run.result)
    }

    @Test
    fun denyIsTerminal() {
        val run = agg(listOf(trigger(), stepStarted(), approvalRequested(), deny()))
        assertEquals(WorkflowRunState.DENIED, run.state)
    }

    @Test
    fun newerApprovalAfterAGrantPausesAgain() {
        // A fresh approval gate (createdAt 1050) after an earlier grant (1030) holds the run.
        val run = agg(listOf(trigger(), grant(1030), approvalRequested(1050)))
        assertEquals(WorkflowRunState.AWAITING_APPROVAL, run.state)
    }

    @Test
    fun awaitingApprovalSortsFirst() {
        val otherId = "9".repeat(64)
        val otherTrig = WorkflowTriggerEvent.build(wf, """{"task":"other"}""", 3000) { workflowChannel(channel) }
        val other = WorkflowTriggerEvent(otherId, requester, otherTrig.createdAt, otherTrig.tags, otherTrig.content, "sig")
        // `runId` is awaiting approval (older); `other` is a newer plain trigger.
        val runs = WorkflowRunAggregator.byPriority(WorkflowRunAggregator.aggregate(listOf(trigger(), approvalRequested(), other)))
        assertEquals(runId, runs.first().runId) // needs-a-human beats recency
    }
}
