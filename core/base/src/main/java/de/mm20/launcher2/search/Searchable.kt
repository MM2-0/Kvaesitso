package de.mm20.launcher2.search

import kotlinx.coroutines.Deferred

interface Searchable {
    val score: ResultScore
        get() = ResultScore.Zero
}