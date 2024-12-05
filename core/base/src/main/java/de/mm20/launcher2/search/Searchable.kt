package de.mm20.launcher2.search

interface Searchable {
    val score: ResultScore
        get() = ResultScore.Unspecified
}