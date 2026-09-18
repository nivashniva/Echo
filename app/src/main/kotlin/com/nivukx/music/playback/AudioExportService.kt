package com.nivukx.music.playback

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.IBinder
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import androidx.datastore.preferences.core.edit
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.nivukx.innertube.YouTube
import com.nivukx.music.constants.AudioQuality
import com.nivukx.music.constants.AudioQualityKey
import com.nivukx.music.constants.ExportProgressKey
import com.nivukx.music.constants.ExportedSongIdsKey
import com.nivukx.music.constants.ExportingSongIdsKey
import com.nivukx.music.utils.YTPlayerUtils
import com.nivukx.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File

class AudioExportService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val songId = intent?.getStringExtra(EXTRA_SONG_ID) ?: return START_NOT_STICKY
        val songTitle = intent.getStringExtra(EXTRA_SONG_TITLE).orEmpty()
        val songArtist = intent.getStringExtra(EXTRA_SONG_ARTIST).orEmpty()
        val songAlbum = intent.getStringExtra(EXTRA_SONG_ALBUM).orEmpty()
        val artworkUrl = intent.getStringExtra(EXTRA_ARTWORK_URL).orEmpty()
        val targetDirectoryUri = intent.getStringExtra(EXTRA_TARGET_DIRECTORY_URI)
            ?: return START_NOT_STICKY

        serviceScope.launch {
            exportSong(songId, songTitle, songArtist, songAlbum, artworkUrl, targetDirectoryUri)
        }
        return START_NOT_STICKY
    }

    private suspend fun selectedAudioQuality(): AudioQuality {
        val stored = dataStore.data.first()[AudioQualityKey]
        return AudioQuality.entries.firstOrNull { it.name == stored } ?: AudioQuality.AUTO
    }

    private suspend fun exportSong(
        songId: String,
        songTitle: String,
        songArtist: String,
        songAlbum: String,
        artworkUrl: String,
        targetDirectoryUri: String,
    ) {
        val safeTitle = sanitizeTitle(songTitle.ifBlank { songId })
        addExportingSongId(songId)

        val tempSourceFile = File.createTempFile("export_source_", ".m4a", cacheDir)
        val tempArtworkFile = File.createTempFile("export_cover_", ".jpg", cacheDir)
        val tempMp3File = File.createTempFile("export_result_", ".mp3", cacheDir)

        try {
            val connectivityManager = getSystemService<ConnectivityManager>()
                ?: error("No connectivity manager")

            val playbackData = YTPlayerUtils.playerResponseForPlayback(
                videoId = songId,
                audioQuality = selectedAudioQuality(),
                connectivityManager = connectivityManager,
            ).getOrThrow()

            val year = fetchSongYear(songId)
            downloadStream(playbackData, tempSourceFile) { percent ->
                updateExportProgress(songId, percent)
            }

            val artworkDownloaded = downloadArtwork(artworkUrl, tempArtworkFile)
            convertToMp3(
                sourceFile = tempSourceFile,
                outputFile = tempMp3File,
                songTitle = songTitle,
                songArtist = songArtist,
                songAlbum = songAlbum,
                year = year,
                artworkFile = if (artworkDownloaded) tempArtworkFile else null,
            )
            writeOutputFile(safeTitle, targetDirectoryUri, tempMp3File)
            addExportedSongId(songId)
        } catch (e: Exception) {
            Timber.e(e, "Export failed for songId=$songId")
        } finally {
            tempSourceFile.delete()
            tempArtworkFile.delete()
            tempMp3File.delete()
            clearExportProgress(songId)
            removeExportingSongId(songId)
            stopSelf()
        }
    }

    private suspend fun fetchSongYear(songId: String): Int? =
        YouTube.getMediaInfo(songId)
            .getOrNull()
            ?.uploadDate
            ?.let { date -> date.filter { it.isDigit() }.take(4).toIntOrNull() }

    private suspend fun downloadStream(
        playbackData: YTPlayerUtils.PlaybackData,
        destFile: File,
        onProgress: suspend (Int) -> Unit = {},
    ) {
        val totalLength = playbackData.format.contentLength ?: 10_000_000L
        val rangedUrl = playbackData.streamUrl + "&range=0-" + totalLength
        val request = Request.Builder().url(rangedUrl).build()
        var totalBytes = -1L
        var bytesWritten = 0L
        var lastReportedPercent = -1

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Stream request failed with ${response.code}")
            val body = response.body ?: error("No response body")
            totalBytes = body.contentLength().takeIf { it > 0 } ?: totalLength
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            body.byteStream().use { input ->
                destFile.outputStream().use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesWritten += read
                        val percent = ((bytesWritten * 100L) / totalBytes).toInt().coerceIn(0, 99)
                        if (percent >= lastReportedPercent + 2) {
                            lastReportedPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
        }

        if (totalBytes > 0 && bytesWritten < totalBytes) {
            error("Incomplete export source: wrote $bytesWritten of $totalBytes bytes")
        }
    }

    private fun downloadArtwork(artworkUrl: String, destFile: File): Boolean {
        if (artworkUrl.isBlank()) return false
        return runCatching {
            httpClient.newCall(Request.Builder().url(artworkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@use
                response.body?.byteStream()?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }.isSuccess && destFile.length() > 0L
    }

    private fun convertToMp3(
        sourceFile: File,
        outputFile: File,
        songTitle: String,
        songArtist: String,
        songAlbum: String,
        year: Int?,
        artworkFile: File?,
    ) {
        val command = buildFfmpegCommand(
            sourceFile.absolutePath,
            outputFile.absolutePath,
            songTitle,
            songArtist,
            songAlbum,
            year,
            artworkFile?.absolutePath,
        )

        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode
        if (returnCode == null || !ReturnCode.isSuccess(returnCode)) {
            error("FFmpeg failed: ${session.output}")
        }
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            error("Exported MP3 file is empty")
        }
    }

    private fun writeOutputFile(
        safeTitle: String,
        targetDirectoryUri: String,
        sourceFile: File,
    ) {
        val uri = Uri.parse(targetDirectoryUri)

        if (uri.scheme == "file") {
            val folder = File(uri.path ?: error("Invalid export directory"))
            if (!folder.exists() && !folder.mkdirs()) {
                error("Unable to create export directory")
            }
            sourceFile.copyTo(File(folder, safeTitle + ".mp3"), overwrite = true)
            return
        }

        val destinationDir = DocumentFile.fromTreeUri(this, uri)
            ?: error("Export directory unavailable")
        val outputFile = destinationDir.createFile("audio/mpeg", safeTitle + ".mp3")
            ?: error("Unable to create output file")

        sourceFile.inputStream().use { input ->
            contentResolver.openOutputStream(outputFile.uri, "w")?.use { input.copyTo(it) }
                ?: error("Unable to open export output stream")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun addExportedSongId(songId: String) {
        dataStore.edit { preferences ->
            val current = preferences[ExportedSongIdsKey].orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            preferences[ExportedSongIdsKey] =
                (listOf(songId) + current.filterNot { it == songId })
                    .take(1000)
                    .joinToString(",")
        }
    }

    private suspend fun addExportingSongId(songId: String) {
        dataStore.edit { preferences ->
            val current = preferences[ExportingSongIdsKey].orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            preferences[ExportingSongIdsKey] =
                (listOf(songId) + current.filterNot { it == songId })
                    .take(1000)
                    .joinToString(",")
        }
    }

    private suspend fun removeExportingSongId(songId: String) {
        dataStore.edit { preferences ->
            preferences[ExportingSongIdsKey] = preferences[ExportingSongIdsKey]
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() && it != songId }
                .joinToString(",")
        }
    }

    private suspend fun updateExportProgress(songId: String, percent: Int) {
        dataStore.edit { preferences ->
            val progress = preferences[ExportProgressKey].orEmpty()
                .split(',')
                .filter { it.isNotBlank() }
                .associate {
                    val parts = it.split(':', limit = 2)
                    parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
                }
                .toMutableMap()
            progress[songId] = percent
            preferences[ExportProgressKey] =
                progress.map { it.key + ":" + it.value }.joinToString(",")
        }
    }

    private suspend fun clearExportProgress(songId: String) {
        dataStore.edit { preferences ->
            val progress = preferences[ExportProgressKey].orEmpty()
                .split(',')
                .filter { it.isNotBlank() }
                .associate {
                    val parts = it.split(':', limit = 2)
                    parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
                }
                .toMutableMap()
            progress.remove(songId)
            preferences[ExportProgressKey] =
                progress.map { it.key + ":" + it.value }.joinToString(",")
        }
    }

    companion object {
        private const val EXTRA_SONG_ID = "extra_song_id"
        private const val EXTRA_SONG_TITLE = "extra_song_title"
        private const val EXTRA_SONG_ARTIST = "extra_song_artist"
        private const val EXTRA_SONG_ALBUM = "extra_song_album"
        private const val EXTRA_ARTWORK_URL = "extra_artwork_url"
        private const val EXTRA_TARGET_DIRECTORY_URI = "extra_target_directory_uri"

        fun start(
            context: Context,
            songId: String,
            songTitle: String,
            songArtist: String,
            songAlbum: String,
            artworkUrl: String,
            targetDirectoryUri: String,
        ) {
            val intent = Intent(context, AudioExportService::class.java).apply {
                putExtra(EXTRA_SONG_ID, songId)
                putExtra(EXTRA_SONG_TITLE, songTitle)
                putExtra(EXTRA_SONG_ARTIST, songArtist)
                putExtra(EXTRA_SONG_ALBUM, songAlbum)
                putExtra(EXTRA_ARTWORK_URL, artworkUrl)
                putExtra(EXTRA_TARGET_DIRECTORY_URI, targetDirectoryUri)
            }
            context.startService(intent)
        }

        private fun sanitizeTitle(title: String): String {
            val invalid = setOf(':', '/', '*', '?', '"', '<', '>', '|', Char(92))
            val cleaned = buildString {
                title.forEach { char ->
                    append(if (char in invalid) '_' else char)
                }
            }.trim()
            return cleaned.ifBlank { "song_" + System.currentTimeMillis() }
        }

        private fun buildFfmpegCommand(
            inputPath: String,
            outputPath: String,
            title: String,
            artist: String,
            album: String,
            year: Int?,
            coverPath: String?,
        ): String {
            val escapedInput = inputPath.ffmpegEscape()
            val escapedOutput = outputPath.ffmpegEscape()
            val titleMeta = title.ffmpegEscape()
            val artistMeta = artist.ffmpegEscape()
            val albumMeta = album.ffmpegEscape()
            val yearFlags = year?.let {
                " -metadata date='" + it + "' -metadata year='" + it + "'"
            }.orEmpty()

            return if (coverPath != null) {
                val escapedCover = coverPath.ffmpegEscape()
                "-y -i '$escapedInput' -i '$escapedCover' -map 0:a -map 1:v " +
                    "-c:v mjpeg -disposition:v attached_pic -c:a libmp3lame " +
                    "-b:a 320k -id3v2_version 3 -metadata title='$titleMeta' " +
                    "-metadata artist='$artistMeta' -metadata album='$albumMeta'" +
                    yearFlags +
                    " -metadata:s:v title='Album cover' -metadata:s:v comment='Cover (front)' '$escapedOutput'"
            } else {
                "-y -i '$escapedInput' -c:a libmp3lame -b:a 320k -id3v2_version 3 " +
                    "-metadata title='$titleMeta' -metadata artist='$artistMeta' " +
                    "-metadata album='$albumMeta'$yearFlags '$escapedOutput'"
            }
        }

        private fun String.ffmpegEscape(): String {
            val quote = 39.toChar().toString()
            val slash = 92.toChar().toString()
            return replace(quote, quote + slash + quote + quote)
        }
    }
}
