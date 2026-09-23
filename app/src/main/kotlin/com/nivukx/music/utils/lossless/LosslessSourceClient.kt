package com.nivukx.music.utils.lossless

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * BitChord-compatible lossless source client.
 *
 * Supports:
 *   /manifest.json
 *   /search?q=...&quality=LOSSLESS
 *   /stream/{id}?quality=LOSSLESS
 *
 * A search row may also contain streamURL, in which case the addon protocol
 * intentionally skips the extra /stream request.
 *
 * The older Nivukx JSON envelope remains accepted for backward compatibility.
 */
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
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = query.trim()
                require(trimmed.isNotBlank()) { "Cannot search a blank lossless query" }

                val manifest = loadManifest().getOrNull()
                val params = LinkedHashMap<String, String>()

                manifest
                    ?.settings
                    .orEmpty()
                    .forEach { setting ->
                        setting.key.takeIf { it.isNotBlank() }?.let { key ->
                            setting.defaultValue?.let { value -> params[key] = value }
                        }
                    }

                params["quality"] = selectLosslessQuality(
                    manifest?.settings.orEmpty(),
                )
                params["q"] = trimmed

                decodeSearch(get(endpoint("search", params))).tracks
                    .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            }
        }

    /**
     * Preferred stream resolution path. A BitChord addon may put streamURL
     * directly on the search row; use that before making another request.
     */
    suspend fun stream(track: LosslessTrack): Result<LosslessStream> {
        track.streamUrl?.trim()?.takeIf { it.isNotBlank() }?.let { url ->
            return Result.success(
                LosslessStream(
                    url = url,
                    format = track.format,
                    quality = "LOSSLESS",
                    audioQuality = track.audioQuality,
                ),
            )
        }

        return stream(track.id)
    }

    /**
     * Backward-compatible stream lookup for the old Nivukx envelope.
     */
    suspend fun stream(trackId: String): Result<LosslessStream> =
        requestJson(streamEndpoint(trackId))

    private fun selectLosslessQuality(settings: List<LosslessAddonSetting>): String {
        val options = settings
            .firstOrNull { it.key.equals("quality", ignoreCase = true) }
            ?.options
            .orEmpty()
            .mapNotNull { it.stringValue }
            .filter { it.isNotBlank() }

        if (options.isEmpty()) return QUALITY_LOSSLESS

        options.firstOrNull { it.equals(QUALITY_LOSSLESS, ignoreCase = true) }?.let { return it }

        val losslessWords = listOf(
            "lossless",
            "flac",
            "hifi",
            "hi-res",
            "hires",
            "max",
            "best",
        )
        return options.firstOrNull { option ->
            val lower = option.lowercase(Locale.ROOT)
            losslessWords.any { lower.contains(it) }
        } ?: QUALITY_LOSSLESS
    }

    private suspend fun loadManifest(): Result<LosslessAddonManifest> =
        requestJson(baseUrl + "/manifest.json")

    private fun streamEndpoint(trackId: String): String {
        val base = baseUrl.toHttpUrlOrNull() ?: error("Invalid lossless source URL")
        return base.newBuilder()
            .addPathSegment("stream")
            .addPathSegment(trackId)
            .addQueryParameter("quality", QUALITY_LOSSLESS)
            .build()
            .toString()
    }

    private fun endpoint(path: String, query: Map<String, String>): String {
        val base = baseUrl.toHttpUrlOrNull() ?: error("Invalid lossless source URL")
        val builder = base.newBuilder().addPathSegment(path)
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    private suspend inline fun <reified T> requestJson(url: String): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                json.decodeFromString<T>(get(url))
            }
        }

    private suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Lossless source HTTP " + response.code)
                }
                response.body?.string()
                    ?.takeIf { it.isNotBlank() }
                    ?: error("Lossless source returned an empty response")
            }
        }

    companion object {
        private const val USER_AGENT = "Nivukx-Lossless/2.0"
        private const val QUALITY_LOSSLESS = "LOSSLESS"

        fun normalizeBase(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isBlank()) return null

            val parsed = trimmed.toHttpUrlOrNull() ?: return null
            if (parsed.scheme.lowercase(Locale.ROOT) !in setOf("https", "http")) return null

            val path = parsed.encodedPath.trimEnd('/')
            val last = path.substringAfterLast('/')
            val normalizedPath =
                if (last.endsWith(".json", ignoreCase = true)) {
                    path.removeSuffix(last).trimEnd('/')
                } else {
                    path
                }

            return parsed.newBuilder()
                .encodedPath(normalizedPath)
                .build()
                .toString()
                .trimEnd('/')
        }
    }
}

@Serializable
data class LosslessAddonManifest(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("version") val version: String = "",
    @SerialName("resources") val resources: List<String> = emptyList(),
    @SerialName("settings") val settings: List<LosslessAddonSetting> = emptyList(),
)

@Serializable
data class LosslessAddonSetting(
    @SerialName("key") val key: String = "",
    @SerialName("default") val default: JsonElement? = null,
    @SerialName("options") val options: List<LosslessAddonSettingOption> = emptyList(),
) {
    val defaultValue: String?
        get() = default.asQueryValue()
}

@Serializable
data class LosslessAddonSettingOption(
    @SerialName("value") val value: JsonElement? = null,
) {
    val stringValue: String?
        get() = value.asQueryValue()
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
    val durationSeconds: Int?
        get() = duration?.takeIf { it > 0.0 }?.toInt()
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
    @SerialName("manifest") val manifest: String? = null,
    @SerialName("mediaType") val mediaType: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("encrypted") val encrypted: JsonElement? = null,
    @SerialName("sampleRate") val sampleRate: Double? = null,
    @SerialName("bitDepth") val bitDepth: Double? = null,
    @SerialName("bitrate") val bitrate: Double? = null,
    @SerialName("error") val error: String? = null,
) {
    val statedCodec: String?
        get() = (codec?.ifBlank { null } ?: fileCodec?.ifBlank { null })
            ?.lowercase(Locale.ROOT)

    val statedContainer: String?
        get() = (container?.ifBlank { null } ?: containerFormat?.ifBlank { null })
            ?.lowercase(Locale.ROOT)

    val qualityText: String
        get() = "$quality $streamQuality $audioQuality $format"

    val sampleRateHz: Int?
        get() =
            sampleRate
                ?.takeIf { it > 0.0 }
                ?.let { if (it < 1000.0) (it * 1000.0).toInt() else it.toInt() }
                ?: Regex("""([\d.]+)\s*kHz""", RegexOption.IGNORE_CASE)
                    .find(qualityText)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toDoubleOrNull()
                    ?.let { (it * 1000.0).toInt() }

    val bitrateKbps: Int?
        get() =
            bitrate
                ?.takeIf { it > 0.0 }
                ?.let { if (it > 3000.0) (it / 1000.0).toInt() else it.toInt() }
                ?: Regex("""(\d{2,4})\s*kbps""", RegexOption.IGNORE_CASE)
                    .find(qualityText)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()

    val mimeTypeOrDerived: String
        get() =
            mimeType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .takeUnless { it.isNullOrBlank() }
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
            val primitive = encrypted as? JsonPrimitive ?: return false
            val value = primitive.content
            return !value.equals("false", ignoreCase = true) &&
                !value.equals("none", ignoreCase = true) &&
                value.isNotBlank()
        }

    val transport: String?
        get() =
            (manifest?.ifBlank { null } ?: mediaType?.ifBlank { null } ?: format.ifBlank { null })
                ?.lowercase(Locale.ROOT)
                ?.let {
                    when (it) {
                        "hls", "m3u8", "application/x-mpegurl", "application/vnd.apple.mpegurl" -> "hls"
                        "dash", "mpd", "application/dash+xml" -> "dash"
                        else -> null
                    }
                }

    val isLossless: Boolean
        get() {
            val mime = mimeTypeOrDerived.lowercase(Locale.ROOT)
            val codec = statedCodec.orEmpty().lowercase(Locale.ROOT)
            val quality = qualityText.lowercase(Locale.ROOT)
            return mime.startsWith("audio/flac") ||
                mime.startsWith("audio/x-flac") ||
                mime.startsWith("audio/wav") ||
                mime.startsWith("audio/x-wav") ||
                mime.startsWith("audio/wave") ||
                mime.startsWith("audio/l16") ||
                mime.startsWith("audio/pcm") ||
                mime.startsWith("audio/x-alac") ||
                codec == "flac" || codec == "alac" || codec == "pcm" || codec == "l16" ||
                codec.contains("flac") || codec.contains("alac") || codec.contains("pcm") ||
                (quality.contains("lossless") && (
                    format.contains("flac", true) ||
                        format.contains("alac", true) ||
                        format.contains("pcm", true)
                    ))
        }
}

private fun JsonElement?.asQueryValue(): String? {
    if (this == null || this is JsonNull) return null
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.ifBlank { null }
}
