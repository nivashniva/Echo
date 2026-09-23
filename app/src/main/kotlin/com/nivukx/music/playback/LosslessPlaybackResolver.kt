package com.nivukx.music.playback

import android.content.Context
import androidx.core.net.toUri
import com.music.innertube.models.response.PlayerResponse
import com.nivukx.music.constants.AudioQuality
import com.nivukx.music.constants.LosslessSourceUrlKey
import com.nivukx.music.utils.YTPlayerUtils
import com.nivukx.music.utils.dataStore
import com.nivukx.music.utils.lossless.LosslessSourceClient
import com.nivukx.music.utils.lossless.LosslessStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LosslessPlaybackResolver @Inject constructor(
    private val context: Context,
) {
    suspend fun resolve(videoId: String): Result<YTPlayerUtils.PlaybackData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val configuredSource = context.dataStore.data.first()[LosslessSourceUrlKey]
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: error(
                        "Lossless source is not configured. " +
                            "Open Settings > Player > Lossless source and add a BitChord-compatible source URL.",
                    )

                val metadata = YTPlayerUtils.playerResponseForMetadata(videoId).getOrThrow()
                val videoDetails = metadata.videoDetails ?: error("YouTube metadata is unavailable for " + videoId)

                val client = LosslessSourceClient(configuredSource)
                val query = buildString {
                    append(videoDetails.title.orEmpty())
                    videoDetails.author?.takeIf { it.isNotBlank() }?.let {
                        append(' ')
                        append(it)
                    }
                }.trim()

                if (query.isBlank()) error("Cannot resolve lossless source without track metadata")

                val candidates = client.search(query).getOrThrow()
                val match = matchRecording(
                    title = videoDetails.title.orEmpty(),
                    artist = videoDetails.author.orEmpty(),
                    durationSeconds = videoDetails.lengthSeconds.toIntOrNull(),
                    candidates = candidates,
                ) ?: error(
                    "No exact lossless recording found for '" +
                        videoDetails.title.orEmpty() +
                        "' by '" +
                        videoDetails.author.orEmpty() +
                        "'",
                )

                val stream = if (!match.streamUrl.isNullOrBlank()) {
                    LosslessStream(
                        url = match.streamUrl!!,
                        format = match.format,
                        audioQuality = match.audioQuality,
                    )
                } else {
                    client.stream(match.id).getOrThrow()
                }

                check(stream.url.isNotBlank()) { "Lossless source returned no stream URL" }
                check(!stream.isEncrypted) { "Lossless source returned encrypted media" }
                check(stream.isLossless) {
                    "Lossless source returned " + stream.mimeTypeOrDerived + ", which is not lossless"
                }

                val format = toMedia3Format(match, stream)
                check(YTPlayerUtils.isGenuinelyLosslessFormat(format)) {
                    "Lossless source verification rejected " + format.mimeType
                }

                YTPlayerUtils.PlaybackData(
                    audioConfig = metadata.playerConfig?.audioConfig,
                    videoDetails = metadata.videoDetails,
                    playbackTracking = metadata.playbackTracking,
                    format = format,
                    streamUrl = stream.url.toUri().toString(),
                    streamExpiresInSeconds = LOSSLESS_STREAM_TTL_SECONDS,
                    requestedAudioQuality = AudioQuality.LOSSLESS_WHEN_AVAILABLE,
                    actualAudioQuality = AudioQuality.LOSSLESS_WHEN_AVAILABLE,
                )
            }
        }

    private fun toMedia3Format(
        track: LosslessSourceClient.LosslessTrack,
        stream: LosslessSourceClient.LosslessStream,
    ): PlayerResponse.StreamingData.Format {
        val codec = stream.statedCodec ?: when {
            stream.mimeTypeOrDerived.startsWith("audio/x-alac") -> "alac"
            stream.mimeTypeOrDerived.startsWith("audio/wav") -> "pcm"
            else -> "flac"
        }

        val mime = when {
            stream.mimeTypeOrDerived.startsWith("audio/x-alac") -> "audio/x-alac"
            stream.mimeTypeOrDerived.startsWith("audio/wav") -> "audio/wav"
            stream.mimeTypeOrDerived.startsWith("audio/l16") -> "audio/l16"
            stream.mimeTypeOrDerived.startsWith("audio/pcm") -> "audio/pcm"
            else -> "audio/flac"
        }

        val bitrate = stream.bitrateKbps?.coerceAtLeast(1)?.times(1000) ?: 0

        return PlayerResponse.StreamingData.Format(
            itag = 1_000_000 + (track.id.hashCode() and 0x7FFF),
            url = stream.url,
            mimeType = "$mime; codecs=\"$codec\"",
            bitrate = bitrate,
            width = null,
            height = null,
            contentLength = null,
            quality = "LOSSLESS",
            fps = null,
            qualityLabel = null,
            averageBitrate = bitrate.takeIf { it > 0 },
            audioQuality = "LOSSLESS",
            approxDurationMs = track.durationSeconds?.times(1000L)?.toString(),
            audioSampleRate = stream.sampleRateHz,
            audioChannels = 2,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
            cipher = null,
            audioTrack = null,
        )
    }

    private fun matchRecording(
        title: String,
        artist: String,
        durationSeconds: Int?,
        candidates: List<LosslessSourceClient.LosslessTrack>,
    ): LosslessSourceClient.LosslessTrack? =
        candidates.asSequence()
            .map { candidate ->
                val titleScore = similarity(normalize(title), normalize(candidate.title))
                val artistScore = similarity(normalize(artist), normalize(candidate.artist))
                val durationScore = durationSeconds?.let { target ->
                    candidate.durationSeconds?.let { actual ->
                        when (kotlin.math.abs(target - actual)) {
                            in 0..2 -> 1.0
                            in 3..5 -> 0.7
                            in 6..10 -> 0.35
                            else -> 0.0
                        }
                    }
                } ?: 0.5
                candidate to (titleScore * 0.55 + artistScore * 0.30 + durationScore * 0.15)
            }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= MIN_MATCH_SCORE }
            ?.first

    private fun similarity(a: String, b: String): Double {
        if (a == b && a.isNotBlank()) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        val left = a.split(' ').filter { it.isNotBlank() }.toSet()
        val right = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return (left.intersect(right).size.toDouble() * 2.0) / (left.size + right.size)
    }

    private fun normalize(value: String): String =
        value
            .lowercase()
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""\b(feat|ft|featuring|official|audio|video|lyrics)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

    private companion object {
        const val LOSSLESS_STREAM_TTL_SECONDS = 3600
        const val MIN_MATCH_SCORE = 0.72
    }
}
