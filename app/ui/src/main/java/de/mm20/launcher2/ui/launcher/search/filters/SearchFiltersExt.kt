package de.mm20.launcher2.ui.launcher.search.filters

import de.mm20.launcher2.search.SearchFilters

fun SearchFilters.withAllCategories(): SearchFilters {
    return copy(
        apps = true,
        websites = true,
        articles = true,
        places = true,
        files = true,
        shortcuts = true,
        contacts = true,
        events = true,
        tools = true
    )
}

fun SearchFilters.withOnlyCategory(
    apps: Boolean = false,
    websites: Boolean = false,
    articles: Boolean = false,
    places: Boolean = false,
    files: Boolean = false,
    shortcuts: Boolean = false,
    contacts: Boolean = false,
    events: Boolean = false,
    utilities: Boolean = false
): SearchFilters {
    return copy(
        apps = apps,
        websites = websites,
        articles = articles,
        places = places,
        files = files,
        shortcuts = shortcuts,
        contacts = contacts,
        events = events,
        tools = utilities
    )
}

/**
 * Create a new [SearchFilters] object with the [apps] property update, according to the following rules:
 *  - If all categories are enabled, disable all categories except for apps.
 *  - If apps is the only enabled category, enable all categories.
 *  - Otherwise, toggle the apps category.
 */
fun SearchFilters.toggleApps(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(apps = true)
    }
    if (apps && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(apps = !apps)
}

fun SearchFilters.toggleWebsites(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(websites = true)
    }
    if (websites && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(websites = !websites)
}

fun SearchFilters.toggleArticles(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(articles = true)
    }
    if (articles && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(articles = !articles)
}

fun SearchFilters.togglePlaces(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(places = true)
    }
    if (places && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(places = !places)
}

fun SearchFilters.toggleFiles(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(files = true)
    }
    if (files && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(files = !files)
}

fun SearchFilters.toggleShortcuts(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(shortcuts = true)
    }
    if (shortcuts && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(shortcuts = !shortcuts)
}

fun SearchFilters.toggleContacts(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(contacts = true)
    }
    if (contacts && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(contacts = !contacts)
}

fun SearchFilters.toggleEvents(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(events = true)
    }
    if (events && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(events = !events)
}

fun SearchFilters.toggleTools(): SearchFilters {
    if (allCategoriesEnabled) {
        return withOnlyCategory(utilities = true)
    }
    if (tools && enabledCategories == 1) {
        return withAllCategories()
    }

    return copy(tools = !tools)
}
