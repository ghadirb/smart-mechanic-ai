import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// -----------------------------------------------------------------------
// خواندن امن API Key:
// اولویت ۱: متغیر محیطی GEMINI_API_KEY (مناسب برای CI/CD)
// اولویت ۲: local.properties (مناسب برای توسعه لوکال - در .gitignore است)
// در هیچ حالتی مقدار کلید داخل کد یا فایل‌های Git-track شده نوشته نمی‌شود.
// -----------------------------------------------------------------------
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        load(FileInputStream(localFile))
    }
}

val geminiApiKey: String = System.getenv("GEMINI_API_KEY")
    ?: localProperties.getProperty("GEMINI_API_KEY", "")

val backendBaseUrl: String = System.getenv("BACKEND_BASE_URL")
    ?: localProperties.getProperty("BACKEND_BASE_URL", "https://generativelanguage.googleapis.com/")

// آدرس Web App گوگل اپس‌اسکریپت (در صورت استفاده از پروکسی رایگان gapgpt.app به‌جای Gemini مستقیم).
// اگر خالی بماند، برنامه مستقیماً و با geminiApiKey با Gemini صحبت می‌کند.
val proxyUrl: String = System.getenv("PROXY_URL")
    ?: localProperties.getProperty("PROXY_URL", "")

// رمز اختیاری اپ که در Script Properties اپس‌اسکریپت هم تنظیم می‌شود (APP_SECRET)
// تا فقط همین اپ بتواند از Web App عمومی شما استفاده کند.
val appSecret: String = System.getenv("APP_SECRET")
    ?: localProperties.getProperty("APP_SECRET", "")

android {
    namespace = "com.smartmechanic.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.smartmechanic.ai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-mvp"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // کلید فقط داخل BuildConfig (در حافظه اپ در زمان اجرا) قرار می‌گیرد،
        // نه به صورت رشته‌ی ثابت قابل مشاهده در سورس یا منابع.
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
        buildConfigField("String", "GEMINI_MODEL_NAME", "\"gemini-3.6-flash\"")
        buildConfigField("String", "PROXY_URL", "\"$proxyUrl\"")
        buildConfigField("String", "APP_SECRET", "\"$appSecret\"")
    }

    signingConfigs {
        // در نسخه انتشار واقعی، امضای اپ باید از طریق Keystore امن و
        // متغیرهای محیطی CI انجام شود، نه فایل‌های commit‌شده.
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Material 3 APIs such as TopAppBar are experimental in the pinned
        // Compose Material 3 version; opt in at module scope so CI treats
        // their compiler diagnostics as warnings instead of errors.
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    // Firebase: anonymous identity is the basis for server-side credit ledgers.
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")

    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coil (نمایش تصاویر)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
