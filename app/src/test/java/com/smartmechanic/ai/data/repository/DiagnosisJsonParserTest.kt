package com.smartmechanic.ai.data.repository

import com.google.common.truth.Truth.assertThat
import com.smartmechanic.ai.util.AppError
import com.smartmechanic.ai.util.AppResult
import org.junit.Test

class DiagnosisJsonParserTest {

    private val validJson = """
        {
          "summary": "صدای تق‌تق ممکن است ناشی از سوپاپ باشد.",
          "possibleCauses": [{"title": "سیستم سوپاپ", "likelihood": "MEDIUM"}],
          "urgency": "NEEDS_CHECK",
          "recommendations": ["روغن موتور را بررسی کنید"],
          "safetyWarning": "این تشخیص قطعی نیست.",
          "followUpQuestions": ["آیا صدا فقط سرد بودن موتور شنیده می‌شود؟"],
          "mechanicNeeded": true,
          "lowQualityInputNote": ""
        }
    """.trimIndent()

    @Test
    fun `parses valid json successfully`() {
        val result = DiagnosisJsonParser.parse(validJson)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val success = result as AppResult.Success
        assertThat(success.data.summary).contains("تق‌تق")
        assertThat(success.data.mechanicNeeded).isTrue()
    }

    @Test
    fun `parses json wrapped in markdown code fence`() {
        val wrapped = "```json\n$validJson\n```"
        val result = DiagnosisJsonParser.parse(wrapped)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `returns invalid response error for malformed json`() {
        val malformed = "این یک متن معمولی است، نه JSON"
        val result = DiagnosisJsonParser.parse(malformed)
        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = (result as AppResult.Error).error
        assertThat(error).isInstanceOf(AppError.InvalidAiResponse::class.java)
    }

    @Test
    fun `returns invalid response error when summary is blank`() {
        val blankSummary = """{"summary": "", "possibleCauses": [], "urgency": "NORMAL", "recommendations": []}"""
        val result = DiagnosisJsonParser.parse(blankSummary)
        assertThat(result).isInstanceOf(AppResult.Error::class.java)
    }
}
