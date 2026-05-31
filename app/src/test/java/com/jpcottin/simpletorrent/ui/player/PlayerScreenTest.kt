package com.jpcottin.simpletorrent.ui.player

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.nio.file.Files

class PlayerScreenTest {

    @Test
    fun findSubtitles_matchesLowercaseCodes() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            File(tempDir, "movie_en.srt").writeText("")
            File(tempDir, "movie_fr.srt").writeText("")

            val result = findSubtitles(tempDir)

            assertEquals(2, result.size)
            assertTrue(result.any { it.first == "en" })
            assertTrue(result.any { it.first == "fr" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitles_matchesUppercaseCodes() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            File(tempDir, "movie_EN.srt").writeText("")
            File(tempDir, "movie_FR.srt").writeText("")

            val result = findSubtitles(tempDir)

            assertEquals(2, result.size)
            assertTrue(result.any { it.first == "en" })
            assertTrue(result.any { it.first == "fr" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitles_fallsBackToCodeForUnknownLocale() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            File(tempDir, "movie_xx.srt").writeText("")

            val result = findSubtitles(tempDir)

            assertEquals(1, result.size)
            assertEquals("xx", result[0].second)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitles_ignoresNonSrtFiles() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            File(tempDir, "movie_en.srt").writeText("")
            File(tempDir, "movie_fr.txt").writeText("")
            File(tempDir, "readme.md").writeText("")

            val result = findSubtitles(tempDir)

            assertEquals(1, result.size)
            assertEquals("en", result[0].first)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitles_handlesEmptyDirectory() {
        val tempDir = Files.createTempDirectory("subtitles_test").toFile()
        try {
            val result = findSubtitles(tempDir)

            assertEquals(0, result.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findSubtitles_handlesNullDirectory() {
        val result = findSubtitles(null)

        assertEquals(0, result.size)
    }
}
