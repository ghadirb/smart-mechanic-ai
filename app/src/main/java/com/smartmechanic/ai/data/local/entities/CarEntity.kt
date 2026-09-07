package com.smartmechanic.ai.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cars")
data class CarEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val model: String,
    val trim: String?,
    val year: Int,
    val engineType: String?,
    val engineDisplacement: String?,
    val fuelType: String?,
    val transmission: String?,
    val currentMileageKm: Int,
    val extraNotes: String?
)
