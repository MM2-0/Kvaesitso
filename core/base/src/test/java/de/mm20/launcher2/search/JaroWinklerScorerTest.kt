package de.mm20.launcher2.search

import org.junit.Assert
import org.junit.Test

/**
 * Pins the behaviour of the default scorer, which is what users see with fuzzy matching off.
 */
class JaroWinklerScorerTest {

    private val scorer = JaroWinklerScorer()

    private fun matches(query: String, term: String): Boolean =
        scorer.isMatch(scorer.score(query, primaryFields = listOf(term)))

    @Test
    fun exactMatchScoresOne() {
        Assert.assertEquals(1f, scorer.score("chrome", primaryFields = listOf("chrome")).score, 0f)
    }

    @Test
    fun anySubstringSaturatesToOne() {
        // The substring bonus is 0.8, so any substring match lands at the 0..1 ceiling.
        Assert.assertEquals(1f, scorer.score("hrom", primaryFields = listOf("chrome")).score, 0f)
    }

    @Test
    fun toleratesTypos() {
        // The capability fzf trades away: a wrong or dropped character still matches here.
        Assert.assertTrue(matches("chrme", "chrome"))
        Assert.assertTrue(matches("settngs", "settings"))
    }

    @Test
    fun missesMostAbbreviations() {
        // The motivation for the fuzzy scorer: these are subsequences, but too dissimilar
        // as whole strings to clear the threshold.
        Assert.assertFalse(matches("ytm", "yt music"))
        Assert.assertFalse(matches("pstr", "play store"))
        Assert.assertFalse(matches("ktso", "kvaesitso"))
    }

    @Test
    fun catchesShortAbbreviationsOfShortLabels() {
        // Not every abbreviation is missed -- when query and label are close in length the
        // similarity alone can clear the threshold.
        Assert.assertTrue(matches("ggl", "google"))
        Assert.assertTrue(matches("cnt", "contacts"))
    }

    @Test
    fun primaryFieldOutranksIdenticalSecondaryField() {
        val primary = scorer.score("chrome", primaryFields = listOf("chrome"))
        val secondary = scorer.score("chrome", secondaryFields = listOf("chrome"))
        Assert.assertTrue(primary.score > secondary.score)
    }

    @Test
    fun noFieldsIsNotAMatch() {
        val score = scorer.score("chrome")
        Assert.assertEquals(ResultScore.Zero.score, score.score, 0f)
        Assert.assertFalse(scorer.isMatch(score))
    }
}
