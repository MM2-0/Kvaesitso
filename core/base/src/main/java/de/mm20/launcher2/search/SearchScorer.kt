package de.mm20.launcher2.search

/**
 * Scores search results against a query. Implementations decide both how a candidate is scored and
 * what counts as a match at all, because those two things are algorithm specific: Jaro-Winkler
 * grades every candidate and needs a cutoff, while fuzzy subsequence matching is binary.
 *
 * Queries and fields are expected to be normalized (see [StringNormalizer]) by the caller.
 */
interface SearchScorer {
    /**
     * Scores [query] against the best of [primaryFields] and [secondaryFields]. Matches on
     * secondary fields are weighted lower than matches on primary fields.
     */
    fun score(
        query: String,
        primaryFields: Iterable<String> = emptyList(),
        secondaryFields: Iterable<String> = emptyList(),
    ): ResultScore

    /**
     * Whether a [score] is good enough for the candidate to be shown at all. Candidates that fail
     * this check should be dropped rather than ranked.
     */
    fun isMatch(score: ResultScore): Boolean
}

/**
 * How much a match on a secondary field is worth relative to the same match on a primary field.
 */
internal const val SecondaryFieldFactor = 0.8f

/**
 * Scores every field with [scoreField] and returns the best result. Shared by all [SearchScorer]
 * implementations so they agree on how primary and secondary fields are combined.
 */
internal inline fun bestFieldScore(
    primaryFields: Iterable<String>,
    secondaryFields: Iterable<String>,
    scoreField: (term: String, isPrimary: Boolean) -> ResultScore,
): ResultScore {
    val bestPrimary = primaryFields.maxOfOrNull { scoreField(it, true) } ?: ResultScore.Zero
    val bestSecondary = secondaryFields.maxOfOrNull { scoreField(it, false) } ?: ResultScore.Zero
    return maxOf(bestPrimary, bestSecondary)
}
