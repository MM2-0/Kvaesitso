package de.mm20.launcher2.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import de.mm20.launcher2.database.entities.ColorsEntity
import de.mm20.launcher2.database.entities.ShapesEntity
import de.mm20.launcher2.database.entities.TransparenciesEntity
import de.mm20.launcher2.database.entities.TypographyEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ThemeDao {
    @Query("SELECT * FROM Theme")
    fun getAllColors(): Flow<List<ColorsEntity>>

    @Query("SELECT * FROM Shapes")
    fun getAllShapes(): Flow<List<ShapesEntity>>

    @Query("SELECT * FROM Transparencies")
    fun getAllTransparencies(): Flow<List<TransparenciesEntity>>

    @Query("SELECT * FROM Typography")
    fun getAllTypographies(): Flow<List<TypographyEntity>>

    @Query("SELECT * FROM Theme WHERE id = :id LIMIT 1")
    fun getColors(id: UUID): Flow<ColorsEntity?>

    @Query("SELECT * FROM Shapes WHERE id = :id LIMIT 1")
    fun getShapes(id: UUID): Flow<ShapesEntity?>

    @Query("SELECT * FROM Transparencies WHERE id = :id LIMIT 1")
    fun getTransparencies(id: UUID): Flow<TransparenciesEntity?>

    @Query("SELECT * FROM Typography WHERE id = :id LIMIT 1")
    fun getTypography(id: UUID): Flow<TypographyEntity?>

    @Insert
    suspend fun insertColors(colors: ColorsEntity)

    @Insert
    suspend fun insertShapes(shapes: ShapesEntity)

    @Insert
    suspend fun insertTransparencies(transparencies: TransparenciesEntity)

    @Insert
    suspend fun insertTypography(typography: TypographyEntity)

    @Update
    suspend fun updateColors(colors: ColorsEntity)
    
    @Update
    suspend fun updateShapes(shapes: ShapesEntity)

    @Update
    suspend fun updateTransparencies(transparencies: TransparenciesEntity)

    @Update
    suspend fun updateTypography(typography: TypographyEntity)

    @Query("DELETE FROM Theme WHERE id = :id")
    suspend fun deleteColors(id: UUID)

    @Query("DELETE FROM Shapes WHERE id = :id")
    suspend fun deleteShapes(id: UUID)

    @Query("DELETE FROM Transparencies WHERE id = :id")
    suspend fun deleteTransparencies(id: UUID)

    @Query("DELETE FROM Typography WHERE id = :id")
    suspend fun deleteTypography(id: UUID)

    @Query("DELETE FROM Theme")
    suspend fun deleteAllColors()

    @Query("DELETE FROM Shapes")
    suspend fun deleteAllShapes()

    @Query("DELETE FROM Transparencies")
    suspend fun deleteAllTransparencies()

    @Query("DELETE FROM Typography")
    suspend fun deleteAllTypographies()

    @Insert
    fun insertAllColors(colors: List<ColorsEntity>)
    
    @Insert
    fun insertAllShapes(shapes: List<ShapesEntity>)

    @Insert
    fun insertAllTransparencies(transparencies: List<TransparenciesEntity>)

    @Insert
    fun insertAllTypographies(typography: List<TypographyEntity>)
}