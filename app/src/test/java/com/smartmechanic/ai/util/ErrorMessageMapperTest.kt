package com.smartmechanic.ai.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ErrorMessageMapperTest {

    @Test
    fun `no internet maps to persian connectivity message`() {
        val msg = ErrorMessageMapper.toPersianMessage(AppError.NoInternet)
        assertThat(msg).contains("اینترنت")
    }

    @Test
    fun `timeout maps to persian timeout message`() {
        val msg = ErrorMessageMapper.toPersianMessage(AppError.Timeout)
        assertThat(msg).contains("زمان پاسخ")
    }

    @Test
    fun `missing api key maps to persian message`() {
        val msg = ErrorMessageMapper.toPersianMessage(AppError.MissingApiKey)
        assertThat(msg).contains("کلید")
    }

    @Test
    fun `file too large includes max size in message`() {
        val msg = ErrorMessageMapper.toPersianMessage(AppError.FileTooLarge(15))
        assertThat(msg).contains("15")
    }

    @Test
    fun `unsupported format includes format in message`() {
        val msg = ErrorMessageMapper.toPersianMessage(AppError.UnsupportedFormat("bmp"))
        assertThat(msg).contains("bmp")
    }

    @Test
    fun `api error includes code when present`() {
        val msg = ErrorMessageMapper.toPersianMessage(AppError.ApiError(code = 500, message = "internal"))
        assertThat(msg).contains("500")
    }

    @Test
    fun `unknown error returns generic persian message without crashing`() {
        val msg = ErrorMessageMapper.toPersianMessage(AppError.Unknown(RuntimeException("boom")))
        assertThat(msg).isNotEmpty()
    }
}
