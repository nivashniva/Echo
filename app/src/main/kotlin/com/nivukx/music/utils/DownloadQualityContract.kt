package com.nivukx.music.utils

import android.content.Context
import com.nivukx.music.constants.AudioQuality
import com.nivukx.music.constants.DownloadQuality
import com.nivukx.music.constants.DownloadQualityKey

/**
 * Keeps the selected download quality attached to the DownloadRequest itself.
 *
 * Queued downloads must not silently switch quality when the global setting changes
 * before the worker starts.
 */
object DownloadQualityContract {
    private const val REQUEST_SEPARATOR = "::echoDownloadQuality="

    fun selectedDownloadQuality(context: Context): DownloadQuality {
        val stored = runCatching { context.dataStore.get(DownloadQualityKey) }.getOrNull()
        return DownloadQuality.entries.firstOrNull { it.name == stored } ?: DownloadQuality.AUTO
    }

    fun requestKey(context: Context, mediaId: String): String =
        requestKey(mediaId, selectedDownloadQuality(context))

    fun requestKey(mediaId: String, quality: DownloadQuality): String =
        mediaId + REQUEST_SEPARATOR + quality.name

    fun requestKey(mediaId: String, quality: AudioQuality): String =
        requestKey(mediaId, quality.toDownloadQuality())

    fun mediaIdFromRequestKey(key: String): String =
        key.substringBefore(REQUEST_SEPARATOR)

    fun qualityFromRequestKey(key: String): DownloadQuality? =
        key.substringAfter(REQUEST_SEPARATOR, "")
            .takeIf { it.isNotBlank() }
            ?.let { encoded ->
                DownloadQuality.entries.firstOrNull { it.name == encoded }
            }

    fun contentCacheKey(mediaId: String, quality: AudioQuality): String =
        mediaId + "_" + quality.name

    private fun AudioQuality.toDownloadQuality(): DownloadQuality =
        when (this) {
            AudioQuality.AUTO -> DownloadQuality.AUTO
            AudioQuality.HIGH -> DownloadQuality.HIGH
            AudioQuality.LOSSLESS_WHEN_AVAILABLE -> DownloadQuality.LOSSLESS_WHEN_AVAILABLE
            AudioQuality.OPUS -> DownloadQuality.YOUTUBE
        }
}
