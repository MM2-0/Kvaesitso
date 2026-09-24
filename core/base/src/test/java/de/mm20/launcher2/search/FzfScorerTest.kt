package de.mm20.launcher2.search

import org.junit.Assert
import org.junit.Test

class FzfScorerTest {

    private val scorer = FzfScorer()

    @Test
    fun primaryFieldOutranksIdenticalSecondaryField() {
        val primary = scorer.score("ytm", primaryFields = listOf("yt music"))
        val secondary = scorer.score("ytm", secondaryFields = listOf("yt music"))
        Assert.assertTrue(primary.score > secondary.score)
    }

    @Test
    fun bestFieldWins() {
        val score = scorer.score("cl", primaryFields = listOf("calendar", "clock", "chrome"))
        Assert.assertEquals(
            scorer.score("cl", primaryFields = listOf("clock")).score,
            score.score,
            0f,
        )
    }

    @Test
    fun nonMatchIsZeroAndNotAMatch() {
        // "gemini" has no s, so this is not a subsequence.
        val score = scorer.score("gems", primaryFields = listOf("gemini"))
        Assert.assertEquals(ResultScore.Zero.score, score.score, 0f)
        Assert.assertFalse(scorer.isMatch(score))
    }

    @Test
    fun matchIsAMatch() {
        Assert.assertTrue(scorer.isMatch(scorer.score("pstr", primaryFields = listOf("play store"))))
    }

    @Test
    fun noFieldsIsNotAMatch() {
        Assert.assertFalse(scorer.isMatch(scorer.score("maps")))
    }

    @Test
    fun emptyQueryMatchesEverything() {
        Assert.assertTrue(scorer.isMatch(scorer.score("", primaryFields = listOf("calendar"))))
    }
}
