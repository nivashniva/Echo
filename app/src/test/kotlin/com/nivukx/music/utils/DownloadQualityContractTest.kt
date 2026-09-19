package com.nivukx.music.utils

import com.nivukx.music.constants.AudioQuality
import com.nivukx.music.constants.DownloadQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadQualityContractTest {

    @Test
    fun requestKeyRoundTripsSelectedQualityWithoutChangingMediaId() {
        val mediaId = "test-video-id"
        val requestKey = DownloadQualityContract.requestKey(
            mediaId,
            DownloadQuality.LOSSLESS_WHEN_AVAILABLE,
        )

        assertEquals(mediaId, DownloadQualityContract.mediaIdFromRequestKey(requestKey))
        assertEquals(
            DownloadQuality.LOSSLESS_WHEN_AVAILABLE,
            DownloadQualityContract.qualityFromRequestKey(requestKey),
        )
    }

    @Test
    fun playbackAndDownloadUseTheSameQualityCacheNamespace() {
        val mediaId = "test-video-id"

        assertEquals(
            mediaId + "_HIGH",
            DownloadQualityContract.contentCacheKey(mediaId, AudioQuality.HIGH),
        )
        assertEquals(
            mediaId + "_LOSSLESS_WHEN_AVAILABLE",
            DownloadQualityContract.contentCacheKey(mediaId, AudioQuality.LOSSLESS_WHEN_AVAILABLE),
        )
        assertEquals(
            mediaId + "_OPUS",
            DownloadQualityContract.contentCacheKey(mediaId, AudioQuality.OPUS),
        )
    }
}
