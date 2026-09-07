package com.smartmechanic.ai.util

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class FileUtilsTest {

    private lateinit var tempFile: File

    @Before
    fun setup() {
        tempFile = File.createTempFile("test_image", ".jpg")
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun `validateSize returns null when file within limit`() {
        tempFile.writeBytes(ByteArray(100))
        val error = FileUtils.validateSize(tempFile, maxBytes = 1000)
        assertThat(error).isNull()
    }

    @Test
    fun `validateSize returns FileTooLarge when file exceeds limit`() {
        tempFile.writeBytes(ByteArray(2000))
        val error = FileUtils.validateSize(tempFile, maxBytes = 1000)
        assertThat(error).isInstanceOf(AppError.FileTooLarge::class.java)
    }

    @Test
    fun `isSupportedImage recognizes common image extensions`() {
        val jpg = File("photo.jpg")
        val png = File("photo.png")
        val exe = File("malware.exe")
        assertThat(FileUtils.isSupportedImage(jpg)).isTrue()
        assertThat(FileUtils.isSupportedImage(png)).isTrue()
        assertThat(FileUtils.isSupportedImage(exe)).isFalse()
    }

    @Test
    fun `mimeTypeForImage returns correct mime for png`() {
        assertThat(FileUtils.mimeTypeForImage(File("x.png"))).isEqualTo("image/png")
    }

    @Test
    fun `mimeTypeForAudio returns correct mime for wav`() {
        assertThat(FileUtils.mimeTypeForAudio(File("x.wav"))).isEqualTo("audio/wav")
    }
}
