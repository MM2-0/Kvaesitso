package de.mm20.launcher2.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "Currency")
data class CurrencyEntity(
        @PrimaryKey val symbol: String,
        val value: Double,
        val lastUpdate: Long
)