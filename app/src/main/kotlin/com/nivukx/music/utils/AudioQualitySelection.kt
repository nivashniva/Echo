package com.nivukx.music.utils

import android.content.Context
import com.nivukx.music.R
import com.nivukx.music.constants.AudioQuality
import com.nivukx.music.constants.DownloadQuality

fun AudioQuality.userVisibleLabel(context: Context): String =
    when (this) {
        AudioQuality.AUTO -> context.getString(R.string.audio_quality_auto)
        AudioQuality.HIGH -> context.getString(R.string.audio_quality_high)
        AudioQuality.LOSSLESS_WHEN_AVAILABLE -> context.getString(R.string.audio_quality_lossless)
        AudioQuality.OPUS -> "Opus (legacy)"
    }

fun DownloadQuality.userVisibleLabel(context: Context): String =
    when (this) {
        DownloadQuality.AUTO -> context.getString(R.string.audio_quality_auto)
        DownloadQuality.HIGH -> context.getString(R.string.audio_quality_high)
        DownloadQuality.LOSSLESS_WHEN_AVAILABLE -> context.getString(R.string.audio_quality_lossless)
        DownloadQuality.YOUTUBE -> "YouTube Music (legacy)"
    }

fun DownloadQuality.toAudioQuality(): AudioQuality =
    when (this) {
        DownloadQuality.AUTO -> AudioQuality.AUTO
        DownloadQuality.HIGH -> AudioQuality.HIGH
        DownloadQuality.LOSSLESS_WHEN_AVAILABLE -> AudioQuality.LOSSLESS_WHEN_AVAILABLE
        DownloadQuality.YOUTUBE -> AudioQuality.OPUS
    }
