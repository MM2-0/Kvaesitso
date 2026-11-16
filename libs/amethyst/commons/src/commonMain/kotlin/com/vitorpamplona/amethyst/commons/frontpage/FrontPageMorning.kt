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
package com.vitorpamplona.amethyst.commons.frontpage

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleRelays
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.SyncedSchedule
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverEdition
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverParams
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSite
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronExpression
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims.Decision
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import com.vitorpamplona.quartz.nipFBScheduledTasks.SimpleCron
import com.vitorpamplona.quartz.nipFBScheduledTasks.tags.TaskTag
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/** How this install names itself in a claim (NIP-FB `claim.by`): "Pixel 8", "Desktop (Linux)". */
@Immutable
data class FrontPageInstallInfo(
    val name: String,
    val app: String,
)

/**
 * A schedule in words, on this device's clock where it can be moved there without changing its
 * day, else on the schedule's own clock with its [zone] named.
 */
@Immutable
sealed interface CronWords {
    /** A time on some weekdays (ISO, null = every day): "Every weekday at 6:00 AM". */
    data class Days(
        val time: SimpleCron,
        val zone: String? = null,
    ) : CronWords

    /** Two to four times a day, every day: "3 times a day at 6:00 AM, 12:00 PM and 6:00 PM". */
    data class TimesADay(
        val times: List<Pair<Int, Int>>,
        val zone: String? = null,
    ) : CronWords

    /** "Every 6 hours, at :15". */
    data class EveryHours(
        val hours: Int,
        val minute: Int,
    ) : CronWords

    /** "Monthly on day 1 at 6:00 AM". */
    data class Monthly(
        val day: Int,
        val hour: Int,
        val minute: Int,
        val zone: String? = null,
    ) : CronWords

    /** Nothing plainer to say: the expression as written. */
    data class Raw(
        val cron: String,
    ) : CronWords
}

/** What the schedule section can show. */
enum class ScheduleState {
    /** Nothing read yet. */
    LOADING,

    /** No synced schedule: the shared switch is off and nothing is made up. */
    NOT_SET_UP,

    /** Read and understood. */
    READY,

    /** The signer needs the reader to open it ([MorningScheduleView.retryable] says whether asking again can help). */
    CANT_DECRYPT,

    /** The event isn't a schedule this app reads: its content, its cron or its zone. Shown, never run or overwritten. */
    UNREADABLE,
}

/** What the schedule section shows, from the synced schedule and this device's own choice. */
@Immutable
data class MorningScheduleView(
    val state: ScheduleState = ScheduleState.LOADING,
    /** Asking the signer again may open it. */
    val retryable: Boolean = false,
    val enabled: Boolean = false,
    /** The expression as written, and its zone, when there is one. */
    val cron: String? = null,
    val zone: String? = null,
    /** The schedule in words, when it is [ScheduleState.READY] (or its cron is at least shown). */
    val words: CronWords? = null,
    /** The time of day this device's editor shows and changes; null when it can't be changed here. */
    val editableTime: Pair<Int, Int>? = null,
    /** "Print from this device": this device takes part in the mornings. */
    val printsHere: Boolean = false,
    /** This install can print with nobody there, so it may take part. */
    val canPrintHere: Boolean = false,
    /** The reader has NIP-65 write relays: the only place a shared schedule lives. */
    val hasWriteRelays: Boolean = true,
    /** Who printed last: the claim's `by`, only when that claim is the one [lastRan] finished. */
    val lastBy: String? = null,
    val lastRan: Long? = null,
    /** How long ago [lastRan] was, rounded to what the line says (minutes, hours or days). */
    val lastRanAgo: Long? = null,
    /** When the last run was, when a due one hasn't happened (NIP-FB's warning); null when fine. */
    val missedSince: Long? = null,
    /**
     * The occurrence a named runner didn't print ([ObserverSite.missedRun] over both sites), when
     * nothing printed before it either, so [missedSince] has no day to name; null when fine.
     */
    val missedDue: Long? = null,
    /** True when [lastRan] is the newest edition on the runner's site (NIP-FB, Runner): "Last printed by your Assistant". */
    val lastByRunner: Boolean = false,
    /** Every key the schedule's public `p` names (NIP-FB, Runner); more than one is a conflict. */
    val runners: List<HexKey> = emptyList(),
    /** The one runner that prints for the reader, or null (none, or a conflict). */
    val runner: HexKey? = null,
    /** The event holds only settings: no `cron`, so it never runs on its own (NIP-FB, `param`). */
    val settingsOnly: Boolean = false,
    /** Params this app doesn't understand: it lists and edits the schedule, but never runs it on schedule. */
    val unknownParams: List<String> = emptyList(),
) {
    /** The schedule names a runner (one, or several in conflict): no device of the reader prints it on schedule. */
    val namesRunner: Boolean get() = runners.isNotEmpty()

    /** More than one public `p`: nobody prints it until the reader hands it back to their devices. */
    val runnerConflict: Boolean get() = runners.size > 1

    /**
     * The schedule names runners but none prints: a conflict, or one `p` that isn't a key. Only the
     * reader's devices are offered as the fix ([FrontPageMorning.printFromMyDevices]).
     */
    val nobodyPrints: Boolean get() = namesRunner && runner == null
}

/**
 * The editions on the reader's own Observer site (and kept on this device) and on the site of the
 * runner their schedule names, for the status line and the missed-run warning
 * ([ObserverSite.lastPrinted], [ObserverSite.missedRun]).
 */
@Immutable
data class ObserverSites(
    val reader: List<ObserverEdition> = emptyList(),
    val runner: List<ObserverEdition> = emptyList(),
    /** The runner's site was read (or there is no runner): until then a missing paper isn't a missed one. */
    val runnerRead: Boolean = true,
)

/** Whether [task] is a morning schedule this app runs: the Observer task, a cron it reads, a zone this platform knows. */
fun runsHereAtAll(task: ScheduledTask) = task.task == ObserverSchedule.TASK && task.expression() != null && FrontPageClock.cronZone(task.tz) != null

/**
 * Whether the reader's own devices print [task] on schedule: it [runsHereAtAll], names no runner
 * (NIP-FB, Runner: then only that runner does, and a conflict runs nowhere), and holds no param
 * this app doesn't understand (NIP-FB, `param`: such a task is never run).
 */
fun devicesPrint(task: ScheduledTask) = runsHereAtAll(task) && !task.namesRunner && FrontPageParams.understands(task)

/**
 * The one runner the schedule names for its author (NIP-FB, Runner): a lowercase hex key that
 * isn't the author's own ([ScheduledTask.runnerFor]); null with none, a conflict, or a `p` that
 * names nobody. Only this runner's Observer site is ever read.
 */
fun SyncedSchedule.runner(): HexKey? = (this as? SyncedSchedule.Readable)?.let { it.task.runnerFor(it.event.pubKey) }

/**
 * [task] printed by the reader's own devices again, changed at [now]: no public `p` (NIP-FB,
 * Runner), the private tags encrypted to the reader when it is published, and the schedule
 * restarted from [now] ([ScheduledTask.withRunner]), so no device claims a morning the runner was
 * due to print before it was cleared. Null, nothing to write, when it names no runner.
 */
fun withoutRunner(
    task: ScheduledTask?,
    now: Long,
): ScheduledTask? = task?.takeIf { it.namesRunner }?.withRunner(null, now)

/** An Observer schedule holding only settings: no `cron`, so it never runs (NIP-FB, `param`). */
fun observerSettingsOnly(): ScheduledTask = ScheduledTask(arrayOf(DTag.assemble(ObserverSchedule.D_TAG)), arrayOf(TaskTag.assemble(ObserverSchedule.TASK)))

/** [task] holds the Observer's settings but no schedule: it never runs on its own. */
fun isSettingsOnly(task: ScheduledTask) = task.cronTag == null && task.task == ObserverSchedule.TASK

/**
 * [task] with the model the reader picked in the print view ([model], blank leaves it) and the
 * key it prints with ([key], null leaves it): written as the Observer's `model` and `key` params
 * ([ObserverParams]); every other param stays as read. With no schedule yet, a settings-only one
 * ([observerSettingsOnly]): it holds the choice for every app and never runs.
 */
fun withObserverSettings(
    current: ScheduledTask?,
    model: String?,
    key: AgentKeyTag?,
): ScheduledTask {
    var next = current ?: observerSettingsOnly()
    if (!model.isNullOrBlank()) next = ObserverParams.withModel(next, model)
    if (key != null) next = ObserverParams.withKey(next, key)
    return next
}

/**
 * The per-install remembered model ([remembered], from [FrontPageModelMemory]) moved into [task]
 * as its `model` param, unless the schedule already names one: the migration this install makes
 * on its first edit of the event.
 */
fun withMigratedModel(
    task: ScheduledTask,
    remembered: String?,
): ScheduledTask = if (remembered.isNullOrBlank() || ObserverParams.model(task) != null) task else ObserverParams.withModel(task, remembered)

/**
 * [current] turned on with [seed]'s schedule when it has none (a settings-only event): its params,
 * runner and every other tag stay; else as [ScheduledTask.withEnabled].
 */
fun withScheduleOn(
    current: ScheduledTask,
    seed: ScheduledTask?,
    on: Boolean,
    at: Long,
): ScheduledTask? {
    if (current.cronTag != null) return current.withEnabled(on, at)
    if (!on) return null
    val cron = seed?.cronTag ?: return null
    return current.withCron(cron.expression, cron.zone ?: return null, at).withEnabled(true, at)
}

/**
 * A read-modify-write of the schedule through [state] with [transform], and this install's model
 * migration ([withMigratedModel]) folded into whatever it writes: [remembered] is null once this
 * install has migrated, or when it remembers no model.
 */
suspend fun editMigrating(
    state: ScheduleRelays,
    at: Long,
    remembered: String?,
    transform: (ScheduledTask?) -> ScheduledTask?,
): ScheduleEdit = state.edit(at) { current -> transform(current)?.let { withMigratedModel(it, remembered) } }

/**
 * The schedule this device keeps, or null when there is nothing to decide on yet (no synced
 * schedule seen: then whatever is scheduled stays). A device prints only where the reader turned
 * it on ([optedIn]), only if it can print alone, and only with write relays to claim on.
 */
fun scheduleForThisDevice(
    synced: SyncedSchedule,
    optedIn: FrontPageSchedule,
    canPrintHere: Boolean,
    hasWriteRelays: Boolean = true,
): FrontPageSchedule? =
    when (synced) {
        is SyncedSchedule.Readable -> {
            val task = synced.task
            FrontPageSchedule(task.enabled && optedIn.enabled && canPrintHere && hasWriteRelays && devicesPrint(task), task.cron.orEmpty(), task.tz)
        }
        SyncedSchedule.Unknown -> null
        else -> FrontPageSchedule()
    }

/** [seconds] rounded down to what "… ago" says: minutes under an hour, hours under a day, else days. */
fun agoBucket(seconds: Long): Long =
    when {
        seconds < 3_600L -> (seconds / 60L).coerceAtLeast(0) * 60L
        seconds < 86_400L -> seconds / 3_600L * 3_600L
        else -> seconds / 86_400L * 86_400L
    }

/**
 * [cron] in words ([CronWords]), read in [zone] (named [zoneId]) and told on [device]'s clock
 * (named [deviceId]; null where this platform can't say): a time and weekdays from one run no
 * clock change moved, a few times a day, every few hours, monthly, else as written.
 */
fun describeCron(
    cron: CronExpression,
    zoneId: String,
    zone: CronZone,
    deviceId: String?,
    device: CronZone?,
    now: Long,
): CronWords {
    val same = deviceId == zoneId
    val label = zoneId.takeUnless { same }
    SimpleCron.parse(cron.text)?.let { simple ->
        if (same) return CronWords.Days(simple)
        if (device != null) simple.seenFrom(zone, device, now)?.let { return CronWords.Days(it) }
        return CronWords.Days(simple, label)
    }
    val fields = cron.text.split(' ')
    val times = (0..23).flatMap { h -> (0..59).filter { m -> cron.runsAt(h, m) }.map { m -> h to m } }
    val minutes = times.map { it.second }.distinct()
    // How far this device's clock is ahead of the schedule's, at its next run.
    val ahead = if (device != null && !same) cron.nextAfter(now, zone)?.let { (device.offsetSecondsAt(it) - zone.offsetSecondsAt(it)) / 60 } else 0
    val everyDay = fields[2] == "*" && fields[3] == "*" && fields[4] == "*"
    val hourStep =
        when {
            fields[1] == "*" -> 1
            fields[1].startsWith("*/") -> fields[1].removePrefix("*/").toIntOrNull()
            else -> null
        }
    if (everyDay && hourStep != null && 24 % hourStep == 0 && minutes.size == 1 && ahead != null) {
        return CronWords.EveryHours(hourStep, (minutes[0] + ahead).mod(60))
    }

    fun onDevice(list: List<Pair<Int, Int>>): Pair<List<Pair<Int, Int>>, String?> {
        if (ahead == null || (!same && device == null)) return list to label
        val moved = list.map { (h, m) -> h * 60 + m + ahead }
        return if (moved.all { it in 0 until 1_440 }) moved.map { (it / 60) to (it % 60) }.sortedBy { it.first * 60 + it.second } to null else list to label
    }
    if (everyDay && times.size in 2..4) {
        val (shown, zoneShown) = onDevice(times)
        return CronWords.TimesADay(shown, zoneShown)
    }
    val day = fields[2].toIntOrNull()
    if (day != null && fields[3] == "*" && fields[4] == "*" && times.size == 1) {
        val (shown, zoneShown) = onDevice(times)
        return CronWords.Monthly(day, shown[0].first, shown[0].second, zoneShown)
    }
    return CronWords.Raw(cron.text)
}

/** What changing the time to [hour]:[minute] on this device's clock does to [task]. */
sealed interface TimeEdit {
    data class Change(
        val task: ScheduledTask,
    ) : TimeEdit

    /** The time shown, confirmed: nothing to write. */
    data object Unchanged : TimeEdit

    /** The schedule isn't a time and weekdays this editor writes, or its zone isn't known here. */
    data object NotEditable : TimeEdit

    /** The time can't be said once in the schedule's zone on that day. */
    data object NotExpressible : TimeEdit
}

/**
 * The time edit: on a device in the schedule's own zone, the fields change in place. From
 * another zone, the run the editor shows moves to the picked time on the device's calendar day,
 * and the expression follows it in the schedule's zone (weekdays moved when it crosses midnight
 * there), so the schedule keeps its zone and the clock changes it follows
 * ([SimpleCron.movedTo]). Confirming the time shown is no change: nothing is written, the time it
 * was set stays.
 */
fun planTimeEdit(
    task: ScheduledTask,
    hour: Int,
    minute: Int,
    deviceId: String,
    device: CronZone,
    now: Long,
): TimeEdit {
    val simple = task.simple() ?: return TimeEdit.NotEditable
    val tz = task.tz ?: return TimeEdit.NotEditable
    val zone = FrontPageClock.cronZone(tz) ?: return TimeEdit.NotEditable
    val next =
        if (tz == deviceId) {
            SimpleCron(hour, minute, simple.weekdays)
        } else {
            val seen = simple.seenFrom(zone, device, now) ?: return TimeEdit.NotEditable
            if (seen.hour == hour && seen.minute == minute) return TimeEdit.Unchanged
            simple.movedTo(hour, minute, zone, device, now) ?: return TimeEdit.NotExpressible
        }
    if (next == simple) return TimeEdit.Unchanged
    return TimeEdit.Change(task.withCron(next.toCron(), tz, now))
}

/** The time this device's editor shows for [task]: its own fields in its own zone, else one unmoved run on this clock. */
fun editableTime(
    task: ScheduledTask,
    deviceId: String?,
    device: CronZone?,
    now: Long,
): Pair<Int, Int>? {
    if (deviceId == null || device == null) return null
    val simple = task.simple() ?: return null
    val tz = task.tz ?: return null
    if (tz == deviceId) return simple.hour to simple.minute
    val zone = FrontPageClock.cronZone(tz) ?: return null
    return simple.seenFrom(zone, device, now)?.let { it.hour to it.minute }
}

fun viewOf(
    synced: SyncedSchedule,
    optedIn: FrontPageSchedule,
    canPrintHere: Boolean,
    hasWriteRelays: Boolean = true,
    now: Long = TimeUtils.now(),
    deviceId: String? = FrontPageClock.localZone(),
    sites: ObserverSites = ObserverSites(),
): MorningScheduleView {
    val base = MorningScheduleView(printsHere = optedIn.enabled, canPrintHere = canPrintHere, hasWriteRelays = hasWriteRelays)
    return when (synced) {
        SyncedSchedule.Unknown -> base.copy(state = ScheduleState.NOT_SET_UP)
        is SyncedSchedule.Locked -> base.copy(state = ScheduleState.CANT_DECRYPT, retryable = true)
        is SyncedSchedule.CantDecrypt -> base.copy(state = ScheduleState.CANT_DECRYPT, retryable = synced.retryable)
        is SyncedSchedule.Unreadable -> base.copy(state = ScheduleState.UNREADABLE)
        is SyncedSchedule.Readable -> {
            val task = synced.task
            val zone = FrontPageClock.cronZone(task.tz)
            val expression = task.expression()
            val device = FrontPageClock.cronZone(deviceId)
            val withRunner =
                base.copy(
                    runners = task.runners,
                    runner = synced.runner(),
                    unknownParams = FrontPageParams.unknownParams(task),
                )
            // Settings and no schedule: nothing runs, the switch is off, the settings are kept.
            if (isSettingsOnly(task)) return withRunner.copy(state = ScheduleState.NOT_SET_UP, settingsOnly = true)
            // NIP-FB: the claim's name only for the claim `ran` finished; an older `ran` has no name here.
            val lastBy = task.claim?.by?.takeIf { ScheduledTaskClaims.isFinished(task) }
            // With a runner, it can't write `ran`: its site and the reader's say when it last printed.
            val last = if (task.namesRunner) ObserverSite.lastPrinted(sites.reader, sites.runner)?.takeIf { it.printedAt >= (task.ran ?: 0) } else null
            val lastRan = last?.printedAt ?: task.ran
            val shown =
                withRunner.copy(
                    enabled = task.enabled,
                    cron = task.cron,
                    zone = task.tz,
                    lastBy = if (last != null) null else lastBy,
                    lastByRunner = last?.byRunner == true,
                    lastRan = lastRan,
                    lastRanAgo = lastRan?.let { agoBucket(now - it) },
                )
            if (task.task != ObserverSchedule.TASK || expression == null || zone == null || task.tz == null) {
                shown.copy(state = ScheduleState.UNREADABLE, words = task.cron?.let { CronWords.Raw(it) })
            } else {
                val missed =
                    when {
                        !task.namesRunner -> task.missedRun(zone, now)
                        // Not read yet: say nothing rather than warn about a paper that may be there.
                        !sites.runnerRead -> null
                        else -> ObserverSite.missedRun(task, sites.reader, sites.runner, zone, now)
                    }
                shown.copy(
                    state = ScheduleState.READY,
                    words = describeCron(expression, task.tz!!, zone, deviceId, device, now),
                    editableTime = editableTime(task, deviceId, device, now),
                    missedSince = lastRan?.takeIf { missed != null },
                    missedDue = missed?.takeIf { lastRan == null && task.namesRunner },
                )
            }
        }
    }
}

/**
 * The morning schedule for every account on this install: one NIP-FB task (kind 34817, `d` =
 * `observer`) shared by all of the reader's devices; any device where the reader turned on
 * "Print from this device" may print an occurrence, and the first to claim it does
 * (`quartz/plans/2026-11-11-observer-site.md`, "The morning schedule, synced"). Reads and edits
 * go through [Account.observerSchedule]. This install's claim id and its own on/off ([optIns],
 * the old per-device schedule) are kept here, in this install's settings.
 */
class FrontPageMorning(
    val optIns: FrontPageScheduleStore,
    private val installs: FrontPageInstallStore,
    private val info: FrontPageInstallInfo?,
    private val now: () -> Long = { TimeUtils.now() },
    private val pause: suspend (Long) -> Unit = { delay(it) },
    /** The model this install remembered per endpoint before settings moved into the schedule: migrated once, on the first edit. */
    private val memory: FrontPageModelMemory? = null,
) {
    suspend fun installId(account: Account) = installs.id(account.signer.pubKey)

    /** This install can print with nobody there: it can print at all, and the key is on the device. */
    fun canPrintHere(account: Account): Boolean = info != null && unattendedBlocker(account.signer, supportedBoundary, busy = false) == null

    private val supportedBoundary by lazy { paperBoundary() }

    private fun hasWriteRelays(relays: Set<NormalizedRelayUrl>) = relays.isNotEmpty()

    /** The schedule this device keeps for [account], as it changes; null while undecided. */
    fun here(account: Account): Flow<FrontPageSchedule?> =
        combine(account.observerSchedule.flow, optIns.flow(account.signer.pubKey), account.observerSchedule.writeRelays) { synced, mine, relays ->
            scheduleForThisDevice(synced, mine, canPrintHere(account), hasWriteRelays(relays))
        }

    /**
     * What the screen shows. A clock ticks each minute only while someone collects it, and a
     * new view goes out only when what it says changed ("2 hours ago", a missed morning, the
     * next run's time on this clock).
     */
    fun view(
        account: Account,
        sites: Flow<ObserverSites> = flowOf(ObserverSites()),
    ): Flow<MorningScheduleView> =
        combine(account.observerSchedule.flow, optIns.flow(account.signer.pubKey), account.observerSchedule.writeRelays, minutes(), sites) { synced, mine, relays, at, both ->
            viewOf(synced, mine, canPrintHere(account), hasWriteRelays(relays), at, sites = both)
        }.distinctUntilChanged()

    /**
     * The Observer's clock ([ObserverNow]): the calendar the "already printed today" check reads (a
     * readable schedule's zone, else this device's), and the time, again each minute while
     * collected. A new one goes out only when the day or the calendar changed: at midnight, or
     * when the schedule's zone does. The zone comes from the [schedule] view the screen already
     * collects ([view]), not a second one.
     */
    fun observerNow(schedule: Flow<MorningScheduleView>): Flow<ObserverNow> =
        combine(schedule.map { ObserverCalendar.readableZone(it) }.distinctUntilChanged(), minutes()) { zone, now -> ObserverNow(zone, now) }
            .distinctUntilChanged()

    /** The time now, then again each minute, while collected. */
    private fun minutes(): Flow<Long> =
        flow {
            while (true) {
                emit(now())
                delay(60_000)
            }
        }

    /**
     * The schedule this device keeps, from what is known now: for the platform's next job. While
     * nothing is known of the synced schedule (a cold process whose relays didn't answer), the
     * job keeps [fallback], the schedule it was made for, so one bad morning doesn't end the chain.
     */
    suspend fun scheduleHere(
        account: Account,
        fallback: FrontPageSchedule = FrontPageSchedule(),
    ): FrontPageSchedule = scheduleForThisDevice(account.observerSchedule.flow.value, optIns.get(account.signer.pubKey), canPrintHere(account), hasWriteRelays(account.observerSchedule.writeRelays.value)) ?: fallback

    /**
     * Whether a launch should try the last due morning: this device prints, and the schedule it
     * holds shows that occurrence unclaimed, claimed long ago and unfinished, or claimed by this
     * install and unfinished (the app died mid-print). The run itself reads the relays again.
     */
    suspend fun catchUpWanted(account: Account): Boolean {
        val task = (account.observerSchedule.flow.value as? SyncedSchedule.Readable)?.task ?: return false
        if (!optIns.get(account.signer.pubKey).enabled || !canPrintHere(account)) return false
        val zone = FrontPageClock.cronZone(task.tz) ?: return false
        val at = now()
        val occurrence = ScheduledTaskClaims.occurrence(task, zone, at) ?: return false
        val id = installId(account)
        if (!devicesPrint(task)) return false
        return ScheduledTaskClaims.decide(task, ObserverSchedule.TASK, occurrence, at, id, ownRun(id, occurrence)) == Decision.CLAIM
    }

    /**
     * What this install's own record says of its run of [occurrence] (NIP-FB keeps none): a run
     * still open is one the process didn't live through, which resumes; one that ended, however,
     * is never run again on its own. A record that can't be read is no record.
     */
    private suspend fun ownRun(
        id: String,
        occurrence: Long,
    ): ScheduledTaskClaims.OwnRun =
        try {
            installs.lastRun(id)?.of(occurrence) ?: ScheduledTaskClaims.OwnRun.NONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read this install's last morning", e)
            ScheduledTaskClaims.OwnRun.NONE
        }

    /** Writes [record] for [id], even while the run is being cancelled: a stopped run must say it ended. */
    private suspend fun recordRun(
        id: String,
        record: OwnRunRecord?,
    ) = withContext(NonCancellable) {
        try {
            installs.setLastRun(id, record)
        } catch (e: Exception) {
            Log.w(TAG, "Could not record this install's morning", e)
        }
    }

    /** One edit at a time, this device's settings read inside it: two quick taps land in order. */
    private val editQueue = Mutex()

    /**
     * Once per account and install: a device that had its own schedule and finds no synced one
     * (the relays answered and hold none) publishes it, in this device's zone. Skipped when a
     * readable synced schedule is already here, or it was done before. A device that can't print
     * alone loses its old on/off instead: it could never print.
     */
    suspend fun migrate(account: Account) {
        val pubKey = account.signer.pubKey
        val mine = optIns.get(pubKey)
        if (!mine.enabled) return
        if (!canPrintHere(account)) {
            optIns.set(pubKey, mine.copy(enabled = false))
            return
        }
        if (optIns.isMigrated(pubKey)) return
        if ((account.observerSchedule.flow.value as? SyncedSchedule.Readable)?.task?.let(::isSettingsOnly) == false) {
            optIns.setMigrated(pubKey)
            return
        }
        val zone = FrontPageClock.localZone() ?: return
        val outcome =
            editQueue.withLock {
                val at = now()
                // Set now: the mornings this device missed before it moved aren't anyone's to catch up.
                val local = ObserverSchedule.new(zone, at).withEnabled(true, at).withCron(mine.cron, zone, at)
                edit(account, at) { current ->
                    when {
                        current == null -> local
                        isSettingsOnly(current) -> withScheduleOn(current, local, on = true, at)
                        else -> null
                    }
                }
            }
        if (outcome is ScheduleEdit.Saved || outcome is ScheduleEdit.Unchanged || outcome is ScheduleEdit.Unreadable) optIns.setMigrated(pubKey)
    }

    /** A new schedule for this account, on, at the old local time when there was one, set [at]; null when this device can't name its zone. */
    private suspend fun seed(
        account: Account,
        at: Long,
    ): ScheduledTask? {
        val zone = FrontPageClock.localZone() ?: return null
        val mine = optIns.get(account.signer.pubKey)
        val fresh = ObserverSchedule.new(zone, at).withEnabled(true, at)
        return if (mine.enabled && mine.simple() != null) fresh.withCron(mine.cron, zone, at) else fresh
    }

    /** On or off for every device; turned on with none yet, one is published now. Switched on, this device takes part when it can. */
    suspend fun setEnabled(
        account: Account,
        on: Boolean,
    ): ScheduleEdit =
        editQueue.withLock {
            val at = now()
            val seed = if (on) seed(account, at) else null
            val known = (account.observerSchedule.flow.value as? SyncedSchedule.Readable)?.task
            if (on && seed == null && (known == null || isSettingsOnly(known))) return@withLock ScheduleEdit.NoZone
            val outcome = edit(account, at) { current -> if (current == null) seed else withScheduleOn(current, seed, on, at) }
            if (on && (outcome is ScheduleEdit.Saved || outcome is ScheduleEdit.Unchanged) && canPrintHere(account)) setOptIn(account, true)
            outcome
        }

    /** The time on this device's clock ([planTimeEdit]); confirming the time shown writes nothing. */
    suspend fun setTime(
        account: Account,
        hour: Int,
        minute: Int,
    ): ScheduleEdit =
        editQueue.withLock {
            val deviceId = FrontPageClock.localZone() ?: return@withLock ScheduleEdit.NoZone
            val device = FrontPageClock.cronZone(deviceId) ?: return@withLock ScheduleEdit.NoZone
            val at = now()
            var refused: ScheduleEdit? = null
            val outcome =
                edit(account, at) { current ->
                    when (val plan = current?.let { planTimeEdit(it, hour, minute, deviceId, device, at) }) {
                        is TimeEdit.Change -> plan.task
                        TimeEdit.NotExpressible -> null.also { refused = ScheduleEdit.NotExpressible }
                        else -> null
                    }
                }
            refused ?: outcome
        }

    /**
     * "Print from this device": only a device that can print alone takes part. Turned on with no
     * synced schedule, one is published now, on (set now), instead of at the next launch.
     */
    suspend fun setPrintsHere(
        account: Account,
        on: Boolean,
    ): ScheduleEdit? =
        editQueue.withLock {
            if (on && !canPrintHere(account)) return@withLock null
            setOptIn(account, on)
            if (!on || account.observerSchedule.flow.value != SyncedSchedule.Unknown) return@withLock null
            val at = now()
            val seed = seed(account, at) ?: return@withLock ScheduleEdit.NoZone
            edit(account, at) { current -> if (current == null) seed else null }
        }

    /**
     * Back to the reader's own devices (NIP-FB, Runner): every public `p` goes, so a named runner,
     * a conflict and a `p` that isn't a key all end the same way. Rewritten through the schedule's
     * edit rules: every other tag stays, and the private tags are encrypted to the reader again
     * ([ScheduledTaskEvent.rewrite]). The job comes back on the devices whose "Print from this
     * device" stayed on ([devicesPrint]). The only runner edit Amethyst makes: choosing a runner is
     * the provider's call, made in Brainstorm, so this app never writes one.
     */
    suspend fun printFromMyDevices(account: Account): ScheduleEdit =
        editQueue.withLock {
            val at = now()
            edit(account, at) { withoutRunner(it, at) }
        }

    /**
     * The model the reader picked in the print view and the key it prints with, written into the
     * schedule as the Observer's `model` and `key` params (every app prints with them); with no
     * schedule yet, as a settings-only event that never runs ([withObserverSettings]).
     */
    suspend fun setSettings(
        account: Account,
        model: String?,
        key: AgentKeyTag?,
    ): ScheduleEdit =
        editQueue.withLock {
            edit(account, now()) { current -> withObserverSettings(current, model, key) }
        }

    /**
     * The writing effort the reader picked, written into the schedule as the Observer's `effort`
     * param (every app, the Assistant and the worker print with it); Standard removes it. With no
     * schedule yet, Deep is kept in a settings-only event; Standard needs nothing written.
     */
    suspend fun setEffort(
        account: Account,
        effort: WritingEffort,
    ): ScheduleEdit =
        editQueue.withLock {
            edit(account, now()) { current ->
                if (current == null && effort == WritingEffort.STANDARD) null else FrontPageParams.withEffort(current ?: observerSettingsOnly(), effort)
            }
        }

    /**
     * Every edit this install makes of the schedule for the reader: the first one that writes also
     * moves the model this install remembered for the key's endpoint into the event, unless it
     * names one already ([withMigratedModel]), once.
     */
    private suspend fun edit(
        account: Account,
        at: Long,
        transform: (ScheduledTask?) -> ScheduledTask?,
    ): ScheduleEdit {
        val pubKey = account.signer.pubKey
        val migrating = memory != null && !memoryMigrated(pubKey)
        val remembered = if (migrating) rememberedModel(account) else null
        val outcome = editMigrating(account.observerSchedule, at, remembered, transform)
        if (migrating && outcome is ScheduleEdit.Saved) {
            try {
                memory.setMigrated(pubKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not record the model's move into the schedule", e)
            }
        }
        return outcome
    }

    private suspend fun memoryMigrated(pubKey: HexKey): Boolean =
        try {
            memory?.isMigrated(pubKey) ?: true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read whether the model moved into the schedule", e)
            true
        }

    /** The model this install remembers for the endpoint the schedule prints with ([ObserverParams.key]). */
    private suspend fun rememberedModel(account: Account): String? {
        val keys = account.agentKeys.flow.value
        val task = (account.observerSchedule.flow.value as? SyncedSchedule.Readable)?.task
        val url = (task?.let { ObserverParams.key(it, keys) } ?: keys.firstOrNull())?.url ?: return null
        return try {
            memory?.get(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the remembered model", e)
            null
        }
    }

    private suspend fun setOptIn(
        account: Account,
        on: Boolean,
    ) {
        val pubKey = account.signer.pubKey
        val mine = optIns.get(pubKey)
        if (mine.enabled != on) optIns.set(pubKey, mine.copy(enabled = on))
    }

    /** Accounts with an occurrence running on this install: a second run (the timer and a catch-up) waits for nothing, it stops. */
    private val running = MutableStateFlow<Set<HexKey>>(emptySet())

    /**
     * One scheduled occurrence of [account]'s morning, by NIP-FB's claim protocol ([claimRun]).
     * [print] gets the seconds it may take (null: no limit) and the check to make before
     * keeping a paper. [deadline] (unix seconds) is when the platform stops this run, if it does.
     */
    suspend fun runOccurrence(
        account: Account,
        alreadyDone: suspend (occurrence: Long, zone: CronZone) -> Boolean,
        deadline: Long? = null,
        print: suspend (budgetSeconds: Long?, keep: suspend () -> Boolean) -> ScheduledOutcome,
    ): ScheduledOutcome {
        val pubKey = account.signer.pubKey
        if (!optIns.get(pubKey).enabled) return ScheduledOutcome.NOT_RUNNER
        while (true) {
            val now = running.value
            if (pubKey in now) return ScheduledOutcome.BUSY
            if (running.compareAndSet(now, now + pubKey)) break
        }
        try {
            return claimRun(account.observerSchedule, installId(account), info?.name, alreadyDone, deadline, print)
        } finally {
            running.update { it - pubKey }
        }
    }

    /**
     * NIP-FB's claim protocol ([ScheduledTaskClaims]) on [state]: a random wait; a read that more
     * than half of the write relays answer; stop unless the occurrence is unclaimed, stale, or
     * this install's own unfinished claim on a run its process didn't live through (this install's
     * own record, [OwnRunRecord]: a run that ended, failed or not, is never run again on its own,
     * since it may have been paid for); claim (watching first, more than half must accept),
     * listen [ScheduledTaskClaims.SETTLE_SECONDS] and go on only if this install's claim is the
     * newest (an edit that overwrote it unseen sends this back to the read, a few times at most).
     * Then a paper that already exists for the occurrence ([alreadyDone]) only sets `ran`; else
     * [print] runs under watch: a newer claim by another device stops it before it keeps
     * anything, and its paper is dropped. A print sets `ran`, retried a few times.
     */
    suspend fun claimRun(
        state: ScheduleRelays,
        id: String,
        by: String?,
        alreadyDone: suspend (occurrence: Long, zone: CronZone) -> Boolean,
        deadline: Long? = null,
        print: suspend (budgetSeconds: Long?, keep: suspend () -> Boolean) -> ScheduledOutcome,
    ): ScheduledOutcome {
        pause(RandomInstance.int(ScheduledTaskClaims.MAX_JITTER_SECONDS.toInt() + 1) * 1000L)

        var first: Long? = null
        repeat(ScheduledTaskClaims.MAX_ROUNDS) {
            // Too little time left to claim, print and say so: leave it to a later run, unclaimed.
            if (deadline != null && deadline - now() < CLAIM_SECONDS + MIN_PRINT_SECONDS + RAN_RESERVE_SECONDS) return ScheduledOutcome.FAILED
            val read = state.readQuorum()
            if (!read.quorum) return ScheduledOutcome.NO_QUORUM
            // No schedule, or one this signer can't decrypt: never run, never write.
            val event = read.event ?: return ScheduledOutcome.NOT_RUNNER
            val task = read.task ?: return ScheduledOutcome.NOT_RUNNER
            // NIP-FB, `param`: a task with a param this app doesn't understand is never run.
            if (!FrontPageParams.understands(task)) return ScheduledOutcome.NOT_RUNNER
            val zone = FrontPageClock.cronZone(task.tz) ?: return ScheduledOutcome.NOT_RUNNER
            val at = now()
            val occurrence = ScheduledTaskClaims.occurrence(task, zone, at) ?: return ScheduledOutcome.NOT_DUE
            // Back for another round only while it is still the same occurrence.
            if (first != null && first != occurrence) return ScheduledOutcome.NOT_DUE
            first = occurrence
            when (ScheduledTaskClaims.decide(task, ObserverSchedule.TASK, occurrence, at, id, ownRun(id, occurrence))) {
                Decision.NOT_RUNNABLE -> return ScheduledOutcome.NOT_RUNNER
                // NIP-FB, Runner: the reader named a runner (their Assistant) to print on schedule;
                // no device of theirs claims or prints it. Printing by hand still works here.
                Decision.RUNNER_NAMED -> return ScheduledOutcome.NOT_RUNNER
                Decision.ALREADY_CLAIMED -> return ScheduledOutcome.ALREADY_CLAIMED
                Decision.ATTEMPTED_HERE -> return ScheduledOutcome.ALREADY_ATTEMPTED
                Decision.CLAIM -> {}
            }
            claimAndRun(state, read.relays, read.floor ?: event, task, occurrence, zone, id, by, alreadyDone, deadline, print)?.let { return it }
        }
        return ScheduledOutcome.LOST_CLAIM
    }

    private enum class Phase { PRINTING, KEEPING, LOST }

    /** Steps 3 to 6: the claim, the wait, the run under watch. Null when the claim was overwritten unseen: read again. */
    private suspend fun claimAndRun(
        state: ScheduleRelays,
        relays: Set<NormalizedRelayUrl>,
        previous: ScheduledTaskEvent,
        task: ScheduledTask,
        occurrence: Long,
        zone: CronZone,
        id: String,
        by: String?,
        alreadyDone: suspend (occurrence: Long, zone: CronZone) -> Boolean,
        deadline: Long?,
        print: suspend (budgetSeconds: Long?, keep: suspend () -> Boolean) -> ScheduledOutcome,
    ): ScheduledOutcome? =
        coroutineScope {
            val seen = MutableStateFlow<List<ScheduledTaskEvent>>(listOf(previous))
            val subscribed = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
            val watching =
                launch {
                    state.watch(relays) { relay -> subscribed.update { it + relay } }.collect { incoming ->
                        seen.update { (it + incoming).distinctBy { e -> e.id } }
                    }
                }
            // Open from before the claim goes out until the run ends: a process that dies anywhere
            // in between leaves it open, and the next launch takes its own claim up again.
            recordRun(id, OwnRunRecord(occurrence))
            var running = false
            var ended: ScheduledOutcome? = null
            try {
                // Watch before claiming, so a competing claim published meanwhile is seen.
                withTimeoutOrNull(SUBSCRIBE_WAIT_MS) { subscribed.first { it.containsAll(relays) } }
                val (mine, accepted) = state.publish(previous, ScheduledTaskClaims.claimed(task, occurrence, id, by, now()), relays)
                seen.update { it + mine }
                if (!accepted) return@coroutineScope ScheduledOutcome.NO_QUORUM
                pause(ScheduledTaskClaims.SETTLE_SECONDS * 1000)

                val newest = ScheduledTaskClaims.winner(seen.value)?.let { state.taskOf(it) } ?: return@coroutineScope ScheduledOutcome.LOST_CLAIM
                when (ScheduledTaskClaims.settle(newest, occurrence, id)) {
                    ScheduledTaskClaims.Settled.LOST -> return@coroutineScope ScheduledOutcome.LOST_CLAIM
                    ScheduledTaskClaims.Settled.AGAIN -> return@coroutineScope null
                    ScheduledTaskClaims.Settled.OURS -> {}
                }
                // From here the occurrence is this install's to run, and running may cost money.
                running = true
                ended = runClaimed(state, occurrence, zone, id, seen, alreadyDone, deadline, print)
                ended
            } finally {
                watching.cancel()
                // A claim that didn't win ran nothing: the occurrence stays open to this install.
                recordRun(id, if (running) OwnRunRecord(occurrence, ended ?: ScheduledOutcome.CANCELLED) else null)
            }
        }

    /** Steps 5 and 6, once the claim is this install's: the paper (or the skip), under watch, then `ran`. */
    private suspend fun runClaimed(
        state: ScheduleRelays,
        occurrence: Long,
        zone: CronZone,
        id: String,
        seen: MutableStateFlow<List<ScheduledTaskEvent>>,
        alreadyDone: suspend (occurrence: Long, zone: CronZone) -> Boolean,
        deadline: Long?,
        print: suspend (budgetSeconds: Long?, keep: suspend () -> Boolean) -> ScheduledOutcome,
    ): ScheduledOutcome =
        coroutineScope {
            // The occurrence's paper exists (a manual print, another app): done, without printing.
            // Claimed first, so a skip never takes a morning from a device printing it.
            val done =
                try {
                    alreadyDone(occurrence, zone)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not tell whether the paper exists", e)
                    false
                }
            if (done) {
                markRan(state, occurrence, id)
                return@coroutineScope ScheduledOutcome.ALREADY_PRINTED
            }

            suspend fun lostTo(events: List<ScheduledTaskEvent>): Boolean {
                val latest = ScheduledTaskClaims.winner(events)?.let { state.taskOf(it) } ?: return false
                return ScheduledTaskClaims.claimsForAnother(latest, occurrence, id)
            }
            val phase = MutableStateFlow(Phase.PRINTING)
            val budget = deadline?.let { it - now() - RAN_RESERVE_SECONDS }
            val printing =
                async {
                    print(budget) {
                        // The last word before the paper is kept: a claim seen by now stops it.
                        yield()
                        if (lostTo(seen.value)) {
                            phase.compareAndSet(Phase.PRINTING, Phase.LOST)
                            false
                        } else {
                            phase.compareAndSet(Phase.PRINTING, Phase.KEEPING)
                        }
                    }
                }
            val guard =
                launch {
                    seen.collect { events ->
                        if (lostTo(events) && phase.compareAndSet(Phase.PRINTING, Phase.LOST)) printing.cancel()
                    }
                }
            val outcome =
                try {
                    printing.await()
                } catch (e: CancellationException) {
                    if (phase.value != Phase.LOST) throw e
                    ScheduledOutcome.LOST_CLAIM
                } finally {
                    guard.cancel()
                }
            if (phase.value == Phase.LOST) return@coroutineScope ScheduledOutcome.LOST_CLAIM
            if (outcome == ScheduledOutcome.PRINTED) markRan(state, occurrence, id)
            outcome
        }

    /**
     * `ran` for this install's claim on [occurrence]: a fresh read-modify-write that changes only
     * `ran`, tried again with a growing pause when too few relays answer or take it.
     */
    private suspend fun markRan(
        state: ScheduleRelays,
        occurrence: Long,
        id: String,
    ) {
        val done = now()
        for (attempt in 0 until RAN_TRIES) {
            val outcome =
                try {
                    state.edit(done, resend = attempt > 0) { current ->
                        current?.takeIf { ScheduledTaskClaims.isOurs(it, occurrence, id) }?.let { if (it.ran == done) it else ScheduledTaskClaims.finished(it, done) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not mark the morning done", e)
                    ScheduleEdit.Unreachable
                }
            if (outcome != ScheduleEdit.Unreachable && outcome != ScheduleEdit.NotAccepted) return
            if (attempt < RAN_TRIES - 1) pause(RAN_BACKOFF_MS[attempt])
        }
        Log.w(TAG) { "The morning of $occurrence printed, but too few relays took its `ran`" }
    }

    suspend fun forget(account: HexKey) {
        optIns.set(account, FrontPageSchedule())
        optIns.forgetMigrated(account)
        installs.forget(account)
    }

    companion object {
        private const val TAG = "FrontPageMorning"

        /** How long the claim waits for each relay's subscription before publishing anyway. */
        const val SUBSCRIBE_WAIT_MS = 8_000L

        /** `ran` is tried this many times, with these pauses between. */
        const val RAN_TRIES = 4
        val RAN_BACKOFF_MS = longArrayOf(5_000L, 15_000L, 40_000L)

        /** One round of the claim, at its slowest: the read, the watch, the publish and the settle. */
        const val CLAIM_SECONDS = 8L + 8L + 10L + ScheduledTaskClaims.SETTLE_SECONDS

        /** What `ran` takes at its slowest: every try's read and publish, and the pauses. */
        const val RAN_RESERVE_SECONDS = RAN_TRIES * (8L + 10L) + 60L

        /** Less than this left for the print: don't start one. */
        const val MIN_PRINT_SECONDS = 120L

        /**
         * The whole of one scheduled run at its slowest, without the print: the random wait, three
         * claim rounds and `ran`. A platform that stops runs adds the print's time to this.
         */
        const val OVERHEAD_SECONDS = ScheduledTaskClaims.MAX_JITTER_SECONDS + ScheduledTaskClaims.MAX_ROUNDS * CLAIM_SECONDS + RAN_RESERVE_SECONDS
    }
}

/**
 * Keeps each loaded account's platform job in step with the synced schedule and this device's
 * own choice: scheduled while the schedule is on and this device prints, cancelled otherwise,
 * moved whenever either changes (never stopping a print that is running: that is the
 * platform's to keep apart). At account load it also moves an old local schedule to the synced
 * one, once, and runs a morning missed while the app was closed when the schedule shows it open.
 */
class FrontPageScheduleWatcher(
    private val morning: FrontPageMorning,
    private val accounts: StateFlow<Map<HexKey, Account>>,
    private val scheduler: FrontPageScheduler,
    private val scope: CoroutineScope,
) {
    private val jobs = HashMap<HexKey, Pair<Account, Job>>()

    fun start() {
        scope.launch {
            accounts.collect { loaded ->
                jobs.keys.filter { loaded[it] !== jobs[it]?.first }.forEach { jobs.remove(it)?.second?.cancel() }
                loaded.forEach { (pubKey, account) ->
                    if (pubKey in jobs) return@forEach
                    jobs[pubKey] = account to scope.launch { watch(pubKey, account) }
                }
            }
        }
    }

    private suspend fun watch(
        pubKey: HexKey,
        account: Account,
    ) = coroutineScope {
        launch {
            try {
                morning.migrate(account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not move the morning schedule", e)
            }
        }
        follow(pubKey, morning.here(account), scheduler) { morning.catchUpWanted(account) }
    }

    companion object {
        private const val TAG = "FrontPageScheduleWatcher"

        /**
         * Keeps [pubKey]'s platform job in step with [schedules] (the schedule this device keeps,
         * null while undecided): every change reschedules it, so a schedule that comes to name a
         * runner, or turns off, cancels the job, and one whose runner is cleared brings it back. The
         * first enabled one also runs a morning missed while the app was closed, when [catchUp] says so.
         */
        suspend fun follow(
            pubKey: HexKey,
            schedules: Flow<FrontPageSchedule?>,
            scheduler: FrontPageScheduler,
            catchUp: suspend () -> Boolean,
        ) {
            var first = true
            schedules
                .filterNotNull()
                .distinctUntilChanged()
                .collect { schedule ->
                    scheduler.reschedule(pubKey, schedule)
                    if (first && schedule.enabled && catchUp()) scheduler.runNow(pubKey)
                    first = false
                }
        }
    }
}
