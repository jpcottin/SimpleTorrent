package com.jpcottin.simpletorrent.data

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleTorrentsTest {

    private fun queryParams(magnet: String): List<Pair<String, String>> =
        magnet.substringAfter("magnet:?")
            .split("&")
            .map { param ->
                val (key, value) = param.split("=", limit = 2)
                key to URLDecoder.decode(value, "UTF-8")
            }

    private fun trackersOf(magnet: String): List<String> =
        queryParams(magnet).filter { it.first == "tr" }.map { it.second }

    private fun infoHashOf(magnet: String): String {
        val xt = queryParams(magnet).single { it.first == "xt" }.second
        assertTrue("xt must be a btih urn: $xt", xt.startsWith("urn:btih:"))
        return xt.removePrefix("urn:btih:")
    }

    @Test
    fun `all samples have well-formed magnets`() {
        SAMPLE_TORRENTS.forEach { sample ->
            assertTrue("title blank", sample.title.isNotBlank())
            assertTrue("description blank for ${sample.title}", sample.description.isNotBlank())
            assertTrue("not a magnet: ${sample.title}", sample.magnet.startsWith("magnet:?"))
            val hash = infoHashOf(sample.magnet)
            assertEquals("bad v1 infohash length for ${sample.title}", 40, hash.length)
            assertTrue(
                "infohash not lowercase hex for ${sample.title}",
                hash.all { it in '0'..'9' || it in 'a'..'f' },
            )
            assertFalse("no trackers for ${sample.title}", trackersOf(sample.magnet).isEmpty())
        }
    }

    @Test
    fun `webtorrent sample announces only over wss trackers`() {
        val sample = SAMPLE_TORRENTS.single { it.title == "Sintel as WebTorrent" }
        val trackers = trackersOf(sample.magnet)
        assertTrue("expected multiple wss trackers", trackers.size >= 2)
        trackers.forEach { tr ->
            assertTrue("non-wss tracker in WebTorrent sample: $tr", tr.startsWith("wss://"))
        }
    }

    @Test
    fun `webtorrent sample is the same torrent as the plain sintel entry`() {
        val plain = SAMPLE_TORRENTS.single { it.title == "Sintel" }
        val web = SAMPLE_TORRENTS.single { it.title == "Sintel as WebTorrent" }
        assertEquals(infoHashOf(plain.magnet), infoHashOf(web.magnet))
    }

    @Test
    fun `non-sintel samples have distinct infohashes`() {
        val hashes = SAMPLE_TORRENTS
            .filterNot { it.title == "Sintel as WebTorrent" }
            .map { infoHashOf(it.magnet) }
        assertEquals(hashes.size, hashes.toSet().size)
    }
}
