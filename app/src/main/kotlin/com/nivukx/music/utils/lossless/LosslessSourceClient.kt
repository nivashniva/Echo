package com.nivukx.music.utils.lossless

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

class LosslessSourceClient(rawBaseUrl: String) {

    private val baseUrl = normalizeBase(rawBaseUrl)
        ?: throw IllegalArgumentException("Invalid lossless source URL")

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    suspend fun search(query: String): Result<List<LosslessTrack>> =
        requestJson<LosslessSearchResponse> {
            url(
                endpoint(
                    "search",
                    mapOf("q" to query, "quality" to TIER_LOSSLESS),
                ),
            )
        }.map { it.tracks.filter { track -> track.id.isNotBlank() && track.title.isNotBlank() } }

    suspend fun stream(trackId: String): Result<LosslessStream> =
        requestJson<LosslessStream> {
            url(streamEndpoint(trackId))
        }

    private suspend inline fun <reified T> requestJson(
        crossinline configure: Request.Builder.() -> Request.Builder,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val request = configure(
                Request.Builder()
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT),
            ).build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Lossless source HTTP " + response.code)
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) error("Lossless source returned an empty response")
                json.decodeFromString<T>(body)
            }
        }
    }

    private fun streamEndpoint(trackId: String): String {
        val base = baseUrl.toHttpUrlOrNull() ?: error("Invalid lossless source URL")
        return base.newBuilder()
            .addPathSegment("stream")
            .addPathSegment(trackId)
            .addQueryParameter("quality", TIER_LOSSLESS)
            .build()
            .toString()
    }

    private fun endpoint(path: String, query: Map<String, String>): String {
        val base = baseUrl.toHttpUrlOrNull() ?: error("Invalid lossless source URL")
        val builder = base.newBuilder().addPathSegment(path)
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    companion object {
        private const val USER_AGENT = "Nivukx-Lossless/1.0"
        private const val TIER_LOSSLESS = "LOSSLESS"

        fun normalizeBase(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isBlank()) return null
            val parsed = trimmed.toHttpUrlOrNull() ?: return null
            if (parsed.scheme.lowercase(Locale.ROOT) !in setOf("https", "http")) return null
            val path = parsed.encodedPath.trimEnd('/')
            val last = path.substringAfterLast('/')
            val normalizedPath =
                if (last.endsWith(".json", ignoreCase = true)) path.removeSuffix(last).trimEnd('/') else path
            return parsed.newBuilder()
                .encodedPath(normalizedPath)
                .build()
                .toString()
                .trimEnd('/')
        }
    }
}

@Serializable
data class LosslessSearchResponse(
    @SerialName("tracks") val tracks: List<LosslessTrack> = emptyList(),
)

@Serializable
data class LosslessTrack(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
    @SerialName("album") val album: String = "",
    @SerialName("duration") val duration: Double? = null,
    @SerialName("format") val format: String = "",
    @SerialName("audioQuality") val audioQuality: String = "",
    @SerialName("streamURL") val streamUrl: String? = null,
) {
    val durationSeconds: Int? get() = duration?.takeIf { it > 0.0 }?.toInt()
}

@Serializable
data class LosslessStream(
    @SerialName("url") val url: String = "",
    @SerialName("format") val format: String = "",
    @SerialName("quality") val quality: String = "",
    @SerialName("streamQuality") val streamQuality: String = "",
    @SerialName("audioQuality") val audioQuality: String = "",
    @SerialName("codec") val codec: String? = null,
    @SerialName("fileCodec") val fileCodec: String? = null,
    @SerialName("container") val container: String? = null,
    @SerialName("containerFormat") val containerFormat: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("encrypted") val encrypted: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("sampleRate") val sampleRate: Double? = null,
    @SerialName("bitDepth") val bitDepth: Double? = null,
    @SerialName("bitrate") val bitrate: Double? = null,
) {
    val statedCodec: String?
        get() = (codec?.ifBlank { null } ?: fileCodec?.ifBlank { null })?.lowercase(Locale.ROOT)

    val statedContainer: String?
        get() = (container?.ifBlank { null } ?: containerFormat?.ifBlank { null })?.lowercase(Locale.ROOT)

    val qualityText: String get() = "$quality $streamQuality $audioQuality $format"

    val sampleRateHz: Int?
        get() = sampleRate?.takeIf { it > 0.0 }?.let { if (it < 1000.0) (it * 1000.0).toInt() else it.toInt() }
            ?: Regex("""([\d.]+)\s*kHz""", RegexOption.IGNORE_CASE)
                .find(qualityText)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { (it * 1000.0).toInt() }

    val bitrateKbps: Int?
        get() = bitrate?.takeIf { it > 0.0 }?.let { if (it > 3000.0) (it / 1000.0).toInt() else it.toInt() }
            ?: Regex("""(\d{2,4})\s*kbps""", RegexOption.IGNORE_CASE)
                .find(qualityText)?.groupValues?.getOrNull(1)?.toIntOrNull()

    val mimeTypeOrDerived: String
        get() = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).takeUnless { it.isNullOrBlank() }
            ?: when {
                statedCodec?.contains("alac") == true -> "audio/x-alac"
                statedCodec?.contains("flac") == true -> "audio/flac"
                statedCodec?.contains("pcm") == true -> "audio/wav"
                statedContainer == "wav" || statedContainer == "wave" -> "audio/wav"
                statedContainer == "flac" -> "audio/flac"
                format.contains("flac", true) -> "audio/flac"
                format.contains("wav", true) || format.contains("pcm", true) -> "audio/wav"
                format.contains("alac", true) -> "audio/x-alac"
                else -> "application/octet-stream"
            }

    val isEncrypted: Boolean
        get() {
            val primitive = encrypted as? kotlinx.serialization.json.JsonPrimitive ?: return false
            val value = primitive.content
            return !value.equals("false", true) && !value.equals("none", true) && value.isNotBlank()
        }

    val isLossless: Boolean
        get() {
            val mime = mimeTypeOrDerived.lowercase(Locale.ROOT)
            val codec = statedCodec.orEmpty().lowercase(Locale.ROOT)
            return mime.startsWith("audio/flac") ||
                mime.startsWith("audio/x-flac") ||
                mime.startsWith("audio/wav") ||
                mime.startsWith("audio/x-wav") ||
                mime.startsWith("audio/wave") ||
                mime.startsWith("audio/l16") ||
                mime.startsWith("audio/pcm") ||
                mime.startsWith("audio/x-alac") ||
                codec == "flac" || codec == "alac" || codec == "pcm" || codec == "l16" ||
                codec.contains("flac") || codec.contains("alac") || codec.contains("pcm")
        }
}
