package de.mm20.launcher2.database

import androidx.room.*
import de.mm20.launcher2.database.entities.CurrencyEntity

@Dao
interface CurrencyDao {

    @Query("SELECT value FROM Currency WHERE symbol = :symbol")
    fun getExchangeRate(symbol: String) : Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(currency: CurrencyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(currencies: List<CurrencyEntity>)

    @Query("SELECT * FROM Currency WHERE symbol = :symbol")
    fun getCurrency(symbol: String) : CurrencyEntity?

    @Query("SELECT * FROM Currency WHERE symbol IN (:symbols)")
    fun getCurrencies(symbols: List<String>) : List<CurrencyEntity>

    @Query("SELECT * FROM Currency")
    fun getAllCurrencies() : List<CurrencyEntity>

    @Transaction
    fun exists(symbol: String): Boolean {
        return getCurrency(symbol) != null
    }

    @Query("SELECT lastUpdate FROM Currency WHERE symbol = :symbol")
    fun getLastUpdate(symbol: String) : Long
}