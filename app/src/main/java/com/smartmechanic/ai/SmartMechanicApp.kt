package com.smartmechanic.ai

import android.app.Application
import com.smartmechanic.ai.auth.FirebaseSession
import com.smartmechanic.ai.config.AIConfig
import com.smartmechanic.ai.data.local.AppDatabase
import com.smartmechanic.ai.data.repository.AIService
import com.smartmechanic.ai.data.repository.CarRepository
import com.smartmechanic.ai.data.repository.DiagnosisRepository
import com.smartmechanic.ai.data.repository.GeminiAIService
import com.smartmechanic.ai.data.repository.ProxyAIService

/**
 * Application class + یک Service Locator ساده (بدون Hilt/Dagger برای سادگی MVP).
 * در نسخه‌های بعدی می‌توان به‌راحتی به Hilt مهاجرت کرد چون همه‌چیز پشت Interface است.
 */
class SmartMechanicApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var carRepository: CarRepository
        private set
    lateinit var diagnosisRepository: DiagnosisRepository
        private set
    lateinit var aiService: AIService
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseSession.ensureSignedIn()
        database = AppDatabase.getInstance(this)
        carRepository = CarRepository(database.carDao())
        diagnosisRepository = DiagnosisRepository(database.diagnosisDao())
        aiService = if (AIConfig.isProxyConfigured()) {
            ProxyAIService(applicationContext)
        } else {
            GeminiAIService(applicationContext)
        }
    }
}
