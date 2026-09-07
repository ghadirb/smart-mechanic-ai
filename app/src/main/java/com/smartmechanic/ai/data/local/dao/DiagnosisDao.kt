package com.smartmechanic.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartmechanic.ai.data.local.entities.DiagnosisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosisDao {
    @Query("SELECT * FROM diagnosis_records ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<DiagnosisEntity>>

    @Query("SELECT * FROM diagnosis_records WHERE carId = :carId ORDER BY timestampMillis DESC")
    fun observeForCar(carId: Long): Flow<List<DiagnosisEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DiagnosisEntity): Long

    @Delete
    suspend fun delete(record: DiagnosisEntity)

    @Query("DELETE FROM diagnosis_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
