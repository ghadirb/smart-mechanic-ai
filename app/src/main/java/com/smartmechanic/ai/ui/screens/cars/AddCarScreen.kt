package com.smartmechanic.ai.ui.screens.cars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddCarScreen(viewModel: AddCarViewModel, onSaved: () -> Unit) {
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var trim by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var engineType by remember { mutableStateOf("") }
    var engineDisplacement by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("") }
    var transmission by remember { mutableStateOf("") }
    var mileage by remember { mutableStateOf("") }
    var extraNotes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("ثبت خودرو") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("برند (مثلاً سایپا)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("مدل (مثلاً تیبا)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = trim, onValueChange = { trim = it }, label = { Text("تیپ") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = year, onValueChange = { year = it.filter { c -> c.isDigit() } }, label = { Text("سال ساخت") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = engineType, onValueChange = { engineType = it }, label = { Text("نوع موتور") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = engineDisplacement, onValueChange = { engineDisplacement = it }, label = { Text("حجم موتور (در صورت اطلاع)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = fuelType, onValueChange = { fuelType = it }, label = { Text("نوع سوخت") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = transmission, onValueChange = { transmission = it }, label = { Text("گیربکس") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = mileage, onValueChange = { mileage = it.filter { c -> c.isDigit() } }, label = { Text("کیلومتر فعلی") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = extraNotes, onValueChange = { extraNotes = it }, label = { Text("توضیحات اضافی") }, modifier = Modifier.fillMaxWidth())

            errorMsg?.let { Text(it, color = androidx.compose.ui.graphics.Color.Red) }

            Button(
                onClick = {
                    val yearInt = year.toIntOrNull()
                    val mileageInt = mileage.toIntOrNull()
                    if (brand.isBlank() || model.isBlank() || yearInt == null || mileageInt == null) {
                        errorMsg = "لطفاً حداقل برند، مدل، سال ساخت و کیلومتر فعلی را به‌درستی وارد کنید."
                        return@Button
                    }
                    viewModel.saveCar(
                        brand = brand, model = model, trim = trim.ifBlank { null },
                        year = yearInt, engineType = engineType.ifBlank { null },
                        engineDisplacement = engineDisplacement.ifBlank { null },
                        fuelType = fuelType.ifBlank { null }, transmission = transmission.ifBlank { null },
                        currentMileageKm = mileageInt, extraNotes = extraNotes.ifBlank { null },
                        onSaved = onSaved
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ذخیره خودرو")
            }
        }
    }
}
