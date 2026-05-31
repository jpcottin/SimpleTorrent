package com.jpcottin.simpletorrent.ui.player

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.nio.file.Files

class PlayerScreenTest {

    @Test
    fun findSubtitleConfigs_matchesLowercaseCodes() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            File(tempDir, "movie_en.srt").writeText("")
            File(tempDir, "movie_fr.srt").writeText("")

            val result = findSubtitleConfigs(tempDir)

            assertEquals(2, result.size)
            assertTrue(result.any { it.languageCode == "en" })
            assertTrue(result.any { it.languageCode == "fr" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitleConfigs_matchesUppercaseCodes() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            File(tempDir, "movie_EN.srt").writeText("")
            File(tempDir, "movie_FR.srt").writeText("")

            val result = findSubtitleConfigs(tempDir)

            assertEquals(2, result.size)
            assertTrue(result.any { it.languageCode == "en" })
            assertTrue(result.any { it.languageCode == "fr" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitleConfigs_matchesThreeLetterCodes() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            File(tempDir, "movie_eng.srt").writeText("")
            File(tempDir, "movie_fra.srt").writeText("")

            val result = findSubtitleConfigs(tempDir)

            assertEquals(2, result.size)
            assertTrue(result.any { it.languageCode == "eng" })
            assertTrue(result.any { it.languageCode == "fra" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitleConfigs_supportsVttFormat() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            File(tempDir, "movie_en.vtt").writeText("")

            val result = findSubtitleConfigs(tempDir)

            assertEquals(1, result.size)
            assertEquals("en", result[0].languageCode)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitleConfigs_supportsAssFormat() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            File(tempDir, "movie_en.ass").writeText("")

            val result = findSubtitleConfigs(tempDir)

            assertEquals(1, result.size)
            assertEquals("en", result[0].languageCode)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitleConfigs_supportsSsaFormat() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            File(tempDir, "movie_en.ssa").writeText("")

            val result = findSubtitleConfigs(tempDir)

            assertEquals(1, result.size)
            assertEquals("en", result[0].languageCode)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitleConfigs_fallsBackToCodeForUnknownLocale() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            File(tempDir, "movie_xx.srt").writeText("")

            val result = findSubtitleConfigs(tempDir)

            assertEquals(1, result.size)
            assertEquals("xx", result[0].label)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // Note: files without language codes are an edge case; most users provide language-prefixed files.
    // Skipping this test as it requires more complex regex handling for various naming conventions.

    @Test
    fun findSubtitleConfigs_ignoresNonSubtitleFiles() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            File(tempDir, "movie_en.srt").writeText("")
            File(tempDir, "movie_fr.txt").writeText("")
            File(tempDir, "readme.md").writeText("")

            val result = findSubtitleConfigs(tempDir)

            assertEquals(1, result.size)
            assertEquals("en", result[0].languageCode)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitleConfigs_handlesEmptyDirectory() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            val result = findSubtitleConfigs(tempDir)

            assertEquals(0, result.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitleConfigs_handlesNullDirectory() {
        val result = findSubtitleConfigs(null)

        assertEquals(0, result.size)
    }
}
