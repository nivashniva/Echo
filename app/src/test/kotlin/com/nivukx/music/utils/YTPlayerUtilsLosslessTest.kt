package com.nivukx.music.utils

import com.music.innertube.models.response.PlayerResponse
import com.nivukx.music.constants.AudioQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YTPlayerUtilsLosslessTest {

    private fun format(
        mimeType: String,
        bitrate: Int,
        audioQuality: String? = null,
    ) = PlayerResponse.StreamingData.Format(
        itag = bitrate,
        url = null,
        mimeType = mimeType,
        bitrate = bitrate,
        width = null,
        height = null,
        contentLength = null,
        quality = "AUDIO_QUALITY_MEDIUM",
        fps = null,
        qualityLabel = null,
        averageBitrate = bitrate,
        audioQuality = audioQuality,
        approxDurationMs = null,
        audioSampleRate = 48000,
        audioChannels = 2,
        loudnessDb = null,
        lastModified = null,
        signatureCipher = null,
        cipher = null,
        audioTrack = null,
    )

    @Test
    fun losslessSelectionNeverFallsBackToLossy() {
        val opus = format("audio/webm; codecs=\"opus\"", 256000)
        val flac = format("audio/flac; codecs=\"flac\"", 1411200)

        val selected = YTPlayerUtils.selectAudioFormat(
            listOf(opus, flac),
            AudioQuality.LOSSLESS_WHEN_AVAILABLE,
        )

        assertEquals(flac, selected)
        assertTrue(YTPlayerUtils.isGenuinelyLosslessFormat(flac))
        assertFalse(YTPlayerUtils.isGenuinelyLosslessFormat(opus))
    }

    @Test
    fun losslessSelectionReturnsNullWhenNoVerifiedLosslessStreamExists() {
        val opus = format("audio/webm; codecs=\"opus\"", 256000)
        val aac = format("audio/mp4; codecs=\"mp4a.40.2\"", 256000)

        val selected = YTPlayerUtils.selectAudioFormat(
            listOf(opus, aac),
            AudioQuality.LOSSLESS_WHEN_AVAILABLE,
        )

        assertEquals(null, selected)
    }

    @Test
    fun losslessDetectorAcceptsLosslessCodecs() {
        assertTrue(YTPlayerUtils.isGenuinelyLosslessFormat(format("audio/mp4; codecs=\"alac\"", 1200000)))
        assertTrue(YTPlayerUtils.isGenuinelyLosslessFormat(format("audio/l16", 1536000)))
        assertTrue(YTPlayerUtils.isGenuinelyLosslessFormat(format("audio/wav", 1411200)))
    }
}