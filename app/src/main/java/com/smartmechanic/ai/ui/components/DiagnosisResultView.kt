package com.smartmechanic.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smartmechanic.ai.data.model.DiagnosisResult
import com.smartmechanic.ai.data.model.Likelihood
import com.smartmechanic.ai.data.model.UrgencyLevel
import com.smartmechanic.ai.ui.theme.UrgencyDanger
import com.smartmechanic.ai.ui.theme.UrgencyNeedsCheck
import com.smartmechanic.ai.ui.theme.UrgencyNormal
import com.smartmechanic.ai.ui.theme.UrgencySerious
import com.smartmechanic.ai.ui.theme.UrgencySoon

fun urgencyColor(level: UrgencyLevel): Color = when (level) {
    UrgencyLevel.NORMAL -> UrgencyNormal
    UrgencyLevel.NEEDS_CHECK -> UrgencyNeedsCheck
    UrgencyLevel.SOON -> UrgencySoon
    UrgencyLevel.SERIOUS -> UrgencySerious
    UrgencyLevel.DANGER -> UrgencyDanger
}

fun urgencyLabelFa(level: UrgencyLevel): String = when (level) {
    UrgencyLevel.NORMAL -> "عادی"
    UrgencyLevel.NEEDS_CHECK -> "نیازمند بررسی"
    UrgencyLevel.SOON -> "بهتر است به‌زودی بررسی شود"
    UrgencyLevel.SERIOUS -> "جدی"
    UrgencyLevel.DANGER -> "احتمال خطر / رانندگی توصیه نمی‌شود"
}

fun likelihoodLabelFa(likelihood: Likelihood): String = when (likelihood) {
    Likelihood.LOW -> "احتمال کمتر"
    Likelihood.MEDIUM -> "احتمال متوسط"
    Likelihood.HIGH -> "محتمل‌تر"
}

@Composable
fun DiagnosisResultView(result: DiagnosisResult, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        item {
            SectionCard(title = "🔍 خلاصه مشکل") {
                Text(result.summary, style = MaterialTheme.typography.bodyLarge)
            }
        }

        item {
            SectionCard(title = "⚠️ موارد محتمل") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    result.possibleCauses.forEachIndexed { index, cause ->
                        Text("${index + 1}. ${cause.title} — ${likelihoodLabelFa(cause.likelihood)}")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = urgencyColor(result.urgency).copy(alpha = 0.12f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🚦 میزان فوریت", style = MaterialTheme.typography.titleMedium)
                    Text(urgencyLabelFa(result.urgency), color = urgencyColor(result.urgency))
                }
            }
        }

        item {
            SectionCard(title = "🛠️ اقدام پیشنهادی") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.recommendations.forEach { Text("• $it") }
                }
            }
        }

        item {
            SectionCard(title = "👨‍🔧 نیاز به مکانیک") {
                Text(if (result.mechanicNeeded) "بله، مراجعه به تعمیرکار پیشنهاد می‌شود." else "در حال حاضر نیازی فوری به مراجعه دیده نمی‌شود.")
            }
        }

        if (result.followUpQuestions.isNotEmpty()) {
            item {
                SectionCard(title = "❓ سؤالات تکمیلی") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        result.followUpQuestions.forEach { Text("• $it") }
                    }
                }
            }
        }

        val lowQualityNote = result.lowQualityInputNote
        if (!lowQualityNote.isNullOrBlank()) {
            item {
                SectionCard(title = "ℹ️ نکته کیفیت ورودی") {
                    Text(lowQualityNote)
                }
            }
        }

        item {
            SafetyWarningBanner(
                message = result.safetyWarning?.takeIf { it.isNotBlank() }
                    ?: "این نتیجه یک ارزیابی هوش مصنوعی است و تشخیص قطعی مکانیکی محسوب نمی‌شود. در صورت وجود علائم خطر، خودرو را متوقف کرده و با تعمیرکار متخصص تماس بگیرید."
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
