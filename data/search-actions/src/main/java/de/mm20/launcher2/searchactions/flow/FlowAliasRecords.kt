package de.mm20.launcher2.searchactions.flow

import de.mm20.launcher2.database.entities.SearchActionEntity

/** Operations retain raw legacy/unknown records, including their backup fields. */
object FlowAliasRecords {
    fun upsert(records: List<SearchActionEntity>, alias: FlowAlias): Pair<List<SearchActionEntity>, FlowAliasError?> {
        val error = FlowAliases.validate(alias, records.filter { it.type == "flow_alias" }.mapNotNull { FlowAliases.decode(it.data) })
        if (error != null) return records to error
        val entity = SearchActionEntity(position = records.size, type = "flow_alias", data = FlowAliases.encode(alias))
        var replaced = false
        val updated = records.mapNotNull { old ->
            if (old.type == "flow_alias" && FlowAliases.decode(old.data)?.id == alias.id) {
                if (replaced) null else entity.copy(position = old.position).also { replaced = true }
            } else old
        }
        return (if (replaced) updated else updated + entity).mapIndexed { i, row -> row.copy(position = i) } to null
    }

    fun remove(records: List<SearchActionEntity>, id: String): List<SearchActionEntity> = records
        .filterNot { it.type == "flow_alias" && FlowAliases.decode(it.data)?.id == id }
        .mapIndexed { i, row -> row.copy(position = i) }
}
