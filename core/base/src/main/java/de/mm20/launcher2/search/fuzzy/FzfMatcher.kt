package de.mm20.launcher2.search.fuzzy

/**
 * A successful fuzzy match.
 *
 * @param score The raw, unnormalized score. Higher is better. Use [FzfMatcher.normalize] to turn
 * it into a comparable 0..1 value.
 * @param positions Indices of the matched characters in the target, or null if they were not
 * requested. Only used for highlighting.
 */
class FzfResult(
    val score: Int,
    val positions: IntArray?,
)

/**
 * An fzf-style fuzzy matcher: a query matches a target if its characters appear in the target in
 * order, but not necessarily contiguously ("ytm" matches "yt music"). The score rewards matches
 * that are tightly clustered, start at a word boundary, and begin early in the target.
 *
 * This is a port of fzf's `FuzzyMatchV1` algorithm: a greedy forward scan to find a match, a
 * backward scan to tighten it, then a single scoring pass over the resulting window. Unlike fzf's
 * `FuzzyMatchV2`, which runs a full dynamic-programming pass, this is linear in the length of the
 * target. That matters here because app search re-scores every installed app on every keystroke,
 * and the two algorithms only disagree on alignments that are rare for short labels.
 *
 * Callers are expected to pass strings that have already been normalized (see `StringNormalizer`).
 * Since normalization lowercases its input, the upper-case character class does not arise there and
 * [BonusCamel123] only fires on digit transitions; the camelCase handling is kept so the matcher is
 * still correct for callers that score raw labels. Word boundary bonuses are unaffected by
 * normalization, because separators (space, `-`, `_`, `.`, `/`) survive it — which covers the shape
 * of most app labels ("google maps", "play store").
 */
object FzfMatcher {

    private const val ScoreMatch = 16
    private const val ScoreGapStart = -3
    private const val ScoreGapExtension = -1

    /** Bonus for a character that follows a whitespace or non-word character. */
    private const val BonusBoundary = ScoreMatch / 2

    /** Bonus for a non-word character, which often acts as a separator. */
    private const val BonusNonWord = ScoreMatch / 2

    /** Bonus for a camelCase hump or a letter-to-digit transition. */
    private const val BonusCamel123 = BonusBoundary + ScoreGapExtension

    /** Bonus for a character that directly follows another matched character. */
    private const val BonusConsecutive = -(ScoreGapStart + ScoreGapExtension)

    /** The first matched character's bonus is worth more, to favour matches that start early. */
    private const val BonusFirstCharMultiplier = 2

    /** Returned by [rawScore] when the query is not a subsequence of the target. */
    private const val NoMatch = Int.MIN_VALUE

    private const val CharWhite = 0
    private const val CharNonWord = 1
    private const val CharDigit = 2
    private const val CharLower = 3
    private const val CharUpper = 4

    /**
     * Matches [query] against [target], returning null if [query] is not a subsequence of [target].
     *
     * @param withPositions Whether to record the index of every matched character. Only needed for
     * highlighting; skipping it avoids allocating an array per candidate.
     */
    fun match(query: String, target: String, withPositions: Boolean = false): FzfResult? {
        val positions = if (withPositions) IntArray(query.length) else null
        val score = rawScore(query, target, positions)
        if (score == NoMatch) return null
        return FzfResult(score, positions)
    }

    /**
     * The score of [query] matched against [target], normalized to 0..1, or 0 if it does not match.
     *
     * When scoring many candidates against the same query, compute [maxScoreFor] once and pass it
     * in rather than letting it be recomputed for every candidate.
     */
    fun normalizedScore(
        query: String,
        target: String,
        maxScore: Int = maxScoreFor(query),
    ): Float {
        if (maxScore <= 0) return 0f
        val score = rawScore(query, target, null)
        return if (score == NoMatch) 0f else normalize(score, maxScore)
    }

    /**
     * The highest score [query] can achieve against any target, which is its score against itself:
     * a fully contiguous match starting at position 0, which is always a boundary.
     */
    fun maxScoreFor(query: String): Int {
        if (query.isEmpty()) return 0
        val score = rawScore(query, query, null)
        return if (score == NoMatch) 0 else score
    }

    /** Scales a raw score from [match] into the 0..1 range, given a [maxScore] for the query. */
    fun normalize(rawScore: Int, maxScore: Int): Float {
        if (maxScore <= 0) return 0f
        return (rawScore.toFloat() / maxScore).coerceIn(0f, 1f)
    }

    /**
     * The raw score of the tightest window of [target] containing [query], or [NoMatch]. Fills
     * [positions] with the matched indices when it is non-null.
     */
    private fun rawScore(query: String, target: String, positions: IntArray?): Int {
        if (query.isEmpty()) return 0
        if (query.length > target.length) return NoMatch

        // Forward scan: find the earliest position at which the whole query has been consumed.
        var queryIndex = 0
        var end = -1
        for (i in target.indices) {
            if (target[i] == query[queryIndex]) {
                queryIndex++
                if (queryIndex == query.length) {
                    end = i + 1
                    break
                }
            }
        }
        if (end == -1) return NoMatch

        // Backward scan: pull the start of the match as far right as possible, so that a later,
        // tighter occurrence of the query wins over an earlier, scattered one.
        queryIndex = query.length - 1
        var start = -1
        for (i in end - 1 downTo 0) {
            if (target[i] == query[queryIndex]) {
                queryIndex--
                if (queryIndex < 0) {
                    start = i
                    break
                }
            }
        }
        if (start == -1) return NoMatch

        return scoreWindow(query, target, start, end, positions)
    }

    private fun scoreWindow(
        query: String,
        target: String,
        start: Int,
        end: Int,
        positions: IntArray?,
    ): Int {
        var queryIndex = 0
        var score = 0
        var inGap = false
        var inRun = false
        var firstBonus = 0

        var prevClass = if (start > 0) charClassOf(target[start - 1]) else CharWhite

        for (i in start until end) {
            val char = target[i]
            val currClass = charClassOf(char)

            if (queryIndex < query.length && char == query[queryIndex]) {
                positions?.set(queryIndex, i)
                queryIndex++
                score += ScoreMatch

                var bonus = bonusFor(prevClass, currClass)
                if (!inRun) {
                    firstBonus = bonus
                } else {
                    // A run of consecutive matches is worth at least BonusConsecutive, but if the
                    // run crosses a boundary we re-anchor to the stronger of the two bonuses.
                    if (bonus >= BonusBoundary && bonus > firstBonus) firstBonus = bonus
                    bonus = maxOf(bonus, firstBonus, BonusConsecutive)
                }

                score += if (queryIndex == 1) bonus * BonusFirstCharMultiplier else bonus

                inGap = false
                inRun = true
            } else {
                score += if (inGap) ScoreGapExtension else ScoreGapStart
                inGap = true
                inRun = false
                firstBonus = 0
            }

            prevClass = currClass
        }

        return score
    }

    private fun charClassOf(char: Char): Int {
        return when {
            char in 'a'..'z' -> CharLower
            char in 'A'..'Z' -> CharUpper
            char in '0'..'9' -> CharDigit
            char.isWhitespace() -> CharWhite
            char.isLetter() -> CharLower
            char.isDigit() -> CharDigit
            else -> CharNonWord
        }
    }

    private fun bonusFor(prevClass: Int, currClass: Int): Int {
        return when {
            prevClass == CharWhite && currClass != CharWhite -> BonusBoundary
            prevClass == CharNonWord && currClass != CharNonWord -> BonusBoundary
            prevClass == CharLower && currClass == CharUpper -> BonusCamel123
            prevClass != CharDigit && currClass == CharDigit -> BonusCamel123
            currClass == CharWhite || currClass == CharNonWord -> BonusNonWord
            else -> 0
        }
    }
}
