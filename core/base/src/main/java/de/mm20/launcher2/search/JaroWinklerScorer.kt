package de.mm20.launcher2.search

import com.aallam.similarity.JaroWinkler

/**
 * Scores results by Jaro-Winkler similarity between the query and the whole field, plus bonuses if
 * the query is a prefix or substring of it. Tolerates typos ("settimgs" still matches "settings"),
 * but cannot match abbreviations, since it compares the two strings as wholes.
 */
class JaroWinklerScorer : SearchScorer {

    private val jaroWinkler = JaroWinkler()

    override fun score(
        query: String,
        primaryFields: Iterable<String>,
        secondaryFields: Iterable<String>,
    ): ResultScore = bestFieldScore(primaryFields, secondaryFields) { term, isPrimary ->
        val similarity = jaroWinkler.similarity(query, term).toFloat()
        // A prefix is always a substring too, so both bonuses apply to a prefix match.
        ResultScore(
            (similarity +
                    (if (term.startsWith(query)) PrefixBonus else 0f) +
                    (if (query in term) SubstringBonus else 0f))
                .coerceIn(0f, 1f) * (if (isPrimary) 1f else SecondaryFieldFactor)
        )
    }

    override fun isMatch(score: ResultScore): Boolean {
        return score.score >= MatchThreshold
    }

    companion object {
        private const val PrefixBonus = 0.2f
        private const val SubstringBonus = 0.8f
        private const val MatchThreshold = 0.8f
    }
}
