package com.datadog.android.core.internal.data.file

import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings()
class FileExtensionsTest {

    @TempDir
    lateinit var tempDir: File

    lateinit var prefix: String
    lateinit var suffix: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        prefix = forge.anAsciiString(size = forge.anInt(min = 2, max = 8))
        suffix = forge.anAsciiString(size = forge.anInt(min = 2, max = 8))
    }

    @Test
    fun `adds the suffix and prefix to the file ByteArray`(forge: Forge) {
        val file = File(tempDir, "testFile")
        file.createNewFile()
        val dataToWrite = forge.anAlphaNumericalString()
        file.writeText(dataToWrite)

        val readData = file.readBytes(prefix, suffix)
        assertThat(String(readData)).isEqualTo("$prefix$dataToWrite$suffix")
    }

    @Test
    fun `adds the suffix and prefix to an empty file`() {
        val file = File(tempDir, "testFile")
        file.createNewFile()

        val readData = file.readBytes(prefix, suffix)
        assertThat(String(readData)).isEqualTo("$prefix$suffix")
    }

    @Test
    fun `returns empty ByteArray if the file is too big`(forge: Forge) {
        val file = File(tempDir, "testFile")
        file.createNewFile()
        val spiedFile = spy(file)
        doReturn(Long.MAX_VALUE).whenever(spiedFile).length()

        val readData = spiedFile.readBytes(prefix, suffix)
        assertThat(readData).isEmpty()
    }
}
