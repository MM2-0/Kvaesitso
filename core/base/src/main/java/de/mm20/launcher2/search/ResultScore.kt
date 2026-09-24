package de.mm20.launcher2.search

/**
 * How well a search result matches a query, between 0 and 1. Higher is better.
 *
 * Each [SearchScorer] decides how its own measurements become a final score, so scores are only
 * meaningfully comparable between results scored by the same scorer.
 */
@JvmInline
value class ResultScore private constructor(private val bits: Int) : Comparable<ResultScore> {

    constructor(score: Float) : this(score.toRawBits())

    val score: Float
        get() = Float.fromBits(bits)

    override fun compareTo(other: ResultScore): Int {
        return score.compareTo(other.score)
    }

    companion object {
        val Zero = ResultScore(0f)

        /**
         * No score has been computed yet. Results that carry this are scored lazily when they are
         * ranked, since not every repository scores its own results.
         */
        val Unspecified = ResultScore(Float.NaN)
    }
}

inline val ResultScore.isUnspecified: Boolean
    get() = this == ResultScore.Unspecified
