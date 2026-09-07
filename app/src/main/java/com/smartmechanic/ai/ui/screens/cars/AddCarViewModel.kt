package com.smartmechanic.ai.ui.screens.cars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.data.repository.CarRepository
import kotlinx.coroutines.launch

class AddCarViewModel(private val repository: CarRepository) : ViewModel() {

    fun saveCar(
        brand: String,
        model: String,
        trim: String?,
        year: Int,
        engineType: String?,
        engineDisplacement: String?,
        fuelType: String?,
        transmission: String?,
        currentMileageKm: Int,
        extraNotes: String?,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            repository.addOrUpdateCar(
                Car(
                    brand = brand, model = model, trim = trim, year = year,
                    engineType = engineType, engineDisplacement = engineDisplacement,
                    fuelType = fuelType, transmission = transmission,
                    currentMileageKm = currentMileageKm, extraNotes = extraNotes
                )
            )
            onSaved()
        }
    }

    class Factory(private val repository: CarRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AddCarViewModel(repository) as T
    }
}
