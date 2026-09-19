package com.nivukx.music.utils

import com.music.innertube.models.response.PlayerResponse
import com.nivukx.music.constants.AudioQuality
import com.nivukx.music.constants.DownloadQuality
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
    fun losslessSelectionPrefersLosslessWhenAvailable() {
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
    fun opusSelectionNeverFallsBackToAac() {
        val opus = format("audio/webm; codecs=\"opus\"", 160000)
        val aac = format("audio/mp4; codecs=\"mp4a.40.2\"", 256000)

        val selected = YTPlayerUtils.selectAudioFormat(
            listOf(aac, opus),
            AudioQuality.OPUS,
        )

        assertEquals(opus, selected)
        assertTrue(YTPlayerUtils.isGenuinelyOpusFormat(opus))
        assertFalse(YTPlayerUtils.isGenuinelyOpusFormat(aac))
    }

    @Test
    fun opusSelectionReturnsNullWhenOpusIsUnavailable() {
        val aac = format("audio/mp4; codecs=\"mp4a.40.2\"", 256000)

        val selected = YTPlayerUtils.selectAudioFormat(
            listOf(aac),
            AudioQuality.OPUS,
        )

        assertEquals(null, selected)
    }

    @Test
    fun highAndAutoSelectTheBestAvailableAudioFormat() {
        val medium = format("audio/webm; codecs=\"opus\"", 160000)
        val high = format("audio/mp4; codecs=\"mp4a.40.2\"", 256000)

        assertEquals(
            high,
            YTPlayerUtils.selectAudioFormat(
                listOf(medium, high),
                AudioQuality.HIGH,
            ),
        )
        assertEquals(
            high,
            YTPlayerUtils.selectAudioFormat(
                listOf(medium, high),
                AudioQuality.AUTO,
            ),
        )
    }

    @Test
    fun downloadQualityMapsToItsIndependentAudioContract() {
        assertEquals(AudioQuality.AUTO, DownloadQuality.AUTO.toAudioQuality())
        assertEquals(AudioQuality.HIGH, DownloadQuality.HIGH.toAudioQuality())
        assertEquals(
            AudioQuality.LOSSLESS_WHEN_AVAILABLE,
            DownloadQuality.LOSSLESS_WHEN_AVAILABLE.toAudioQuality(),
        )
        assertEquals(AudioQuality.OPUS, DownloadQuality.YOUTUBE.toAudioQuality())
    }

    @Test
    fun losslessSelectionFallsBackToBestAvailableWhenUnavailable() {
        val opus = format("audio/webm; codecs=\"opus\"", 256000)
        val aac = format("audio/mp4; codecs=\"mp4a.40.2\"", 320000)

        val selected = YTPlayerUtils.selectAudioFormat(
            listOf(opus, aac),
            AudioQuality.LOSSLESS_WHEN_AVAILABLE,
        )

        assertEquals(aac, selected)
        assertFalse(YTPlayerUtils.isGenuinelyLosslessFormat(selected!!))
    }

    @Test
    fun resolvedQualityReflectsActualStreamClass() {
        val flac = format("audio/flac; codecs=\"flac\"", 1411200)
        val opus = format("audio/webm; codecs=\"opus\"", 256000)

        assertEquals(
            AudioQuality.LOSSLESS_WHEN_AVAILABLE,
            YTPlayerUtils.resolvedAudioQuality(flac),
        )
        assertEquals(
            AudioQuality.OPUS,
            YTPlayerUtils.resolvedAudioQuality(opus),
        )
    }

    @Test
    fun audioSelectionReturnsNullForEmptyFormatList() {
        assertEquals(
            null,
            YTPlayerUtils.selectAudioFormat(emptyList(), AudioQuality.AUTO),
        )
    }

    @Test
    fun losslessDetectorAcceptsLosslessCodecs() {
        assertTrue(YTPlayerUtils.isGenuinelyLosslessFormat(format("audio/mp4; codecs=\"alac\"", 1200000)))
        assertTrue(YTPlayerUtils.isGenuinelyLosslessFormat(format("audio/l16", 1536000)))
        assertTrue(YTPlayerUtils.isGenuinelyLosslessFormat(format("audio/wav", 1411200)))
    }
}
