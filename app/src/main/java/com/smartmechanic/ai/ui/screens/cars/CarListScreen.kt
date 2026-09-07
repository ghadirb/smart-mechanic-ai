package com.smartmechanic.ai.ui.screens.cars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartmechanic.ai.data.model.Car

@Composable
fun CarListScreen(
    viewModel: CarListViewModel,
    onAddCar: () -> Unit,
    onBack: () -> Unit
) {
    val cars by viewModel.cars.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("خودروهای من") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCar) { Icon(Icons.Filled.Add, contentDescription = "افزودن خودرو") }
        }
    ) { padding ->
        if (cars.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("هنوز خودرویی ثبت نکرده‌اید. با دکمه + یک خودرو اضافه کنید.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
                items(cars, key = { it.id }) { car ->
                    CarRow(car = car, onDelete = { viewModel.deleteCar(car) })
                }
            }
        }
    }
}

@Composable
private fun CarRow(car: Car, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${car.brand} ${car.model}", style = MaterialTheme.typography.titleMedium)
                Text("سال ${car.year} — کارکرد ${car.currentMileageKm} کیلومتر", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف")
            }
        }
    }
}
