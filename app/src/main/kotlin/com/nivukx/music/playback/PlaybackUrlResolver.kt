package com.nivukx.music.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import com.nivukx.music.constants.AudioQuality
import com.nivukx.music.constants.LosslessSourceUrlKey
import com.nivukx.music.utils.YTPlayerUtils
import com.nivukx.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackUrlResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val losslessPlaybackResolver: LosslessPlaybackResolver,
) {
    private val connectivityManager =
        context.getSystemService<ConnectivityManager>()
            ?: error("ConnectivityManager unavailable")

    private data class Key(
        val videoId: String,
        val audioQuality: AudioQuality,
    )

    private data class CachedUrl(
        val playback: YTPlayerUtils.PlaybackData,
        val expiresAtMs: Long,
    )

    private val scope = CoroutineScope(
        SupervisorJob() + kotlinx.coroutines.Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_RESOLVES)
    )
    private val cache = ConcurrentHashMap<Key, CachedUrl>()
    private val inFlight = ConcurrentHashMap<Key, Deferred<Result<YTPlayerUtils.PlaybackData>>>()

    fun cached(videoId: String, audioQuality: AudioQuality): YTPlayerUtils.PlaybackData? {
        val key = Key(videoId, audioQuality)
        val entry = cache[key] ?: return null
        if (entry.expiresAtMs <= System.currentTimeMillis() + CACHE_SAFETY_WINDOW_MS) {
            cache.remove(key, entry)
            return null
        }

        // A lossy fallback must never poison the LOSSLESS_WHEN_AVAILABLE cache namespace.
        // Otherwise the first unavailable-lossless resolution would be replayed forever,
        // even after a genuine lossless format becomes available from another resolver/client.
        if (
            audioQuality == AudioQuality.LOSSLESS_WHEN_AVAILABLE &&
            entry.playback.actualAudioQuality != AudioQuality.LOSSLESS_WHEN_AVAILABLE
        ) {
            cache.remove(key, entry)
            return null
        }

        return entry.playback
    }

    fun prefetch(videoId: String, audioQuality: AudioQuality) {
        if (videoId.isBlank() || videoId.isLocalId()) return
        scope.launch {
            resolve(videoId, audioQuality)
        }
    }

    suspend fun resolve(
        videoId: String,
        audioQuality: AudioQuality,
    ): Result<YTPlayerUtils.PlaybackData> {
        cached(videoId, audioQuality)?.let {
            return Result.success<YTPlayerUtils.PlaybackData>(it)
        }

        val key = Key(videoId, audioQuality)

        // Strict lossless bypasses the YouTube audio resolver entirely. YouTube is
        // only consulted for recording identity/metadata by LosslessPlaybackResolver.
        // A configured lossless source is mandatory; there is no lossy fallback.
        if (audioQuality == AudioQuality.LOSSLESS_WHEN_AVAILABLE) {
            val configured = context.dataStore.data.first()[LosslessSourceUrlKey]
                ?.trim()
                ?.isNotBlank() == true
            check(configured) {
                "Lossless source is not configured. Configure a BitChord-compatible source before selecting Lossless."
            }
            return losslessPlaybackResolver.resolve(videoId).also { result ->
                result.getOrNull()?.let { playback ->
                    check(playback.actualAudioQuality == AudioQuality.LOSSLESS_WHEN_AVAILABLE)
                    check(YTPlayerUtils.isGenuinelyLosslessFormat(playback.format))
                    cache[key] = CachedUrl(
                        playback = playback,
                        expiresAtMs = System.currentTimeMillis() + playback.streamExpiresInSeconds * 1000L,
                    )
                }
            }
        }

        val deferred = inFlight[key] ?: synchronized(inFlight) {
            inFlight[key] ?: scope.async {
                YTPlayerUtils.playerResponseForPlayback(
                    videoId = videoId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                ).map { playback ->
                    check(playback.format.isAudio) {
                        "Playback resolver returned a non-audio format for $videoId"
                    }
                    check(playback.requestedAudioQuality == audioQuality) {
                        "Playback quality contract mismatch for $videoId: requested=$audioQuality, resolved=${playback.requestedAudioQuality}"
                    }

                    when (audioQuality) {
                        AudioQuality.LOSSLESS_WHEN_AVAILABLE -> {
                            check(playback.actualAudioQuality == AudioQuality.LOSSLESS_WHEN_AVAILABLE) {
                                "Lossless playback contract violated for $videoId: " +
                                    "resolved=${playback.actualAudioQuality} ${playback.format.mimeType} @ " +
                                    "${playback.format.bitrate}bps"
                            }
                        }

                        AudioQuality.OPUS ->
                            check(YTPlayerUtils.isGenuinelyOpusFormat(playback.format)) {
                                "Opus playback contract violated for $videoId"
                            }

                        AudioQuality.HIGH,
                        AudioQuality.AUTO -> {
                            check(playback.format.bitrate > 0) {
                                "High/Auto playback returned an invalid bitrate for $videoId"
                            }
                        }
                    }
                    val ttlSeconds =
                        playback.streamExpiresInSeconds.coerceAtLeast(MIN_STREAM_TTL_SECONDS)

                    // Never persist a compressed fallback in the lossless namespace.
                    // This keeps subsequent resolutions honest and lets a verified lossless
                    // stream replace an earlier unavailable result.
                    if (
                        audioQuality != AudioQuality.LOSSLESS_WHEN_AVAILABLE ||
                        playback.actualAudioQuality == AudioQuality.LOSSLESS_WHEN_AVAILABLE
                    ) {
                        cache[key] = CachedUrl(
                            playback = playback,
                            expiresAtMs = System.currentTimeMillis() + ttlSeconds * 1000L,
                        )
                    }
                    playback
                }
            }.also { inFlight[key] = it }
        }

        return try {
            deferred.await()
        } finally {
            if (deferred.isCompleted) {
                inFlight.remove(key, deferred)
            }
        }
    }

    fun resolveBlocking(
        videoId: String,
        audioQuality: AudioQuality,
    ): Result<YTPlayerUtils.PlaybackData> = runBlocking {
        resolve(videoId, audioQuality)
    }

    fun invalidate(videoId: String, audioQuality: AudioQuality? = null) {
        if (audioQuality != null) {
            val key = Key(videoId, audioQuality)
            cache.remove(key)
            inFlight.remove(key)
            return
        }
        cache.keys.removeIf { it.videoId == videoId }
        inFlight.keys.removeIf { it.videoId == videoId }
    }

    fun clear() {
        cache.clear()
        inFlight.clear()
    }

    private fun String.isLocalId(): Boolean = startsWith("local:")

    private companion object {
        const val MAX_CONCURRENT_RESOLVES = 4
        const val MIN_STREAM_TTL_SECONDS = 30
        const val CACHE_SAFETY_WINDOW_MS = 5_000L
    }
}
