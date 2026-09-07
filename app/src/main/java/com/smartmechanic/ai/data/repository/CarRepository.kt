package com.smartmechanic.ai.data.repository

import com.smartmechanic.ai.data.local.dao.CarDao
import com.smartmechanic.ai.data.local.entities.CarEntity
import com.smartmechanic.ai.data.model.Car
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CarRepository(private val carDao: CarDao) {

    fun observeCars(): Flow<List<Car>> =
        carDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getCar(id: Long): Car? = carDao.getById(id)?.toDomain()

    suspend fun addOrUpdateCar(car: Car): Long = carDao.insert(car.toEntity())

    suspend fun deleteCar(car: Car) = carDao.delete(car.toEntity())

    private fun CarEntity.toDomain() = Car(
        id = id, brand = brand, model = model, trim = trim, year = year,
        engineType = engineType, engineDisplacement = engineDisplacement,
        fuelType = fuelType, transmission = transmission,
        currentMileageKm = currentMileageKm, extraNotes = extraNotes
    )

    private fun Car.toEntity() = CarEntity(
        id = id, brand = brand, model = model, trim = trim, year = year,
        engineType = engineType, engineDisplacement = engineDisplacement,
        fuelType = fuelType, transmission = transmission,
        currentMileageKm = currentMileageKm, extraNotes = extraNotes
    )
}
