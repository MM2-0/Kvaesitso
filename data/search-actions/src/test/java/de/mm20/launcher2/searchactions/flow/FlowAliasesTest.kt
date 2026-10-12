package de.mm20.launcher2.searchactions.flow

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class FlowAliasesTest {
    private val alias = FlowAlias("one", "vlak", "app://train:Main")

    @Test fun matchingIsExactAndHasNoPrefixFallback() {
        assertTrue(FlowAliases.matches(alias, " VLAK "))
        for (query in listOf("", " ", "vla", "vlaky", "vlak dnes", "dnes vlak")) assertFalse(query, FlowAliases.matches(alias, query))
    }

    @Test fun unicodeWhitespaceAndCompatibilityCharactersAreNormalized() {
        assertEquals("vlak isic", FlowAliases.normalize("  ＶＬＡＫ\u00a0  ISIC  "))
        val accent = alias.copy(alias = "vlák")
        assertTrue(FlowAliases.matches(accent, "VLA\u0301K"))
        assertFalse(FlowAliases.matches(accent, "vlak"))
    }

    @Test fun normalizationIsIdempotentAndLocaleIndependent() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            for (value in listOf(" ISIC ", "\u00a0ＶＬＡＫ", "VLA\u0301K", "a  b")) {
                val normalized = FlowAliases.normalize(value)
                assertEquals(normalized, FlowAliases.normalize(normalized))
            }
            assertEquals("isic", FlowAliases.normalize("ISIC"))
        } finally { Locale.setDefault(original) }
    }

    @Test fun inputLimitsCountCodePointsAndRejectControls() {
        assertEquals(FlowAliasError.Empty, FlowAliases.validate(alias.copy(alias = "\u00a0 ")))
        for (control in listOf("\n", "\t", "\u0000", "\u007f")) assertEquals(FlowAliasError.ControlCharacter, FlowAliases.validate(alias.copy(alias = "vlak$control")))
        assertNull(FlowAliases.validate(alias.copy(alias = "🚆".repeat(64))))
        assertEquals(FlowAliasError.TooLong, FlowAliases.validate(alias.copy(alias = "🚆".repeat(65))))
        assertEquals(FlowAliasError.InvalidTarget, FlowAliases.validate(alias.copy(targetKey = "intent://anything")))
    }

    @Test fun duplicateAliasesAreCanonicalAndEditingExcludesOwnId() {
        assertNull(FlowAliases.validate(alias, listOf(alias)))
        assertEquals(FlowAliasError.Duplicate, FlowAliases.validate(alias.copy(id = "two", alias = " ＶＬＡＫ "), listOf(alias)))
    }

    @Test fun payloadRoundTripsOriginalSpellingAndIdentity() {
        val original = alias.copy(alias = " VláK 🚆 ")
        assertEquals(original, FlowAliases.decode(FlowAliases.encode(original)))
    }

    @Test fun malformedAndUnknownPayloadsFailClosed() {
        for (payload in listOf(null, "", "[]", "{}", "broken", "{\"version\":2}",
            "{\"version\":1,\"id\":\"one\",\"alias\":[],\"targetKey\":\"app://train:Main\"}",
            FlowAliases.encode(alias).replace("\"version\":1", "\"version\":\"1\""),
            FlowAliases.encode(alias).replace("app://train:Main", "intent://evil"))) {
            assertNull(payload, FlowAliases.decode(payload))
        }
    }
}
