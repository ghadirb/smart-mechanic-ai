package com.smartmechanic.ai.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CriticalSafetyKeywordsTest {

    @Test
    fun `detects brake related critical term`() {
        assertThat(CriticalSafetyKeywords.containsCriticalTerm("مشکل ترمز دارم")).isTrue()
    }

    @Test
    fun `detects smoke related critical term`() {
        assertThat(CriticalSafetyKeywords.containsCriticalTerm("دود شدید از موتور بیرون می‌آید")).isTrue()
    }

    @Test
    fun `returns false for non-critical text`() {
        assertThat(CriticalSafetyKeywords.containsCriticalTerm("صدای خفیف از داشبورد می‌آید")).isFalse()
    }
}
