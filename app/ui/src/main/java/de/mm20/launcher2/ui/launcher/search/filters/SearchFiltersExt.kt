package de.mm20.launcher2.ui.launcher.search.filters

import de.mm20.launcher2.search.SearchFilters

fun SearchFilters.toggleWebsites(): SearchFilters = SearchFilters(websites = !websites)
fun SearchFilters.toggleArticles(): SearchFilters = SearchFilters(articles = !articles)
fun SearchFilters.togglePlaces(): SearchFilters = SearchFilters(places = !places)
fun SearchFilters.toggleFiles(): SearchFilters = SearchFilters(files = !files)
fun SearchFilters.toggleEvents(): SearchFilters = SearchFilters(events = !events)
