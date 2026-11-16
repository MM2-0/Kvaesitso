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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * The windows layout: the main window shows the [sections] (Home and the bar's places, one at a
 * time, [top] being the one it shows), and every other screen gets an OS window of its own
 * ([windows]). A window holds one screen and has no back arrow; it is closed instead. Opening a
 * screen that is already open raises its window, and a section asked for from a window raises the
 * main one ([windowFocus]).
 */
@Stable
class WindowsModel(
    ids: EntryIds,
    initialSections: List<NavStackEntry>,
    initialActiveId: Long,
    initialWindows: List<NavStackEntry> = emptyList(),
    initialOpeners: Map<Long, Long> = emptyMap(),
) : SingleScreenModel(ids, initialOpeners) {
    val sections: SnapshotStateList<NavStackEntry> = mutableStateListOf<NavStackEntry>().apply { addAll(initialSections) }

    private val screenWindows: SnapshotStateList<NavStackEntry> = mutableStateListOf<NavStackEntry>().apply { addAll(initialWindows) }

    private var activeId by mutableStateOf(initialActiveId)

    override val arrangement: NavArrangement get() = NavArrangement.WINDOWS

    override val entries: List<NavStackEntry> get() = sections + screenWindows

    override val top: NavStackEntry get() = sections.firstOrNull { it.id == activeId } ?: sections.last()

    override fun isMainScreen(entry: NavStackEntry): Boolean = entry in sections

    override fun windows(): List<NavStackEntry> = screenWindows.toList()

    override fun canGoBack(
        entry: NavStackEntry,
        wide: Boolean,
    ): Boolean = false

    /** Only the main window's sections carry the bar; a screen window has none. */
    override fun showsBar(
        entry: NavStackEntry,
        wide: Boolean,
    ): Boolean = isMainScreen(entry)

    /** A window's title bar names its screen; a section is named by the rail or the drawer. */
    override fun titleShownOutside(
        entry: NavStackEntry,
        wide: Boolean,
    ): Boolean = true

    override fun shown(wide: Boolean): List<NavStackEntry> = listOf(top)

    override fun retained(): List<NavStackEntry> = sections.toList()

    override fun open(request: NavRequest) {
        if (request.kind is NavRequest.Kind.Replace) {
            replace(request.origin ?: top.id, request.route)
            return
        }
        existing(request)?.let { open ->
            if (request.kind == NavRequest.Kind.Section) open.tabRoute = request.route
            if (!request.background) activate(open.id)
            flash(open.id)
            return
        }
        if (request.kind == NavRequest.Kind.Section || request.route == Route.Home) {
            val section = ids.entry(request.route, tabRoute = request.route)
            sections.add(section)
            activate(section.id)
            return
        }
        val window = ids.entry(request.route, drawerRoot = request.kind == NavRequest.Kind.Drawer)
        screenWindows.add(window)
        rememberOpener(window, request.origin ?: activeId, NavRequest.Kind.Push)
        if (!request.background) raise(window.id)
    }

    override fun activate(id: Long) {
        val entry = entries.firstOrNull { it.id == id } ?: return
        if (isMainScreen(entry)) activeId = id
        // A section raises the main window; anything else, its own.
        raise(id)
    }

    /** Back from the main window's section does nothing; from a window, closes it. */
    override fun back(from: NavStackEntry?): Boolean {
        val entry = from ?: return false
        return !isMainScreen(entry) && close(entry.id)
    }

    /** Closes the window [id], or the section [id]: the main window always keeps one, Home at worst. */
    override fun close(id: Long): Boolean {
        val entry = entries.firstOrNull { it.id == id } ?: return false
        if (!screenWindows.remove(entry)) sections.remove(entry)
        forget(entry)
        if (sections.isEmpty()) sections.add(ids.entry(Route.Home))
        if (sections.none { it.id == activeId }) activeId = sections.last().id
        return true
    }

    internal val activeSectionId: Long get() = activeId

    companion object {
        /** Keeps [stack]'s sections in the main window, the top one shown; its pushes are dropped. */
        fun fromStack(
            ids: EntryIds,
            stack: List<NavStackEntry>,
        ): WindowsModel {
            val sections = stack.filter { it.tabRoot || it.route == Route.Home }.ifEmpty { listOf(ids.entry(Route.Home)) }
            return WindowsModel(ids, sections, sections.last().id)
        }
    }
}
