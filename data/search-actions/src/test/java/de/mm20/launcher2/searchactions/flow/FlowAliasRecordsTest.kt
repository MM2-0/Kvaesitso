package de.mm20.launcher2.searchactions.flow

import de.mm20.launcher2.database.entities.SearchActionEntity
import org.junit.Assert.*
import org.junit.Test

class FlowAliasRecordsTest {
    private val legacy = SearchActionEntity(0, "url", "https://example.test?q=\${1}", "Search", options = "{\"encoding\":0}")
    private val unknown = SearchActionEntity(1, "flow_alias", "{\"version\":99}", "future", customIcon = "preserved")
    private val alias = FlowAlias("one", "vlak", "app://train:Main")

    @Test fun createEditDeletePreserveExistingAndUnknownBackupFields() {
        val original = listOf(legacy, unknown)
        val (created, error) = FlowAliasRecords.upsert(original, alias)
        assertNull(error)
        assertEquals(original, created.take(2))
        // Reconstruct from persisted data to represent a fresh reader/backup payload round-trip.
        assertEquals(alias, FlowAliases.decode(created.last().copy().data))
        val edited = alias.copy(alias = " ISIC ", targetKey = "app://card:Main")
        val (updated, updateError) = FlowAliasRecords.upsert(created, edited)
        assertNull(updateError)
        assertEquals(3, updated.size)
        assertEquals(original, updated.take(2))
        assertEquals(edited, FlowAliases.decode(updated.last().data))
        assertEquals(original, FlowAliasRecords.remove(updated, alias.id))
    }

    @Test fun duplicateOrInvalidInputLeavesRecordsUntouched() {
        val (created, _) = FlowAliasRecords.upsert(listOf(legacy), alias)
        val (unchanged, error) = FlowAliasRecords.upsert(created, alias.copy(id = "two", alias = " ＶＬＡＫ "))
        assertEquals(FlowAliasError.Duplicate, error)
        assertEquals(created, unchanged)
        val (invalid, invalidError) = FlowAliasRecords.upsert(created, alias.copy(alias = ""))
        assertEquals(FlowAliasError.Empty, invalidError)
        assertEquals(created, invalid)
    }

    @Test fun removalKeepsOrderAndAllowsReusingDeletedAlias() {
        val (first, _) = FlowAliasRecords.upsert(listOf(legacy), alias)
        val secondAlias = alias.copy(id = "two", alias = "isic", targetKey = "app://card:Main")
        val (second, _) = FlowAliasRecords.upsert(first, secondAlias)
        val removed = FlowAliasRecords.remove(second, alias.id)
        assertEquals(listOf(0, 1), removed.map { it.position })
        assertEquals(legacy, removed.first())
        assertEquals(secondAlias, FlowAliases.decode(removed.last().data))
        assertNull(FlowAliasRecords.upsert(removed, alias.copy(id = "three")).second)
    }
}
