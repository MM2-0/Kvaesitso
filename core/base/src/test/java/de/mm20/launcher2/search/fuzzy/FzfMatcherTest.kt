package de.mm20.launcher2.search.fuzzy

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A representative set of app labels, already lowercased the way [de.mm20.launcher2.search
 * .StringNormalizer] would leave them before scoring.
 */
internal val AppLabels = listOf(
    "calendar", "camera", "chrome", "clock", "contacts",
    "drive", "files", "gemini", "glasses", "gmail",
    "google", "kvaesitso", "maps", "messages", "phone",
    "photos", "play store", "safety", "settings",
    "youtube", "yt music",
)

/** Checks which queries match at all. */
@RunWith(Parameterized::class)
class FzfMatcherMatchesTest(
    private val query: String,
    private val target: String,
    private val expected: Boolean,
) {

    @Test
    fun matches() {
        Assert.assertEquals(
            "\"$query\" vs \"$target\"",
            expected,
            FzfMatcher.match(query, target) != null,
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} in {1} = {2}")
        fun data(): List<Array<Any>> = listOf(
            // Abbreviations: the point of the whole exercise.
            arrayOf("ytm", "yt music", true),
            arrayOf("pstr", "play store", true),
            arrayOf("ktso", "kvaesitso", true),
            arrayOf("ggl", "google", true),
            arrayOf("cnt", "contacts", true),
            arrayOf("msg", "messages", true),
            arrayOf("cal", "calendar", true),
            // A dropped character still leaves a subsequence, so it matches.
            arrayOf("settngs", "settings", true),
            // A wrong or transposed character does not. This is the tradeoff against
            // Jaro-Winkler, which would still match both of these.
            arrayOf("settimgs", "settings", false),
            arrayOf("mpas", "maps", false),
            // Characters the target simply does not have.
            arrayOf("gems", "gemini", false),
            arrayOf("chromium", "chrome", false),
            // Degenerate inputs.
            arrayOf("", "calendar", true),
            arrayOf("calendar", "clock", false),
            arrayOf("c", "calendar", true),
            arrayOf("c", "", false),
            arrayOf("", "", true),
        )
    }
}

/**
 * Checks the ordering the score induces. Absolute scores are an implementation detail that will
 * shift if the bonus constants are tuned; the relative ordering is the contract.
 */
class FzfMatcherScoreTest {

    private fun score(query: String, target: String): Float =
        FzfMatcher.normalizedScore(query, target)

    @Test
    fun exactMatchScoresOne() {
        Assert.assertEquals(1f, score("calendar", "calendar"), 0f)
    }

    @Test
    fun contiguousBeatsScattered() {
        // "yt" is the whole first word of "yt music", but straddles "youtube".
        Assert.assertTrue(score("yt", "yt music") > score("yt", "youtube"))
    }

    @Test
    fun prefixBeatsMidWord() {
        // "ps" opens "play store", but is buried at the end of "maps".
        Assert.assertTrue(score("ps", "play store") > score("ps", "maps"))
    }

    @Test
    fun tighterClusterBeatsWiderGaps() {
        Assert.assertTrue(score("cl", "clock") > score("cl", "calendar"))
        Assert.assertTrue(score("gm", "gmail") > score("gm", "gemini"))
        Assert.assertTrue(score("dr", "drive") > score("dr", "calendar"))
    }

    @Test
    fun wordBoundaryBeatsMidWord() {
        // Both letters of "ps" start a word in "play store"; in "photos" the s is mid-word.
        Assert.assertTrue(score("ps", "play store") > score("ps", "photos"))
    }

    @Test
    fun exactPrefixBeatsSkippedLetters() {
        Assert.assertTrue(score("set", "settings") > score("set", "safety"))
    }

    @Test
    fun nonMatchScoresZero() {
        Assert.assertEquals(0f, score("gems", "gemini"), 0f)
    }

    @Test
    fun longerQueryThanTargetNeverMatches() {
        Assert.assertEquals(0f, score("calendar", "clock"), 0f)
    }

    @Test
    fun scoresStayWithinUnitRange() {
        val queries = listOf("c", "cl", "ytm", "pstr", "ggl", "set", "yt music", "a b", "1", "-")
        for (query in queries) {
            for (target in AppLabels + listOf("", "x", "app 2")) {
                val score = score(query, target)
                Assert.assertTrue("\"$query\" vs \"$target\" scored $score", score in 0f..1f)
            }
        }
    }

    @Test
    fun rankingIsStableAcrossTheWholeAppList() {
        Assert.assertEquals(listOf("yt music", "youtube", "play store"), rank("yt"))
        Assert.assertEquals(listOf("play store", "photos", "maps"), rank("ps"))
        Assert.assertEquals(listOf("clock", "calendar"), rank("cl"))
        Assert.assertEquals(listOf("gmail", "gemini"), rank("gm"))
        Assert.assertEquals(listOf("settings", "safety"), rank("set"))
        // Abbreviations that are unambiguous return exactly one app.
        Assert.assertEquals(listOf("yt music"), rank("ytm"))
        Assert.assertEquals(listOf("play store"), rank("pstr"))
        Assert.assertEquals(listOf("kvaesitso"), rank("ktso"))
    }

    private fun rank(query: String): List<String> =
        AppLabels.map { it to score(query, it) }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .map { it.first }

    @Test
    fun positionsAreOnlyRecordedOnRequest() {
        Assert.assertNull(FzfMatcher.match("ytm", "yt music")?.positions)
        val positions = FzfMatcher.match("ytm", "yt music", withPositions = true)?.positions
        // y·t·_·m -> the m of "music", not a letter of "yt".
        Assert.assertArrayEquals(intArrayOf(0, 1, 3), positions)
    }
}
