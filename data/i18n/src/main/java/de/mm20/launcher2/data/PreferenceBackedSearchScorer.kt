package de.mm20.launcher2.data

import de.mm20.launcher2.preferences.search.RankingSettings
import de.mm20.launcher2.search.FzfScorer
import de.mm20.launcher2.search.JaroWinklerScorer
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.SearchScorer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * Delegates to either [FzfScorer] or [JaroWinklerScorer], depending on the user's preference.
 *
 * Scoring happens on the search hot path and cannot suspend, so the preference is collected into a
 * [kotlinx.coroutines.flow.StateFlow] and read synchronously, the same way `IcuStringNormalizer`
 * reads its transliterator setting.
 */
internal class PreferenceBackedSearchScorer(
    rankingSettings: RankingSettings,
) : SearchScorer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val jaroWinklerScorer = JaroWinklerScorer()
    private val fzfScorer = FzfScorer()

    private val fuzzyMatching = rankingSettings.fuzzyMatching
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val current: SearchScorer
        get() = if (fuzzyMatching.value) fzfScorer else jaroWinklerScorer

    override fun score(
        query: String,
        primaryFields: Iterable<String>,
        secondaryFields: Iterable<String>,
    ): ResultScore = current.score(query, primaryFields, secondaryFields)

    override fun isMatch(score: ResultScore): Boolean = current.isMatch(score)
}
