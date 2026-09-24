package de.mm20.launcher2.search

import de.mm20.launcher2.search.fuzzy.FzfMatcher

/**
 * Scores results by fzf-style fuzzy matching: a field matches if the query's characters appear in
 * it in order ("ytm" matches "yt music"), scored higher the more tightly those characters are
 * clustered and the closer they are to a word boundary. Matches abbreviations, but unlike
 * [JaroWinklerScorer] it does not tolerate a wrong or transposed character.
 */
class FzfScorer : SearchScorer {

    /**
     * The normalization denominator depends only on the query, but [score] is called once per
     * candidate with the same query, so computing it per call would repeat the same work for every
     * installed app on every keystroke. Cached as one immutable object so that a concurrent search
     * for a different query can only cause a recomputation, never a mismatched denominator.
     */
    private class MaxScore(val query: String, val value: Int)

    @Volatile
    private var cachedMaxScore: MaxScore? = null

    override fun score(
        query: String,
        primaryFields: Iterable<String>,
        secondaryFields: Iterable<String>,
    ): ResultScore {
        // An empty query matches everything, as it does for JaroWinklerScorer, where every field
        // trivially starts with and contains "".
        if (query.isEmpty()) return ResultScore(1f)

        val maxScore = maxScoreFor(query)
        return bestFieldScore(primaryFields, secondaryFields) { term, isPrimary ->
            val similarity = FzfMatcher.normalizedScore(query, term, maxScore)
            ResultScore(if (isPrimary) similarity else similarity * SecondaryFieldFactor)
        }
    }

    override fun isMatch(score: ResultScore): Boolean {
        return score.score > 0f
    }

    private fun maxScoreFor(query: String): Int {
        val cached = cachedMaxScore
        if (cached != null && cached.query == query) return cached.value
        return FzfMatcher.maxScoreFor(query).also {
            cachedMaxScore = MaxScore(query, it)
        }
    }
}
