package com.smartmechanic.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartmechanic.ai.data.model.InputType
import com.smartmechanic.ai.ui.AppViewModel
import com.smartmechanic.ai.ui.navigation.Routes
import com.smartmechanic.ai.ui.screens.cars.AddCarScreen
import com.smartmechanic.ai.ui.screens.cars.AddCarViewModel
import com.smartmechanic.ai.ui.screens.cars.CarListScreen
import com.smartmechanic.ai.ui.screens.cars.CarListViewModel
import com.smartmechanic.ai.ui.screens.diagnosis.MediaDiagnosisViewModel
import com.smartmechanic.ai.ui.screens.diagnosis.audio.AudioDiagnosisScreen
import com.smartmechanic.ai.ui.screens.diagnosis.image.ImageDiagnosisScreen
import com.smartmechanic.ai.ui.screens.diagnosis.text.TextDiagnosisScreen
import com.smartmechanic.ai.ui.screens.diagnosis.text.TextDiagnosisViewModel
import com.smartmechanic.ai.ui.screens.diagnosis.video.VideoDiagnosisScreen
import com.smartmechanic.ai.ui.screens.history.HistoryScreen
import com.smartmechanic.ai.ui.screens.history.HistoryViewModel
import com.smartmechanic.ai.ui.screens.home.HomeScreen
import com.smartmechanic.ai.ui.screens.result.DiagnosisResultScreen
import com.smartmechanic.ai.ui.theme.SmartMechanicAITheme
import com.smartmechanic.ai.util.MediaFileFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SmartMechanicApp
        // پاک‌سازی فایل‌های موقت رسانه‌ای قدیمی در هر اجرای برنامه (حریم خصوصی: بخش ۱۶)
        MediaFileFactory.clearOldCacheFiles(this)

        setContent {
            SmartMechanicAITheme {
                val navController = rememberNavController()
                val appViewModel: AppViewModel = viewModel(factory = AppViewModel.Factory(app.carRepository))
                val selectedCar by appViewModel.selectedCar.collectAsState()

                NavHost(navController = navController, startDestination = Routes.HOME) {

                    composable(Routes.HOME) {
                        HomeScreen(onNavigate = { navController.navigate(it) })
                    }

                    composable(Routes.CARS) {
                        val vm: CarListViewModel = viewModel(factory = CarListViewModel.Factory(app.carRepository))
                        CarListScreen(
                            viewModel = vm,
                            onAddCar = { navController.navigate(Routes.ADD_CAR) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.ADD_CAR) {
                        val vm: AddCarViewModel = viewModel(factory = AddCarViewModel.Factory(app.carRepository))
                        AddCarScreen(viewModel = vm, onSaved = { navController.popBackStack() })
                    }

                    composable(Routes.DIAGNOSE_TEXT) {
                        val vm: TextDiagnosisViewModel = viewModel(
                            factory = TextDiagnosisViewModel.Factory(app.aiService, app.diagnosisRepository)
                        )
                        TextDiagnosisScreen(
                            viewModel = vm,
                            selectedCar = selectedCar,
                            onNavigateToResult = { navController.navigate(Routes.RESULT) }
                        )
                    }

                    composable(Routes.DIAGNOSE_IMAGE) {
                        val vm: MediaDiagnosisViewModel = viewModel(
                            factory = MediaDiagnosisViewModel.Factory(app.aiService, app.diagnosisRepository, InputType.IMAGE)
                        )
                        ImageDiagnosisScreen(
                            viewModel = vm,
                            selectedCar = selectedCar,
                            onNavigateToResult = { navController.navigate(Routes.RESULT) }
                        )
                    }

                    composable(Routes.DIAGNOSE_AUDIO) {
                        val vm: MediaDiagnosisViewModel = viewModel(
                            factory = MediaDiagnosisViewModel.Factory(app.aiService, app.diagnosisRepository, InputType.AUDIO)
                        )
                        AudioDiagnosisScreen(
                            viewModel = vm,
                            selectedCar = selectedCar,
                            onNavigateToResult = { navController.navigate(Routes.RESULT) }
                        )
                    }

                    composable(Routes.DIAGNOSE_VIDEO) {
                        val vm: MediaDiagnosisViewModel = viewModel(
                            factory = MediaDiagnosisViewModel.Factory(app.aiService, app.diagnosisRepository, InputType.VIDEO)
                        )
                        VideoDiagnosisScreen(
                            viewModel = vm,
                            selectedCar = selectedCar,
                            onNavigateToResult = { navController.navigate(Routes.RESULT) }
                        )
                    }

                    composable(Routes.RESULT) {
                        DiagnosisResultScreen()
                    }

                    composable(Routes.HISTORY) {
                        val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(app.diagnosisRepository))
                        HistoryScreen(viewModel = vm)
                    }
                }
            }
        }
    }
}
