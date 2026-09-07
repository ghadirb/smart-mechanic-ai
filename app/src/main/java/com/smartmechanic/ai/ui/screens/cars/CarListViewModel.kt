package com.smartmechanic.ai.ui.screens.cars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.data.repository.CarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CarListViewModel(private val repository: CarRepository) : ViewModel() {

    val cars: StateFlow<List<Car>> = repository.observeCars()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteCar(car: Car) {
        viewModelScope.launch { repository.deleteCar(car) }
    }

    class Factory(private val repository: CarRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CarListViewModel(repository) as T
    }
}
