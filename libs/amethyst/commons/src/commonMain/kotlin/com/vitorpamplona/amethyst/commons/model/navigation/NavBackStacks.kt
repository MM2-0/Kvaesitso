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
package com.vitorpamplona.amethyst.commons.model.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

/**
 * A window's navigation state, owned by the app (Navigation 3 renders whatever this holds): the
 * [model] of the current [NavArrangement] (a [StackModel], a [TabsModel] or a [WindowsModel]), and the
 * conversions between them as the user switches layouts. Every navigation goes through [open]; the
 * stack calls ([push], [pop], [newStack], [popUpTo], [switchTab]) are shorthands for its requests.
 */
@Stable
class NavBackStacks private constructor(
    private val ids: EntryIds,
    initialModel: (EntryIds) -> NavModel,
) {
    constructor(start: Route = Route.Home) : this(EntryIds(0), { ids -> StackModel(ids, listOf(ids.entry(start))) })

    var model: NavModel by mutableStateOf(initialModel(ids))
        private set

    val arrangement: NavArrangement get() = model.arrangement

    /** Whether each screen is a tab or a window of its own, closed instead of left. */
    val isSingleScreen: Boolean get() = model is SingleScreenModel

    val top: NavStackEntry get() = model.top

    val topRoute: Route get() = top.route

    /** Every screen the model holds, in its own order: the stack bottom to top, the tabs in strip order. */
    val stack: List<NavStackEntry> get() = model.entries

    /** The bar's places the stack layout keeps alive while the user is elsewhere; empty in the other layouts. */
    val savedTabs: Map<Route, NavStackEntry> get() = (model as? StackModel)?.savedTabs ?: emptyMap()

    fun open(request: NavRequest) = model.open(request)

    fun back(from: NavStackEntry? = null): Boolean = model.back(from)

    fun shown(wide: Boolean): List<NavStackEntry> = model.shown(wide)

    fun retained(): List<NavStackEntry> = model.retained()

    fun windows(): List<NavStackEntry> = model.windows()

    /** Closes the tab or window [id]; the stack has nothing to close. */
    fun close(id: Long): Boolean = (model as? SingleScreenModel)?.close(id) ?: false

    /** Pushes [route], unless it is already the screen on top (in the stack). Returns whether anything opened. */
    fun push(
        route: Route,
        drawerRoot: Boolean = false,
    ): Boolean {
        (model as? StackModel)?.let { return it.push(route, drawerRoot) }
        open(NavRequest(route, if (drawerRoot) NavRequest.Kind.Drawer else NavRequest.Kind.Push))
        return true
    }

    fun pop(): Boolean = back()

    fun newStack(route: Route) = open(NavRequest(route, NavRequest.Kind.Restart))

    fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) = open(NavRequest(route, NavRequest.Kind.Replace(klass)))

    fun switchTab(route: Route) = open(NavRequest(route, NavRequest.Kind.Section))

    /**
     * Switches to [target]. Every switch goes through a stack: leaving the tabs keeps the active tab
     * and the tabs that opened it, leaving the windows keeps the main window's section. Entering the
     * tabs makes each screen of that stack a tab opened by the one below it; entering the windows
     * keeps only its sections, in the main window.
     */
    fun changeArrangement(target: NavArrangement) {
        if (target == arrangement) return
        val stack = asStack()
        model =
            when (target) {
                NavArrangement.STACK -> StackModel(ids, stack)
                NavArrangement.TABS -> TabsModel.fromStack(ids, stack)
                NavArrangement.WINDOWS -> WindowsModel.fromStack(ids, stack)
            }
    }

    /** The current screens as a stack, Home at the bottom. */
    private fun asStack(): List<NavStackEntry> {
        val entries =
            when (val current = model) {
                is StackModel -> current.stack.toList()
                is TabsModel -> current.chain(current.top).filter(current::isMainScreen)
                is WindowsModel -> listOf(current.top)
            }
        return if (entries.firstOrNull()?.route == Route.Home) entries else listOf(ids.entry(Route.Home)) + entries
    }

    /**
     * The screens as [encode] writes them, copied out without serializing: cheap enough to take on
     * every change (it reads every piece of state, so a snapshotFlow over it follows them all),
     * then [encode] once the changes settle, on any thread.
     */
    fun savedState(): SavedState = SavedState(Saved(model.toSaved(), ids.next))

    fun encode(): String = encode(savedState())

    /** An immutable copy of a window's screens, for [encode]. */
    class SavedState internal constructor(
        internal val saved: Saved,
    )

    @Serializable
    internal class SavedEntry(
        val route: Route,
        val id: Long,
        val tabRoute: Route? = null,
        val drawerRoot: Boolean = false,
        val routeVersion: Int = 0,
    )

    @Serializable
    internal sealed class SavedModel {
        @Serializable
        @SerialName("stack")
        class Stack(
            val stack: List<SavedEntry>,
            val savedTabs: List<SavedEntry> = emptyList(),
        ) : SavedModel()

        @Serializable
        @SerialName("tabs")
        class Tabs(
            val tabs: List<SavedEntry>,
            val activeId: Long,
            val openers: Map<Long, Long> = emptyMap(),
            val pinned: Set<Long> = emptySet(),
            val detached: Set<Long> = emptySet(),
        ) : SavedModel()

        @Serializable
        @SerialName("windows")
        class Windows(
            val sections: List<SavedEntry>,
            val activeId: Long,
            val windows: List<SavedEntry> = emptyList(),
            val openers: Map<Long, Long> = emptyMap(),
        ) : SavedModel()
    }

    @Serializable
    internal class Saved(
        val model: SavedModel,
        val nextId: Long,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Writes [state] for [decode]. Touches no snapshot state, so it can run off the main thread. */
        fun encode(state: SavedState): String = json.encodeToString(Saved.serializer(), state.saved)

        private fun NavStackEntry.toSaved() = SavedEntry(route, id, tabRoute, drawerRoot, routeVersion)

        private fun SavedEntry.toEntry() = NavStackEntry(route, id, tabRoute, drawerRoot, routeVersion)

        private fun NavModel.toSaved(): SavedModel =
            when (this) {
                is StackModel -> SavedModel.Stack(stack.map { it.toSaved() }, savedTabs.values.map { it.toSaved() })
                is TabsModel -> SavedModel.Tabs(tabs.map { it.toSaved() }, activeTabId, openerMap(), pinnedIds(), detachedIds())
                is WindowsModel -> SavedModel.Windows(sections.map { it.toSaved() }, activeSectionId, windows().map { it.toSaved() }, openerMap())
            }

        /** Restores what [encode] wrote, or null when it can't be read (an app update renamed a route). */
        fun decode(encoded: String): NavBackStacks? =
            runCatching {
                val saved = json.decodeFromString(Saved.serializer(), encoded)
                NavBackStacks(EntryIds(saved.nextId)) { ids ->
                    when (val model = saved.model) {
                        is SavedModel.Stack -> {
                            require(model.stack.isNotEmpty())
                            // Filed under the route the bar asks for, as switchTab files them: a tab re-argued
                            // in place no longer matches its own route.
                            StackModel(ids, model.stack.map { it.toEntry() }, model.savedTabs.map { it.toEntry() }.associateBy { it.tabRoute ?: it.route })
                        }
                        is SavedModel.Tabs -> {
                            require(model.tabs.any { it.id !in model.detached })
                            TabsModel(ids, model.tabs.map { it.toEntry() }, model.activeId, model.openers, model.pinned, model.detached)
                        }
                        is SavedModel.Windows -> {
                            require(model.sections.isNotEmpty())
                            WindowsModel(ids, model.sections.map { it.toEntry() }, model.activeId, model.windows.map { it.toEntry() }, model.openers)
                        }
                    }
                }
            }.getOrNull()
    }
}
