package com.nivukx.music.utils.lossless

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
        assertTrue(
            LosslessSourceClient.normalizeBase("https://example.test/manifest.json") ==
                "https://example.test",
        )
    }
}
