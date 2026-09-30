package de.mm20.launcher2.search

import org.junit.Assert
import org.junit.Test

class ResultScoreTest {

    private fun score(query: String, term: String): Float =
        ResultScore.from(query, primaryFields = listOf(term)).score

    private fun matches(query: String, term: String): Boolean =
        score(query, term) >= ResultScore.MatchThreshold

    @Test
    fun exactMatchScoresOne() {
        Assert.assertEquals(1f, score("chrome", "chrome"), 0f)
    }

    @Test
    fun emptyQueryMatchesEverything() {
        Assert.assertEquals(1f, score("", "chrome"), 0f)
    }

    @Test
    fun matchesSubstrings() {
        Assert.assertTrue(matches("chr", "chrome"))
        Assert.assertTrue(matches("hrom", "chrome"))
        Assert.assertTrue(matches("music", "yt music"))
    }

    @Test
    fun matchesAbbreviations() {
        Assert.assertTrue(matches("ytm", "yt music"))
        Assert.assertTrue(matches("pstr", "play store"))
        Assert.assertTrue(matches("ktso", "kvaesitso"))
        Assert.assertTrue(matches("ggl", "google"))
        Assert.assertTrue(matches("cnt", "contacts"))
    }

    @Test
    fun toleratesTypos() {
        // Neither query is a subsequence of its label, so these can only match by similarity.
        Assert.assertTrue(matches("settimgs", "settings"))
        Assert.assertTrue(matches("chorme", "chrome"))
    }

    @Test
    fun toleratesDroppedCharacters() {
        Assert.assertTrue(matches("chrme", "chrome"))
        Assert.assertTrue(matches("settngs", "settings"))
    }

    @Test
    fun rejectsUnrelatedLabels() {
        Assert.assertFalse(matches("chrome", "calendar"))
        Assert.assertFalse(matches("maps", "settings"))
    }

    @Test
    fun noTypoCorrectionForShortQueries() {
        // Similar enough by Jaro-Winkler, but two characters are too little to guess a typo from.
        Assert.assertFalse(matches("ab", "a"))
    }

    @Test
    fun ranksPrefixOverScatteredOverTypo() {
        // Only the matched window is scored, so an exact match and a prefix match tie, as they
        // did when every substring match saturated to 1. Usage weights decide between them.
        val exact = score("chrome", "chrome")
        val prefix = score("chrome", "chrome beta")
        val scattered = score("chrome", "cash register over media")
        val typo = score("chorme", "chrome")
        Assert.assertEquals(exact, prefix, 0f)
        Assert.assertTrue("prefix $prefix > scattered $scattered", prefix > scattered)
        Assert.assertTrue("scattered $scattered > typo $typo", scattered > typo)
    }

    @Test
    fun literalMatchOutranksCloseTypo() {
        // A near-perfect typo still ranks below a loose literal match.
        Assert.assertTrue(score("settimgs", "settings") < score("stg", "settings"))
    }

    @Test
    fun primaryFieldOutranksIdenticalSecondaryField() {
        val primary = ResultScore.from("chrome", primaryFields = listOf("chrome"))
        val secondary = ResultScore.from("chrome", secondaryFields = listOf("chrome"))
        Assert.assertTrue(primary > secondary)
    }

    @Test
    fun noFieldsIsNotAMatch() {
        Assert.assertEquals(ResultScore.Zero, ResultScore.from("chrome"))
    }
}
