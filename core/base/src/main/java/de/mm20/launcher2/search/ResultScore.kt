package de.mm20.launcher2.search

import com.aallam.similarity.JaroWinkler
import de.mm20.launcher2.ktx.normalize

@JvmInline
value class ResultScore private constructor(private val packed: Long) : Comparable<ResultScore> {
    constructor(
        isPrefix: Boolean,
        isSubstring: Boolean,
        isPrimary: Boolean,
        similarity: Float,
    ) : this(
        (similarity.toRawBits().toLong()) or
                (if (isPrefix) (1L shl 32) else 0) or
                (if (isSubstring) (1L shl 33) else 0) or
                (if (isPrimary) (1L shl 34) else 0)
    )

    /**
     * Whether the query is a literal prefix of the result.
     */
    val isPrefix: Boolean
        get() = (packed and (1L shl 32)) != 0L

    /**
     * Whether the query is a substring of the result.
     */
    val isSubstring: Boolean
        get() = (packed and (1L shl 33)) != 0L

    /**
     * Whether the query was matched against a primary field.
     */
    val isPrimary: Boolean
        get() = (packed and (1L shl 34)) != 0L

    /**
     * The Jaro-Winkler similarity between the query and the result.
     */
    val similarity: Float
        get() = Float.fromBits((packed and 0xffffffffL).toInt())

    /**
     * A total score for the result, combining the similarity with additional factors.
     * The score is normalized to be between 0 and 1.
     */
    val score: Float
        get() = (similarity + (if (isPrefix) 0.2f else 0f) + (if (isSubstring) 0.8f else 0f)).coerceIn(0f, 1f) * (if (isPrimary) 1f else 0.8f)

    override fun compareTo(other: ResultScore): Int {
        return score.compareTo(other.score)
    }

    companion object {
        operator fun invoke(
            query: String,
            primaryFields: Iterable<String> = emptyList(),
            secondaryFields: Iterable<String> = emptyList(),
        ): ResultScore {
            val normalizedQuery = query.normalize()
            val jaroWinkler = JaroWinkler()
            val bestPrimaryScore = primaryFields.maxOfOrNull {
                val normalizedTerm = it.normalize()
                val sim = jaroWinkler.similarity(normalizedQuery, normalizedTerm).toFloat()
                ResultScore(
                    isPrefix = normalizedTerm.startsWith(normalizedQuery),
                    isSubstring = normalizedQuery in normalizedTerm,
                    isPrimary = true,
                    similarity = sim
                )
            } ?: Zero
            val bestSecondaryScore = secondaryFields.maxOfOrNull {
                val normalizedTerm = it.normalize()
                val sim = jaroWinkler.similarity(normalizedQuery, normalizedTerm).toFloat()
                ResultScore(
                    isPrefix = normalizedTerm.startsWith(normalizedQuery),
                    isSubstring = normalizedQuery in normalizedTerm,
                    isPrimary = false,
                    similarity = sim
                )
            } ?: Zero

            return maxOf(bestPrimaryScore, bestSecondaryScore)
        }

        val Zero = ResultScore(
            isPrefix = false,
            isSubstring = false,
            isPrimary = false,
            similarity = 0f
        )

        val Unspecified = ResultScore(
            isPrefix = false,
            isSubstring = false,
            isPrimary = false,
            similarity = Float.NaN,
        )
    }
}

inline val ResultScore.isUnspecified : Boolean
    get() = this == ResultScore.Unspecified