package de.mm20.launcher2.currencies

import de.mm20.launcher2.database.entities.CurrencyEntity

data class Currency(
        val symbol: String,
        val value: Double,
        val lastUpdate: Long
) {
    constructor(entity: CurrencyEntity) : this(
            symbol = entity.symbol,
            value = entity.value,
            lastUpdate = entity.lastUpdate
    )

    fun toDatabaseEntity(): CurrencyEntity {
        return CurrencyEntity(symbol, value, lastUpdate)
    }
}