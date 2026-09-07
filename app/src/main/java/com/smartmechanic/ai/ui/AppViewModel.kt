package com.smartmechanic.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.data.repository.CarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** ViewModel مشترک در سطح اپ برای نگهداری خودروی انتخاب‌شده بین صفحات مختلف تشخیص. */
class AppViewModel(private val carRepository: CarRepository) : ViewModel() {

    private val _cars = MutableStateFlow<List<Car>>(emptyList())
    val cars: StateFlow<List<Car>> = _cars

    private val _selectedCar = MutableStateFlow<Car?>(null)
    val selectedCar: StateFlow<Car?> = _selectedCar

    init {
        viewModelScope.launch {
            carRepository.observeCars().collect { list ->
                _cars.value = list
                if (_selectedCar.value == null && list.isNotEmpty()) {
                    _selectedCar.value = list.first()
                }
            }
        }
    }

    fun selectCar(car: Car?) {
        _selectedCar.value = car
    }

    class Factory(private val carRepository: CarRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(carRepository) as T
    }
}
