package com.smartmechanic.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartmechanic.ai.data.local.dao.CarDao
import com.smartmechanic.ai.data.local.dao.DiagnosisDao
import com.smartmechanic.ai.data.local.entities.CarEntity
import com.smartmechanic.ai.data.local.entities.DiagnosisEntity

@Database(
    entities = [CarEntity::class, DiagnosisEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun diagnosisDao(): DiagnosisDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_mechanic_ai.db"
                ).build().also { INSTANCE = it }
            }
    }
}
