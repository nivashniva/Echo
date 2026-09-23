package com.nivukx.music.utils.lossless

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LosslessSourceClientTest {

    @Test
    fun flacIsLossless() {
        val stream = LosslessStream(
            url = "https://example.test/song.flac",
            mimeType = "audio/flac",
            codec = "flac",
        )
        assertTrue(stream.isLossless)
    }

    @Test
    fun alacInsideMp4IsLosslessOnlyWhenCodecSaysAlac() {
        val alac = LosslessStream(
            url = "https://example.test/song.m4a",
            mimeType = "audio/mp4",
            codec = "alac",
        )
        val aac = LosslessStream(
            url = "https://example.test/song.m4a",
            mimeType = "audio/mp4",
            codec = "mp4a.40.2",
            quality = "LOSSLESS",
        )

        assertTrue(alac.isLossless)
        assertFalse(aac.isLossless)
    }

    @Test
    fun wavIsLossless() {
        val stream = LosslessStream(
            url = "https://example.test/song.wav",
            mimeType = "audio/wav",
            codec = "pcm",
        )
        assertTrue(stream.isLossless)
    }

    @Test
    fun compressedCodecsAreRejectedEvenWithLosslessLabel() {
        val stream = LosslessStream(
            url = "https://example.test/song.webm",
            mimeType = "audio/webm",
            codec = "opus",
            quality = "LOSSLESS",
            audioQuality = "HI-RES LOSSLESS",
        )
        assertFalse(stream.isLossless)
    }

    @Test
    fun addonStyleJsonFileUrlNormalizesToSourceRoot() {
        assertEquals(
            "https://example.test",
            LosslessSourceClient.normalizeBase("https://example.test/manifest.json"),
        )
    }

    @Test
    fun addonSearchRowCanCarryItsOwnDirectLosslessStream() {
        val track = LosslessTrack(
            id = "1",
            title = "Song",
            format = "flac",
            audioQuality = "LOSSLESS",
            streamUrl = "https://example.test/song.flac",
        )
        val stream = kotlinx.coroutines.runBlocking {
            LosslessSourceClient("https://example.test").stream(track).getOrNull()
        }

        assertEquals("https://example.test/song.flac", stream?.url)
        assertTrue(stream?.isLossless == true)
    }

    @Test
    fun addonManifestQualityOptionCanBeParsed() {
        val manifest = Json.decodeFromString<LosslessAddonManifest>(
            """
            {
              "id": "demo",
              "name": "Demo",
              "resources": ["search", "stream"],
              "settings": [
                {
                  "key": "quality",
                  "default": "HIGH",
                  "options": [
                    {"value": "HIGH"},
                    {"value": "LOSSLESS"}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("LOSSLESS", manifest.settings.first().options.last().stringValue)
    }
}
