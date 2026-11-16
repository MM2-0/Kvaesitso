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
package com.vitorpamplona.amethyst.calendar

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.CalendarsViewMode
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.CalendarsViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.startOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The paging arithmetic the calendar lenses share, now that it lives on the screen's ViewModel
 * instead of being spelled out in each view's click handlers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarsViewModelTest {
    // The model's derived flows are `stateIn(viewModelScope, …)`, and stateIn launches its
    // sharing coroutine as soon as the property initializer runs — so simply CONSTRUCTING the
    // model touches Dispatchers.Main. Without these, that launch fails on a plain JVM test and
    // the failure lands on a background thread, where `runTest` reports it against whichever
    // test happens to start next. Clearing the store afterwards cancels the scope, exactly as
    // the back stack does when the screen goes.
    private var store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        store = ViewModelStore()
    }

    private val scopes = mutableListOf<Job>()

    @After
    fun tearDown() {
        store.clear()
        // Clearing only requests cancellation: a producer the model runs on Dispatchers.Default
        // (its flowOn) can still be finishing, and it hands its completion back to the Main
        // collector. Waiting for the scopes keeps that inside setMain, instead of throwing onto a
        // background thread after resetMain and failing whichever runTest starts next.
        runBlocking { withTimeout(SCOPE_CLOSE_MS) { scopes.joinAll() } }
        scopes.clear()
        Dispatchers.resetMain()
    }

    private fun newModel(): CalendarsViewModel =
        ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[CalendarsViewModel::class.java].also {
            scopes += it.viewModelScope.coroutineContext.job
        }

    @Test
    fun aFreshScreenOpensOnToday() {
        val now = LocalDate.now()
        val today = SearchDate(now.year, now.monthValue, now.dayOfMonth)
        val model = newModel()

        assertEquals(CalendarsViewMode.FEED, model.viewMode)
        assertNull(model.filterDTag.value)
        assertEquals(today.firstOfMonth(), model.visibleMonth)
        assertEquals(today, model.visibleDate)
        assertEquals(startOfWeek(today), model.weekStart)
        assertEquals(0, model.selectedDayIndex)
        assertNull(model.selectedDayKey)
    }

    @Test
    fun pagingTheMonthDropsTheDaySelectedInTheOldOne() {
        val model = newModel()
        model.selectedDayKey = LocalDate.of(2025, 1, 15).toEpochDay()

        model.showMonth(model.visibleMonth.firstOfMonth(1))

        assertNull("a day picked in January cannot stay selected in February", model.selectedDayKey)
    }

    @Test
    fun pagingTheWeekMovesSevenDaysAndReturnsTheStripToTheFirstDay() {
        val model = newModel()
        model.showWeekOf(SearchDate(2025, 1, 15)) // a Wednesday
        model.selectedDayIndex = 4

        val before = model.weekStart
        model.shiftWeeks(1)

        assertEquals(before.plusDays(7), model.weekStart)
        assertEquals(0, model.selectedDayIndex)
    }

    @Test
    fun showWeekOfSnapsToTheSundayOnOrBeforeTheDate() {
        val model = newModel()

        model.showWeekOf(SearchDate(2025, 1, 15)) // Wednesday
        assertEquals(SearchDate(2025, 1, 12), model.weekStart)
        assertEquals(0, model.weekStart.dayOfWeek())
        model.weekStart.let { assertEquals(DayOfWeek.SUNDAY, LocalDate.of(it.year, it.month, it.day).dayOfWeek) }

        model.showWeekOf(SearchDate(2025, 1, 12)) // already a Sunday: stays put
        assertEquals(SearchDate(2025, 1, 12), model.weekStart)
    }

    @Test
    fun theStripsSelectedDateIsTheWeekStartPlusItsIndex() {
        val model = newModel()
        model.showWeekOf(SearchDate(2025, 1, 15))
        model.selectedDayIndex = 3

        assertEquals(SearchDate(2025, 1, 15), model.selectedWeekDate)
    }

    @Test
    fun pagingTheDayCrossesDaylightSavingWithoutSlipping() {
        // US spring-forward lands on 2025-03-09; stepping by milliseconds used to land an hour
        // short and repeat a day. Calendar-day arithmetic ignores zones, so a step is always a day.
        val model = newModel()
        model.visibleEpochDay = LocalDate.of(2025, 3, 8).toEpochDay()

        model.shiftDays(1)
        assertEquals(SearchDate(2025, 3, 9), model.visibleDate)

        model.shiftDays(1)
        assertEquals(SearchDate(2025, 3, 10), model.visibleDate)

        model.shiftDays(-2)
        assertEquals(SearchDate(2025, 3, 8), model.visibleDate)
    }

    @Test
    fun everyLensKeepsItsOwnScrollPosition() {
        val model = newModel()
        val states = listOf(model.feedListState, model.monthListState, model.weekListState, model.dayListState)

        assertEquals("the lenses must not share one scroll offset", 4, states.distinct().size)
    }

    companion object {
        private const val SCOPE_CLOSE_MS = 5_000L
    }
}
