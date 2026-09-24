package de.mm20.launcher2.search

import com.aallam.similarity.JaroWinkler
import de.mm20.launcher2.search.fuzzy.FzfMatcher

/**
 * How well a search result matches a query, between 0 and 1. Higher is better. Any score of at
 * least [MatchThreshold] is a match.
 */
@JvmInline
value class ResultScore(val score: Float) : Comparable<ResultScore> {

    override fun compareTo(other: ResultScore): Int {
        return score.compareTo(other.score)
    }

    companion object {
        /**
         * Scores [query] against the best of [primaryFields] and [secondaryFields]. Queries and
         * fields are expected to be normalized (see [StringNormalizer]) by the caller.
         *
         * A field matches if it contains the query's characters in order (an fzf-style
         * subsequence match, e.g. "ytm" in "yt music"), or failing that, if it is close enough to
         * the query to be a typo of it (Jaro-Winkler similarity, e.g. "settimgs" for "settings").
         *
         * Every match lands in one of three non-overlapping bands, so that the kind of evidence
         * decides the order before the similarity within a band does:
         *
         * - `[0.95, 1]` Strong literal match: the characters are all there, tightly clustered or at
         *   word boundaries. An exact match scores 1.
         * - `[0.90, 0.95)` Weak literal match: the characters are all there, but scattered.
         * - `[0.80, 0.90)` Typo match: some typed character is not in the field at all, so this is
         *   a guess at a correction.
         *
         * All bands sit above [MatchThreshold], where the previous substring-or-similar scorer put
         * its matches too, so the balance against usage weights in ranking is unchanged.
         */
        fun from(
            query: String,
            primaryFields: Iterable<String> = emptyList(),
            secondaryFields: Iterable<String> = emptyList(),
        ): ResultScore {
            // An empty query is a prefix of everything.
            if (query.isEmpty()) return ResultScore(1f)

            val maxScore = FzfMatcher.maxScoreFor(query)
            val bestPrimary = primaryFields.maxOfOrNull { scoreField(query, it, maxScore) } ?: 0f
            val bestSecondary = secondaryFields.maxOfOrNull {
                scoreField(query, it, maxScore) * SecondaryFieldFactor
            } ?: 0f
            return ResultScore(maxOf(bestPrimary, bestSecondary))
        }

        private fun scoreField(query: String, term: String, maxScore: Int): Float {
            val literal = FzfMatcher.normalizedScore(query, term, maxScore)
            if (literal > 0f) {
                return if (literal >= StrongLiteralThreshold) {
                    lerp(
                        StrongLiteralBand,
                        1f,
                        (literal - StrongLiteralThreshold) / (1f - StrongLiteralThreshold)
                    )
                } else {
                    lerp(WeakLiteralBand, StrongLiteralBand, literal / StrongLiteralThreshold)
                }
            }

            // With only one or two characters, there is nothing to base a correction on.
            if (query.length < MinTypoQueryLength) return 0f

            val similarity = JaroWinkler().similarity(query, term).toFloat()
            if (similarity < MatchThreshold) return 0f
            // Similarity is only 1 for identical strings, which the literal path has already
            // caught, so this stays below WeakLiteralBand.
            return lerp(
                TypoBand,
                WeakLiteralBand,
                (similarity - MatchThreshold) / (1f - MatchThreshold)
            )
        }

        private fun lerp(start: Float, end: Float, fraction: Float): Float {
            return start + (end - start) * fraction.coerceIn(0f, 1f)
        }

        /**
         * The lowest score that counts as a match.
         */
        const val MatchThreshold = 0.8f

        /**
         * Normalized fzf score from which a literal match is considered strong.
         */
        private const val StrongLiteralThreshold = 0.5f

        private const val StrongLiteralBand = 0.95f
        private const val WeakLiteralBand = 0.9f
        private const val TypoBand = MatchThreshold

        private const val MinTypoQueryLength = 3

        /**
         * How much a match on a secondary field is worth relative to the same match on a primary
         * field.
         */
        private const val SecondaryFieldFactor = 0.8f

        val Zero = ResultScore(0f)

        /**
         * No score has been computed yet. Results that carry this are scored lazily when they are
         * ranked, since not every repository scores its own results.
         */
        val Unspecified = ResultScore(Float.NaN)
    }
}

inline val ResultScore.isUnspecified: Boolean
    get() = score.isNaN()
