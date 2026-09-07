package com.khmercalendar.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Validating a downloaded bundle before anything tries to load it.
 *
 * A truncated or wrong file used to reach the native runtime, which fails with an error no
 * user could act on - and, before the manifest fix, could take the process with it. These
 * cover the cheap checks that keep it from getting that far.
 */
class ModelIntegrityTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun file(name: String, bytes: Int): File =
        folder.newFile(name).apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun `a file of the right size and shape is ok`() {
        assertEquals(ModelIntegrity.Ok, ModelStore.inspect(file("model.task", 1024), 1024))
    }

    @Test
    fun `a short file is partial and reports how far it got`() {
        val result = ModelStore.inspect(file("model.task", 400), 1024)
        assertTrue(result.toString(), result is ModelIntegrity.Partial)
        result as ModelIntegrity.Partial
        assertEquals(400L, result.downloadedBytes)
        assertEquals(1024L, result.totalBytes)
    }

    @Test
    fun `an over-long file is damaged, not partial`() {
        // Resuming past the end would corrupt it further, so this must not look resumable.
        val result = ModelStore.inspect(file("model.task", 2048), 1024)
        assertTrue(result.toString(), result is ModelIntegrity.Damaged)
    }

    @Test
    fun `content is not sniffed, only length`() {
        // `.task` bundles have no common signature - the Qwen ones are a length-prefixed zip,
        // the Gemma one a length-prefixed TFLite flatbuffer - so a right-sized file is
        // accepted whatever its bytes. An earlier magic-byte check rejected both real
        // catalogue files; failing valid input is worse than not checking.
        assertEquals(ModelIntegrity.Ok, ModelStore.inspect(file("model.task", 1024), 1024))
    }

    @Test
    fun `a missing or empty file is missing`() {
        assertEquals(
            ModelIntegrity.Missing,
            ModelStore.inspect(File(folder.root, "absent.task"), 1024),
        )
        assertEquals(ModelIntegrity.Missing, ModelStore.inspect(file("empty.task", 0), 1024))
    }

    @Test
    fun `download failures round-trip through their names`() {
        DownloadFailure.entries.forEach { failure ->
            assertEquals(failure, DownloadFailure.fromName(failure.name))
        }
        // Anything unrecognised, including a null from an absent output key, is UNKNOWN
        // rather than an exception - a failed download must not fail again on the way out.
        assertEquals(DownloadFailure.UNKNOWN, DownloadFailure.fromName(null))
        assertEquals(DownloadFailure.UNKNOWN, DownloadFailure.fromName("NOT_A_FAILURE"))
    }

    @Test
    fun `progress percentage is bounded and safe at zero total`() {
        assertEquals(0, DownloadProgress("m", 0, 0).percent)
        assertEquals(50, DownloadProgress("m", 512, 1024).percent)
        assertEquals(100, DownloadProgress("m", 1024, 1024).percent)
    }
}
